# grunnmur

Felles Kotlin-bibliotek for alle Ktor-apper i portefoljen.
Brukes av lo-finans, biologportal, 6810 og summa-summarum.

Sist oppdatert: 2026-04-23

## Komplett modulreferanse (23 filer, 18 moduler)

### Auth og sikkerhet

#### CsrfPlugin (`CsrfPlugin.kt`) — Ktor ApplicationPlugin
CSRF-beskyttelse som validerer X-CSRF-Token header mot csrf_token cookie for POST/PUT/DELETE/PATCH.
Hopper over sjekk for stier i `exemptPaths` og forespoersler uten auth-cookie.

```kotlin
install(GrunnmurCsrf) {
    exemptPaths = setOf("/api/auth/login", "/api/auth/request-code", "/api/health")
    authCookieName = "auth_token"   // default
    csrfCookieName = "csrf_token"   // default
    csrfHeaderName = "X-CSRF-Token" // default
}
```

Eksporterer: `GrunnmurCsrf` (plugin), `CsrfConfig` (class)

#### RateLimiter (`RateLimiter.kt`) — class
In-memory sliding window rate limiter. Traadsikker (ConcurrentHashMap). Automatisk cleanup hvert 60s. `maxEntries` beskytter mot minnelekkasje.

```kotlin
val limiter = RateLimiter(maxAttempts = 5, windowMs = 300_000, maxEntries = 10_000)
```

- `isAllowed(key: String): Boolean` — sjekker og registrerer
- `reset(key: String)` — nullstiller (etter vellykket login)
- `remainingAttempts(key: String): Int`
- `retryAfterSeconds(key: String): Long?` — sekunder til vindu utloeper (null hvis ikke blokkert)
- `windowMs: Long` — tidsvinduet (public property)
- `clear()` — kun for testing
- `size(): Int` — for monitorering

#### RateLimitPresets (`RateLimitPresets.kt`) — funksjoner og class
Ferdigkonfigurerte rate limitere for vanlige bruksscenarier. Bygger paa RateLimiter.

**CompositeRateLimiter** — sjekker flere vinduer (alle maa tillate):
```kotlin
val limiter = CompositeRateLimiter(
    RateLimiter(maxAttempts = 5, windowMs = 60_000),
    RateLimiter(maxAttempts = 10, windowMs = 3_600_000)
)
```
- `isAllowed(key: String): Boolean` — alle limitere maa tillate
- `reset(key: String)` — nullstiller alle
- `remainingAttempts(key: String): Int` — minimum av alle
- `retryAfterSeconds(key: String): Long?` — maksimum av alle

**AuthRateLimiter** — kombinerer IP-basert og identifikator-basert limiting:
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
- `isAllowed(ip: String, identifier: String): Boolean` — begge maa tillate
- `reset(ip: String, identifier: String)` — nullstiller begge
- `remainingAttempts(ip: String, identifier: String): Int` — minimum av begge
- `retryAfterSeconds(ip: String, identifier: String): Long?` — maksimum av begge
- Identifikatorer hashes med SHA-256 (ikke lagret i klartekst)

**Preset-funksjoner:**
- `authRateLimiter()` — 5/min + 10/time per IP (for send-otp, verify-otp)
- `authRateLimiterWithIdentifier()` — IP (5/min + 10/time) + identifikator (5/15min)
- `apiRateLimiterAuthenticated()` — 60/min per IP
- `apiRateLimiterAnonymous()` — 20/min per IP

Brukseksempel i apper:
```kotlin
// Opprett én gang i Application.kt
val authLimiter = authRateLimiter()
val apiLimiterAuth = apiRateLimiterAuthenticated()
val apiLimiterAnon = apiRateLimiterAnonymous()

// Auth-ruter
post("/api/auth/send-otp") {
    val ip = call.getClientIp()
    call.checkRateLimit(
        authLimiter.isAllowed(ip),
        retryAfterSeconds = authLimiter.retryAfterSeconds(ip)
    )
}

// API-ruter
get("/api/data") {
    val ip = call.getClientIp()
    val limiter = if (call.getUserId() != null) apiLimiterAuth else apiLimiterAnon
    call.checkRateLimit(limiter.isAllowed(ip))
}
```

#### EncryptionUtils (`EncryptionUtils.kt`) — object
AES-256-GCM-kryptering. Nokler: 64 hex-tegn (32 bytes). Output: Base64(IV || ciphertext || GCM-tag). Ingen fallback til plaintext.

