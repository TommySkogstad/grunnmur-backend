package no.grunnmur

/**
 * Felles valideringsbibliotek for alle apper i portefoljen.
 * Ingen Ktor/Exposed-avhengigheter — kun ren Kotlin.
 */
object Validators {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    ) {
        /** Forste feilmelding (for kompatibilitet med enkelt-feil-moenster) */
        val error: String? get() = errors.firstOrNull()

        companion object {
            fun valid() = ValidationResult(true)
            fun invalid(vararg errors: String) = ValidationResult(false, errors.toList())
        }
    }

    // E-post regex (RFC 5322 forenklet)
    private val EMAIL_REGEX = Regex(
        "^[a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\$"
    )

    // URL regex
    private val URL_REGEX = Regex(
        "^https?://[a-zA-Z0-9][-a-zA-Z0-9]*(\\.[-a-zA-Z0-9]+)+(:\\d+)?(/.*)?$",
        RegexOption.IGNORE_CASE
    )

    // Norsk telefonnummer: 8 siffer, evt. med +47 prefix
    private val PHONE_REGEX_STRICT = Regex("^(\\+47)?[2-9]\\d{7}\$")

    // Liberalt telefonnummer: internasjonalt format
    private val PHONE_REGEX_LIBERAL = Regex("^[+]?[0-9\\s-]{8,20}\$")

    // Farlige URL-protokoller
    private val DANGEROUS_PROTOCOLS = listOf("javascript:", "data:", "file:", "vbscript:", "about:")

    // XSS-monstre
    private val SUSPICIOUS_PATTERNS = listOf("<script", "javascript:", "onerror=", "onclick=")

    /**
     * Validerer e-postadresse.
     */
    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return ValidationResult.invalid("E-post kan ikke vaere tom")
        if (trimmed.length >= 255) return ValidationResult.invalid("E-post kan ikke vaere lengre enn 254 tegn")
        if (!EMAIL_REGEX.matches(trimmed)) return ValidationResult.invalid("Ugyldig e-postformat")
        return ValidationResult.valid()
    }

    /**
     * Enkel e-post-validering (Boolean).
     */
    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && email.length < 255 && EMAIL_REGEX.matches(email.trim())

    /**
     * Validerer telefonnummer (valgfritt felt).
     *
     * @param strict Hvis true, krever norsk 8-siffer med forste siffer 2-9. Default: true.
     */
    fun validatePhone(phone: String?, strict: Boolean = true): ValidationResult {
        if (phone.isNullOrBlank()) return ValidationResult.valid()
        val cleaned = phone.replace(Regex("[\\s-]"), "")
        if (cleaned.length > 20) return ValidationResult.invalid("Telefonnummer er for langt")
        val regex = if (strict) PHONE_REGEX_STRICT else PHONE_REGEX_LIBERAL
        if (!regex.matches(cleaned)) return ValidationResult.invalid("Ugyldig telefonnummerformat")
        return ValidationResult.valid()
    }

    /**
     * Enkel telefon-validering (Boolean). Liberalt format.
     */
    fun isValidPhone(phone: String): Boolean =
        phone.replace(Regex("[\\s-]"), "").matches(PHONE_REGEX_LIBERAL)

    /**
     * Validerer URL med sikkerhetsjekker (blokkerer farlige protokoller).
     */
    fun validateUrl(url: String, maxLength: Int = 2048): ValidationResult {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ValidationResult.invalid("URL kan ikke vaere tom")
        if (trimmed.length > maxLength) return ValidationResult.invalid("URL kan ikke vaere lengre enn $maxLength tegn")
        val lowercaseUrl = trimmed.lowercase()
        for (protocol in DANGEROUS_PROTOCOLS) {
            if (lowercaseUrl.startsWith(protocol)) {
                return ValidationResult.invalid("URL med protokoll '$protocol' er ikke tillatt")
            }
        }
        if (!URL_REGEX.matches(trimmed)) {
            return ValidationResult.invalid("Ugyldig URL-format. URL maa starte med http:// eller https://")
        }
        return ValidationResult.valid()
    }

    /**
     * Validerer navn (person, firma, etc.) med XSS-sjekk.
     */
    fun validateName(
        name: String,
        fieldName: String = "Navn",
        minLength: Int = 1,
        maxLength: Int = 255
    ): ValidationResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ValidationResult.invalid("$fieldName kan ikke vaere tomt")
        if (trimmed.length < minLength) return ValidationResult.invalid("$fieldName maa vaere minst $minLength tegn")
        if (trimmed.length > maxLength) return ValidationResult.invalid("$fieldName kan ikke vaere lengre enn $maxLength tegn")
        if (SUSPICIOUS_PATTERNS.any { trimmed.lowercase().contains(it) }) {
            return ValidationResult.invalid("$fieldName inneholder ugyldige tegn")
        }
        if (trimmed.contains(Regex("[<>\"'`]"))) {
            return ValidationResult.invalid("$fieldName inneholder ugyldige tegn")
        }
        return ValidationResult.valid()
    }

    /**
     * Validerer generelt tekstfelt.
     */
    fun validateTextField(
        value: String?,
        fieldName: String = "Tekst",
        required: Boolean = false,
        minLength: Int = 0,
        maxLength: Int = 10000
    ): ValidationResult {
        if (value.isNullOrBlank()) {
            return if (required) ValidationResult.invalid("$fieldName er paakrevd") else ValidationResult.valid()
        }
        val trimmed = value.trim()
        if (trimmed.length < minLength) return ValidationResult.invalid("$fieldName maa vaere minst $minLength tegn")
        if (trimmed.length > maxLength) return ValidationResult.invalid("$fieldName kan ikke vaere lengre enn $maxLength tegn")
        return ValidationResult.valid()
    }

    /**
     * Validerer soekestreng.
     */
    fun validateSearchQuery(query: String?, maxLength: Int = 100): ValidationResult {
        if (query.isNullOrBlank()) return ValidationResult.valid()
        val trimmed = query.trim()
        if (trimmed.length > maxLength) return ValidationResult.invalid("Soekestreng er for lang (maks $maxLength tegn)")
        if (trimmed.contains(Regex("[;'\"\\\\]"))) return ValidationResult.invalid("Soekestreng inneholder ugyldige tegn")
        return ValidationResult.valid()
    }

    /**
     * Validerer norsk organisasjonsnummer (9 siffer). Valgfritt felt.
     */
    fun validateOrganizationNumber(orgNumber: String?): ValidationResult {
        if (orgNumber.isNullOrBlank()) return ValidationResult.valid()
        val cleaned = orgNumber.replace(Regex("[\\s]"), "")
        if (!cleaned.matches(Regex("^\\d{9}\$"))) return ValidationResult.invalid("Organisasjonsnummer maa vaere 9 siffer")
        return ValidationResult.valid()
    }

    /**
     * Validerer passordstyrke.
     * Krav: minst 8 tegn, minst en bokstav og ett tall.
     */
    fun validatePassword(password: String): ValidationResult {
        val errors = mutableListOf<String>()
        if (password.length < 8) errors.add("Passord maa vaere minst 8 tegn")
        if (!password.any { it.isLetter() }) errors.add("Passord maa inneholde minst en bokstav")
        if (!password.any { it.isDigit() }) errors.add("Passord maa inneholde minst ett tall")
        val commonPasswords = listOf("password", "passord", "12345678", "qwerty123")
        if (commonPasswords.any { password.lowercase().contains(it) }) errors.add("Passord er for enkelt")
        return if (errors.isEmpty()) ValidationResult.valid() else ValidationResult(false, errors)
    }

    /**
     * Saniterer tekst ved aa erstatte HTML-spesialtegn.
     */
    fun sanitizeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
}
