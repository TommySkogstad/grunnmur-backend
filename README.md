<p align="center">
  <img src="logo.svg" alt="grunnmur" width="80" height="80">
</p>

<h1 align="center">grunnmur</h1>

<p align="center">Felles Kotlin-bibliotek for Ktor-appene i portefoljen.<br>Standardiserer patterns som tidligere var duplisert med variasjoner mellom apper.</p>

---

## Moduloversikt

### Auth og sikkerhet

#### CsrfPlugin (`CsrfPlugin.kt`)
Ktor-plugin for CSRF-beskyttelse. Validerer at `X-CSRF-Token`-header matcher `csrf_token`-cookie for muterende operasjoner (POST/PUT/DELETE/PATCH).

```kotlin
install(GrunnmurCsrf) {
    exemptPaths = setOf("/api/auth/login", "/api/auth/request-code", "/api/health")
    authCookieName = "auth_token"
}
```

- **`GrunnmurCsrf`** — Ktor ApplicationPlugin (installeres med `install()`)
- **`CsrfConfig`** — Konfigurasjon: `exemptPaths`, `authCookieName`, `csrfCookieName`, `csrfHeaderName`

#### RateLimiter (`RateLimiter.kt`)
In-memory sliding window rate limiter med automatisk cleanup og beskyttelse mot minnelekkasje.

```kotlin
val loginLimiter = RateLimiter(maxAttempts = 5, windowMs = 300_000)

if (!loginLimiter.isAllowed(clientIp)) throw RateLimitException()
loginLimiter.reset(clientIp) // Etter vellykket login
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `isAllowed` | `(key: String): Boolean` | Sjekker og registrerer forsok |
| `reset` | `(key: String)` | Nullstiller teller for en noekkel |
| `remainingAttempts` | `(key: String): Int` | Gjenvaerende forsok |
| `retryAfterSeconds` | `(key: String): Long?` | Sekunder til vindu utloeper (null hvis ikke blokkert) |
| `windowMs` | `Long` | Tidsvinduet i millisekunder (public property) |
| `clear` | `()` | Nullstill alt (kun testing) |
| `size` | `(): Int` | Antall aktive entries |

#### RateLimitPresets (`RateLimitPresets.kt`)
Ferdigkonfigurerte rate limitere for vanlige bruksscenarier. Bygger på `RateLimiter`.

**CompositeRateLimiter** — sjekker flere vinduer (alle må tillate):

```kotlin
val limiter = CompositeRateLimiter(
    RateLimiter(maxAttempts = 5, windowMs = 60_000),      // 5/min
    RateLimiter(maxAttempts = 10, windowMs = 3_600_000)   // 10/time
)
if (!limiter.isAllowed(clientIp)) throw RateLimitException()
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `isAllowed` | `(key: String): Boolean` | Sjekker alle limitere, registrerer forsøk i alle |
| `reset` | `(key: String)` | Nullstiller alle limitere for nøkkelen |
| `remainingAttempts` | `(key: String): Int` | Minimum gjenstående på tvers av alle |
| `retryAfterSeconds` | `(key: String): Long?` | Maksimum retry-tid på tvers av alle |

**AuthRateLimiter** — kombinerer IP-basert og identifikator-basert limiting (identifikatorer hashes med SHA-256):