- `encrypt(plaintext: String, hexKey: String): String`
- `decrypt(ciphertext: String, hexKey: String): String?` — returnerer null ved feil
- `generateKey(): String` — ny tilfeldig 64 hex-tegn noekkel
- `base64KeyToHex(base64Key: String): String` — for migrering

#### TotpService (`TotpService.kt`) — object
TOTP tofaktorautentisering (RFC 6238). SHA1, 30s steg, 6 siffer, +-2 vinduer toleranse. Bruker EncryptionUtils for kryptering. Dev-kode: "123456".

- `setupTotp(encryptionKey: String, issuer: String, accountName: String): TotpSetupResult` — genererer hemmelighet + QR-URI
- `confirmTotp(encryptedSecret: String, encryptionKey: String, code: String): Boolean`
- `verifyTotp(encryptedSecret: String, encryptionKey: String, code: String, devMode: Boolean = false): Boolean`
- `generateBackupCodes(count: Int = 10): List<String>` — format XXXX-XXXX
- `verifyBackupCode(code: String, encryptedCodes: String, encryptionKey: String): Pair<Boolean, String?>` — returnerer (gyldig, oppdaterte krypterte koder)
- `encryptBackupCodes(codes: List<String>, encryptionKey: String): String`
- `disableTotp(): Triple<Boolean, String?, String?>` — null-verdier for DB-nullstilling

#### TotpModels (`TotpModels.kt`) — data classes
- `TotpSetupResult(secret: String, qrUri: String)` — @Serializable
- `TotpConfirmResult(backupCodes: List<String>)` — @Serializable

#### OtpUtils (`OtpUtils.kt`) — object
One-Time Password med SHA-256. Dev-modus: kode "123456" fungerer alltid. Lagring/utsending er appens ansvar.

- `generateCode(): String` — 6-sifret (100000-999999)
- `hashCode(code: String): String` — SHA-256, 64 hex-tegn
- `verify(code, storedHash, expiresAt, attempts, maxAttempts = 3, devMode = false): OtpVerificationResult`

`OtpVerificationResult` (sealed class): `Success`, `InvalidCode`, `Expired`, `TooManyAttempts`

#### AuthExtensions (`AuthExtensions.kt`) — extension functions paa ApplicationCall
- `getUserId(): Int?` — fra JWT "userId"-claim
- `requireUserId(): Int` — kaster AuthenticationException
- `getUserEmail(): String?` — fra JWT "email"-claim

### HTTP og routing

#### RouteUtils (`RouteUtils.kt`) — extension functions paa ApplicationCall
- `requireIntParam(name: String): Int` — kaster BadRequestException
- `requireParam(name: String): String` — kaster BadRequestException
- `checkRateLimit(allowed: Boolean, message: String, retryAfterSeconds: Long?)` — kaster RateLimitException
- `getClientIp(): String` — sjekker CF-Connecting-IP -> X-Real-IP -> X-Forwarded-For -> remoteAddress

#### StatusPagesConfig (`StatusPagesConfig.kt`) — extension function paa StatusPagesConfig
`grunnmurExceptionHandlers()` — mapper grunnmur-exceptions til HTTP-statuskoder:
- BadRequestException -> 400, NotFoundException -> 404, ForbiddenException -> 403
- RateLimitException -> 429, AuthenticationException -> 401
- IllegalArgumentException -> 400, Throwable -> 500 (skjuler detaljer i prod via KTOR_ENV=production)

### Database

#### FlywayMigration (`FlywayMigration.kt`) — object
Felles Flyway-konfigurasjon: baselineOnMigrate=true, baselineVersion="0", cleanDisabled=true.

- `configure(dataSource: DataSource, locations: List<String> = listOf("classpath:db/migration")): Flyway`
- `migrate(dataSource: DataSource, locations: List<String>): Int` — returnerer antall kjorte migrasjoner

#### AuditLogTable (`AuditLogTable.kt`) — object AuditLogs : Table("audit_logs")
Exposed-tabelldefinisjón: id, userId, userEmail, action, entityType, entityId, details, ipAddress, createdAt.
Indekser: (entityType, entityId), (createdAt), (userId).

#### AuditLogService (`AuditLogService.kt`) — class
Revisjonslogging med streng-basert action/entityType (apper definerer egne enums). Feil i logging stopper ikke hovedoperasjonen.

