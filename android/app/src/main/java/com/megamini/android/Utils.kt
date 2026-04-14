package com.megamini.android

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Утилиты для работы с Mega.nz
 */
object Utils {

    /**
     * Преобразовать base64 строку (URL-safe) в байты
     */
    fun fromBase64(data: String): ByteArray {
        val normalized = buildString {
            append(data)
            append("=".repeat((4 - data.length % 4) % 4))
        }.replace(",", "")

        return android.util.Base64.decode(
            normalized,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
    }

    /**
     * Извлечь ID и ключ из ссылки
     */
    fun getIdAndKeyFromLink(link: String): Pair<String, ByteArray>? {
        val regex = """/(?<type>(file|folder))/(?<id>[^#]+)#(?<key>[^$/]+)"""
            .toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(link) ?: return null

        val shareId = match.groups["id"]?.value ?: return null
        val decryptedKey = fromBase64(match.groups["key"]?.value ?: return null)

        return Pair(shareId, decryptedKey)
    }

    /**
     * POST запрос к API и возврат тела как строки
     */
    fun postRequest(url: String, data: String, contentType: MediaType): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val body = data.toRequestBody(contentType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP error: ${response.code}")
            }

            return response.body?.string()
                ?: throw IllegalStateException("Empty response body")
        }
    }

    /**
     * Расшифровать AES-CBC без IV
     */
    fun decryptAes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val ivSpec = IvParameterSpec(ByteArray(16))
        val keySpec = SecretKeySpec(key, "AES")

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Расшифровать ключ (массив блоков по 16 байт)
     */
    fun decryptKey(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)

        var idx = 0
        while (idx < data.size) {
            val block = data.copyOfRange(idx, minOf(idx + 16, data.size))
            val decryptedBlock = decryptAes(block, key)
            System.arraycopy(decryptedBlock, 0, result, idx, 16)
            idx += 16
        }

        return result
    }

    /**
     * Извлечь части из расшифрованного ключа
     */
    fun getPartsFromDecryptedKey(decryptedKey: ByteArray): Triple<ByteArray, ByteArray, ByteArray> {
        val iv = decryptedKey.copyOfRange(16, 24)
        val metaMac = decryptedKey.copyOfRange(24, 32)

        val fileKey = ByteArray(16)
        for (i in 0 until 16) {
            fileKey[i] = (decryptedKey[i].toInt() xor decryptedKey[i + 16].toInt()).toByte()
        }

        return Triple(iv, metaMac, fileKey)
    }

    /**
     * Получить имя файла из атрибутов
     */
    fun getName(attributes: ByteArray, nodeKey: ByteArray): String? {
        val decryptedAttributes = decryptAes(attributes, nodeKey)

        return try {
            val jsonWithPrefix = String(decryptedAttributes, Charsets.UTF_8)
            val json = jsonWithPrefix.removePrefix("MEGA")
                .substringBefore('\u0000')

            JSONObject(json).optString("n").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Создать шифратор AES для CTR режима
     */
    fun createAesEncryptor(key: ByteArray): AesEncryptor {
        return AesEncryptor(key)
    }

    /**
     * Шифровать блок данных
     */
    fun encryptAes(data: ByteArray, encryptor: AesEncryptor): ByteArray {
        return encryptor.transform(data)
    }
}

/**
 * Класс для AES шифрования (замена ICryptoTransform)
 */
class AesEncryptor(private val key: ByteArray) {

    fun transform(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val ivSpec = IvParameterSpec(ByteArray(16))
        val keySpec = SecretKeySpec(key, "AES")

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }
}
