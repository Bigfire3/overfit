package app.overfit.chat

import app.overfit.data.InMemoryKeyValueStore
import app.overfit.fuel.FuelException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncTest {
    private class Rig(scope: TestScope, val source: FakeSource = FakeSource(), val chat: FakeChat = FakeChat(), val kv: InMemoryKeyValueStore = InMemoryKeyValueStore()) {
        var now = 1_000L
        val sync = ChatSync({ chat }, kv, source, scope, { now }, throttleMs = 10_000)
    }

    @Test
    fun firstUploadResetsInFirstPackageAndSplitsIn500() = runTest {
        val rig = Rig(this)
        rig.source.workouts = (1..1100).map { record("w$it", it * 1000L) }
        rig.source.templates = listOf(template())
        rig.sync.enable(photos = false)
        val calls = rig.chat.calls
        assertEquals(listOf(true, false, false), calls.map { it.reset })
        assertEquals(listOf(500, 500, 102), calls.map { it.upserts.size + it.deletes.size }) // 1 profile + 1 template + 1100 workouts
        assertEquals(false, rig.chat.enabledWith)
        assertTrue(rig.sync.state.value.enabled)
        assertEquals(1_762_128_500_000, rig.sync.state.value.lastSyncAt)
    }

    @Test
    fun incrementalSendsOnlyChangedWorkoutAfterThrottleWindow() = runTest {
        val rig = Rig(this)
        rig.source.workouts = listOf(record("a", 1000), record("b", 2000))
        rig.sync.enable(false)
        rig.chat.calls.clear()

        rig.source.workouts = listOf(record("a", 1000), record("b", 2000, sets = listOf(app.overfit.domain.SetValues(70.0, 5, null))))
        rig.sync.markWorkout("b")
        rig.sync.markWorkout("b") // bundled
        advanceTimeBy(9_000)
        assertTrue(rig.chat.calls.isEmpty())
        advanceTimeBy(2_000)
        advanceUntilIdle()
        assertEquals(1, rig.chat.calls.size)
        assertEquals(listOf("workout/b"), rig.chat.calls[0].upserts.map { it.key })
        assertFalse(rig.chat.calls[0].reset)
    }

    @Test
    fun flushSendsImmediatelyAndUnchangedDocumentsAreSkipped() = runTest {
        val rig = Rig(this)
        rig.source.workouts = listOf(record("a", 1000, ended = false))
        rig.sync.enable(false)
        rig.chat.calls.clear()
        rig.sync.markWorkout("a") // nothing changed
        rig.sync.flush()
        advanceUntilIdle()
        assertTrue(rig.chat.calls.isEmpty())
        rig.source.workouts = listOf(record("a", 1000, ended = true))
        rig.sync.markWorkout("a")
        rig.sync.flush()
        advanceTimeBy(1)
        assertEquals(1, rig.chat.calls.size)
    }

    @Test
    fun deletionsGoThroughDeletes() = runTest {
        val rig = Rig(this)
        rig.source.workouts = listOf(record("a", 1000), record("b", 2000))
        rig.source.templates = listOf(template(3))
        rig.sync.enable(false)
        rig.chat.calls.clear()
        rig.source.workouts = listOf(record("a", 1000))
        rig.source.templates = emptyList()
        rig.sync.markWorkout("b")
        rig.sync.markTemplates()
        rig.sync.flush()
        advanceUntilIdle()
        val deletes = rig.chat.calls.single().deletes.map { it.key }.toSet()
        assertEquals(setOf("workout/b", "template/3"), deletes)
    }

    @Test
    fun offlineFailureKeepsStateAndNextRunSendsTheDifference() = runTest {
        val rig = Rig(this)
        rig.sync.enable(false)
        rig.chat.calls.clear()
        rig.source.workouts = listOf(record("a", 1000))
        rig.chat.failWith = FuelException("network")
        rig.sync.markWorkout("a")
        rig.sync.flush()
        advanceUntilIdle()
        assertEquals(ChatSyncProblem.OFFLINE, rig.sync.state.value.problem)
        assertTrue(rig.chat.calls.isEmpty())

        rig.chat.failWith = null
        rig.sync.flush() // e.g. app back in the foreground
        advanceUntilIdle()
        assertEquals(ChatSyncProblem.NONE, rig.sync.state.value.problem)
        assertEquals(listOf("workout/a"), rig.chat.calls.single().upserts.map { it.key })
    }

    @Test
    fun quotaExceededIsReported() = runTest {
        val rig = Rig(this)
        rig.sync.enable(false)
        rig.chat.failWith = FuelException("sync_quota_exceeded", 413)
        rig.source.workouts = listOf(record("a", 1))
        rig.sync.markWorkout("a")
        rig.sync.flush()
        advanceUntilIdle()
        assertEquals(ChatSyncProblem.QUOTA, rig.sync.state.value.problem)
    }

    @Test
    fun failedFirstUploadRetriesWithReset() = runTest {
        val rig = Rig(this)
        rig.chat.failWith = FuelException("network")
        rig.sync.enable(false)
        assertEquals(ChatSyncProblem.OFFLINE, rig.sync.state.value.problem)
        rig.chat.failWith = null
        rig.sync.flush()
        advanceUntilIdle()
        assertTrue(rig.chat.calls.first().reset)
    }

    @Test
    fun disableForgetsEverythingAndStateSurvivesRestart() = runTest {
        val rig = Rig(this)
        rig.source.workouts = listOf(record("a", 1))
        rig.sync.enable(photos = true)
        val restarted = ChatSync({ rig.chat }, rig.kv, rig.source, this, { 5 })
        assertTrue(restarted.state.value.enabled)
        assertTrue(restarted.state.value.photos)
        rig.sync.disable()
        assertTrue(rig.chat.disabled)
        assertFalse(rig.sync.state.value.enabled)
        assertFalse(ChatSync({ rig.chat }, rig.kv, rig.source, this, { 5 }).state.value.enabled)
    }

    @Test
    fun photosUploadOnlyWithConsentAndAreDeletedWhenGone() = runTest {
        val off = Rig(this)
        off.source.photos = listOf(ChatPhotoRef("p1", 10, null))
        off.sync.enable(photos = false)
        assertTrue(off.chat.photoPuts.isEmpty())

        val on = Rig(this)
        on.source.photos = listOf(ChatPhotoRef("p1", 10, "w"))
        on.sync.enable(photos = true)
        assertEquals(listOf("p1"), on.chat.photoPuts)
        on.source.photos = emptyList()
        on.sync.flush()
        advanceUntilIdle()
        assertEquals(listOf("p1"), on.chat.photoDeletes)
    }
}
