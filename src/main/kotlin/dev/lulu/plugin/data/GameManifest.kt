package dev.lulu.plugin.data

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@OptIn(ExperimentalTime::class)
data class GameManifest(
    val downloads: Downloads,
    val libraries: Set<GameLibrary>,
    val id: String,
    val releaseTime: Instant
)
