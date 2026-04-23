package no.grunnmur

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class GitHubAppAuthTest {

    // Test RSA key pair (2048 bit) — kun for testing, ikke en reell noekkel
    private val testPrivateKey = """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEowIBAAKCAQEA2OD0AHsbfr5AaYpFJSzfjVSzDxCLen9PazjIZbHKIH7PVbRs
        AeEN4Ym1Zjz7Tlr+SyN87K201/JpsxqkKkFL+k+iG+JuwCx/x6mHl8kk/0v9YwxF
        XOqp1ZaDgQor0mHmjrBhi9T+ZYIn+041uIVpir1UVAWLwU1XHFhAt4+fcpeYnbHG
        B6TtXn6mo7UbnQL2OlLaB+qbc6vUyKvRuwNq8Z1likIRdf9PrffgoV0gKbdz6mRc
        81AO6uuicE3yG/ezljLhDUrbpxjtnWQbny8cuxUDmlXQ5zU08jGwiQgMBOU5InLr
        Qr55bthVLQ57HuoE7OiRRZ9s8NwjSM0qbB8NWwIDAQABAoIBADIDRiFqQj/rDZ2I
        9kMJYxALbTNXJGL+Qsk+EfBpnGv78kIYViPkyzescvl9rJI4J5YaO++0axS1YSyd
        Qyg/YI77mC2H/PQyDtFzRcJ57x80Xd1ecgxoTPvlNrQmLU7ZprpW8Fe3qWatKh0o
        vvirQ1hsKqspkD3mYOU3cM0jwKhC4j3Xzh027RzwMCk7BEyRyGiF0R/6xIBWWjhA
        TRPQiTzpdY4TtQy8PG7vapwazWYv/2irrbOb/1bXWkjeKn/K9bW1sWdImNG63huu
        ysxBsjWRG4y315PtfjGjBc4sNzCFxMQvANKm9VymG9LAQxgw0JI/a50D96l8pRFC
        qY7ttskCgYEA/pJJjQBJhuS1CGLb2xIjzNhAulClcZwu4+a8FfoKqwCe8avFvSv3
        dvAVT3sa2XimotWXMyw+yC9d/o4+LljBO4hs++P+7SWf52VobL6uOi9bnrd0xd+q
        aGonPSRMfGJVMd0cKDOxJxZQP5NxM04PcOt8cett+Q09AnZ0c5rvW6cCgYEA2hiE
        ZKV/2Pxu9WoknzjM89m5eIfkuKLguDG6QFEhLrLD1ec9HExQBWg6DEme4+/nIvQO
        hlPnZ2B+fpiw3am+3uV8MP91BioBN5HdDJVAdTMgpxHyMoSvzwlD5oq6A3OouHz0
        KNn3/sIa0cdH17BDvWdPmXVnhxLKq0zSDTrupy0CgYEAvedw+L9jGj7YkXX13nms
        vS4BMzvf/11sWVSRsK9DcAdZip0COLlotJAqxYznHZ30aPp+/YyfFQTI0JFZ74cE
        Nx3xdwLA9DWiEKNEgALKw9r6NO9ULBxK6fNubBz89bkBJt50F8Vf/PGXUaXyxzwP
        JsR0pCLleemXPpQREQBeWHcCgYB34ow8KwFZDIIN41fYMkfvL1qVl9WxbM8sUSF5
        o18jJV8jIOZlvMkr/7wQ7xMpZsFeZFvrmQmVuOQvwM1QO7PRIMKgyHvSdJqQqlyh
        QxXYls83J1VEUc22d/hcLRvNM/Gl4AHyxsZcwuQtNmcWeCz0W2rVB0VuaXUArsy0
        OxXezQKBgHvRBgsv5mKiUdTlPaeZ5k70bGRTzDin6hMhMRX6hBEaoneRBr9kJ474
        E/Vpa5KdpKcuGN0gH9xr4XUycBQee0P05zjI/8vzqQa4lt57ZbSFWaJpyzyy38Q0
        1Atrs4zP2rNiYTQRgbOy3V2gwcnxkJYueETExVertuAAR2c15JfC
        -----END RSA PRIVATE KEY-----
    """.trimIndent()

    @Nested
    inner class JwtGenerering {

        @Test
        fun `JWT har tre deler separert med punktum`() {
            val auth = GitHubAppAuth("123456", testPrivateKey, "789")
            val jwt = auth.createJwt()
            val parts = jwt.split(".")
            assertEquals(3, parts.size, "JWT skal ha header.payload.signature")
        }

        @Test
        fun `JWT header inneholder RS256 algoritme`() {
            val auth = GitHubAppAuth("123456", testPrivateKey, "789")
            val jwt = auth.createJwt()
            val header = String(Base64.getUrlDecoder().decode(jwt.split(".")[0]))
            assertContains(header, "RS256")
            assertContains(header, "JWT")
        }

        @Test
        fun `JWT payload inneholder app ID som issuer`() {
            val auth = GitHubAppAuth("123456", testPrivateKey, "789")
            val jwt = auth.createJwt()
            val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))
            assertContains(payload, "\"iss\":\"123456\"")
        }

        @Test
        fun `JWT payload inneholder iat og exp`() {
            val auth = GitHubAppAuth("123456", testPrivateKey, "789")
            val jwt = auth.createJwt()
            val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))
            assertContains(payload, "\"iat\":")
            assertContains(payload, "\"exp\":")
        }

        @Test
        fun `JWT exp er ca 10 minutter etter iat`() {
            val auth = GitHubAppAuth("123456", testPrivateKey, "789")
            val jwt = auth.createJwt()
            val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))
            val iat = Regex("\"iat\":(\\d+)").find(payload)!!.groupValues[1].toLong()
            val exp = Regex("\"exp\":(\\d+)").find(payload)!!.groupValues[1].toLong()
            // iat er now-60, exp er now+600, saa diff er 660
            assertTrue(exp - iat in 600..720, "exp - iat skal vaere ca 660 sekunder, var ${exp - iat}")
        }
    }

    @Nested
    inner class ConfigValidering {

        @Test
        fun `Config uten auth rapporterer hasAuth false`() {
            val config = GitHubIssueService.Config(repo = "test/repo")
            assertFalse(config.hasAuth(), "Config uten token og appAuth skal returnere hasAuth()=false")
        }

        @Test
        fun `Config med token er gyldig`() {
            val config = GitHubIssueService.Config(token = "test", repo = "test/repo")
            assertEquals("test", config.token)
        }

        @Test
        fun `Config med appAuth er gyldig`() {
            val auth = GitHubAppAuth("123", testPrivateKey, "456")
            val config = GitHubIssueService.Config(appAuth = auth, repo = "test/repo")
            assertEquals(auth, config.appAuth)
        }
    }

    @Nested
    inner class Lifecycle {

        @Test
        fun `GitHubAppAuth implementerer Closeable og close frigjoer HttpClient uten aa kaste`() {
            val auth = GitHubAppAuth("123", testPrivateKey, "456")
            assertTrue(auth is Closeable, "GitHubAppAuth skal implementere java.io.Closeable")
            auth.close()
            // Skal vaere idempotent
            auth.close()
        }
    }
}