```kotlin
val limiter = authRateLimiterWithIdentifier()

post("/api/auth/send-otp") {
    val ip = call.getClientIp()
    val phone = call.receive<OtpRequest>().phone
    call.checkRateLimit(
        limiter.isAllowed(ip, phone),
        retryAfterSeconds = limiter.retryAfterSeconds(ip, phone)
    )
}
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `isAllowed` | `(ip: String, identifier: String): Boolean` | Begge må tillate |
| `reset` | `(ip: String, identifier: String)` | Nullstiller begge |
| `remainingAttempts` | `(ip: String, identifier: String): Int` | Minimum av begge |
| `retryAfterSeconds` | `(ip: String, identifier: String): Long?` | Maksimum av begge |

**Preset-funksjoner:**

| Funksjon | Standard | Beskrivelse |
|----------|----------|-------------|
| `authRateLimiter()` | 5/min + 10/time per IP | For send-otp, verify-otp — returnerer `CompositeRateLimiter` |
| `authRateLimiterWithIdentifier()` | IP: 5/min + 10/time, identifikator: 5/15min | For send-otp med telefon/e-post — returnerer `AuthRateLimiter` |
| `apiRateLimiterAuthenticated()` | 60/min per IP | For autentiserte API-ruter — returnerer `RateLimiter` |
| `apiRateLimiterAnonymous()` | 20/min per IP | For åpne API-ruter — returnerer `RateLimiter` |

```kotlin
// Opprett én gang ved appstart
val authLimiter = authRateLimiter()
val apiLimiterAuth = apiRateLimiterAuthenticated()
val apiLimiterAnon = apiRateLimiterAnonymous()

// Auth-ruter
post("/api/auth/send-otp") {
    val ip = call.getClientIp()
    call.checkRateLimit(authLimiter.isAllowed(ip), retryAfterSeconds = authLimiter.retryAfterSeconds(ip))
}

// API-ruter
get("/api/data") {
    val ip = call.getClientIp()
    val limiter = if (call.getUserId() != null) apiLimiterAuth else apiLimiterAnon
    call.checkRateLimit(limiter.isAllowed(ip))
}
```

#### EncryptionUtils (`EncryptionUtils.kt`)
AES-256-GCM-kryptering med hex-nokler (64 hex-tegn = 32 bytes). Output: Base64(IV + ciphertext + GCM-tag).

```kotlin
val key = EncryptionUtils.generateKey()               // Ny 64 hex-tegn noekkel
val encrypted = EncryptionUtils.encrypt(plaintext, key)
val decrypted = EncryptionUtils.decrypt(encrypted, key) // null ved feil
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `encrypt` | `(plaintext: String, hexKey: String): String` | Krypterer med AES-256-GCM |
| `decrypt` | `(ciphertext: String, hexKey: String): String?` | Dekrypterer, null ved feil |
| `generateKey` | `(): String` | Genererer tilfeldig 64 hex-tegn noekkel |
| `base64KeyToHex` | `(base64Key: String): String` | Konverterer Base64-noekkel til hex |

#### TotpService (`TotpService.kt`)
TOTP-tjeneste for tofaktorautentisering (RFC 6238). Bruker EncryptionUtils for kryptering av hemmeligheter.

```kotlin
val setup = TotpService.setupTotp(encryptionKey, "Min App", "bruker@eksempel.no")
// setup.secret -> kryptert hemmelighet, setup.qrUri -> otpauth://-URI

val ok = TotpService.verifyTotp(encryptedSecret, encryptionKey, "123456", devMode = false)
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `setupTotp` | `(encryptionKey, issuer, accountName): TotpSetupResult` | Starter TOTP-oppsett |
| `confirmTotp` | `(encryptedSecret, encryptionKey, code): Boolean` | Bekrefter QR-kode-skanning |
| `verifyTotp` | `(encryptedSecret, encryptionKey, code, devMode): Boolean` | Verifiserer TOTP-kode |
| `generateBackupCodes` | `(count: Int = 10): List<String>` | Genererer backup-koder (XXXX-XXXX) |
| `verifyBackupCode` | `(code, encryptedCodes, encryptionKey): Pair<Boolean, String?>` | Verifiserer backup-kode |
| `encryptBackupCodes` | `(codes, encryptionKey): String` | Krypterer backup-koder for DB |
| `disableTotp` | `(): Triple<Boolean, String?, String?>` | Null-verdier for deaktivering |

#### OtpUtils (`OtpUtils.kt`)
OTP-haandtering (One-Time Password) med SHA-256-hashing. Dev-modus: kode "123456" fungerer alltid. Salt beskytter mot forhåndsberegnede regnbuetabeller.

```kotlin
val code = OtpUtils.generateCode()                           // "847291"
val hash = OtpUtils.hashCode(code, salt = "app-secret")    // SHA-256 hex med salt
val result = OtpUtils.verify(code, hash, expiresAt, attempts, salt = "app-secret", devMode = true)
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `generateCode` | `(): String` | Tilfeldig 6-sifret kode (100000-999999) |
| `hashCode` | `(code: String, salt: String = ""): String` | SHA-256 hash (64 hex-tegn). Salt beskytter mot regnbuetabeller, tom salt gir bakoverkompatibilitet |
| `verify` | `(code, storedHash, expiresAt, attempts, ..., salt: String = ""): OtpVerificationResult` | Verifiserer OTP med samme salt som hashing |

