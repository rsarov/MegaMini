package com.example.megamini.mega

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.InputStream
import java.util.concurrent.TimeUnit

object MegaMiniClient {
    private val client = OkHttpClient.Builder()
        .apply {
            if (Const.responseTimeoutMs > 0) {
                callTimeout(Const.responseTimeoutMs, TimeUnit.MILLISECONDS)
            }
        }
        .build()

    fun getNodesFromLink(link: String): List<MegaFile> {
        require(link.startsWith("https://mega.nz/folder/")) {
            "Link must be a folder share"
        }

        val (shareId, decryptedKey) = Utils.getIdAndKeyFromLink(link)
        val url = "${Const.baseLink}?n=$shareId&id=${nextSequence()}&ak=${Const.applicationKey}"
        val json = postJson(url, "[{\"c\":1,\"r\":1,\"a\":\"f\"}]")
        val response = JSONArray(json)

        val files = mutableListOf<MegaFile>()
        for (i in 0 until response.length()) {
            val fArray = response.getJSONObject(i).optJSONArray("f") ?: continue
            for (j in 0 until fArray.length()) {
                val node = fArray.getJSONObject(j)
                val type = node.optInt("t", -1)
                if (type != 0) continue

                val id = node.optString("h", null) ?: continue
                val serializedKey = node.optString("k", null) ?: continue
                val attributes = node.optString("a", null) ?: continue

                val keyPart = serializedKey.substringBefore('/').substringAfter(':')
                val encryptedKey = Utils.fromBase64(keyPart)
                val fullKey = Utils.decryptKey(encryptedKey, decryptedKey)
                val (iv, metaMac, fileKey) = Utils.getPartsFromDecryptedKey(fullKey)
                val name = Utils.getName(Utils.fromBase64(attributes), fileKey)

                files += MegaFile(
                    id = id,
                    name = name,
                    shareId = shareId,
                    iv = iv,
                    metaMac = metaMac,
                    key = fileKey
                )
            }
        }
        return files
    }

    fun download(megaFile: MegaFile): InputStream {
        val url = "${Const.baseLink}?n=${megaFile.shareId}&id=${nextSequence()}&ak=${Const.applicationKey}"
        val body = "[{\"g\":1,\"n\":\"${megaFile.id}\",\"a\":\"g\"}]"
        val json = postJson(url, body)
        val array = JSONArray(json)
        val fileUrl = array.getJSONObject(0).getString("g")

        val request = Request.Builder().url(fileUrl).get().build()
        val response = client.newCall(request).execute()
        val stream = response.body?.byteStream() ?: error("Empty file response")

        return MegaAesCtrDecryptingInputStream(stream, megaFile.key, megaFile.iv)
    }

    private fun postJson(url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return response.body?.string() ?: error("Empty response")
        }
    }

    private fun nextSequence(): UInt {
        val current = Const.sequenceIndex
        Const.sequenceIndex = Const.sequenceIndex.inc()
        return current
    }
}
