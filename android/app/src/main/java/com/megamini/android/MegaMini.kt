package com.megamini.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.FilterInputStream
import java.io.InputStream
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

        val json = Utils.postRequest(url, """[{"c":1,"r":1,"a":"f"}]""", "application/json".toMediaType())
        val responseArray = JSONArray(json)
        if (responseArray.length() == 0) return emptyList()

        val filesArray = responseArray.getJSONObject(0).optJSONArray("f") ?: return emptyList()
        val megaFiles = mutableListOf<MegaFile>()

        for (i in 0 until filesArray.length()) {
            val node = filesArray.optJSONObject(i) ?: continue
            val id = node.optString("h", null) ?: continue
            val serializedKey = node.optString("k", null) ?: continue
            val type = node.optInt("t", -1)
            val serializedAttributes = node.optString("a", null) ?: continue

            val cleanKey = serializedKey.substringBefore('/')
            val splitPosition = cleanKey.indexOf(':')
            if (splitPosition < 0 || splitPosition == cleanKey.lastIndex) continue

            val encryptedKey = Utils.fromBase64(cleanKey.substring(splitPosition + 1))
            val fullKey = Utils.decryptKey(encryptedKey, decryptedKey)

            if (type == 0) {
                val (iv, metaMac, fileKey) = Utils.getPartsFromDecryptedKey(fullKey)
                val name = Utils.getName(Utils.fromBase64(serializedAttributes), fileKey)
                megaFiles.add(MegaFile(id, name, shareId, iv, metaMac, fileKey))
            }
        }

        return megaFiles
    }

    /**
     * Скачать файл
     */
    fun download(megaFile: MegaFile): InputStream? {
        val key = megaFile.key ?: return null
        val iv = megaFile.iv ?: return null
        val metaMac = megaFile.metaMac ?: return null

        val url = "${Const.baseLink}?n=${megaFile.shareId}" +
            "&id=${Const.sequenceIndex++ % UInt.MAX_VALUE}" +
            "&ak=${Const.applicationKey}"

        val requestJson = Utils.postRequest(
            url,
            """[{"g":1,"n":"${megaFile.id}","a":"g"}]""",
            "application/json".toMediaType()
        )
        val nodeInfo = JSONArray(requestJson).optJSONObject(0) ?: return null

        val fileUrl = nodeInfo.optString("g").takeIf { it.isNotBlank() } ?: return null
        val size = nodeInfo.optLong("s", 0L)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(fileUrl).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP error while downloading: ${response.code}")
        }

        val fileStream = response.body?.byteStream() ?: run {
            response.close()
            return null
        }

        val bufferedStream = BufferedStream(ResponseBodyInputStream(fileStream, response))
        return MegaAesCtrStreamDecrypter(bufferedStream, size, key, iv, metaMac)
    }
}

private class ResponseBodyInputStream(
    source: InputStream,
    private val response: Response
) : FilterInputStream(source) {
    override fun close() {
        try {
            super.close()
        } finally {
            response.close()
        }
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