**`OtpVerificationResult`** (sealed class): `Success`, `InvalidCode`, `Expired`, `TooManyAttempts`

#### AuthExtensions (`AuthExtensions.kt`)
Extension functions for JWT-autentisering paa `ApplicationCall`.

```kotlin
val userId = call.getUserId()       // Int? fra JWT "userId"-claim
val userId = call.requireUserId()   // Int, kaster AuthenticationException
val email = call.getUserEmail()     // String? fra JWT "email"-claim
```

---

### HTTP og routing

#### RouteUtils (`RouteUtils.kt`)
Extension functions for Ktor `ApplicationCall` — parameteruthenting, rate limiting og IP-oppslag.

```kotlin
val id = call.requireIntParam("id")
val slug = call.requireParam("slug")
call.checkRateLimit(limiter.isAllowed(ip))
val clientIp = call.getClientIp()
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `requireIntParam` | `(name: String): Int` | Henter int-param, kaster BadRequestException |
| `requireParam` | `(name: String): String` | Henter string-param, kaster BadRequestException |
| `checkRateLimit` | `(allowed: Boolean, message: String)` | Kaster RateLimitException |
| `getClientIp` | `(): String` | IP via CF-Connecting-IP / X-Real-IP / X-Forwarded-For |

#### StatusPagesConfig (`StatusPagesConfig.kt`)
Standard exception-handlers for alle grunnmur-exceptions.

```kotlin
install(StatusPages) {
    grunnmurExceptionHandlers()
}
```

Mapper: `BadRequestException` -> 400, `NotFoundException` -> 404, `ForbiddenException` -> 403, `RateLimitException` -> 429, `AuthenticationException` -> 401, `GitHubApiException` -> 500 (fra GitHub API-feil), `IllegalArgumentException` -> 400, `Throwable` -> 500 (skjuler detaljer i produksjon).

---

### Database

#### FlywayMigration (`FlywayMigration.kt`)
Felles Flyway-konfigurasjon med fornuftige standardverdier (baselineOnMigrate, cleanDisabled). Testdekning via PostgreSQL 16-integrasjonstester (Testcontainers).

```kotlin
FlywayMigration.migrate(dataSource) // Kjoerer alle ventende migrasjoner
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `configure` | `(dataSource, locations): Flyway` | Konfigurerer Flyway-instans |
| `migrate` | `(dataSource, locations): Int` | Kjoerer migrasjoner, returnerer antall |

#### AuditLog (`AuditLogTable.kt` + `AuditLogService.kt`)
Database-basert revisjonslogging med Exposed DSL. Tabellen `audit_logs` med indekser paa entity, created_at og user. Testdekning via PostgreSQL 16-integrasjonstester (Testcontainers).

```kotlin
val auditLog = AuditLogService()
auditLog.log(userId = 1, userEmail = "admin@ex.no", action = "CREATE", entityType = "POST", entityId = 42)
val logs = auditLog.findAll(action = "DELETE", limit = 50)
auditLog.cleanupOldLogs(retentionDays = 365)
```

**AuditLogService** (class):
| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `log` | `(userId, userEmail, action, entityType, entityId?, details?, ipAddress?)` | Logger handling |
| `findAll` | `(action?, entityType?, userId?, startDate?, endDate?, limit, offset): List<AuditLogEntry>` | Henter med filtrering |
| `count` | `(action?, entityType?, userId?, startDate?, endDate?): Long` | Teller med filtrering |
| `cleanupOldLogs` | `(retentionDays: Int = 365): Int` | Sletter gamle logger |

