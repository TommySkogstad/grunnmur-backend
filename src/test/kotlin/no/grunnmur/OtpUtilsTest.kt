package no.grunnmur

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OtpUtilsTest {

    @Nested
    inner class GenerateCode {
        @Test
        fun `generert kode er 6 siffer`() {
            val code = OtpUtils.generateCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
        }

        @Test
        fun `generert kode er minst 100000`() {
            repeat(100) {
                val code = OtpUtils.generateCode()
                assertTrue(code.toInt() >= 100000, "Kode $code er under 100000")
            }
        }

        @Test
        fun `genererte koder er unike`() {
            val codes = (1..50).map { OtpUtils.generateCode() }.toSet()
            assertTrue(codes.size > 1, "50 genererte koder bor ikke vaere identiske")
        }
    }

    @Nested
    inner class HashCode {
        @Test
        fun `hashing gir konsistent resultat`() {
            val code = "123456"
            val hash1 = OtpUtils.hashCode(code)
            val hash2 = OtpUtils.hashCode(code)
            assertEquals(hash1, hash2)
        }

        @Test
        fun `hash er 64 hex-tegn (SHA-256)`() {
            val hash = OtpUtils.hashCode("123456")
            assertEquals(64, hash.length)
            assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        }

        @Test
        fun `forskjellige koder gir forskjellige hasher`() {
            val hash1 = OtpUtils.hashCode("123456")
            val hash2 = OtpUtils.hashCode("654321")
            assertNotEquals(hash1, hash2)
        }

        @Test
        fun `hash er ikke lik original kode`() {
            val code = "123456"
            val hash = OtpUtils.hashCode(code)
            assertNotEquals(code, hash)
        }
    }

    @Nested
    inner class Verify {
        @Test
        fun `gyldig kode returnerer Success`() {
            val code = "123456"
            val storedHash = OtpUtils.hashCode(code)
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify(code, storedHash, expiry, attempts = 0)
            assertTrue(result is OtpVerificationResult.Success)
        }

        @Test
        fun `feil kode returnerer InvalidCode`() {
            val storedHash = OtpUtils.hashCode("123456")
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify("999999", storedHash, expiry, attempts = 0)
            assertTrue(result is OtpVerificationResult.InvalidCode)
        }

        @Test
        fun `utlopt kode returnerer Expired`() {
            val code = "123456"
            val storedHash = OtpUtils.hashCode(code)
            val expiry = TimeUtils.nowOslo().minusMinutes(1)

            val result = OtpUtils.verify(code, storedHash, expiry, attempts = 0)
            assertTrue(result is OtpVerificationResult.Expired)
        }

        @Test
        fun `for mange forsok returnerer TooManyAttempts`() {
            val code = "123456"
            val storedHash = OtpUtils.hashCode(code)
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify(code, storedHash, expiry, attempts = 3)
            assertTrue(result is OtpVerificationResult.TooManyAttempts)
        }

        @Test
        fun `maks forsok er konfigurerbar`() {
            val code = "123456"
            val storedHash = OtpUtils.hashCode(code)
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify(code, storedHash, expiry, attempts = 5, maxAttempts = 5)
            assertTrue(result is OtpVerificationResult.TooManyAttempts)

            val result2 = OtpUtils.verify(code, storedHash, expiry, attempts = 4, maxAttempts = 5)
            assertTrue(result2 is OtpVerificationResult.Success)
        }
    }

    @Nested
    inner class DevModus {
        @Test
        fun `dev-kode 123456 fungerer i dev-modus`() {
            val storedHash = OtpUtils.hashCode("999888")
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify("123456", storedHash, expiry, attempts = 0, devMode = true)
            assertTrue(result is OtpVerificationResult.Success)
        }

        @Test
        fun `dev-kode fungerer ikke uten dev-modus`() {
            val storedHash = OtpUtils.hashCode("999888")
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify("123456", storedHash, expiry, attempts = 0, devMode = false)
            assertTrue(result is OtpVerificationResult.InvalidCode)
        }

        @Test
        fun `dev-kode sjekker fortsatt utlop`() {
            val storedHash = OtpUtils.hashCode("999888")
            val expiry = TimeUtils.nowOslo().minusMinutes(1)

            val result = OtpUtils.verify("123456", storedHash, expiry, attempts = 0, devMode = true)
            assertTrue(result is OtpVerificationResult.Expired)
        }

        @Test
        fun `dev-kode sjekker fortsatt forsoksgrense`() {
            val storedHash = OtpUtils.hashCode("999888")
            val expiry = TimeUtils.nowOslo().plusMinutes(5)

            val result = OtpUtils.verify("123456", storedHash, expiry, attempts = 3, devMode = true)
            assertTrue(result is OtpVerificationResult.TooManyAttempts)
        }
    }
}