- `log(userId: Int?, userEmail: String = "system", action: String, entityType: String, entityId: Int? = null, details: String? = null, ipAddress: String? = null)`
- `findAll(action?, entityType?, userId?, startDate?, endDate?, limit = 100, offset = 0): List<AuditLogEntry>`
- `count(action?, entityType?, userId?, startDate?, endDate?): Long`
- `cleanupOldLogs(retentionDays: Int = 365): Int`

`AuditLogEntry` (data class): id, userId, userEmail, action, entityType, entityId, details, ipAddress, timestamp

#### PaginatedResponse (`PaginatedResponse.kt`) — @Serializable data class
`PaginatedResponse<T>(items: List<T>, total: Long, limit: Int, offset: Long)`

### Validering

#### Validators (`Validators.kt`) — object
Ren Kotlin (ingen Ktor/Exposed). Returnerer `ValidationResult(isValid: Boolean, errors: List<String>)`.

- `validateEmail(email: String): ValidationResult` — RFC 5322 forenklet
- `isValidEmail(email: String): Boolean`
- `validatePhone(phone: String?, strict: Boolean = true): ValidationResult` — strict = norsk 8-sifret
- `isValidPhone(phone: String): Boolean` — liberalt format
- `validateUrl(url: String, maxLength: Int = 2048): ValidationResult` — blokkerer javascript:, data:, file: etc
- `validateName(name, fieldName = "Navn", minLength = 1, maxLength = 255): ValidationResult` — XSS-sjekk
- `validateTextField(value?, fieldName, required, minLength, maxLength): ValidationResult`
- `validateSearchQuery(query?, maxLength = 100): ValidationResult` — SQL-injeksjonsbeskyttelse
- `validateOrganizationNumber(orgNumber?): ValidationResult` — 9 siffer
- `validatePassword(password: String): ValidationResult` — 8+ tegn, bokstav+tall, blokkerer vanlige
- `sanitizeHtml(input: String): String` — erstatter <>&"' med HTML-entiteter

#### InputSanitizer (`InputSanitizer.kt`) — object
Sanitering for GitHub Issues. Alle funksjoner er rene og traadsikre.

- `sanitize(text: String, maxLength: Int): String` — kombinerer alle steg
- `sanitizeMarkdown(text: String): String` — fjerner markdown-lenker/bilder, HTML-tags, script-blokker
- `sanitizeMentions(text: String): String` — wrapper @mentions i backticks
- `filterSecrets(text: String): String` — maskerer JWT, GitHub-tokens (ghp_/gho_/ghu_/ghs_/ghr_), sk_-tokens, lange hex
- `truncate(text: String, maxLength: Int): String`

Konstanter: `MAX_DESCRIPTION_LENGTH = 2000`, `MAX_LOGS_LENGTH = 10000`, `MAX_TITLE_LENGTH = 256`

### Tid

#### TimeUtils (`TimeUtils.kt`) — object
Europe/Oslo-tidssone for konsistent norsk tid.

- `nowOslo(): LocalDateTime`
- `formatDateTime(dt: LocalDateTime): String` — "yyyy-MM-dd HH:mm"
- `formatDateTimeIso(dt: LocalDateTime): String` — ISO-format
- `parseDateTime(value: String): LocalDateTime` — stoetter "2026-03-15T14:30:00" og "2026-03-15"

Felt: `OSLO_ZONE: ZoneId`, `isoDateTime: DateTimeFormatter`, `isoDate: DateTimeFormatter`

### Tjenester

#### SmtpClient (`SmtpClient.kt`) — class
Jakarta Mail SMTP-klient. Stoetter plain text + HTML (multipart/alternative), vedlegg (multipart/mixed), traad-kobling (Message-ID/In-Reply-To/References), rate limiting mellom sendinger, dev-modus (logger i stedet for aa sende). **SMTP Session er cached og gjenbrukt** (ikke opprettet paa nytt per sending).

- `send(message: EmailMessage, forceDelivery: Boolean = false): SendResult`
- `sendWithMessageId(message: EmailMessage, messageId: String, forceDelivery: Boolean = false): SendResult`

