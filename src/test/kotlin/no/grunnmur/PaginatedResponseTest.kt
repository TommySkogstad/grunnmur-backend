package no.grunnmur

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PaginatedResponseTest {

    @Serializable
    data class TestItem(val id: Int, val name: String)

    private val json = Json { encodeDefaults = true }

    @Test
    fun `serialiserer PaginatedResponse med items korrekt`() {
        val response = PaginatedResponse(
            items = listOf(TestItem(1, "Ola"), TestItem(2, "Kari")),
            total = 10,
            limit = 2,
            offset = 0
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<PaginatedResponse<TestItem>>(jsonString)

        assertEquals(2, decoded.items.size)
        assertEquals(10, decoded.total)
        assertEquals(2, decoded.limit)
        assertEquals(0, decoded.offset)
        assertEquals("Ola", decoded.items[0].name)
    }

    @Test
    fun `serialiserer tom liste korrekt`() {
        val response = PaginatedResponse<TestItem>(
            items = emptyList(),
            total = 0,
            limit = 25,
            offset = 0
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<PaginatedResponse<TestItem>>(jsonString)

        assertTrue(decoded.items.isEmpty())
        assertEquals(0, decoded.total)
    }

    @Test
    fun `JSON-struktur inneholder riktige felter`() {
        val response = PaginatedResponse(
            items = listOf(TestItem(1, "Test")),
            total = 100,
            limit = 10,
            offset = 20
        )

        val jsonString = json.encodeToString(response)

        assertTrue(jsonString.contains("\"items\""))
        assertTrue(jsonString.contains("\"total\""))
        assertTrue(jsonString.contains("\"limit\""))
        assertTrue(jsonString.contains("\"offset\""))
    }

    @Test
    fun `fungerer med String som type`() {
        val response = PaginatedResponse(
            items = listOf("alpha", "beta"),
            total = 5,
            limit = 2,
            offset = 0
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<PaginatedResponse<String>>(jsonString)

        assertEquals(listOf("alpha", "beta"), decoded.items)
    }

    @Test
    fun `offset som Long haandterer store verdier`() {
        val response = PaginatedResponse<TestItem>(
            items = emptyList(),
            total = 5_000_000,
            limit = 25,
            offset = 4_999_975
        )

        val jsonString = json.encodeToString(response)
        val decoded = json.decodeFromString<PaginatedResponse<TestItem>>(jsonString)

        assertEquals(4_999_975, decoded.offset)
        assertEquals(5_000_000, decoded.total)
    }
}
