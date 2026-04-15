package dev.lulu.plugin.util

import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.nio.file.Path

const val MANIFEST_PATH = "META-INF/MANIFEST.MF"

val json = Json {
    ignoreUnknownKeys = true
}

fun String.capitalized(): String {
    return replaceFirstChar(Char::uppercase)
}

val FileSystemLocation.path: Path
    get() = asFile.toPath()
val Provider<out FileSystemLocation>.path: Path
    get() = get().path
val Provider<out FileSystemLocation>.pathOrNull: Path?
    get() = orNull?.path

@Suppress("UNCHECKED_CAST")
val Project.download: DownloadService
    get() = (gradle.sharedServices.registrations.getByName("download").service as Provider<DownloadService>).get()
