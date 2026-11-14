package com.dudeapp.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random

class BackupCryptoTest {
    private val salt = ByteArray(16) { it.toByte() }
    // Few iterations keep the tests quick; the app uses the default.
    private val key = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, 1_000)

    private fun seal(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(ByteArrayInputStream(plain), out, key, salt, 1_000)
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, with: ByteArray = key): ByteArray {
        val input = ByteArrayInputStream(sealed)
        val header = BackupCrypto.readHeader(input)
        val out = ByteArrayOutputStream()
        BackupCrypto.decrypt(input, header, with, out)
        return out.toByteArray()
    }

    @Test
    fun roundTripForSeveralSizes() {
        val chunk = 64 * 1024
        for (size in listOf(0, 1, 100, chunk - 1, chunk, chunk + 1, 3 * chunk, 3 * chunk + 777)) {
            val plain = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
            val sealed = seal(plain)
            assertTrue(BackupCrypto.isEncrypted(sealed))
            assertArrayEquals("size $size", plain, open(sealed))
        }
    }

    @Test
    fun theHeaderCarriesWhatIsNeededToDeriveTheKeyAgain() {
        val sealed = seal("hello".toByteArray())
        val header = BackupCrypto.readHeader(ByteArrayInputStream(sealed))
        val again = BackupCrypto.deriveKey("correct horse".toCharArray(), header.salt, header.iterations)
        assertArrayEquals("hello".toByteArray(), open(sealed, again))
    }

    @Test
    fun wrongPasswordIsReported() {
        val sealed = seal("secret".toByteArray())
        val other = BackupCrypto.deriveKey("wrong".toCharArray(), salt, 1_000)
        try {
            open(sealed, other)
            fail("expected a wrong-password error")
        } catch (_: WrongPasswordException) {
        }
    }

    @Test
    fun theContentIsNotReadableInTheFile() {
        val plain = "PK-this-would-be-a-zip-with-private-names".toByteArray()
        val sealed = String(seal(plain), Charsets.ISO_8859_1)
        assertFalse(sealed.contains("private-names"))
    }

    @Test
    fun changedOrShortenedFilesAreRejected() {
        val plain = ByteArray(200_000).also { Random(7).nextBytes(it) }
        val sealed = seal(plain)

        val flipped = sealed.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        try {
            open(flipped)
            fail("a changed file must not open")
        } catch (_: IOException) {
        }

        // Cut after the first whole chunk: every remaining byte is genuine, but the end is missing.
        val headerSize = BackupCrypto.headerPeekBytes + 4 + 16 + 16
        val firstChunk = headerSize + 1 + 4 + 64 * 1024 + 16
        try {
            open(sealed.copyOf(firstChunk))
            fail("a shortened file must not open")
        } catch (_: IOException) {
        }
    }

    @Test
    fun twoBackupsOfTheSameDataDiffer() {
        val plain = "same".toByteArray()
        assertFalse(seal(plain).contentEquals(seal(plain)))
    }

    @Test
    fun plainZipsAreNotMistakenForProtectedOnes() {
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf(0x50, 0x4b, 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf(1, 2)))
    }
}
