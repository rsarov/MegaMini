package com.megamini.android

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Утилиты для работы с Mega.nz
 */
object Utils {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Преобразовать base64 строку (URL-safe) в байты
     */
    fun fromBase64(data: String): ByteArray {
        val sb = StringBuilder()
        sb.append(data)
        sb.append("=".repeat((4 - data.length % 4) % 4))
        sb.replace('-', '+')
        sb.replace('_', '/')
        sb.replace(",", "")

        return android.util.Base64.decode(sb.toString(), android.util.Base64.DEFAULT)
    }

    /**
     * Извлечь ID и ключ из ссылки
     */
    fun getIdAndKeyFromLink(link: String): Pair<String, ByteArray>? {
        val regex = """/(?<type>(file|folder))/(?<id>[^#]+)#(?<key>[^$/]+)""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(link) ?: return null
        
        val shareId = match.groups["id"]?.value ?: return null
        val decryptedKey = fromBase64(match.groups["key"]?.value ?: return null)
        
        return Pair(shareId, decryptedKey)
    }

    /**
     * POST запрос к API
     */
    fun postRequest(url: String, data: String, contentType: MediaType): java.io.InputStream {
        val client = OkHttpClient.Builder()
            .connectTimeout(Const.responseTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(Const.responseTimeout, TimeUnit.MILLISECONDS)
            .build()

        val body = data.toRequestBody(contentType)
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP error: ${response.code}")
        }
        
        return response.body?.byteStream() 
            ?: throw Exception("Empty response body")
    }

    /**
     * Расшифровать AES-CBC без IV
     */
    fun decryptAes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val ivSpec = IvParameterSpec(ByteArray(16)) // нулевой IV
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
        // Extract Iv and MetaMac
        val iv = decryptedKey.copyOfRange(16, 24)
        val metaMac = decryptedKey.copyOfRange(24, 32)

        // For files, key is 256 bits long. Compute the key to retrieve 128 AES key
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

        // Remove MEGA prefix (первые 4 байта)
        try {
            val json = String(decryptedAttributes, Charsets.UTF_8).drop(4)
            val nullTerminationIndex = json.indexOf('\u0000')
            val cleanJson = if (nullTerminationIndex != -1) {
                json.substring(0, nullTerminationIndex)
            } else {
                json
            }
            
            // Простой парсинг JSON для получения имени
            val nameMatch = "\"n\":\"([^\"]*)\"".toRegex().find(cleanJson)
            return nameMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
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
