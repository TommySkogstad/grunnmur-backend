# grunnmur

Felles Kotlin-bibliotek for alle Ktor-apper i portefoljen.
Eies av utviklingsavdelingen, brukes av lo-finans, biologportal, 6810 og summa-summarum.

## Hva er grunnmur?

Standardiserte patterns som tidligere var copy-pastet med variasjoner mellom apper:

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

- **Kotlin**: 2.1.0
- **Ktor**: 3.0.3 (compileOnly)
- **Exposed**: 0.57.0 (compileOnly)
- **JVM**: 21
- **Publisering**: `./gradlew publishToMavenLocal`

## Bruk i apper

```kotlin
// build.gradle.kts
repositories {
    mavenLocal()
}
dependencies {
    implementation("no.grunnmur:grunnmur:1.0.0")
}
```

## Konvensjoner

- Ktor og Exposed er `compileOnly` — apper har sine egne versjoner
- AuditLogService bruker strenger for action/entityType — apper definerer egne enums
- Feilmeldinger paa norsk
- Alle tider via `TimeUtils.nowOslo()`
- Tester kjoeres med `./gradlew test`

## Utvikling

```bash
./gradlew build          # Bygg
./gradlew test           # Kjoer tester
./gradlew publishToMavenLocal  # Publiser lokalt
```
