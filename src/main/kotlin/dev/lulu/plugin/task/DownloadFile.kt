package dev.lulu.plugin.task

import dev.lulu.plugin.util.DownloadService
import dev.lulu.plugin.util.Hash
import dev.lulu.plugin.util.HashingAlgorithm
import dev.lulu.plugin.util.path
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.*
import java.net.URI

@CacheableTask
abstract class DownloadFile : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    @get:Optional
    abstract val expectedSha1: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Console
    abstract val verbose: Property<Boolean>

    @get:ServiceReference("download")
    abstract val downloader: Property<DownloadService>

    @TaskAction
    fun run() {
        downloader.get().download(
            URI.create(url.get()), output.path,
            expectedSha1.map { Hash(it, HashingAlgorithm.SHA1) }.orNull
        ) {
            if (verbose.get()) {
                logger.lifecycle("Downloading ${url.get()}...")
            }
        }
    }
}
