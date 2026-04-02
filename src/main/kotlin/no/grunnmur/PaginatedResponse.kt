package no.grunnmur

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val total: Long,
    val limit: Int,
    val offset: Long
)
