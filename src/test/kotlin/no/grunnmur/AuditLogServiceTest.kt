package no.grunnmur

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AuditLogServiceTest {

    private lateinit var database: Database
    private lateinit var service: AuditLogService

    @BeforeEach
    fun setUp() {
        database = Database.connect(
            url = "jdbc:h2:mem:auditlog_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction(database) {
            SchemaUtils.create(AuditLogs)
        }
        service = AuditLogService()
    }

    @AfterEach
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(AuditLogs)
        }
    }

    private fun insertWithCreatedAt(
        userId: Int?,
        action: String,
        entityType: String,
        createdAt: java.time.LocalDateTime
    ) {
        transaction(database) {
            AuditLogs.insert {
                it[AuditLogs.userId] = userId
                it[AuditLogs.userEmail] = "test@test.no"
                it[AuditLogs.action] = action
                it[AuditLogs.entityType] = entityType
                it[AuditLogs.createdAt] = createdAt
            }
        }
    }

    @Nested
    inner class Log {
        @Test
        fun `log skriver entry med korrekte felter`() {
            service.log(
                userId = 1,
                userEmail = "admin@test.no",
                action = "CREATE",
                entityType = "USER",
                entityId = 42,
                details = "Opprettet bruker",
                ipAddress = "10.0.0.1"
            )

            val entries = service.findAll()
            assertEquals(1, entries.size)
            val e = entries[0]
            assertEquals(1, e.userId)
            assertEquals("admin@test.no", e.userEmail)
            assertEquals("CREATE", e.action)
            assertEquals("USER", e.entityType)
            assertEquals(42, e.entityId)
            assertEquals("Opprettet bruker", e.details)
            assertEquals("10.0.0.1", e.ipAddress)
        }

        @Test
        fun `log med null userId bruker system som standard`() {
            service.log(userId = null, action = "SYSTEM_JOB", entityType = "CRON")

            val entries = service.findAll()
            assertEquals(1, entries.size)
            assertNull(entries[0].userId)
            assertEquals("system", entries[0].userEmail)
        }

        @Test
        fun `log med valgfrie felter som null lagres korrekt`() {
            service.log(userId = 5, action = "READ", entityType = "POST")

            val entries = service.findAll()
            assertEquals(1, entries.size)
            assertNull(entries[0].entityId)
            assertNull(entries[0].details)
            assertNull(entries[0].ipAddress)
        }

        @Test
        fun `log kaster ikke exception ved databasefeil`() {
            transaction(database) {
                SchemaUtils.drop(AuditLogs)
            }
            assertDoesNotThrow {
                service.log(userId = 1, action = "CREATE", entityType = "USER")
            }
        }
    }

    @Nested
    inner class FindAll {
        @Test
        fun `findAll returnerer tom liste naar databasen er tom`() {
            assertTrue(service.findAll().isEmpty())
        }

        @Test
        fun `findAll returnerer entries sortert nyeste forst`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "A1", "T", now.minusMinutes(2))
            insertWithCreatedAt(2, "A2", "T", now.minusMinutes(1))
            insertWithCreatedAt(3, "A3", "T", now)

            val entries = service.findAll()
            assertEquals(3, entries.size)
            assertEquals("A3", entries[0].action)
            assertEquals("A2", entries[1].action)
            assertEquals("A1", entries[2].action)
        }

        @Test
        fun `findAll respekterer limit`() {
            repeat(20) { i ->
                service.log(userId = i, action = "A", entityType = "T")
            }

            assertEquals(10, service.findAll(limit = 10).size)
        }

        @Test
        fun `findAll respekterer offset`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "FIRST", "T", now.minusMinutes(2))
            insertWithCreatedAt(2, "SECOND", "T", now.minusMinutes(1))
            insertWithCreatedAt(3, "THIRD", "T", now)

            val page1 = service.findAll(limit = 1, offset = 0)
            val page2 = service.findAll(limit = 1, offset = 1)
            val page3 = service.findAll(limit = 1, offset = 2)

            assertEquals("THIRD", page1[0].action)
            assertEquals("SECOND", page2[0].action)
            assertEquals("FIRST", page3[0].action)
        }

        @Test
        fun `findAll filtrerer paa action`() {
            service.log(userId = 1, action = "CREATE", entityType = "T")
            service.log(userId = 2, action = "UPDATE", entityType = "T")
            service.log(userId = 3, action = "DELETE", entityType = "T")

            val result = service.findAll(action = "CREATE")
            assertEquals(1, result.size)
            assertEquals("CREATE", result[0].action)
        }

        @Test
        fun `findAll filtrerer paa entityType`() {
            service.log(userId = 1, action = "A", entityType = "USER")
            service.log(userId = 2, action = "A", entityType = "POST")

            val result = service.findAll(entityType = "USER")
            assertEquals(1, result.size)
            assertEquals("USER", result[0].entityType)
        }

        @Test
        fun `findAll filtrerer paa userId`() {
            service.log(userId = 10, action = "A", entityType = "T")
            service.log(userId = 20, action = "A", entityType = "T")

            val result = service.findAll(userId = 10)
            assertEquals(1, result.size)
            assertEquals(10, result[0].userId)
        }

        @Test
        fun `findAll filtrerer paa startDate`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "OLD", "T", now.minusDays(10))
            insertWithCreatedAt(2, "NEW", "T", now)

            val cutoff = now.minusDays(1).toLocalDate().toString()
            val result = service.findAll(startDate = cutoff)

            assertEquals(1, result.size)
            assertEquals("NEW", result[0].action)
        }

        @Test
        fun `findAll filtrerer paa endDate`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "OLD", "T", now.minusDays(10))
            insertWithCreatedAt(2, "NEW", "T", now)

            val cutoff = now.minusDays(1).toLocalDate().toString()
            val result = service.findAll(endDate = cutoff)

            assertEquals(1, result.size)
            assertEquals("OLD", result[0].action)
        }

        @Test
        fun `findAll filtrerer paa datoperiode`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "WEEK_AGO", "T", now.minusDays(7))
            insertWithCreatedAt(2, "YESTERDAY", "T", now.minusDays(1))
            insertWithCreatedAt(3, "TODAY", "T", now)

            // start = 2 dager siden, end = i går — skal kun returnere YESTERDAY
            val start = now.minusDays(2).toLocalDate().toString()
            val end = now.minusDays(1).toLocalDate().toString()
            val result = service.findAll(startDate = start, endDate = end)

            assertEquals(1, result.size)
            assertEquals("YESTERDAY", result[0].action)
        }

        @Test
        fun `findAll ignorerer ugyldig datoformat og returnerer alle`() {
            service.log(userId = 1, action = "A", entityType = "T")
            service.log(userId = 2, action = "B", entityType = "T")

            val result = service.findAll(startDate = "ikke-en-dato")
            assertEquals(2, result.size)
        }

        @Test
        fun `findAll kombinerer flere filtre`() {
            service.log(userId = 1, action = "CREATE", entityType = "USER")
            service.log(userId = 1, action = "UPDATE", entityType = "USER")
            service.log(userId = 2, action = "CREATE", entityType = "USER")
            service.log(userId = 1, action = "CREATE", entityType = "POST")

            val result = service.findAll(userId = 1, action = "CREATE", entityType = "USER")
            assertEquals(1, result.size)
            assertEquals(1, result[0].userId)
            assertEquals("CREATE", result[0].action)
            assertEquals("USER", result[0].entityType)
        }
    }

    @Nested
    inner class Count {
        @Test
        fun `count returnerer 0 for tom database`() {
            assertEquals(0L, service.count())
        }

        @Test
        fun `count returnerer totalt antall entries`() {
            repeat(7) { service.log(userId = it, action = "A", entityType = "T") }
            assertEquals(7L, service.count())
        }

        @Test
        fun `count respekterer action-filter`() {
            service.log(userId = 1, action = "CREATE", entityType = "T")
            service.log(userId = 2, action = "UPDATE", entityType = "T")
            service.log(userId = 3, action = "CREATE", entityType = "T")

            assertEquals(2L, service.count(action = "CREATE"))
            assertEquals(1L, service.count(action = "UPDATE"))
        }

        @Test
        fun `count og findAll returnerer konsistent resultat`() {
            repeat(5) { i ->
                service.log(userId = if (i % 2 == 0) 1 else 2, action = "A", entityType = "T")
            }

            val countForUser1 = service.count(userId = 1)
            val entriesForUser1 = service.findAll(userId = 1)
            assertEquals(countForUser1, entriesForUser1.size.toLong())
        }
    }

    @Nested
    inner class CleanupOldLogs {
        @Test
        fun `cleanupOldLogs returnerer 0 naar ingen er eldre enn retention`() {
            service.log(userId = 1, action = "A", entityType = "T")

            val deleted = service.cleanupOldLogs(retentionDays = 365)
            assertEquals(0, deleted)
            assertEquals(1, service.findAll().size)
        }

        @Test
        fun `cleanupOldLogs sletter entries eldre enn retentionDays`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "GAMMEL", "T", now.minusDays(400))
            insertWithCreatedAt(2, "NY", "T", now)

            val deleted = service.cleanupOldLogs(retentionDays = 365)

            assertEquals(1, deleted)
            val remaining = service.findAll()
            assertEquals(1, remaining.size)
            assertEquals("NY", remaining[0].action)
        }

        @Test
        fun `cleanupOldLogs med tilpasset retentionDays`() {
            val now = TimeUtils.nowOslo()
            insertWithCreatedAt(1, "FOR_GAMMEL", "T", now.minusDays(100))
            insertWithCreatedAt(2, "NY_NOK", "T", now.minusDays(30))

            val deleted = service.cleanupOldLogs(retentionDays = 60)

            assertEquals(1, deleted)
            assertEquals(1, service.findAll().size)
            assertEquals("NY_NOK", service.findAll()[0].action)
        }

        @Test
        fun `cleanupOldLogs returnerer 0 naar databasen er tom`() {
            assertEquals(0, service.cleanupOldLogs())
        }
    }
}
