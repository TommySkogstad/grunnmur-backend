package no.grunnmur

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InputSanitizerTest {

    @Nested
    inner class SanitizeMarkdown {
        @Test
        fun `fjerner Markdown-lenker`() {
            assertEquals("klikk her", InputSanitizer.sanitizeMarkdown("[klikk her](https://evil.com)"))
            assertEquals("klikk her", InputSanitizer.sanitizeMarkdown("[klikk her](javascript:alert(1))"))
        }

        @Test
        fun `fjerner Markdown-bilder`() {
            assertEquals("", InputSanitizer.sanitizeMarkdown("![bilde](https://evil.com/img.png)"))
        }

        @Test
        fun `fjerner HTML-tags`() {
            assertEquals("hei", InputSanitizer.sanitizeMarkdown("<script>alert(1)</script>hei"))
            assertEquals("tekst", InputSanitizer.sanitizeMarkdown("<div onclick='evil()'>tekst</div>"))
            assertEquals("test", InputSanitizer.sanitizeMarkdown("<img src=x onerror=alert(1)>test"))
        }

        @Test
        fun `beholder vanlig tekst`() {
            assertEquals("Vanlig tekst uten problemer", InputSanitizer.sanitizeMarkdown("Vanlig tekst uten problemer"))
        }

        @Test
        fun `beholder linjeskift og mellomrom`() {
            assertEquals("linje 1\nlinje 2", InputSanitizer.sanitizeMarkdown("linje 1\nlinje 2"))
        }
    }

    @Nested
    inner class SanitizeMentions {
        @Test
        fun `beskytter mentions med code blocks`() {
            assertEquals("Hei `@bruker` dette er en test", InputSanitizer.sanitizeMentions("Hei @bruker dette er en test"))
        }

        @Test
        fun `beskytter flere mentions`() {
            val result = InputSanitizer.sanitizeMentions("@foo og @bar")
            assertEquals("`@foo` og `@bar`", result)
        }

        @Test
        fun `ignorerer e-postadresser`() {
            assertEquals("test@example.com", InputSanitizer.sanitizeMentions("test@example.com"))
        }

        @Test
        fun `mention i starten av tekst`() {
            assertEquals("`@admin` hjelp!", InputSanitizer.sanitizeMentions("@admin hjelp!"))
        }

        @Test
        fun `allerede beskyttet mention endres ikke`() {
            assertEquals("`@bruker`", InputSanitizer.sanitizeMentions("`@bruker`"))
        }
    }

    @Nested
    inner class FilterSecrets {
        @Test
        fun `maskerer JWT-tokens`() {
            val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
            val result = InputSanitizer.filterSecrets("Token: $jwt")
            assertFalse(result.contains("eyJ"))
            assertTrue(result.contains("[MASKERT]"))
        }

        @Test
        fun `maskerer GitHub-tokens`() {
            val result = InputSanitizer.filterSecrets("ghp_abc123DEF456ghi789jkl012mno345pqr678")
            assertFalse(result.contains("ghp_"))
            assertTrue(result.contains("[MASKERT]"))
        }

        @Test
        fun `maskerer sk-tokens`() {
            val result = InputSanitizer.filterSecrets("sk-abcdefghijklmnopqrstuvwxyz123456789012345678")
            assertFalse(result.contains("sk-"))
            assertTrue(result.contains("[MASKERT]"))
        }

        @Test
        fun `maskerer lange hex-strenger`() {
            val hex = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
            val result = InputSanitizer.filterSecrets("Nokkel: $hex")
            assertFalse(result.contains(hex))
            assertTrue(result.contains("[MASKERT]"))
        }

        @Test
        fun `beholder vanlig tekst`() {
            assertEquals("Vanlig tekst uten hemmeligheter", InputSanitizer.filterSecrets("Vanlig tekst uten hemmeligheter"))
        }

        @Test
        fun `beholder korte hex-strenger`() {
            assertEquals("Farge: #ff0000", InputSanitizer.filterSecrets("Farge: #ff0000"))
        }
    }

    @Nested
    inner class Truncate {
        @Test
        fun `kort tekst endres ikke`() {
            assertEquals("hei", InputSanitizer.truncate("hei", 100))
        }

        @Test
        fun `lang tekst kuttes med markor`() {
            val result = InputSanitizer.truncate("a".repeat(50), 20)
            assertTrue(result.length <= 20)
            assertTrue(result.endsWith("…"))
        }

        @Test
        fun `tom tekst endres ikke`() {
            assertEquals("", InputSanitizer.truncate("", 100))
        }
    }

    @Nested
    inner class Sanitize {
        @Test
        fun `kombinerer alle steg`() {
            val input = "Hei @admin, se [her](https://evil.com) <script>alert(1)</script> ghp_abc123DEF456ghi789jkl012mno345pqr678"
            val result = InputSanitizer.sanitize(input, 2000)

            // Mentions beskyttet
            assertTrue(result.contains("`@admin`"))
            // Lenke fjernet (kun tekst beholdt)
            assertFalse(result.contains("evil.com"))
            assertTrue(result.contains("her"))
            // HTML fjernet
            assertFalse(result.contains("<script>"))
            // Token maskert
            assertFalse(result.contains("ghp_"))
        }

        @Test
        fun `respekterer lengdegrense`() {
            val long = "a".repeat(5000)
            val result = InputSanitizer.sanitize(long, InputSanitizer.MAX_DESCRIPTION_LENGTH)
            assertTrue(result.length <= InputSanitizer.MAX_DESCRIPTION_LENGTH)
        }
    }

    @Nested
    inner class Constants {
        @Test
        fun `konstanter har forventede verdier`() {
            assertEquals(2000, InputSanitizer.MAX_DESCRIPTION_LENGTH)
            assertEquals(10000, InputSanitizer.MAX_LOGS_LENGTH)
            assertEquals(256, InputSanitizer.MAX_TITLE_LENGTH)
        }
    }
}