**AuditLogs** (object: Table) — Exposed-tabelldefinisjonen.

**AuditLogEntry** (data class) — DTO med id, userId, userEmail, action, entityType, entityId, details, ipAddress, timestamp.

#### PaginatedResponse (`PaginatedResponse.kt`)
Generisk paginert responsformat for API-endepunkter.

```kotlin
val response = PaginatedResponse(items = liste, total = 150L, limit = 20, offset = 0L)
```

---

### Validering

#### Validators (`Validators.kt`)
Felles valideringsbibliotek uten Ktor/Exposed-avhengigheter. Returnerer `ValidationResult(isValid, errors)`.

```kotlin
val result = Validators.validateEmail("bruker@eksempel.no")
if (!result.isValid) println(result.errors)
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `validateEmail` | `(email: String): ValidationResult` | RFC 5322 forenklet |
| `isValidEmail` | `(email: String): Boolean` | Forenklet boolean-variant |
| `validatePhone` | `(phone: String?, strict: Boolean = true): ValidationResult` | Norsk (strict) eller internasjonalt |
| `isValidPhone` | `(phone: String): Boolean` | Forenklet boolean-variant |
| `validateUrl` | `(url: String, maxLength: Int = 2048): ValidationResult` | Med sikkerhetsjekk mot farlige protokoller |
| `validateName` | `(name, fieldName, minLength, maxLength): ValidationResult` | Med XSS-sjekk |
| `validateTextField` | `(value?, fieldName, required, minLength, maxLength): ValidationResult` | Generelt tekstfelt |
| `validateSearchQuery` | `(query: String?, maxLength: Int = 100): ValidationResult` | Soekestreng med SQL-injeksjonsbeskyttelse |
| `validateOrganizationNumber` | `(orgNumber: String?): ValidationResult` | Norsk org.nr (9 siffer) |
| `validatePassword` | `(password: String): ValidationResult` | Min 8 tegn, bokstav + tall, blokkerer vanlige passord og sesongmønstre (f.eks. Summer2026, Januar2025) |
| `sanitizeHtml` | `(input: String): String` | Erstatter HTML-spesialtegn |

#### InputSanitizer (`InputSanitizer.kt`)
Sanitering av brukerinput for GitHub Issues. Fjerner markdown/HTML, maskerer hemmeligheter, beskytter @mentions.

```kotlin
val safe = InputSanitizer.sanitize(userInput, InputSanitizer.MAX_DESCRIPTION_LENGTH)
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `sanitize` | `(text: String, maxLength: Int): String` | Kombinerer alle steg |
| `sanitizeMarkdown` | `(text: String): String` | Fjerner markdown-lenker, bilder, HTML |
| `sanitizeMentions` | `(text: String): String` | Wrapper @mentions i backticks |
| `filterSecrets` | `(text: String): String` | Maskerer JWT, GitHub/OpenAI-tokens, hex-secrets |
| `truncate` | `(text: String, maxLength: Int): String` | Kutter med markering |

Konstanter: `MAX_DESCRIPTION_LENGTH = 2000`, `MAX_LOGS_LENGTH = 10000`, `MAX_TITLE_LENGTH = 256`

---

### Tid

#### TimeUtils (`TimeUtils.kt`)
Konsistent norsk tid med Europe/Oslo-tidssone.

