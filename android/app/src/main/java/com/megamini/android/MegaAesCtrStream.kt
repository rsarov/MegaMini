package com.megamini.android

import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Поток для расшифровки AES-CTR с проверкой MetaMac
 */
class MegaAesCtrStreamDecrypter(
    stream: InputStream,
    streamLength: Long,
    fileKey: ByteArray,
    iv: ByteArray,
    private val expectedMetaMac: ByteArray
) : MegaAesCtrStream(stream, streamLength, Mode.DECRYPT, fileKey, iv) {

    init {
        require(expectedMetaMac.size == 8) { "Invalid expectedMetaMac" }
    }

    override fun onStreamRead() {
        if (!metaMac.contentEquals(expectedMetaMac)) {
            throw Exception("MetaMac verification failed")
        }
    }
}

/**
 * Абстрактный класс для AES-CTR потока
 */
abstract class MegaAesCtrStream(
    protected val stream: InputStream,
    protected val streamLength: Long,
    mode: Mode,
    protected val fileKey: ByteArray,
    protected val iv: ByteArray
) : InputStream() {

    protected val metaMac = ByteArray(8)
    protected var position: Long = 0L

    private val chunksPositionsCache: HashSet<Long>
    private val counter = ByteArray(8)
    private val encryptor: AesEncryptor
    private var currentCounter: Long = 0
    private var currentChunkMac = ByteArray(16)
    private var fileMac = ByteArray(16)
    private val mode: Mode

    enum class Mode {
        CRYPT,
        DECRYPT
    }

    protected val chunksPositions: LongArray

    init {
        require(fileKey.size == 16) { "Invalid fileKey" }
        require(iv.size == 8) { "Invalid Iv" }

        this.mode = mode
        chunksPositions = getChunksPositions(streamLength).toLongArray()
        chunksPositionsCache = chunksPositions.toHashSet()
        encryptor = Utils.createAesEncryptor(fileKey)
    }

    override fun close() {
        super.close()
        // encryptor не требует явного освобождения в Java
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (position == streamLength) {
            return -1
        }

        if (position + count < streamLength && count < 16) {
            throw UnsupportedOperationException(
                "Invalid count argument. Minimal read operation must be greater than 16 bytes (except for last read operation)."
            )
        }

        // Validate count boundaries
        val actualCount = if (position + count < streamLength) {
            count - (count % 16) // Make count divisible by 16 for partial reads
        } else {
            count
        }

        var pos = position
        while (pos < minOf(position + actualCount, streamLength)) {
            // We are on a chunk boundary
            if (chunksPositionsCache.contains(pos)) {
                if (pos != 0L) {
                    // Compute the current chunk mac data on each chunk boundary
                    computeChunk()
                }

                // Init chunk mac with Iv values
                for (i in 0..7) {
                    currentChunkMac[i] = iv[i]
                    currentChunkMac[i + 8] = iv[i]
                }
            }

            incrementCounter()

            // Iterate each AES 16 bytes block
            val input = ByteArray(16)
            var inputLength = stream.read(input)
            if (inputLength != 16) {
                // Sometimes, the stream is not finished but the read is not complete
                while (inputLength < 16) {
                    val additionalRead = stream.read(input, inputLength, 16 - inputLength)
                    if (additionalRead == -1) break
                    inputLength += additionalRead
                }
            }

            // Merge Iv and counter
            val ivCounter = ByteArray(16)
            System.arraycopy(iv, 0, ivCounter, 0, 8)
            System.arraycopy(counter, 0, ivCounter, 8, 8)

            val encryptedIvCounter = Utils.encryptAes(ivCounter, encryptor)

            val output = ByteArray(inputLength)
            for (inputPos in 0 until inputLength) {
                output[inputPos] = (encryptedIvCounter[inputPos].toInt() xor input[inputPos].toInt()).toByte()
                currentChunkMac[inputPos] = if (mode == Mode.CRYPT) {
                    (currentChunkMac[inputPos].toInt() xor input[inputPos].toInt()).toByte()
                } else {
                    (currentChunkMac[inputPos].toInt() xor output[inputPos].toInt()).toByte()
                }
            }

            // Copy to buffer
            val copyLength = minOf(output.size.toLong(), streamLength - pos).toInt()
            System.arraycopy(output, 0, buffer, (offset + pos - position).toInt(), copyLength)

            // Crypt to current chunk mac
            currentChunkMac = Utils.encryptAes(currentChunkMac, encryptor)

            pos += 16
        }

        val len = minOf(actualCount.toLong(), streamLength - position).toInt()
        position += len

        // When stream is fully processed, we compute the last chunk
        if (position == streamLength) {
            computeChunk()

            // Compute Meta MAC
            for (i in 0..3) {
                metaMac[i] = (fileMac[i].toInt() xor fileMac[i + 4].toInt()).toByte()
                metaMac[i + 4] = (fileMac[i + 8].toInt() xor fileMac[i + 12].toInt()).toByte()
            }

            onStreamRead()
        }

        return if (len == 0 && position < streamLength) -1 else len
    }

    override fun read(): Int {
        val buffer = ByteArray(1)
        return if (read(buffer, 0, 1) == 1) buffer[0].toInt() and 0xFF else -1
    }

    protected open fun onStreamRead() {
        // По умолчанию ничего не делаем
    }

    private fun incrementCounter() {
        if ((currentCounter and 0xFF) != 0xFFL && (currentCounter and 0xFF) != 0x00L) {
            // Fast path - no wrapping.
            counter[7]++
        } else {
            val counterBytes = Long.equals(currentCounter).padStart(16, '0')
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
            
            System.arraycopy(counterBytes, 0, counter, 0, 8)
        }

        currentCounter++
    }

    private fun computeChunk() {
        for (i in 0..15) {
            fileMac[i] = (fileMac[i].toInt() xor currentChunkMac[i].toInt()).toByte()
        }

        fileMac = Utils.encryptAes(fileMac, encryptor)
    }

    private fun getChunksPositions(size: Long): Sequence<Long> = sequence {
        yield(0L)

        var chunkStartPosition = 0L
        var idx = 1
        while (idx <= 8 && chunkStartPosition < (size - (idx * 131072L))) {
            chunkStartPosition += idx * 131072L
            yield(chunkStartPosition)
            idx++
        }

        while ((chunkStartPosition + 1048576L) < size) {
            chunkStartPosition += 1048576L
            yield(chunkStartPosition)
        }
    }
}
