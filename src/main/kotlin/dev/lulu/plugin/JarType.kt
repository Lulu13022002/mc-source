package dev.lulu.plugin

import dev.lulu.plugin.data.GameManifest
import dev.lulu.plugin.data.ManifestFile

enum class JarType(val id: String) {
    CLIENT("client") {
        override fun getFile(manifest: GameManifest): ManifestFile {
            return manifest.downloads.client
        }

        override fun getMappingFile(manifest: GameManifest): ManifestFile? {
            return manifest.downloads.clientMappings
        }
    },
    SERVER("server") {
        override fun getFile(manifest: GameManifest): ManifestFile? {
            return manifest.downloads.server
        }

        override fun getMappingFile(manifest: GameManifest): ManifestFile? {
            return manifest.downloads.serverMappings
        }
    };

    abstract fun getFile(manifest: GameManifest): ManifestFile?

    abstract fun getMappingFile(manifest: GameManifest): ManifestFile?
}
