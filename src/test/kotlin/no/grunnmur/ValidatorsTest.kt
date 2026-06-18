package no.grunnmur

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidatorsTest {

    @Nested
    inner class EmailValidation {
        @Test
        fun `gyldig e-post`() {
            assertTrue(Validators.validateEmail("test@example.com").isValid)
            assertTrue(Validators.validateEmail("user.name+tag@domain.co").isValid)
            assertTrue(Validators.validateEmail("a@b.no").isValid)
        }

        @Test
        fun `ugyldig e-post`() {
            assertFalse(Validators.validateEmail("").isValid)
            assertFalse(Validators.validateEmail("   ").isValid)
            assertFalse(Validators.validateEmail("ikke-epost").isValid)
            assertFalse(Validators.validateEmail("@domain.com").isValid)
            assertFalse(Validators.validateEmail("user@").isValid)
        }

        @Test
        fun `e-post for lang`() {
            val lang = "a".repeat(250) + "@b.no"
            assertFalse(Validators.validateEmail(lang).isValid)
        }

        @Test
        fun `isValidEmail boolean`() {
            assertTrue(Validators.isValidEmail("test@example.com"))
            assertFalse(Validators.isValidEmail("ugyldig"))
            assertFalse(Validators.isValidEmail(""))
        }

        @Test
        fun `single-label domener avvises`() {
            assertFalse(Validators.validateEmail("a@b").isValid)
            assertFalse(Validators.validateEmail("user@localhost").isValid)
            assertFalse(Validators.isValidEmail("a@b"))
            assertFalse(Validators.isValidEmail("user@localhost"))
        }
    }

    @Nested
    inner class PhoneValidation {
        @Test
        fun `gyldig norsk telefonnummer`() {
            assertTrue(Validators.validatePhone("91234567").isValid)
            assertTrue(Validators.validatePhone("+4791234567").isValid)
            assertTrue(Validators.validatePhone("912 34 567", strict = false).isValid)
        }

        @Test
        fun `null og blank er gyldig`() {
            assertTrue(Validators.validatePhone(null).isValid)
            assertTrue(Validators.validatePhone("").isValid)
            assertTrue(Validators.validatePhone("   ").isValid)
        }

        @Test
        fun `ugyldig telefonnummer strikt`() {
            assertFalse(Validators.validatePhone("12345678").isValid) // starter med 1
            assertFalse(Validators.validatePhone("1234").isValid)
        }

        @Test
        fun `isValidPhone boolean`() {
            assertTrue(Validators.isValidPhone("91234567"))
            assertTrue(Validators.isValidPhone("+4791234567"))
        }
    }

    @Nested
    inner class UrlValidation {
        @Test
        fun `gyldig URL`() {
            assertTrue(Validators.validateUrl("https://example.com").isValid)
            assertTrue(Validators.validateUrl("http://sub.domain.no/path").isValid)
            assertTrue(Validators.validateUrl("https://example.com:8080/path").isValid)
        }

        @Test
        fun `ugyldig URL`() {
            assertFalse(Validators.validateUrl("").isValid)
            assertFalse(Validators.validateUrl("ftp://example.com").isValid)
            assertFalse(Validators.validateUrl("bare tekst").isValid)
        }

        @Test
        fun `farlige protokoller blokkeres`() {
            assertFalse(Validators.validateUrl("javascript:alert(1)").isValid)
            assertFalse(Validators.validateUrl("data:text/html,<h1>test</h1>").isValid)
            assertTrue(Validators.validateUrl("javascript:alert(1)").error!!.contains("protokoll"))
        }
    }

    @Nested
    inner class NameValidation {
        @Test
        fun `gyldig navn`() {
            assertTrue(Validators.validateName("Ola Nordmann").isValid)
            assertTrue(Validators.validateName("A", minLength = 1).isValid)
        }

        @Test
        fun `ugyldig navn`() {
            assertFalse(Validators.validateName("").isValid)
            assertFalse(Validators.validateName("   ").isValid)
            assertFalse(Validators.validateName("A", minLength = 2).isValid)
        }

        @Test
        fun `XSS-forsok blokkeres`() {
            assertFalse(Validators.validateName("<script>alert(1)</script>").isValid)
            assertFalse(Validators.validateName("onclick=evil()").isValid)
        }

        @Test
        fun `tilpasset feltnavn i feilmelding`() {
            val result = Validators.validateName("", fieldName = "Firmanavn")
            assertEquals("Firmanavn kan ikke vaere tomt", result.error)
        }
    }

    @Nested
    inner class TextFieldValidation {
        @Test
        fun `valgfritt felt aksepterer null og blank`() {
            assertTrue(Validators.validateTextField(null).isValid)
            assertTrue(Validators.validateTextField("").isValid)
        }

        @Test
        fun `paakrevd felt krever verdi`() {
            assertFalse(Validators.validateTextField(null, required = true).isValid)
            assertFalse(Validators.validateTextField("", required = true).isValid)
        }

        @Test
        fun `lengdebegrensning`() {
            assertFalse(Validators.validateTextField("abc", maxLength = 2).isValid)
            assertTrue(Validators.validateTextField("ab", maxLength = 2).isValid)
        }
    }

    @Nested
    inner class SearchQueryValidation {
        @Test
        fun `gyldig soek`() {
            assertTrue(Validators.validateSearchQuery("ola nordmann").isValid)
            assertTrue(Validators.validateSearchQuery(null).isValid)
            assertTrue(Validators.validateSearchQuery("").isValid)
        }

        @Test
        fun `blokkerer farlige tegn`() {
            assertFalse(Validators.validateSearchQuery("'; DROP TABLE--").isValid)
            assertFalse(Validators.validateSearchQuery("test\\injection").isValid)
        }

        @Test
        fun `for lang soekestreng`() {
            assertFalse(Validators.validateSearchQuery("a".repeat(101)).isValid)
        }
    }

    @Nested
    inner class OrganizationNumberValidation {
        @Test
        fun `gyldig orgnummer`() {
            assertTrue(Validators.validateOrganizationNumber("925900524").isValid)
            assertTrue(Validators.validateOrganizationNumber(null).isValid)
            assertTrue(Validators.validateOrganizationNumber("").isValid)
        }

        @Test
        fun `ugyldig orgnummer`() {
            assertFalse(Validators.validateOrganizationNumber("12345").isValid)
            assertFalse(Validators.validateOrganizationNumber("abcdefghi").isValid)
        }
    }

    @Nested
    inner class PasswordValidation {
        @Test
        fun `gyldig passord`() {
            assertTrue(Validators.validatePassword("Sikker123").isValid)
            assertTrue(Validators.validatePassword("KompleksT8kst!").isValid)
        }

        @Test
        fun `for kort passord`() {
            assertFalse(Validators.validatePassword("Ab1").isValid)
        }

        @Test
        fun `vanlig passord`() {
            val result = Validators.validatePassword("password123")
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("enkelt") })
        }

        @Test
        fun `mangler bokstav eller tall`() {
            assertFalse(Validators.validatePassword("12345678").isValid)
            assertFalse(Validators.validatePassword("abcdefgh").isValid)
        }

        @Test
        fun `norske vanlige passord avvises`() {
            assertFalse(Validators.validatePassword("Passord123").isValid)
            assertFalse(Validators.validatePassword("hemmelig1").isValid)
            assertFalse(Validators.validatePassword("fotball12").isValid)
        }

        @Test
        fun `sesongpassord avvises`() {
            assertFalse(Validators.validatePassword("Summer2026!").isValid)
            assertFalse(Validators.validatePassword("Vinter2025").isValid)
            assertFalse(Validators.validatePassword("sommer2024").isValid)
        }

        @Test
        fun `idrettsbegreper avvises`() {
            assertFalse(Validators.validatePassword("Handball123").isValid)
            assertFalse(Validators.validatePassword("Basketball1").isValid)
            assertFalse(Validators.validatePassword("Volleyball12").isValid)
        }

        @Test
        fun `keyboard walks avvises`() {
            assertFalse(Validators.validatePassword("Qwerty12").isValid)
            assertFalse(Validators.validatePassword("Asdfgh12").isValid)
            assertFalse(Validators.validatePassword("Zxcvbn12").isValid)
        }

        @Test
        fun `siffer-repetisjon avvises`() {
            assertFalse(Validators.validatePassword("11111111a").isValid)
            assertFalse(Validators.validatePassword("22222222b").isValid)
        }

        @Test
        fun `sterke passord med vanlige rotord skal ikke avvises`() {
            assertTrue(Validators.validatePassword("Daniella2024!").isValid)
            assertTrue(Validators.validatePassword("Masterchef9000").isValid)
            assertTrue(Validators.validatePassword("Dragonfly2025!").isValid)
            assertTrue(Validators.validatePassword("HunterGatherer3!").isValid)
        }

        @Test
        fun `lange vanlige basisord avvises fortsatt som substring`() {
            assertFalse(Validators.validatePassword("password2024!").isValid)
            assertFalse(Validators.validatePassword("sunshine123!!").isValid)
        }
    }

    @Nested
    inner class SanitizeHtml {
        @Test
        fun `saniterer HTML-tegn`() {
            assertEquals("&lt;script&gt;", Validators.sanitizeHtml("<script>"))
            assertEquals("&amp;amp;", Validators.sanitizeHtml("&amp;"))
            assertEquals("&quot;test&quot;", Validators.sanitizeHtml("\"test\""))
        }
    }

    @Nested
    inner class ValidationResultCompat {
        @Test
        fun `error property gir foerste feil`() {
            val result = Validators.ValidationResult(false, listOf("feil1", "feil2"))
            assertEquals("feil1", result.error)
        }

        @Test
        fun `error property er null for gyldig`() {
            assertNull(Validators.ValidationResult.valid().error)
        }
    }
}