Dataklasser:
- `SmtpConfig(host, port = 587, user, password, from, fromName, requireAuth = true, devMode = false, timeoutMs = 10000, minIntervalMs = 100)`
- `EmailMessage(to, subject, body, htmlBody?, replyTo?, inReplyTo?, attachments = [], from?, fromName?)`
- `EmailAttachment(filename, content: ByteArray, contentType)`
- `SendResult(success: Boolean, messageId?, error?)`

#### GitHubIssueService (`GitHubIssueService.kt`) — class
GitHub API for issues. Stoetter PAT og GitHub App-autentisering. Saniterer all input via InputSanitizer. **Implementerer Closeable — lukk instansen med `close()` eller try-with-resources.**

- `createIssue(title, senderName, senderEmail, description, consoleLogs?, imageFilenames?, labels): GitHubIssueResponse` (suspend)
- `updateIssueBody(issueNumber: Int, body: String)` (suspend)
- `buildBody(senderName, senderEmail, description, consoleLogs?, imageFilenames?): String`
- `close()` — lukker den interne HttpClient-instansen

Config: `Config(token?, appAuth?, repo, uploadDir?, publicBaseUrl?)`

#### GitHubAppAuth (`GitHubAppAuth.kt`) — class
GitHub App JWT (RS256) autentisering med installation token-caching. Leser faktisk `expires_at` fra GitHub API-respons og fornyer automatisk 5 min foer utloep. **Implementerer Closeable — lukk instansen med `close()` eller try-with-resources.**

- `getToken(): String` (suspend) — returnerer gyldig installation token, cacher basert paa faktisk utløpstid fra API
- `parseExpiresAt(expiresAt: String): Long` — parser ISO-8601 `expires_at` fra GitHub til millisekunder siden epoch
- `close()` — lukker den interne HttpClient-instansen

Konstruktoer: `GitHubAppAuth(appId: String, privateKeyPem: String, installationId: String)`

#### GitHubIssueRoutes (`GitHubIssueRoutes.kt`) — extension function paa Route
Registrerer ruter for issue-opprettelse og GitHub webhook.

- `Route.gitHubIssueRoutes(config: GitHubIssueRoutesConfig)` — registrerer POST /api/issues og POST /api/issues/webhook

Hjelpefunksjoner:
- `verifyWebhookSignature(payload: ByteArray, signature: String, secret: String): Boolean` — HMAC-SHA256
- `parseWebhookAction(payload: String): Pair<String, Int>?` — (action, issueNumber)

Dataklasser: `GitHubIssueRoutesConfig`, `CreateIssueResponse`

#### ImageUploadService (`ImageUploadService.kt`) — class
Sikker bildeopplasting. Validerer filtype via magic bytes (PNG/JPEG/WEBP), haandhever stoerrelsesbegrensning, genererer tilfeldige filnavn (UUID).

- `uploadImage(issueNumber: Int, data: ByteArray, originalFilename: String): Result<String>` — returnerer offentlig URL
- `deleteIssueImages(issueNumber: Int)` — sletter alle bilder for en issue
- `cleanupClosedIssues(openIssueNumbers: Set<Int>)` — sletter bilder for lukkede issues

Config: `Config(uploadDir, baseUrl, repo = "", maxFileSize = 2MB, maxImagesPerIssue = 3)`

### Modeller

#### Exceptions (`Exceptions.kt`) — 5 exception-klasser
- `BadRequestException(message)` -> 400
- `NotFoundException(message)` -> 404
- `ForbiddenException(message)` -> 403
- `RateLimitException(message, retryAfterSeconds?)` -> 429 (med `Retry-After`-header naar tilgjengelig)
- `AuthenticationException(message)` -> 401

## Teknisk

- **Kotlin**: 2.3.20
- **Ktor**: 3.4.2 (compileOnly — Server + Client CIO)
- **Exposed**: 1.2.0 (compileOnly)
- **kotlinx-serialization-json**: 1.11.0 (compileOnly)
- **Jakarta Mail**: 2.1.5 (compileOnly)
- **Flyway**: 11.8.2 (compileOnly — pinnet pga bug i 11.20.3/12.x)
- **kotlin-onetimepassword**: 2.4.1 (compileOnly + testImplementation)
- **SLF4J**: 2.0.17 (compileOnly)
- **JVM**: 25
- **Tester**: JUnit 5.14.3

## Integrasjon i apper

Grunnmur brukes via Gradle composite build (`includeBuild`), IKKE mavenLocal.

### Lokal utvikling

