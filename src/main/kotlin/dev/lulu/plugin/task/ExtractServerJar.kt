package dev.lulu.plugin.task

import dev.lulu.plugin.util.MANIFEST_PATH
import dev.lulu.plugin.util.path
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.jar.Manifest
import kotlin.io.path.copyTo
import kotlin.io.path.readText

@CacheableTask
abstract class ExtractServerJar : DefaultTask() {

    private companion object {
        const val BUNDLER_FORMAT_VERSION = "1.0"
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundlerJar: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        FileSystems.newFileSystem(bundlerJar.path).use { fs ->
            val root = fs.getPath("/")
            val manifest: Manifest
            Files.newInputStream(root.resolve(MANIFEST_PATH)).use { stream ->
                manifest = Manifest(stream)
            }

            val bundlerVersion = manifest.mainAttributes.getValue("Bundler-Format")
            require(bundlerVersion == BUNDLER_FORMAT_VERSION) {
                "Bundler format version $bundlerVersion is unknown, expected: $BUNDLER_FORMAT_VERSION"
            }

            val jarPath = root.resolve("META-INF/versions.list").readText().substringAfterLast('\t')
            root.resolve("META-INF/versions/$jarPath").copyTo(output.path, true)
        }
    }
}
