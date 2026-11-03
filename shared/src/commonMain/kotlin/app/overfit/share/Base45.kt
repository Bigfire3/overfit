package app.overfit.share

/**
 * Base45 (RFC 9285): 2 bytes → 3 characters of the QR alphanumeric alphabet. A QR code stores such a character in
 * 5.5 bits, so binary data costs about 8.25 bits per byte instead of 8 + header overhead of a longer text, and the
 * symbol can use the compact alphanumeric mode.
 */
object Base45 {
    const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder(data.size * 3 / 2 + 2)
        var i = 0
        while (i + 1 < data.size) {
            val n = (data[i].toInt() and 0xFF) * 256 + (data[i + 1].toInt() and 0xFF)
            sb.append(ALPHABET[n % 45]).append(ALPHABET[n / 45 % 45]).append(ALPHABET[n / 2025])
            i += 2
        }
        if (i < data.size) {
            val n = data[i].toInt() and 0xFF
            sb.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return sb.toString()
    }

    /** Null for characters outside the alphabet, wrong length or values that do not fit in bytes. */
    fun decode(text: String): ByteArray? {
        if (text.length % 3 == 1) return null
        val out = ArrayList<Byte>(text.length * 2 / 3)
        var i = 0
        while (i < text.length) {
            val c = ALPHABET.indexOf(text[i])
            val d = ALPHABET.indexOf(text[i + 1])
            if (c < 0 || d < 0) return null
            if (i + 2 < text.length) {
                val e = ALPHABET.indexOf(text[i + 2])
                if (e < 0) return null
                val n = c + d * 45 + e * 2025
                if (n > 0xFFFF) return null
                out.add((n / 256).toByte()); out.add((n % 256).toByte())
                i += 3
            } else {
                val n = c + d * 45
                if (n > 0xFF) return null
                out.add(n.toByte())
                i += 2
            }
        }
        return out.toByteArray()
    }
}
