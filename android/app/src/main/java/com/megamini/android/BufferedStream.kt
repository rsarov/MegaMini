package com.megamini.android

import java.io.InputStream

/**
 * Буферизированный поток для эффективного чтения
 */
class BufferedStream(private val innerStream: InputStream) : InputStream() {

    companion object {
        private const val BUFFER_SIZE = 65536
    }

    private val streamBuffer = ByteArray(BUFFER_SIZE)
    private var streamBufferDataStartIndex = 0
    private var streamBufferDataCount = 0

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        var totalReadCount = 0
        var currentOffset = offset
        var remainingCount = count

        while (true) {
            val copyCount = minOf(streamBufferDataCount, remainingCount)
            if (copyCount != 0) {
                System.arraycopy(streamBuffer, streamBufferDataStartIndex, buffer, currentOffset, copyCount)
                currentOffset += copyCount
                remainingCount -= copyCount
                streamBufferDataStartIndex += copyCount
                streamBufferDataCount -= copyCount
                totalReadCount += copyCount
            }

            if (remainingCount == 0) {
                break // Request has been filled.
            }

            require(streamBufferDataCount == 0) { "Buffer should be empty" }

            streamBufferDataStartIndex = 0
            streamBufferDataCount = 0

            fillBuffer()

            if (streamBufferDataCount == 0) {
                break // End of stream.
            }
        }

        return if (totalReadCount == 0 && streamBufferDataCount == 0) -1 else totalReadCount
    }

    override fun read(): Int {
        val buffer = ByteArray(1)
        return if (read(buffer, 0, 1) == 1) buffer[0].toInt() and 0xFF else -1
    }

    private fun fillBuffer() {
        while (true) {
            val startOfFreeSpace = streamBufferDataStartIndex + streamBufferDataCount

            val availableSpaceInBuffer = streamBuffer.size - startOfFreeSpace
            if (availableSpaceInBuffer == 0) {
                break // Buffer is full.
            }

            val readCount = innerStream.read(streamBuffer, startOfFreeSpace, availableSpaceInBuffer)
            if (readCount == -1 || readCount == 0) {
                break // End of stream.
            }

            streamBufferDataCount += readCount
        }
    }

    override fun close() {
        innerStream.close()
    }

    override fun available(): Int {
        return streamBufferDataCount + innerStream.available()
    }
}
