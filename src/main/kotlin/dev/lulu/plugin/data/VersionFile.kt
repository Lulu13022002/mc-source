package dev.lulu.plugin.data

import kotlinx.serialization.Serializable

@Serializable
data class VersionFile(
    val id: String,
    val name: String
)
