package app.overfit.data

import app.overfit.ai.ProviderId
import app.overfit.domain.Language
import app.overfit.domain.WeightEntry
import app.overfit.domain.WeightUnit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreTest {
    private val defaults = DeviceDefaults(Language.DE, WeightUnit.KG)

    @Test
    fun startsWithDeviceDefaults() {
        val store = AppStore(InMemoryKeyValueStore(), InMemorySecretStore(), DeviceDefaults(Language.EN, WeightUnit.LB))
        val state = store.state.value
        assertEquals(Language.EN, state.language)
        assertEquals(WeightUnit.LB, state.profile.unit)
        assertFalse(state.onboardingDone)
        assertFalse(state.hasApiKey)
    }

    @Test
    fun restTicksOnByDefaultAndPersisted() {
        val kv = InMemoryKeyValueStore()
        val secrets = InMemorySecretStore()
        val store = AppStore(kv, secrets, defaults)
        assertTrue(store.state.value.settings.restTicks)
        store.setRestTicks(false)
        assertFalse(AppStore(kv, secrets, defaults).state.value.settings.restTicks)
    }

    @Test
    fun olderStateWithoutRestTicksDecodesWithTicksOn() {
        val kv = InMemoryKeyValueStore()
        kv.put(
            "overfit.state.v1",
            """{"version":1,"profile":{"name":"Fabi"},"settings":{"language":"DE","vibration":false},"onboardingDone":true}""",
        )
        val settings = AppStore(kv, InMemorySecretStore(), defaults).state.value.settings
        assertFalse(settings.vibration)
        assertTrue(settings.restTicks)
    }

    @Test
    fun olderStateWithLightThemeStillDecodes_themeIsGone() {
        // Before "dark only" the settings stored a theme; it is ignored now and dropped on the next save.
        val kv = InMemoryKeyValueStore()
        kv.put(
            "overfit.state.v1",
            """{"version":1,"profile":{"name":"Fabi"},"settings":{"language":"EN","theme":"LIGHT","vibration":false},"onboardingDone":true}""",
        )
        val store = AppStore(kv, InMemorySecretStore(), defaults)
        assertEquals(Language.EN, store.state.value.language)
        assertFalse(store.state.value.settings.vibration)
        assertTrue(store.state.value.onboardingDone)
        store.setRestTicks(false)
        assertFalse(kv.get("overfit.state.v1")!!.contains("theme"))
    }

    @Test
    fun persistsAcrossInstances() {
        val kv = InMemoryKeyValueStore()
        val secrets = InMemorySecretStore()
        AppStore(kv, secrets, defaults).apply {
            setName("  Fabi ")
            setBirthYear(1995)
            logWeight(82.4, LocalDate(2026, 11, 2))
            setLanguage(Language.EN)
            connectAi(ProviderId.ANTHROPIC, "sk-ant-test")
            completeOnboarding()
        }

        val state = AppStore(kv, secrets, defaults).state.value
        assertEquals("Fabi", state.profile.name)
        assertEquals(1995, state.profile.birthYear)
        assertEquals(listOf(WeightEntry("2026-11-02", 82.4)), state.weights)
        assertEquals(Language.EN, state.language)
        assertEquals(ProviderId.ANTHROPIC, state.providerId)
        assertTrue(state.hasApiKey)
        assertTrue(state.onboardingDone)
    }

    @Test
    fun apiKeyNeverLandsInPlainStorage() {
        val kv = InMemoryKeyValueStore()
        AppStore(kv, InMemorySecretStore(), defaults).connectAi(ProviderId.OPENAI, "sk-secret-123")
        assertFalse(kv.get("overfit.state.v1").orEmpty().contains("sk-secret-123"))
    }

    @Test
    fun resetClearsEverything() {
        val kv = InMemoryKeyValueStore()
        val secrets = InMemorySecretStore()
        val store = AppStore(kv, secrets, defaults)
        store.setName("Fabi")
        store.connectAi(ProviderId.OPENAI, "sk-x")
        store.completeOnboarding()
        store.markCoachStorySeen()

        store.resetAll()

        assertFalse(store.coachStorySeen())
        assertEquals("", store.state.value.profile.name)
        assertFalse(store.state.value.onboardingDone)
        assertNull(secrets.load())
        assertNull(kv.get("overfit.state.v1"))
    }

    @Test
    fun corruptStorageFallsBackToDefaults() {
        val kv = InMemoryKeyValueStore().apply { put("overfit.state.v1", "{not json") }
        val state = AppStore(kv, InMemorySecretStore(), defaults).state.value
        assertEquals("", state.profile.name)
        assertEquals(Language.DE, state.language)
    }
}
