package com.example.megamini.mega

import kotlin.random.Random

object Const {
    const val bufferSize = 1024 * 64
    const val responseTimeoutMs = 0L
    const val applicationKey = "axhQiYyQ"
    const val baseLink = "https://g.api.mega.co.nz/cs"
    var sequenceIndex: UInt = (UInt.MAX_VALUE.toDouble() * Random.nextDouble()).toUInt()
}
