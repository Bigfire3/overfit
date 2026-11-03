package app.overfit.data

import app.overfit.data.backup.Zip
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipTest {
    @Test
    fun roundTripStoredAndDeflated() {
        val json = ("{\"a\":1}".repeat(2000)).encodeToByteArray()
        val photo = Random(1).nextBytes(50_000)
        val zip = Zip.write(listOf(Zip.Entry("backup.json", json, compress = true), Zip.Entry("photos/x.jpg", photo), Zip.Entry("empty", ByteArray(0))))
        val back = Zip.read(zip)
        assertEquals(listOf("backup.json", "photos/x.jpg", "empty"), back.keys.toList())
        assertContentEquals(json, back["backup.json"])
        assertContentEquals(photo, back["photos/x.jpg"])
        assertEquals(0, back.getValue("empty").size)
    }

    @Test
    fun brokenInputFailsCleanly() {
        val zip = Zip.write(listOf(Zip.Entry("a.txt", "hello".encodeToByteArray(), compress = true)))
        assertFailsWith<Zip.Error> { Zip.read(ByteArray(0)) }
        assertFailsWith<Zip.Error> { Zip.read("not a zip at all".encodeToByteArray()) }
        assertFailsWith<Zip.Error> { Zip.read(zip.copyOf(zip.size - 5)) }
        val corrupted = zip.copyOf().also { it[36] = (it[36] + 1).toByte() }
        assertFailsWith<Zip.Error> { Zip.read(corrupted) }
        // Random garbage never crashes with anything but Zip.Error.
        repeat(200) { i ->
            val junk = zip.copyOf().also { it[Random(i).nextInt(it.size)] = Random(i + 1).nextInt().toByte() }
            runCatching { Zip.read(junk) }.exceptionOrNull()?.let { assertEquals(Zip.Error::class, it::class) }
        }
    }
}
