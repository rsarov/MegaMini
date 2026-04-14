package com.megamini.android

import org.json.JSONArray

object MegaMiniClient {
    fun getNodesFromLink(link: String): List<MegaFile> {
        require(link.startsWith("https://mega.nz/folder/")) {
            "Link must start with /folder/."
        }

        val parsed = MegaUtils.parseLink(link) ?: return emptyList()
        val shareId = parsed.first
        val decryptedKey = parsed.second

        val url = "${MegaConst.BASE_LINK}?n=$shareId&id=${MegaConst.sequenceIndex++}&ak=${MegaConst.APPLICATION_KEY}"
        val response = MegaUtils.postJson(url, "[{\"c\":1,\"r\":1,\"a\":\"f\"}]")
        val array = JSONArray(response)
        val files = mutableListOf<MegaFile>()

        for (i in 0 until array.length()) {
            val fileArray = array.getJSONObject(i).optJSONArray("f") ?: continue
            for (j in 0 until fileArray.length()) {
                val item = fileArray.getJSONObject(j)
                if (item.optInt("t") != 0) continue

                val id = item.optString("h")
                val serializedKey = item.optString("k").substringBefore('/').substringAfter(':')
                val attrs = item.optString("a")

                val encryptedKey = MegaUtils.fromBase64(serializedKey)
                val fullKey = MegaUtils.decryptKey(encryptedKey, decryptedKey)
                val (iv, metaMac, fileKey) = MegaUtils.getPartsFromDecryptedKey(fullKey)
                val name = MegaUtils.getName(MegaUtils.fromBase64(attrs), fileKey)

                files += MegaFile(id, name, shareId, iv, metaMac, fileKey)
            }
        }

        return files
    }

    fun download(file: MegaFile): ByteArray {
        val url = "${MegaConst.BASE_LINK}?n=${file.shareId}&id=${MegaConst.sequenceIndex++}&ak=${MegaConst.APPLICATION_KEY}"
        val payload = "[{\"g\":1,\"n\":\"${file.id}\",\"a\":\"g\"}]"
        val response = JSONArray(MegaUtils.postJson(url, payload)).getJSONObject(0)

        val fileUrl = response.getString("g")
        val encrypted = MegaUtils.openHttpStream(fileUrl).use(MegaUtils::readAll)
        val (decrypted, metaMac) = MegaUtils.computeMetaMacAndDecrypt(encrypted, file.key, file.iv)

        require(metaMac.contentEquals(file.metaMac)) { "MetaMac check failed" }
        return decrypted
    }
}
