package com.dudeapp.vault

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The password did not open the backup (or the file was altered). */
internal class WrongPasswordException : IOException("Wrong password")

/**
 * Password protection for backups. The finished zip is wrapped as a whole:
 *
 *     magic | iterations | password salt | file salt | chunks…
 *
 * The password is stretched once with PBKDF2-HMAC-SHA256 into a 256-bit key. Each file
 * then gets its own key (HMAC of that key and a random file salt), and the content is
 * sealed with AES-256-GCM in 64 KB chunks, so large vaults never sit in memory and any
 * change, reordering or cut-off of the file is detected.
 */
internal object BackupCrypto {
    const val EXTENSION = "pvault"
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PASSWORD_LENGTH = 6

    private val MAGIC = "PVAULT-ENC-1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private const val CHUNK = 64 * 1024
    private const val TAG_BYTES = 16
    private const val MAX_ITERATIONS = 5_000_000

    class Header(val iterations: Int, val salt: ByteArray, val fileSalt: ByteArray)

    val headerPeekBytes: Int get() = MAGIC.size

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** Slow on purpose (about a second on a phone); call it off the main thread. */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** True when [start] (the first bytes of a file) marks a password-protected backup. */
    fun isEncrypted(start: ByteArray): Boolean =
        start.size >= MAGIC.size && MAGIC.indices.all { start[it] == MAGIC[it] }

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        key: ByteArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ) {
        val fileSalt = newSalt()
        val out = DataOutputStream(output)
        out.write(MAGIC)
        out.writeInt(iterations)
        out.write(salt)
        out.write(fileSalt)

        val fileKey = SecretKeySpec(fileKey(key, fileSalt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var current = ByteArray(CHUNK)
        var next = ByteArray(CHUNK)
        var currentSize = readFully(input, current)
        var counter = 0L
        while (true) {
            // Read one chunk ahead so the last chunk can be marked as the last.
            val nextSize = if (currentSize < CHUNK) 0 else readFully(input, next)
            val last = nextSize == 0
            cipher.init(Cipher.ENCRYPT_MODE, fileKey, GCMParameterSpec(TAG_BYTES * 8, nonce(counter)))
            cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
            val sealed = cipher.doFinal(current, 0, currentSize)
            out.writeByte(if (last) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            val swap = current
            current = next
            next = swap
            currentSize = nextSize
            counter += 1
        }
        out.flush()
    }

    /** Reads the header; the stream must be at the very start of an encrypted backup. */
    fun readHeader(input: InputStream): Header {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(MAGIC.size)
            data.readFully(magic)
            if (!isEncrypted(magic)) throw IOException("Not a protected backup")
            val iterations = data.readInt()
            if (iterations !in 1..MAX_ITERATIONS) throw IOException("Damaged backup")
            val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
            val fileSalt = ByteArray(SALT_BYTES).also { data.readFully(it) }
            return Header(iterations, salt, fileSalt)
        } catch (_: EOFException) {
            throw IOException("Damaged backup")
        }
    }

    /**
     * Decrypts what follows the header. Throws [WrongPasswordException] when the key does
     * not fit, and [IOException] when the file is cut short.
     */
    fun decrypt(input: InputStream, header: Header, key: ByteArray, output: OutputStream) {
        val data = DataInputStream(input)
        val fileKey = SecretKeySpec(fileKey(key, header.fileSalt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var counter = 0L
        try {
            while (true) {
                val flag = data.readByte().toInt()
                val size = data.readInt()
                if (flag !in 0..1 || size !in TAG_BYTES..(CHUNK + TAG_BYTES)) throw IOException("Damaged backup")
                val sealed = ByteArray(size).also { data.readFully(it) }
                cipher.init(Cipher.DECRYPT_MODE, fileKey, GCMParameterSpec(TAG_BYTES * 8, nonce(counter)))
                cipher.updateAAD(byteArrayOf(flag.toByte()))
                val plain = try {
                    cipher.doFinal(sealed)
                } catch (_: GeneralSecurityException) {
                    throw WrongPasswordException()
                }
                output.write(plain)
                if (flag == 1) break
                counter += 1
            }
        } catch (_: EOFException) {
            throw IOException("The backup is incomplete")
        }
        output.flush()
    }

    private fun fileKey(key: ByteArray, fileSalt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(fileSalt)
    }

    /** Every file has its own key, so a plain chunk counter is a safe nonce. */
    private fun nonce(counter: Long): ByteArray {
        val bytes = ByteArray(12)
        for (i in 0 until 8) bytes[11 - i] = (counter ushr (8 * i)).toByte()
        return bytes
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }
}
