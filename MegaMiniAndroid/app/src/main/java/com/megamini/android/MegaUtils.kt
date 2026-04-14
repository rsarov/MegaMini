package com.megamini.android

import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MegaUtils {
    fun fromBase64(data: String): ByteArray {
        var normalized = data
            .replace("-", "+")
            .replace("_", "/")
            .replace(",", "")
        val padLen = (4 - normalized.length % 4) % 4
        normalized += "=".repeat(padLen)
        return Base64.decode(normalized, Base64.DEFAULT)
    }

    fun toUtf8Bytes(data: String): ByteArray = data.toByteArray(StandardCharsets.UTF_8)

    fun decryptAesCbcNoPadding(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(data)
    }

    fun encryptAesCbcNoPadding(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(data)
    }

    fun decryptKey(data: ByteArray, key: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var idx = 0
        while (idx < data.size) {
            val block = data.copyOfRange(idx, idx + 16)
            val decrypted = decryptAesCbcNoPadding(block, key)
            System.arraycopy(decrypted, 0, out, idx, 16)
            idx += 16
        }
        return out
    }

    fun getPartsFromDecryptedKey(decryptedKey: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val iv = decryptedKey.copyOfRange(16, 24)
        val metaMac = decryptedKey.copyOfRange(24, 32)
        val fileKey = ByteArray(16)
        for (i in 0 until 16) {
            fileKey[i] = (decryptedKey[i].toInt() xor decryptedKey[i + 16].toInt()).toByte()
        }
        return Triple(iv, metaMac, fileKey)
    }

    fun getName(attributes: ByteArray, nodeKey: ByteArray): String {
        val decrypted = decryptAesCbcNoPadding(attributes, nodeKey)
        val raw = String(decrypted, StandardCharsets.UTF_8)
        val json = raw.removePrefix("MEGA").substringBefore('\u0000')
        return JSONObject(json).optString("n", "")
    }

    fun parseLink(link: String): Pair<String, ByteArray>? {
        val regex = Regex("/(file|folder)/([^#]+)#([^$/]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(link) ?: return null
        val shareId = match.groupValues[2]
        val key = fromBase64(match.groupValues[3])
        return shareId to key
    }

    fun postJson(url: String, body: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(toUtf8Bytes(body)) }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun openHttpStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        return BufferedInputStream(conn.inputStream)
    }

    fun readAll(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(MegaConst.BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    @Throws(GeneralSecurityException::class)
    fun computeMetaMacAndDecrypt(cipherText: ByteArray, key: ByteArray, iv: ByteArray): Pair<ByteArray, ByteArray> {
        val plain = ByteArray(cipherText.size)
        val fileMac = ByteArray(16)
        var currentChunkMac = ByteArray(16)
        var counter = 0L

        val chunkStarts = chunkPositions(cipherText.size.toLong()).toHashSet()

        var pos = 0
        while (pos < cipherText.size) {
            if (chunkStarts.contains(pos.toLong())) {
                if (pos != 0) {
                    xorInto(fileMac, currentChunkMac)
                    currentChunkMac = encryptAesCbcNoPadding(fileMac, key)
                    System.arraycopy(currentChunkMac, 0, fileMac, 0, 16)
                }
                currentChunkMac = ByteArray(16)
                for (i in 0 until 8) {
                    currentChunkMac[i] = iv[i]
                    currentChunkMac[i + 8] = iv[i]
                }
            }

            val blockLen = minOf(16, cipherText.size - pos)
            val ivCounter = ByteArray(16)
            System.arraycopy(iv, 0, ivCounter, 0, 8)
            val cBytes = longToBigEndian(counter)
            System.arraycopy(cBytes, 0, ivCounter, 8, 8)
            val keystream = encryptAesCbcNoPadding(ivCounter, key)

            for (i in 0 until blockLen) {
                val p = (cipherText[pos + i].toInt() xor keystream[i].toInt()).toByte()
                plain[pos + i] = p
                currentChunkMac[i] = (currentChunkMac[i].toInt() xor p.toInt()).toByte()
            }

            currentChunkMac = encryptAesCbcNoPadding(currentChunkMac, key)
            counter++
            pos += blockLen
        }

        xorInto(fileMac, currentChunkMac)
        val finalMac = encryptAesCbcNoPadding(fileMac, key)
        val metaMac = ByteArray(8)
        for (i in 0 until 4) {
            metaMac[i] = (finalMac[i].toInt() xor finalMac[i + 4].toInt()).toByte()
            metaMac[i + 4] = (finalMac[i + 8].toInt() xor finalMac[i + 12].toInt()).toByte()
        }

        return plain to metaMac
    }

    private fun chunkPositions(size: Long): Sequence<Long> = sequence {
        yield(0)
        var chunkStart = 0L
        for (idx in 1..8) {
            if (chunkStart >= (size - idx * 131072L)) break
            chunkStart += idx * 131072L
            yield(chunkStart)
        }
        while ((chunkStart + 1048576L) < size) {
            chunkStart += 1048576L
            yield(chunkStart)
        }
    }

    private fun xorInto(target: ByteArray, source: ByteArray) {
        for (i in target.indices) {
            target[i] = (target[i].toInt() xor source[i].toInt()).toByte()
        }
    }

    private fun longToBigEndian(value: Long): ByteArray {
        val out = ByteArray(8)
        for (i in 0 until 8) {
            out[7 - i] = ((value ushr (i * 8)) and 0xFF).toByte()
        }
        return out
    }
}
