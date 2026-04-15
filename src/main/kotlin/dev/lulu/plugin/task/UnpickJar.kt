package dev.lulu.plugin.task

import daomephsta.unpick.api.ConstantUninliner
import daomephsta.unpick.api.classresolvers.ClassResolvers
import daomephsta.unpick.api.constantgroupers.ConstantGroupers
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader
import daomephsta.unpick.constantmappers.datadriven.tree.ForwardingUnpickV3Visitor
import daomephsta.unpick.constantmappers.datadriven.tree.GroupDefinition
import daomephsta.unpick.constantmappers.datadriven.tree.expr.ExpressionVisitor
import daomephsta.unpick.constantmappers.datadriven.tree.expr.FieldExpression
import dev.lulu.plugin.util.path
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.*

@CacheableTask
abstract class UnpickJar : DefaultTask() {

    private companion object {
        val FS_CREATE_ARGS = mapOf("create" to "true")
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val definitions: RegularFileProperty

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @TaskAction
    fun run() {
        output.path.deleteIfExists()

        maybeUnwrap(definitions.path) { input ->
            require(input.name.endsWith(".unpick")) {
                "Expected one combined definitions file for unpick data"
            }

            unpick(input)
        }
    }

    private fun maybeUnwrap(definitions: Path, unpick: (input: Path) -> Unit) {
        try {
            ZipFile(definitions.toFile()).use { defZip ->
                FileSystems.newFileSystem(definitions).use { fs ->
                    val entry = defZip.stream().findFirst().orElseThrow { IllegalStateException("Expected one combined definitions file for unpick data") }
                    unpick(fs.getPath(entry.name))
                }
            }
        } catch (_: ZipException) { // not a zip
            unpick(definitions)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun unpick(definitions: Path) {
        Files.newBufferedReader(definitions).use { mappingsReader ->
            FileSystems.newFileSystem(input.path).use { inputFs ->
                val zips = mutableListOf<ZipFile>()
                try {
                    var classResolver = ClassResolvers.fromDirectory(inputFs.getPath("/"))

                    for (file in classpath.files) {
                        val zip = ZipFile(file)
                        zips.add(zip)
                        classResolver = classResolver.chain(ClassResolvers.jar(zip))
                    }

                    classResolver = classResolver.chain(ClassResolvers.classpath(null))

                    val constantResolver = classResolver.asConstantResolver()
                    val uninliner = ConstantUninliner.builder()
                        .classResolver(classResolver)
                        .grouper(
                            ConstantGroupers.dataDriven()
                                .lenient(true)
                                .classResolver(classResolver)
                                .mappingSource { visitor ->
                                    try {
                                        UnpickV3Reader(mappingsReader)
                                            .accept(object : ForwardingUnpickV3Visitor(visitor) {
                                                // Filter out any groups where all constants reference missing classes
                                                // (client classes when applying to the server)
                                                override fun visitGroupDefinition(groupDefinition: GroupDefinition) {
                                                    val constants = groupDefinition.constants().toMutableList()
                                                    for (constant in groupDefinition.constants()) {
                                                        constant.accept(object : ExpressionVisitor() {
                                                            private fun shouldDrop(fieldExpression: FieldExpression): Boolean {
                                                                val owner = fieldExpression.className.replace('.', '/')
                                                                classResolver.resolveClass(owner) ?: return true
                                                                if (fieldExpression.fieldName != null) {
                                                                    constantResolver.resolveConstant(owner, fieldExpression.fieldName) ?: return true
                                                                }
                                                                return false
                                                            }

                                                            override fun visitFieldExpression(fieldExpression: FieldExpression) {
                                                                if (shouldDrop(fieldExpression)) {
                                                                    constants.remove(constant)
                                                                }
                                                            }
                                                        })
                                                    }
                                                    if (constants.isNotEmpty()) {
                                                        super.visitGroupDefinition(GroupDefinition.Builder.from(groupDefinition).setConstants(constants).build())
                                                    }
                                                }
                                            })
                                    } catch (e: IOException) {
                                        throw UncheckedIOException(e)
                                    }
                                }.build()
                        )
                        .build()

                    FileSystems.newFileSystem(output.path, FS_CREATE_ARGS).use { outputFs ->
                        copyContents(inputFs.getPath("/"), outputFs.getPath("/")) { input ->
                            val node = ClassNode()
                            ClassReader(input).accept(node, 0)
                            uninliner.transform(node)
                            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
                            node.accept(writer)
                            return@copyContents writer.toByteArray()
                        }
                    }
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                } finally {
                    for (zip in zips) {
                        try {
                            zip.close()
                        } catch (_: IOException) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private fun copyContents(
        inputRoot: Path, outputRoot: Path, classTransformer: (input: InputStream) -> ByteArray
    ) {
        inputRoot.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST).forEach { sourcePath ->
            if (sourcePath == inputRoot) {
                return@forEach
            }

            val relativePath = sourcePath.relativeTo(inputRoot)
            val outputPath = outputRoot.resolve(relativePath.invariantSeparatorsPathString)
            if (sourcePath.isDirectory()) {
                outputPath.createDirectory()
            } else {
                if (sourcePath.name.endsWith(".class")) {
                    outputPath.writeBytes(classTransformer(sourcePath.inputStream()))
                } else {
                    sourcePath.copyTo(outputPath)
                }
            }
        }
    }
}
