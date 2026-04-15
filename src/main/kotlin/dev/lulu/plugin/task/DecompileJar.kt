package dev.lulu.plugin.task

import dev.lulu.plugin.util.FilteredDirectoryResultSaver
import dev.lulu.plugin.util.ProgressGroupLogger
import dev.lulu.plugin.util.VineflowerLogger
import dev.lulu.plugin.util.path
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.jetbrains.java.decompiler.api.Decompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@CacheableTask
@OptIn(ExperimentalPathApi::class)
abstract class DecompileJar : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:Input
    abstract val args: MapProperty<String, Any>

    @get:CompileClasspath
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val allowedPaths: SetProperty<String>

    @get:Input
    @get:Optional
    abstract val stripSignatures: Property<Boolean>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Console
    abstract val startingMessage: Property<String>

    @get:Console
    abstract val loggerFactory: Property<ProgressLoggerFactory>

    init {
        // weird things happens for map property with convention: https://github.com/gradle/gradle/issues/18352
        args.set(mapOf(
            IFernflowerPreferences.INDENT_STRING to " ".repeat(4),
            IFernflowerPreferences.LOG_LEVEL to IFernflowerLogger.Severity.TRACE.name,
            IFernflowerPreferences.BYTECODE_SOURCE_MAPPING to true,
            IFernflowerPreferences.THREADS to Math.ceilDiv(Runtime.getRuntime().availableProcessors(), 2).toString()
        ))
        stripSignatures.convention(false)
        startingMessage.convention("Starting decompilation")
    }

    @TaskAction
    fun run() {
        output.path.deleteRecursively()

        val args = args.get()
        ProgressGroupLogger(
            startingMessage.get(),
            loggerFactory.get(),
            (args[IFernflowerPreferences.THREADS] as String?)?.toInt() ?: 4
        ).use {
            val decompiler = Decompiler.Builder()
                .inputs(inputJar.get().asFile)
                .output(FilteredDirectoryResultSaver(output.get().asFile, allowedPaths.getOrElse(setOf()), stripSignatures.get()))
                .logger(VineflowerLogger(it, logger))

            if (allowedPaths.isPresent) {
                decompiler.allowedPrefixes(*allowedPaths.get().toTypedArray()) // sadly only works on class files
            }
            decompiler.libraries(*libraries.files.toTypedArray())

            args.forEach { (key, value) ->
                decompiler.option(key, value)
            }

            decompiler.build().decompile()
        }
    }

    fun printFormattedArgument(key: String, value: String) {
        if (key == IFernflowerPreferences.INDENT_STRING && value.all { it.isWhitespace() }) {
            logger.lifecycle("  - $key=$value (${value.length} chars)")
        } else {
            logger.lifecycle("  - $key=$value")
        }
    }
}
