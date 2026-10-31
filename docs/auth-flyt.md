# Innlogging med OTP, JWT-cookie og CSRF

grunnmur har byggesteinene til innloggingen, men ikke hele flyten. Appen lagrer OTP-hashen, sender koden, lager JWT-en og konfigurerer Ktor sin `authenticate`. grunnmur sørger for at koden lages og sjekkes likt i alle appene, at cookiene får samme flagg, og at muterende kall uten riktig CSRF-token stoppes.

```mermaid
%% Avledet fra: src/main/kotlin/no/grunnmur/{OtpUtils,CsrfPlugin,RateLimitPresets,RouteUtils,AuthExtensions,TotpService,StatusPagesConfig}.kt (2026-10-31)
sequenceDiagram
    autonumber
    participant N as Nettleser
    participant A as Appen
    participant G as grunnmur

    Note over N,G: 1. Be om engangskode
    N->>A: POST /api/auth/request-code
    A->>G: getClientIp + AuthRateLimiter.isAllowed(ip, e-post)
    G-->>A: for mange forsøk gir RateLimitException (429)
    A->>G: OtpUtils.generateCode + hashCode(kode, salt)
    A->>A: lagrer hash og utløpstid, sender koden

    Note over N,G: 2. Logg inn med koden
    N->>A: POST /api/auth/login (kode)
    A->>G: OtpUtils.verify(kode, hash, utløp, forsøk)
    G-->>A: Success, InvalidCode, Expired eller TooManyAttempts
    opt Totrinns er slått på
        A->>G: TotpService.verifyTotp
    end
    A->>A: lager JWT
    A->>G: setAuthCookie(jwt) + setCsrfCookie(generateCsrfToken())
    A-->>N: auth_token (HttpOnly) og csrf_token (lesbar for JS)

    Note over N,G: 3. Endre noe som innlogget
    N->>A: POST/PUT/PATCH/DELETE med cookies + X-CSRF-Token
    A->>G: GrunnmurCsrf sammenligner header og cookie
    G-->>N: mangler eller ulik gir 403
    A->>G: requireUserId() fra JWT-claim
    G-->>A: mangler gir AuthenticationException (401)
    A-->>N: svar

    Note over N,G: 4. Logg ut
    N->>A: POST /api/auth/logout
    A->>G: clearAuthCookies()
```

Detaljer som ikke får plass i figuren:

- `GrunnmurCsrf` sjekker bare POST, PUT, PATCH og DELETE, hopper over stier i `exemptPaths`, og slipper gjennom kall uten auth-cookie. Innloggingsrutene må derfor stå i `exemptPaths`, og beskyttelsen gjelder fra cookien er satt.
- Sammenligningen av token skjer i konstant tid (`MessageDigest.isEqual`).
- Begge cookiene har `SameSite=Strict` i produksjon og `Lax` med `devMode = true`, og levetid 30 dager som standard.
- `OtpUtils.verify` sjekker i denne rekkefølgen: forsøksgrense, utløp, dev-kode og til slutt hash. Dev-koden virker bare med `devMode = true`.
- `authRateLimiterWithIdentifier()` teller både per IP (5 per minutt og 10 per time) og per identifikator (5 per 15 minutter). Identifikatoren hashes med et salt som lages ved oppstart.
- Unntakene blir til HTTP-svar i `grunnmurExceptionHandlers`, som appen installerer i `StatusPages`.
