package dev.lulu.plugin.task

import dev.lulu.plugin.util.path
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@CacheableTask
abstract class RemapJar : JavaExec() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappings: RegularFileProperty

    @get:CompileClasspath
    abstract val minecraftClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    init {
        standardOutput = OutputStream.nullOutputStream()
    }

    override fun exec() {
        args = args(inputJar.path, outputJar.path, mappings.map { it.path }.orNull)
        super.exec()
    }

    private fun args(inputJar: Path, outputJar: Path, mappingsFile: Path?): List<String> {
        val args = mutableListOf(
            "--ann-fix",
            "--record-fix",
            "--ids-fix",
            "--src-fix",
            "--strip-sigs",
            "--disable-abstract-param",
            *minecraftClasspath.files.map { "--lib=" + it.absolutePath }.toTypedArray(),
            "--log=${outputJar.resolveSibling("${outputJar.name}.log").absolutePathString()}",
            "--input=${inputJar.absolutePathString()}",
            "--output=${outputJar.absolutePathString()}"
        )

        if (mappingsFile != null) {
            args.add("--reverse") // Input mappings is expected to be OBF->MOJ (ProGuard layout)
            args.add("--map=${mappingsFile.absolutePathString()}")
        }

        return args.toList()
    }
}
