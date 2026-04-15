package dev.lulu.plugin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Downloads(
    val client: ManifestFile,
    val server: ManifestFile? = null, // old version only have client jar
    @SerialName("client_mappings")
    val clientMappings: ManifestFile? = null,
    @SerialName("server_mappings")
    val serverMappings: ManifestFile? = null,
)
