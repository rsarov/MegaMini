package com.example.megamini.mega

import org.json.JSONObject
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Utils {
    fun fromBase64(data: String): ByteArray {
        val normalized = buildString {
            append(data)
            repeat((4 - data.length % 4) % 4) { append('=') }
        }
            .replace('-', '+')
            .replace('_', '/')
            .replace(",", "")
        return Base64.getDecoder().decode(normalized)
    }

    fun decryptAes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    fun decryptKey(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        var i = 0
        while (i < data.size) {
            val block = data.copyOfRange(i, i + 16)
            val decrypted = decryptAes(block, key)
            decrypted.copyInto(result, i)
            i += 16
        }
        return result
    }

    fun getPartsFromDecryptedKey(decryptedKey: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val iv = decryptedKey.copyOfRange(16, 24)
        val metaMac = decryptedKey.copyOfRange(24, 32)
        val fileKey = ByteArray(16)
        for (idx in 0 until 16) {
            fileKey[idx] = (decryptedKey[idx].toInt() xor decryptedKey[idx + 16].toInt()).toByte()
        }
        return Triple(iv, metaMac, fileKey)
    }

    fun getName(attributes: ByteArray, nodeKey: ByteArray): String? {
        val decrypted = decryptAes(attributes, nodeKey)
        val raw = decrypted.toString(Charsets.UTF_8)
        val withoutPrefix = raw.drop(4)
        val zeroIndex = withoutPrefix.indexOf('\u0000')
        val jsonString = if (zeroIndex >= 0) withoutPrefix.substring(0, zeroIndex) else withoutPrefix
        return JSONObject(jsonString).optString("n", null)
    }

    fun getIdAndKeyFromLink(link: String): Pair<String, ByteArray> {
        val regex = Regex("""/(?<type>(file|folder))/(?<id>[^#]+)#(?<key>[^$/]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(link) ?: error("Invalid MEGA link")
        val shareId = match.groups["id"]?.value ?: error("No id")
        val key = match.groups["key"]?.value ?: error("No key")
        return shareId to fromBase64(key)
    }
}
