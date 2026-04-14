package com.example.megamini.mega

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MegaAesCtrDecryptingInputStream(
    private val source: InputStream,
    private val key: ByteArray,
    iv8: ByteArray
) : InputStream() {

    private val cipher: Cipher
    private var done = false

    init {
        val iv = ByteArray(16)
        iv8.copyInto(iv, 0, 0, 8)
        cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val read = read(one, 0, 1)
        return if (read <= 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (done) return -1
        val temp = ByteArray(len)
        val count = source.read(temp)
        if (count <= 0) {
            done = true
            return -1
        }
        val out = cipher.update(temp, 0, count)
        out.copyInto(b, off)
        return out.size
    }

    override fun close() {
        source.close()
    }
}
