package no.grunnmur

/**
 * Sanitering av brukerinput for GitHub Issues.
 * Alle funksjoner er rene (ingen sideeffekter) og traadsikre.
 */
object InputSanitizer {

    const val MAX_DESCRIPTION_LENGTH = 2000
    const val MAX_LOGS_LENGTH = 10000
    const val MAX_TITLE_LENGTH = 256

    // Markdown-lenker: [tekst](url) — haandterer parenteser i URL
    private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]*)\]\([^()]*(?:\([^()]*\)[^()]*)*\)""")

    // Markdown-bilder: ![alt](url) — haandterer parenteser i URL
    private val MARKDOWN_IMAGE_REGEX = Regex("""!\[[^\]]*\]\([^()]*(?:\([^()]*\)[^()]*)*\)""")

    // Script-blokker med innhold
    private val SCRIPT_BLOCK_REGEX = Regex("""<script[^>]*>.*?</script>""", RegexOption.IGNORE_CASE)

    // HTML-tags (inkludert self-closing)
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    // Mentions: @bruker (men ikke e-post som har tegn foer @)
    private val MENTION_REGEX = Regex("""(?<=^|(?<=\s))@([a-zA-Z0-9_-]+)""")

    // Allerede beskyttet mention: `@bruker`
    private val PROTECTED_MENTION_REGEX = Regex("""`@[a-zA-Z0-9_-]+`""")

    // JWT-tokens (starter med eyJ)
    private val JWT_REGEX = Regex("""eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}""")

    // GitHub tokens (ghp_, gho_, ghu_, ghs_, ghr_)
    private val GITHUB_TOKEN_REGEX = Regex("""gh[pousr]_[a-zA-Z0-9]{20,}""")

    // OpenAI/Anthropic tokens (sk-)
    private val SK_TOKEN_REGEX = Regex("""sk-[a-zA-Z0-9]{20,}""")

    // Lange hex-strenger (minst 32 tegn, typisk API-nokler)
    private val HEX_SECRET_REGEX = Regex("""(?<![a-zA-Z0-9])[a-fA-F0-9]{32,}(?![a-zA-Z0-9])""")

    private const val MASK = "[MASKERT]"

    /**
     * Fjerner Markdown-lenker (beholder lenketteksten), bilder og HTML-tags.
     */
    fun sanitizeMarkdown(text: String): String {
        return text
            .replace(MARKDOWN_IMAGE_REGEX, "")
            .replace(MARKDOWN_LINK_REGEX) { it.groupValues[1] }
            .replace(SCRIPT_BLOCK_REGEX, "")
            .replace(HTML_TAG_REGEX, "")
    }

    /**
     * Erstatter @mentions med code blocks for aa unngaa GitHub-notifikasjoner.
     * E-postadresser (tegn foer @) ignoreres.
     */
    fun sanitizeMentions(text: String): String {
        // Forst finn allerede beskyttede mentions og bevar dem
        val protected = mutableListOf<Pair<IntRange, String>>()
        PROTECTED_MENTION_REGEX.findAll(text).forEach {
            protected.add(it.range to it.value)
        }

        // Erstatt ubeskyttede mentions
        return MENTION_REGEX.replace(text) { match ->
            // Sjekk om denne matchen er innenfor en allerede beskyttet mention
            val isProtected = protected.any { (range, _) ->
                match.range.first >= range.first && match.range.last <= range.last
            }
            if (isProtected) match.value else "`${match.value}`"
        }
    }

    /**
     * Maskerer kjente token-formater og lange hex-strenger.
     */
    fun filterSecrets(text: String): String {
        return text
            .replace(JWT_REGEX, MASK)
            .replace(GITHUB_TOKEN_REGEX, MASK)
            .replace(SK_TOKEN_REGEX, MASK)
            .replace(HEX_SECRET_REGEX, MASK)
    }

    /**
     * Kutter tekst til angitt lengde med markaar.
     */
    fun truncate(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength - 1) + "…"
    }

    /**
     * Kombinerer alle saniterings-steg i riktig rekkefoelge:
     * 1. Fjern Markdown/HTML
     * 2. Masker secrets
     * 3. Beskytt mentions
     * 4. Kutt til maks lengde
     */
    fun sanitize(text: String, maxLength: Int): String {
        return text
            .let { sanitizeMarkdown(it) }
            .let { filterSecrets(it) }
            .let { sanitizeMentions(it) }
            .let { truncate(it, maxLength) }
    }
}
