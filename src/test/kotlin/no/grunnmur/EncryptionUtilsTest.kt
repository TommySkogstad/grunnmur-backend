package no.grunnmur

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EncryptionUtilsTest {

    // Testnokkel: 32 bytes = 64 hex-tegn
    private val testKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Nested
    inner class EncryptDecrypt {
        @Test
        fun `krypterer og dekrypterer korrekt`() {
            val plaintext = "Hemmelig data"
            val encrypted = EncryptionUtils.encrypt(plaintext, testKey)
            val decrypted = EncryptionUtils.decrypt(encrypted, testKey)
            assertEquals(plaintext, decrypted)
        }

        @Test
        fun `kryptert tekst er forskjellig fra plaintext`() {
            val plaintext = "Hemmelig data"
            val encrypted = EncryptionUtils.encrypt(plaintext, testKey)
            assertNotEquals(plaintext, encrypted)
        }

        @Test
        fun `to krypteringer av samme tekst gir forskjellig resultat (unik IV)`() {
            val plaintext = "Samme tekst"
            val encrypted1 = EncryptionUtils.encrypt(plaintext, testKey)
            val encrypted2 = EncryptionUtils.encrypt(plaintext, testKey)
            assertNotEquals(encrypted1, encrypted2)
            // Men begge dekrypterer til samme plaintext
            assertEquals(plaintext, EncryptionUtils.decrypt(encrypted1, testKey))
            assertEquals(plaintext, EncryptionUtils.decrypt(encrypted2, testKey))
        }

        @Test
        fun `haandterer norske tegn`() {
            val plaintext = "Aeoeaa AeOeAa blaabaer oel"
            val encrypted = EncryptionUtils.encrypt(plaintext, testKey)
            assertEquals(plaintext, EncryptionUtils.decrypt(encrypted, testKey))
        }

        @Test
        fun `haandterer tom streng`() {
            val encrypted = EncryptionUtils.encrypt("", testKey)
            assertEquals("", EncryptionUtils.decrypt(encrypted, testKey))
        }

        @Test
        fun `haandterer lang tekst`() {
            val plaintext = "A".repeat(10000)
            val encrypted = EncryptionUtils.encrypt(plaintext, testKey)
            assertEquals(plaintext, EncryptionUtils.decrypt(encrypted, testKey))
        }
    }

    @Nested
    inner class DecryptFailure {
        @Test
        fun `feil nokkel gir null`() {
            val encrypted = EncryptionUtils.encrypt("test", testKey)
            val wrongKey = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
            assertNull(EncryptionUtils.decrypt(encrypted, wrongKey))
        }

        @Test
        fun `ugyldig base64 gir null`() {
            assertNull(EncryptionUtils.decrypt("ikke-base64!!!", testKey))
        }

        @Test
        fun `for kort ciphertext gir null`() {
            assertNull(EncryptionUtils.decrypt("AAAA", testKey)) // under 12 bytes
        }

        @Test
        fun `korrupt data gir null`() {
            val encrypted = EncryptionUtils.encrypt("test", testKey)
            val corrupted = encrypted.dropLast(3) + "xxx"
            assertNull(EncryptionUtils.decrypt(corrupted, testKey))
        }
    }

    @Nested
    inner class KeyValidation {
        @Test
        fun `for kort nokkel kaster feil`() {
            assertThrows<IllegalArgumentException> {
                EncryptionUtils.encrypt("test", "abcdef")
            }
        }

        @Test
        fun `for lang nokkel kaster feil`() {
            assertThrows<IllegalArgumentException> {
                EncryptionUtils.encrypt("test", testKey + "ff")
            }
        }

        @Test
        fun `ugyldige hex-tegn kaster feil`() {
            val invalidKey = "g" + testKey.drop(1)
            assertThrows<IllegalArgumentException> {
                EncryptionUtils.encrypt("test", invalidKey)
            }
        }

        @Test
        fun `mellomrom og kolon i nokkel aksepteres`() {
            val keyWithSpaces = testKey.chunked(8).joinToString(" ")
            val encrypted = EncryptionUtils.encrypt("test", keyWithSpaces)
            assertEquals("test", EncryptionUtils.decrypt(encrypted, testKey))
        }
    }

    @Nested
    inner class GenerateKey {
        @Test
        fun `genererer nokkel med riktig lengde`() {
            val key = EncryptionUtils.generateKey()
            assertEquals(64, key.length)
        }

        @Test
        fun `genererte nokler er unike`() {
            val key1 = EncryptionUtils.generateKey()
            val key2 = EncryptionUtils.generateKey()
            assertNotEquals(key1, key2)
        }

        @Test
        fun `generert nokkel kan brukes til kryptering`() {
            val key = EncryptionUtils.generateKey()
            val encrypted = EncryptionUtils.encrypt("test", key)
            assertEquals("test", EncryptionUtils.decrypt(encrypted, key))
        }
    }

    @Nested
    inner class Base64KeyConversion {
        @Test
        fun `konverterer base64-nokkel til hex`() {
            // 32 bytes som base64
            val base64Key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
            val hexKey = EncryptionUtils.base64KeyToHex(base64Key)
            assertEquals(64, hexKey.length)
            assertTrue(hexKey.all { it in '0'..'9' || it in 'a'..'f' })
        }

        @Test
        fun `for kort base64-nokkel kaster feil`() {
            val shortKey = java.util.Base64.getEncoder().encodeToString(ByteArray(16))
            assertThrows<IllegalArgumentException> {
                EncryptionUtils.base64KeyToHex(shortKey)
            }
        }

        @Test
        fun `konvertert nokkel dekrypterer data kryptert med same bytes`() {
            val rawBytes = ByteArray(32) { (it * 7).toByte() }
            val hexKey = rawBytes.joinToString("") { "%02x".format(it) }
            val base64Key = java.util.Base64.getEncoder().encodeToString(rawBytes)

            val encrypted = EncryptionUtils.encrypt("hemmelig", hexKey)
            val convertedHexKey = EncryptionUtils.base64KeyToHex(base64Key)
            assertEquals(hexKey, convertedHexKey)
            assertEquals("hemmelig", EncryptionUtils.decrypt(encrypted, convertedHexKey))
        }
    }
}
