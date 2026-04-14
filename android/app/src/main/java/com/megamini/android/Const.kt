package com.megamini.android

/**
 * Константы для Mega.nz API
 */
object Const {
    const val bufferSize: Int = 1024 * 64
    const val responseTimeout: Int = -1 // Бесконечный таймаут
    const val applicationKey: String = "axhQiYyQ"
    const val baseLink: String = "https://g.api.mega.co.nz/cs"
    var sequenceIndex: UInt = (UInt.MAX_VALUE * Math.random()).toUInt()
}