Appene refererer til grunnmur via `settings.gradle.kts`:
```kotlin
// settings.gradle.kts (i appen)
listOf("grunnmur/", "../../grunnmur/", "../grunnmur/").forEach { path ->
    if (file(path).exists()) {
        includeBuild(path)
        return@forEach
    }
}
```

Fallback-stier:
1. `grunnmur/` — Docker (COPY --from=grunnmur)
2. `../../grunnmur/` — Lokal utvikling (sibling-dirs)
3. `../grunnmur/` — CI (actions/checkout path)

### Docker

Appenes `docker-compose.yml` bruker `additional_contexts`:
```yaml
services:
  backend:
    build:
      context: ./backend
      additional_contexts:
        grunnmur: ../grunnmur
```

Appenes `Dockerfile` kopierer grunnmur inn:
```dockerfile
COPY --from=grunnmur . /app/grunnmur
```

### CI (GitHub Actions)

To-nivaa CI-pipeline (kun backend, ingen frontend):

**Hurtigsjekk** (`test.yml`) — kjores ved push til `main` og `auto/**`:
- `compileKotlin` + `compileTestKotlin` (verifiserer at koden kompilerer)
- Kjoretid: ~1 minutt

**Fullstendig testsuite** (`test-full.yml`) — kjores ved PR mot `main` og manuelt (`workflow_dispatch`):
- Alle JUnit 5-tester
- Dependabot-PRer utloeser kun hurtigsjekk, ikke full suite
- Kjoretid: ~2-3 minutter

**Nattlig** — ci-fix-daily (kl 04:30) kjoerer full testsuite og fikser eventuelle feil.

Begge workflows har `concurrency: cancel-in-progress` for aa avbryte utdaterte kjoeringer.

Apper sjekker ut grunnmur med `GRUNNMUR_TOKEN` secret:
```yaml
- uses: actions/checkout@v4
  with:
    repository: TommySkogstad/grunnmur
    token: ${{ secrets.GRUNNMUR_TOKEN }}
    path: grunnmur
```

## Konvensjoner

- Ktor og Exposed er `compileOnly` — apper har sine egne versjoner
- Versjoner i grunnmur MAA matche appene (Kotlin metadata og Exposed er binaer-inkompatible)
- AuditLogService bruker strenger for action/entityType — apper definerer egne enums
- Feilmeldinger paa norsk
- Alle tider via `TimeUtils.nowOslo()`

## Utvikling

```bash
./gradlew build    # Bygg og kjoer tester
./gradlew test     # Kjoer tester
```

## Versjonsstrategi

`build.gradle.kts` har `version = "1.0.0"` hardkodet med hensikt. Appene (lo-finans, biologportal, 6810, styreportal) konsumerer grunnmur via Gradle composite build (`includeBuild`) — ikke via Maven-koordinater. Sporbarhet skjer via git commit-hash, ikke versjonsnummer.

`publishing`-blokken i `build.gradle.kts` eksisterer for fremtidig publisering til GitHub Packages, men er ikke i rutinemessig bruk. Versjonen bumpes kun manuelt ved breaking changes — ingen automatisk bump, ingen SNAPSHOT-konvensjon, ingen CHANGELOG.

**Hvis rutinemessig publisering til GitHub Packages tas i bruk senere**, bytt til en av:
- Dato-basert versjon: `version = "1.0.${LocalDate.now().format(YYYYMMDD)}"` (bumpes per release)
- Git commit-hash suffix: `version = "1.0.0-${gitShortHash}"` (entydig per commit)

Inntil da holdes versjonen fast. Dette er en bevisst avgjoerelse, ikke en glemt TODO.

## Viktig

- Ved Dependabot-oppgraderinger: oppgrader grunnmur FOERST, deretter alle 4 apper til samme versjoner
- Kotlin metadata 2.3 kan ikke leses av 2.1-kompilator (NoClassDefFoundError)
- Exposed-versjoner maa vaere identiske mellom grunnmur og apper
- **Kryssrepo-avhengigheter**: Grunnmur er et delt bibliotek. Naar ny funksjonalitet legges til, opprett GitHub issue paa grunnmur FOERST, deretter issues paa alle apper som skal bruke den nye modulen med `Blokkert av: TommySkogstad/grunnmur#nummer` i issue-bodyen. Issue-triage haandterer avhengighetsrekkefoeolgen automatisk — blokkerte issues venter til grunnmur-issuen er lukket.
