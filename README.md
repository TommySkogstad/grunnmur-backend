# grunnmur

Felles Kotlin-bibliotek for Ktor-appene i portefoljen. Standardiserer patterns som tidligere var duplisert med variasjoner mellom apper.

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

- Kotlin 2.3.10, Ktor 3.4.1, Exposed 0.61.0, JVM 21
- Ktor og Exposed er `compileOnly` — apper bruker sine egne versjoner
- Versjoner MÅ holdes i sync med appene (binar inkompatibilitet)

## Brukes av

- [lo-finans](https://github.com/TommySkogstad/lo-finans)
- [biologportal](https://github.com/TommySkogstad/biologportal)
- [6810](https://github.com/TommySkogstad/6810)
- [summa-summarum](https://github.com/TommySkogstad/Summa-Summarum)
