# grunnmur

Felles Kotlin-bibliotek for alle Ktor-apper i portefoljen.
Brukes av lo-finans, biologportal, 6810 og summa-summarum.

## Moduler

| Modul | Fil | Beskrivelse |
|-------|-----|-------------|
| Exceptions | `Exceptions.kt` | BadRequest, NotFound, Forbidden, RateLimit, Authentication |
| RouteUtils | `RouteUtils.kt` | `requireIntParam()`, `requireParam()`, `checkRateLimit()` |
| StatusPages | `StatusPagesConfig.kt` | `grunnmurExceptionHandlers()` for Ktor StatusPages |
| CSRF | `CsrfPlugin.kt` | `GrunnmurCsrf` Ktor-plugin med konfigurerbare exempt paths |
| RateLimiter | `RateLimiter.kt` | In-memory sliding window med cleanup og maxEntries |
| TimeUtils | `TimeUtils.kt` | `nowOslo()`, `formatDateTime()`, `formatDateTimeIso()` |
| AuditLog | `AuditLogTable.kt` + `AuditLogService.kt` | Database-basert revisjonslogging med filtrering |

## Teknisk

- **Kotlin**: 2.3.10
- **Ktor**: 3.4.1 (compileOnly)
- **Exposed**: 0.61.0 (compileOnly)
- **kotlinx-serialization-json**: 1.10.0 (compileOnly)
- **JVM**: 21
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
- Versjoner i grunnmur MÅ matche appene (Kotlin metadata og Exposed er binar-inkompatible)
- AuditLogService bruker strenger for action/entityType — apper definerer egne enums
- Feilmeldinger pa norsk
- Alle tider via `TimeUtils.nowOslo()`

## Utvikling

```bash
./gradlew build    # Bygg og kjoer tester
./gradlew test     # Kjoer tester
```

## Viktig

- Ved Dependabot-oppgraderinger: oppgrader grunnmur FORST, deretter alle 4 apper til samme versjoner
- Kotlin metadata 2.3 kan ikke leses av 2.1-kompilator (NoClassDefFoundError)
- Exposed-versjoner ma vaere identiske mellom grunnmur og apper