```kotlin
val now = TimeUtils.nowOslo()
val formatted = TimeUtils.formatDateTime(now)      // "2026-03-07 14:30"
val iso = TimeUtils.formatDateTimeIso(now)          // "2026-03-07T14:30:00"
val parsed = TimeUtils.parseDateTime("2026-03-07")  // LocalDateTime (start av dag)
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `nowOslo` | `(): LocalDateTime` | Naaværende tid i Oslo |
| `formatDateTime` | `(dt: LocalDateTime): String` | Format: yyyy-MM-dd HH:mm |
| `formatDateTimeIso` | `(dt: LocalDateTime): String` | ISO-format |
| `parseDateTime` | `(value: String): LocalDateTime` | Parser dato eller dato/tid |

Felt: `OSLO_ZONE: ZoneId`, `isoDateTime: DateTimeFormatter`, `isoDate: DateTimeFormatter`

---

### Tjenester

#### SmtpClient (`SmtpClient.kt`)
SMTP-klient for e-postsending via Jakarta Mail. Stoetter HTML, vedlegg, traad-kobling (Message-ID/In-Reply-To), rate limiting og dev-modus.

```kotlin
val smtp = SmtpClient(SmtpConfig(
    host = "smtp.example.com", port = 587,
    user = "bruker", password = "passord",
    from = "noreply@example.com", fromName = "Min App"
))

val result = smtp.send(EmailMessage(
    to = "mottaker@example.com",
    subject = "Hei",
    body = "Innhold",
    htmlBody = "<h1>Hei</h1>"
))
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `send` | `suspend (message: EmailMessage, forceDelivery: Boolean = false): SendResult` | Sender e-post |
| `sendWithMessageId` | `suspend (message, messageId, forceDelivery): SendResult` | Sender med egendefinert Message-ID |

Dataklasser: **SmtpConfig**, **EmailMessage**, **EmailAttachment**, **SendResult**

**SmtpConfig.fromEnv()** leser konfigurasjon fra miljøvariabler. Numeriske felt (`SMTP_PORT`, `SMTP_TIMEOUT_MS`, `SMTP_MIN_INTERVAL_MS`) valideres med `toIntOrNull()`/`toLongOrNull()` og gir beskrivende feilmelding ved ugyldig verdi, f.eks.:

```
SMTP_PORT må være et heltall, fikk: 'ikke-et-tall'
```

`SMTP_STARTTLS` og `SMTP_REQUIRE_AUTH` er uavhengige — STARTTLS kan aktiveres uten autentisering (f.eks. for Postfix med self-signed sertifikat).

#### GitHubIssueService (`GitHubIssueService.kt`)
Oppretter og oppdaterer GitHub Issues via API. Stoetter baade PAT og GitHub App-autentisering. All input saniteres via InputSanitizer. Kaster `GitHubApiException` ved API-feil.

```kotlin
val service = GitHubIssueService(GitHubIssueService.Config(
    token = "ghp_...",   // eller appAuth = GitHubAppAuth(...)
    repo = "owner/repo"
))
val result = service.createIssue(title = "Bug", senderName = "Ola", senderEmail = "ola@ex.no", description = "...")
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `createIssue` | `(title, senderName, senderEmail, description, ...): GitHubIssueResponse` | Oppretter issue (suspend), kaster GitHubApiException |
| `updateIssueBody` | `(issueNumber: Int, body: String)` | Oppdaterer issue-body (suspend), kaster GitHubApiException |
| `buildBody` | `(senderName, senderEmail, description, ...): String` | Bygger markdown-body |

#### GitHubAppAuth (`GitHubAppAuth.kt`)
GitHub App-autentisering med JWT (RS256), automatisk caching av installation tokens og **thread-safe token-refresh** via Mutex (double-checked locking). Kaster `GitHubApiException` ved API-feil eller parsefeil.

```kotlin
val auth = GitHubAppAuth(appId = "12345", privateKeyPem = pemKey, installationId = "67890")
val token = auth.getToken() // suspend — cacher og fornyer automatisk, atomisk under parallell last, kaster GitHubApiException
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `getToken` | `(): String` (suspend) | Henter gyldig installation token, thread-safe, kaster GitHubApiException |

#### GitHubIssueRoutes (`GitHubIssueRoutes.kt`)
Ktor-ruter for issue-oppretting (multipart med bilder) og GitHub webhook-mottak med HMAC-SHA256-signaturverifisering.

