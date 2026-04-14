package com.megamini.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Основной класс для работы с Mega.nz API
 */
object MegaMini {

    /**
     * Получить список файлов из общей папки Mega.nz
     */
    fun getNodesFromLink(link: String): List<MegaFile>? {
        require(link.startsWith("https://mega.nz/folder/")) {
            "Link must be a valid folder share starting with /folder/. Use GetNodeFromLink() for file share"
        }

        val (shareId, decryptedKey) = Utils.getIdAndKeyFromLink(link)
            ?: return null

        val url = "${Const.baseLink}?n=$shareId" +
                "&id=${Const.sequenceIndex++ % UInt.MAX_VALUE}" +
                "&ak=${Const.applicationKey}"
        
        val data = """[{"c":1,"r":1,"a":"f"}]"""
        val requestStream = Utils.postRequest(url, data, "application/json".toMediaType())
        val json = requestStream.bufferedReader().readText()
        
        // Парсим JSON вручную без внешних библиотек
        val allF = parseJsonArray(json)
        val megaFiles = mutableListOf<MegaFile>()

        for (jToken in allF) {
            val id = jToken["h"]
            val serializedKey = jToken["k"]
            val type = jToken["t"]?.toIntOrNull() ?: continue
            val serializedAttributes = jToken["a"]

            if (id != null && serializedKey != null && serializedAttributes != null) {
                val cleanKey = serializedKey.split('/')[0]
                val splitPosition = cleanKey.indexOf(':')
                val encryptedKey = Utils.fromBase64(cleanKey.substring(splitPosition + 1))
                val fullKey = Utils.decryptKey(encryptedKey, decryptedKey)

                if (type == 0) {
                    val (iv, metaMac, fileKey) = Utils.getPartsFromDecryptedKey(fullKey)
                    val name = Utils.getName(Utils.fromBase64(serializedAttributes), fileKey)
                    megaFiles.add(MegaFile(id, name, shareId, iv, metaMac, fileKey))
                }
            }
        }

        return megaFiles
    }

    /**
     * Скачать файл
     */
    fun download(megaFile: MegaFile): java.io.InputStream? {
        val url = "${Const.baseLink}?n=${megaFile.shareId}" +
                "&id=${Const.sequenceIndex++ % UInt.MAX_VALUE}" +
                "&ak=${Const.applicationKey}"
        
        val dataRequest = """[{"g":1,"n":"${megaFile.id}","a":"g"}]"""
        val requestStream = Utils.postRequest(url, dataRequest, "application/json".toMediaType())
        val json = requestStream.bufferedReader().readText()
        
        val fileUrl = parseDownloadUrl(json) ?: return null
        val size = parseFileSize(json)

        val client = OkHttpClient.Builder()
            .connectTimeout(Const.responseTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(Const.responseTimeout, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(fileUrl)
            .build()

        val response = client.newCall(request).execute()
        val fileStream = response.body?.byteStream() ?: return null

        val bufferedStream = BufferedStream(fileStream)
        
        if (megaFile.key != null && megaFile.iv != null && megaFile.metaMac != null) {
            return MegaAesCtrStreamDecrypter(bufferedStream, size, megaFile.key, megaFile.iv, megaFile.metaMac)
        }
        
        return null
    }

    // Простой парсер JSON для базовых нужд
    private fun parseJsonArray(json: String): List<Map<String, String?>> {
        val result = mutableListOf<Map<String, String?>>()
        
        // Ищем все объекты в массиве
        var depth = 0
        var start = -1
        
        for (i in json.indices) {
            when (json[i]) {
                '[' -> {
                    if (depth == 1) start = i
                    depth++
                }
                ']' -> {
                    depth--
                    if (depth == 1 && start != -1) {
                        val objStr = json.substring(start, i + 1)
                        parseJsonObject(objStr)?.let { result.add(it) }
                        start = -1
                    }
                }
            }
        }
        
        return result
    }

    private fun parseJsonObject(objStr: String): Map<String, String?>? {
        if (!objStr.startsWith("[{") && !objStr.startsWith("{")) return null
        
        val result = mutableMapOf<String, String?>()
        val content = objStr.trim('[', ']', ' ', '\n', '\r')
        
        // Извлекаем поля простым способом
        val patterns = listOf(
            "\"h\":\"([^\"]*)\"".toRegex(),
            "\"k\":\"([^\"]*)\"".toRegex(),
            "\"a\":\"([^\"]*)\"".toRegex(),
            "\"t\":([0-9]+)".toRegex(),
            "\"s\":([0-9]+)".toRegex(),
            "\"g\":\"([^\"]*)\"".toRegex()
        )
        
        for (pattern in patterns) {
            pattern.find(content)?.let { match ->
                val key = when {
                    match.groupValues[0].startsWith("\"h\"") -> "h"
                    match.groupValues[0].startsWith("\"k\"") -> "k"
                    match.groupValues[0].startsWith("\"a\"") -> "a"
                    match.groupValues[0].startsWith("\"t\"") -> "t"
                    match.groupValues[0].startsWith("\"s\"") -> "s"
                    match.groupValues[0].startsWith("\"g\"") -> "g"
                    else -> null
                }
                key?.let { result[it] = match.groupValues[1] }
            }
        }
        
        return if (result.isNotEmpty()) result else null
    }

    private fun parseDownloadUrl(json: String): String? {
        return "\"g\":\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)
    }

    private fun parseFileSize(json: String): Long {
        return "\"s\":([0-9]+)".toRegex().find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}

/**
 * Модель файла Mega.nz
 */
data class MegaFile(
    val id: String?,
    val name: String?,
    val shareId: String?,
    val iv: ByteArray?,
    val metaMac: ByteArray?,
    val key: ByteArray?
)
