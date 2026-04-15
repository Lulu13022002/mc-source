package dev.lulu.plugin.data

import kotlinx.serialization.Serializable

@Serializable
data class MainManifest(
    val latest: Map<String, String>,
    val versions: Set<VersionManifest>
)
