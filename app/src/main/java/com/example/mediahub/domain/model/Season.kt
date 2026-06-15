package com.example.mediahub.domain.model

data class Season(
    val id: Long = 0,
    val showId: Long,
    val seasonNumber: Int,
    val name: String?,
    val posterPath: String?
)
