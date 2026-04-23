package no.grunnmur

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

class TotpServiceTest {

    private val testKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun encryptedSecretFrom(secretBytes: ByteArray): String {
        val secret = Base64.getEncoder().encodeToString(secretBytes)
        return EncryptionUtils.encrypt(secret, testKey)
    }

    private fun generateTotpCode(secretBytes: ByteArray): String {
        val config = TimeBasedOneTimePasswordConfig(
            timeStep = 30,
            timeStepUnit = TimeUnit.SECONDS,
            codeDigits = 6,
            hmacAlgorithm = HmacAlgorithm.SHA1
        )
        return TimeBasedOneTimePasswordGenerator(secretBytes, config).generate(Instant.now())
    }

    @Nested
    inner class SetupTotp {
        @Test
        fun `returnerer gyldig otpauth-URI`() {
            val result = TotpService.setupTotp(testKey, "TestApp", "bruker@example.com")
            assertTrue(result.qrUri.startsWith("otpauth://totp/"))
        }

        @Test
        fun `otpauth-URI inneholder issuer og account`() {
            val result = TotpService.setupTotp(testKey, "MinApp", "bruker@example.com")
            assertTrue(result.qrUri.contains("MinApp"))
            assertTrue(result.qrUri.contains("bruker"))
        }

        @Test
        fun `otpauth-URI inneholder riktige TOTP-parametere`() {
            val result = TotpService.setupTotp(testKey, "App", "user@test.no")
            assertTrue(result.qrUri.contains("algorithm=SHA1"))
            assertTrue(result.qrUri.contains("digits=6"))
            assertTrue(result.qrUri.contains("period=30"))
        }

        @Test
        fun `returnerer kryptert secret som kan dekrypteres`() {
            val result = TotpService.setupTotp(testKey, "App", "user@test.no")
            val decrypted = EncryptionUtils.decrypt(result.secret, testKey)
            assertNotNull(decrypted)
            assertTrue(decrypted!!.isNotEmpty())
        }

        @Test
        fun `to oppsett gir forskjellige krypterte secrets`() {
            val result1 = TotpService.setupTotp(testKey, "App", "user@test.no")
            val result2 = TotpService.setupTotp(testKey, "App", "user@test.no")
            assertNotEquals(result1.secret, result2.secret)
        }
    }

    @Nested
    inner class ConfirmTotp {
        @Test
        fun `avviser ugyldig kryptert secret`() {
            assertFalse(TotpService.confirmTotp("ikke-gyldig-kryptering", testKey, "123456"))
        }

        @Test
        fun `godtar gyldig TOTP-kode`() {
            val secretBytes = ByteArray(20) { it.toByte() }
            val encryptedSecret = encryptedSecretFrom(secretBytes)
            val code = generateTotpCode(secretBytes)

            assertTrue(TotpService.confirmTotp(encryptedSecret, testKey, code))
        }

        @Test
        fun `avviser feil TOTP-kode`() {
            val secretBytes = ByteArray(20) { it.toByte() }
            val encryptedSecret = encryptedSecretFrom(secretBytes)

            assertFalse(TotpService.confirmTotp(encryptedSecret, testKey, "000000"))
        }
    }

    @Nested
    inner class VerifyTotp {
        @Test
        fun `dev-modus godtar 000000`() {
            val secretBytes = ByteArray(20) { it.toByte() }
            val encryptedSecret = encryptedSecretFrom(secretBytes)

            assertTrue(TotpService.verifyTotp(encryptedSecret, testKey, "000000", devMode = true))
        }

        @Test
        fun `avviser ugyldig kryptert secret`() {
            assertFalse(TotpService.verifyTotp("ikke-gyldig", testKey, "123456"))
        }

        @Test
        fun `produksjonsmodus avviser 000000`() {
            val secretBytes = ByteArray(20) { it.toByte() }
            val encryptedSecret = encryptedSecretFrom(secretBytes)

            assertFalse(TotpService.verifyTotp(encryptedSecret, testKey, "000000", devMode = false))
        }

        @Test
        fun `godtar gyldig TOTP-kode`() {
            val secretBytes = ByteArray(20) { it.toByte() }
            val encryptedSecret = encryptedSecretFrom(secretBytes)
            val code = generateTotpCode(secretBytes)

            assertTrue(TotpService.verifyTotp(encryptedSecret, testKey, code))
        }
    }

