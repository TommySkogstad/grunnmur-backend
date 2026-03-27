<p align="center">
  <img src="logo.svg" alt="grunnmur" width="80" height="80">
</p>

<h1 align="center">grunnmur</h1>

<p align="center">Felles Kotlin-bibliotek for Ktor-appene i portefoljen.<br>Standardiserer patterns som tidligere var duplisert med variasjoner mellom apper.</p>

## Innhold

### Exceptions (`no.grunnmur`)
Typed exceptions som mappes til HTTP-statuskoder via StatusPages:

```kotlin
throw BadRequestException("Ugyldig e-postadresse")   // -> 400
throw NotFoundException("Bruker ikke funnet")         // -> 404
throw ForbiddenException("Krever admin-tilgang")      // -> 403
throw RateLimitException()                            // -> 429
throw AuthenticationException()                       // -> 401
```

### RouteUtils (`no.grunnmur`)
Extension functions for Ktor `ApplicationCall`:

```kotlin
get("/{id}") {
    val id = call.requireIntParam("id")           // Kaster BadRequestException ved ugyldig
    val slug = call.requireParam("slug")          // Kaster BadRequestException ved manglende
    call.checkRateLimit(limiter.isAllowed(ip))     // Kaster RateLimitException ved overskridelse
}
```

### StatusPages (`no.grunnmur`)
Installer standard exception-handlers:

```kotlin
install(StatusPages) {
    grunnmurExceptionHandlers()
}
```

### CSRF Plugin (`no.grunnmur`)
Konfigurerbar CSRF-beskyttelse:

```kotlin
install(GrunnmurCsrf) {
    exemptPaths = setOf("/api/auth/login", "/api/auth/request-code", "/api/health")
    authCookieName = "auth_token"
}
```

### RateLimiter (`no.grunnmur`)
In-memory sliding window med automatisk cleanup:

```kotlin
val loginLimiter = RateLimiter(maxAttempts = 5, windowMs = 300_000)
val searchLimiter = RateLimiter(maxAttempts = 60, windowMs = 60_000)

if (!loginLimiter.isAllowed(clientIp)) {
    throw RateLimitException()
}

// Etter vellykket login
loginLimiter.reset(clientIp)
```

### TimeUtils (`no.grunnmur`)
Konsistent norsk tid:

```kotlin
val now = TimeUtils.nowOslo()
val formatted = TimeUtils.formatDateTime(now)  // "2026-03-07 14:30"
```

### AuditLog (`no.grunnmur`)
Database-basert revisjonslogging:

```kotlin
val auditLog = AuditLogService()

auditLog.log(
    userId = user.id,
    userEmail = user.email,
    action = "CREATE",
    entityType = "POST",
    entityId = post.id,
    details = "Opprettet beskjed: ${post.title}",
    ipAddress = clientIp
)

// Filtrering
val logs = auditLog.findAll(action = "DELETE", limit = 50)

// Retention
auditLog.cleanupOldLogs(retentionDays = 365)
```

### Validators (`no.grunnmur`)
Validering av vanlige inputtyper med XSS-beskyttelse:

```kotlin
Validators.email("bruker@eksempel.no")      // true/false
Validators.phone("+4712345678")             // true/false
Validators.url("https://eksempel.no")       // true/false
Validators.name("Ola Nordmann")             // true/false
Validators.text("Fritekst her", maxLength = 500)
Validators.search("sokeord")
Validators.orgNumber("123456789")           // Norsk org.nr
Validators.password("Sikkert passord")
```

### EncryptionUtils (`no.grunnmur`)
AES-256-GCM-kryptering med hex-nokler:

```kotlin
val encrypted = EncryptionUtils.encrypt(plaintext, hexKey)
val decrypted = EncryptionUtils.decrypt(encrypted, hexKey)
```

### RouteUtils.getClientIp (`no.grunnmur`)
Hent klientens IP med proxy-stotte:

```kotlin
val clientIp = call.getClientIp()  // Sjekker CF-Connecting-IP, X-Forwarded-For, fallback
```

### InputSanitizer (`no.grunnmur`)
Sanitering av brukerinput for GitHub Issues — fjerner markdown/HTML, maskerer hemmeligheter, beskytter mentions:

```kotlin
val sanitized = InputSanitizer.sanitizeDescription(userInput)
val safeTitle = InputSanitizer.sanitizeTitle(title)
val safeLogs = InputSanitizer.sanitizeLogs(logOutput)
```

### GitHubIssueService (`no.grunnmur`)
Oppretter GitHub Issues via API med automatisk sanitering:

```kotlin
val service = GitHubIssueService(GitHubIssueService.Config(
    token = "ghp_...",
    repo = "owner/repo"
))

val result = service.createIssue(
    title = "Feilrapport",
    description = "Beskrivelse av problemet",
    labels = listOf("bug")
)
```

### ImageUploadService (`no.grunnmur`)
Sikker bildeopplasting med magic byte-validering og stoerrelsesbegrensning:

```kotlin
val imageService = ImageUploadService(ImageUploadService.Config(
    uploadDir = "/uploads/issues",
    baseUrl = "https://example.com/uploads/issues",
    maxFileSize = 2 * 1024 * 1024,
    maxImagesPerIssue = 3
))

val url = imageService.uploadImage(issueNumber = 1, data = bytes, originalFilename = "bilde.png")
```

### GitHubIssueRoutes (`no.grunnmur`)
Ktor-ruter for issue-oppretting og GitHub webhook-mottak:

```kotlin
routing {
    githubIssueRoutes(GitHubIssueRoutesConfig(
        issueService = issueService,
        imageService = imageService,
        rateLimiter = RateLimiter(maxAttempts = 10, windowMs = 60_000),
        webhookSecret = "webhook-secret"
    ))
}
```

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

- Kotlin 2.3.20, Ktor 3.4.1 (Server + Client CIO), Exposed 0.61.0, JVM 21
- SLF4J 2.0.17 (compileOnly)
- Ktor og Exposed er `compileOnly` — apper bruker sine egne versjoner
- Versjoner MÅ holdes i sync med appene (binar inkompatibilitet)

## Brukes av

- [lo-finans](https://github.com/TommySkogstad/lo-finans)
- [biologportal](https://github.com/TommySkogstad/biologportal)
- [6810](https://github.com/TommySkogstad/6810)
- [summa-summarum](https://github.com/TommySkogstad/Summa-Summarum)
