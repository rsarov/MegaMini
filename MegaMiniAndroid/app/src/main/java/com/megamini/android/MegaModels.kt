package com.megamini.android

data class MegaFile(
    val id: String,
    val name: String,
    val shareId: String,
    val iv: ByteArray,
    val metaMac: ByteArray,
    val key: ByteArray
)