    @Nested
    inner class GenerateBackupCodes {
        @Test
        fun `genererer 10 koder som standard`() {
            val codes = TotpService.generateBackupCodes()
            assertEquals(10, codes.size)
        }

        @Test
        fun `genererer spesifisert antall koder`() {
            assertEquals(5, TotpService.generateBackupCodes(5).size)
            assertEquals(15, TotpService.generateBackupCodes(15).size)
        }

        @Test
        fun `koder har format XXXX-XXXX`() {
            val formatRegex = Regex("^[0-9A-F]{4}-[0-9A-F]{4}$")
            TotpService.generateBackupCodes().forEach { code ->
                assertTrue(formatRegex.matches(code), "Kode '$code' matcher ikke XXXX-XXXX formatet")
            }
        }

        @Test
        fun `genererte koder er unike`() {
            val codes = TotpService.generateBackupCodes(10)
            assertEquals(codes.size, codes.toSet().size)
        }
    }

    @Nested
    inner class VerifyBackupCode {
        @Test
        fun `godtar gyldig backup-kode`() {
            val codes = TotpService.generateBackupCodes()
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, _) = TotpService.verifyBackupCode(codes[0], encrypted, testKey)
            assertTrue(valid)
        }

        @Test
        fun `brukt kode fjernes fra listen`() {
            val codes = listOf("AAAA-BBBB", "CCCC-DDDD", "EEEE-FFFF")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, updatedEncrypted) = TotpService.verifyBackupCode("AAAA-BBBB", encrypted, testKey)
            assertTrue(valid)
            assertNotNull(updatedEncrypted)

            val (validAgain, _) = TotpService.verifyBackupCode("AAAA-BBBB", updatedEncrypted!!, testKey)
            assertFalse(validAgain)
        }

        @Test
        fun `avviser ugyldig kode`() {
            val codes = TotpService.generateBackupCodes()
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, _) = TotpService.verifyBackupCode("XXXX-XXXX", encrypted, testKey)
            assertFalse(valid)
        }

        @Test
        fun `returnerer null som oppdatert liste naar koden ikke finnes`() {
            val codes = listOf("AAAA-BBBB")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, updatedEncrypted) = TotpService.verifyBackupCode("XXXX-YYYY", encrypted, testKey)
            assertFalse(valid)
            assertNull(updatedEncrypted)
        }

        @Test
        fun `returnerer null naar siste kode er brukt`() {
            val codes = listOf("AAAA-BBBB")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, updatedEncrypted) = TotpService.verifyBackupCode("AAAA-BBBB", encrypted, testKey)
            assertTrue(valid)
            assertNull(updatedEncrypted, "Ingen koder igjen — updatedEncrypted skal vaere null")
        }

        @Test
        fun `godtar kode uten bindestrek`() {
            val codes = listOf("AAAA-BBBB")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, _) = TotpService.verifyBackupCode("AAAABBBB", encrypted, testKey)
            assertTrue(valid)
        }

        @Test
        fun `godtar kode med smaabokstaver`() {
            val codes = listOf("AAAA-BBBB")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)

            val (valid, _) = TotpService.verifyBackupCode("aaaa-bbbb", encrypted, testKey)
            assertTrue(valid)
        }
    }

    @Nested
    inner class EncryptBackupCodes {
        @Test
        fun `krypterer og dekrypterer korrekt roundtrip`() {
            val codes = listOf("AAAA-BBBB", "CCCC-DDDD", "EEEE-FFFF")
            val encrypted = TotpService.encryptBackupCodes(codes, testKey)
            val decrypted = EncryptionUtils.decrypt(encrypted, testKey)

            assertNotNull(decrypted)
            assertEquals(codes, decrypted!!.split(","))
        }

        @Test
        fun `to krypteringer av samme koder gir forskjellig chipertekst`() {
            val codes = listOf("AAAA-BBBB", "CCCC-DDDD")
            val encrypted1 = TotpService.encryptBackupCodes(codes, testKey)
            val encrypted2 = TotpService.encryptBackupCodes(codes, testKey)
            assertNotEquals(encrypted1, encrypted2)
        }
    }

    @Nested
    inner class DisableTotp {
        @Test
        fun `returnerer false og null-verdier`() {
            val (enabled, secret, backupCodes) = TotpService.disableTotp()
            assertFalse(enabled)
            assertNull(secret)
            assertNull(backupCodes)
        }
    }
}
