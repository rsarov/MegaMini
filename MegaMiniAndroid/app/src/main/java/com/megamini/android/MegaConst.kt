package com.megamini.android

object MegaConst {
    const val BUFFER_SIZE = 64 * 1024
    const val APPLICATION_KEY = "axhQiYyQ"
    const val BASE_LINK = "https://g.api.mega.co.nz/cs"
    var sequenceIndex: UInt = (UInt.MAX_VALUE.toDouble() * Math.random()).toUInt()
}
