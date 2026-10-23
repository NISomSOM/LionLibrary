package com.singam.lionlibrary.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Signals durable artwork publication, including replacement at an unchanged local path. */
interface ArtworkRepository {
    val revision: StateFlow<Long>
}