```kotlin
routing {
    gitHubIssueRoutes(GitHubIssueRoutesConfig(
        issueService = issueService,
        imageService = imageService,
        rateLimiter = RateLimiter(maxAttempts = 10, windowMs = 60_000),
        webhookSecret = "secret"
    ))
}
```

Ruter: `POST /api/issues` (oppretter issue med bilder), `POST /api/issues/webhook` (GitHub webhook for opprydding).

Hjelpefunksjoner: `verifyWebhookSignature(payload, signature, secret): Boolean`, `parseWebhookAction(payload): Pair<String, Int>?`

#### ImageUploadService (`ImageUploadService.kt`)
Sikker bildeopplasting med magic byte-validering (PNG/JPEG/WEBP), stoerrelsesbegrensning og tilfeldige filnavn.

```kotlin
val service = ImageUploadService(ImageUploadService.Config(
    uploadDir = "/uploads/issues",
    baseUrl = "https://example.com/uploads/issues",
    maxFileSize = 2 * 1024 * 1024,
    maxImagesPerIssue = 3
))
val url = service.uploadImage(issueNumber = 1, data = bytes, originalFilename = "bilde.png")
```

| Funksjon | Signatur | Beskrivelse |
|----------|----------|-------------|
| `uploadImage` | `(issueNumber, data, originalFilename): Result<String>` | Laster opp bilde, returnerer URL |
| `deleteIssueImages` | `(issueNumber: Int)` | Sletter alle bilder for en issue |
| `cleanupClosedIssues` | `(openIssueNumbers: Set<Int>)` | Sletter bilder for lukkede issues |

---

### Modeller

#### Exceptions (`Exceptions.kt`)
Typed exceptions som mappes til HTTP-statuskoder via StatusPages:

| Exception | HTTP-status |
|-----------|-------------|
| `BadRequestException` | 400 Bad Request |
| `NotFoundException` | 404 Not Found |
| `ForbiddenException` | 403 Forbidden |
| `RateLimitException` | 429 Too Many Requests |
| `AuthenticationException` | 401 Unauthorized |
| `GitHubApiException` | 500 Internal Server Error (fra GitHub API-feil) |

#### TotpModels (`TotpModels.kt`)
Serialiserbare dataklasser for TOTP-operasjoner:

- **`TotpSetupResult`** — `secret: String` (kryptert), `qrUri: String`
- **`TotpConfirmResult`** — `backupCodes: List<String>`

---

## Integrasjon

Grunnmur brukes via Gradle composite build (`includeBuild`), ikke mavenLocal.

### settings.gradle.kts (i appen)
```kotlin
listOf("grunnmur/", "../../grunnmur/", "../grunnmur/").forEach { path ->
    if (file(path).exists()) {
        includeBuild(path)
        return@forEach
    }
}
```

### Docker (docker-compose.yml i appen)
```yaml
services:
  backend:
    build:
      context: ./backend
      additional_contexts:
        grunnmur: ../grunnmur
```

## Bygg

```bash
./gradlew build    # Bygg og kjoer tester
./gradlew test     # Kjoer kun tester
```

## Versjoner

- Kotlin 2.4.0, Ktor 3.5.0 (Server + Client CIO), Exposed 1.3.0, JVM 25
- kotlinx-serialization-json 1.11.0, Jakarta Mail 2.1.5, Flyway 11.19.1
- kotlin-onetimepassword 2.4.1, SLF4J 2.0.18
- Testcontainers 1.21.4, PostgreSQL JDBC 42.7.11 (integrasjonstester)
- Alle avhengigheter er `compileOnly` — apper bruker sine egne versjoner
- Versjoner MA holdes i sync med appene (binaer inkompatibilitet)

## Brukes av

- [biologportal](https://github.com/TommySkogstad/biologportal)
- [6810](https://github.com/TommySkogstad/6810)
- [styreportal](https://github.com/TommySkogstad/styreportal)
- [smart-casual](https://github.com/TommySkogstad/smart-casual)
- [maskemester](https://github.com/TommySkogstad/maskemester)
- [vinforalle](https://github.com/TommySkogstad/vinforalle)
