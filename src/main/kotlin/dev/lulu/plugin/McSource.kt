package dev.lulu.plugin

import dev.lulu.plugin.data.GameManifest
import dev.lulu.plugin.data.MainManifest
import dev.lulu.plugin.data.VersionFile
import dev.lulu.plugin.task.*
import dev.lulu.plugin.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Provider
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.get
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class McSource : Plugin<Project> {

    private companion object {
        const val TASK_GROUP = "mc-source"
        const val MAIN_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        val PRE_BUNDLER_ERA = Instant.parse("2021-09-29T16:27:05+00:00") // pre 21w39a
        val PRE_MOJMAP_ERA = Instant.parse("2019-09-04T11:19:34+00:00") // pre 19w36a

        const val UNPICK_DEFINITIONS_CONFIG = "unpickDefinitions"
        const val REMAPPER_CONFIG = "remapper"
    }

    override fun apply(target: Project) {
        val ext = target.extensions.create<McSourceExtension>("mcSource")

        target.gradle.sharedServices.registerIfAbsent("download", DownloadService::class) {}

        target.configurations.register(UNPICK_DEFINITIONS_CONFIG) {
            isTransitive = false
        }
        target.configurations.register(REMAPPER_CONFIG) {
            isTransitive = false
        }

        target.repositories {
            maven("https://libraries.minecraft.net/")
        }

        target.afterEvaluate {
            ext.validate()

            val manifestFile = target.layout.buildDirectory.file("manifest/manifest.json")
            if (!ext.manifestUrlOverride.isPresent) {
                val mainManifestFile = target.layout.buildDirectory.file("manifest/main_manifest.json")
                download(project, MAIN_MANIFEST, mainManifestFile)

                val mainManifest = json.decodeFromString<MainManifest>(mainManifestFile.path.readText())
                val manifest = mainManifest.versions.firstOrNull { it.id == ext.mcVersion.get() }
                    ?: throw UnsupportedOperationException("Could not find Minecraft version '${ext.mcVersion.get()}' in the downloaded manifest.")

                download(project, manifest.url, manifestFile, Hash(manifest.sha1, HashingAlgorithm.SHA1))
            } else {
                download(project, ext.manifestUrlOverride.get(), manifestFile)
            }

            val gameManifest = json.decodeFromString<GameManifest>(manifestFile.path.readText())
            registerSidedTasks(ext, target, gameManifest)
        }
    }

    private fun download(project: Project, url: String, target: Provider<RegularFile>, hash: Hash? = null) {
        project.download.download(URI.create(url), target.path, hash) {
            if (project.gradle.startParameter.logLevel != LogLevel.QUIET) {
                project.logger.lifecycle("Downloading $url...")
            }
        }
    }

    private fun registerSidedTasks(ext: McSourceExtension, project: Project, manifest: GameManifest) {
        val factory = (project as ProjectInternal).services.get<ProgressLoggerFactory>()
        val isVerbose = project.gradle.startParameter.logLevel != LogLevel.QUIET
        val jar =  ext.type.get()
        val jarId = jar.id

        val preBundler = (manifest.releaseTime - PRE_BUNDLER_ERA).isNegative()
        val customJarSet = ext.jarOverride.url.isPresent
        val versionId = manifest.id

        val downloadJar = project.tasks.register("download${jarId.capitalized()}", DownloadFile::class) {
            group = TASK_GROUP

            if (customJarSet) {
                url = ext.jarOverride.url
            } else {
                val file = jar.getFile(manifest) ?: throw UnsupportedOperationException("${jarId.capitalized()} jar is not available for Minecraft version $versionId.")
                url = file.url
                expectedSha1 = file.sha1
            }

            val name = if (jar == JarType.CLIENT || preBundler) {
                "$jarId.jar"
            } else {
                "bundler.jar"
            }
            output = project.layout.buildDirectory.file("$jarId/$name")
            verbose = isVerbose
        }

        var extractedJar = downloadJar.flatMap { it.output }
        if (jar == JarType.SERVER && !preBundler) { // extract server jar from bundler
            val extractServerJar by project.tasks.registering(ExtractServerJar::class) {
                group = TASK_GROUP
                bundlerJar = downloadJar.flatMap { it.output }
                output = project.layout.buildDirectory.file("$jarId/$jarId.jar")
            }

            extractedJar = extractServerJar.flatMap { it.output }
        }

        val minecraft by project.configurations.registering {
            isTransitive = false

            for (library in manifest.libraries) {
                dependencies.add(project.dependencyFactory.create(library.name))
            }
        }

        var remappedJar = extractedJar
        val preMojMap = (manifest.releaseTime - PRE_MOJMAP_ERA).isNegative() && versionId != "1.14.4" // 1.14.4 got obfuscation map retroactively
        val mappingFile = jar.getMappingFile(manifest)
        val mappingUrlOverride = ext.jarOverride.mappingUrl.orNull
        val remapper = project.configurations.getByName(REMAPPER_CONFIG)

        // for pre mojmap ART needs to run to fix snowman identifier
        // if jar override is setup skip remapper for empty config (mainly for unobfuscated jar not present in the manifest)
        if ((mappingFile != null && (!customJarSet || !remapper.isEmpty)) || mappingUrlOverride != null || preMojMap) {
            val remapJar by project.tasks.registering(RemapJar::class) {
                group = TASK_GROUP

                mainClass = "net.neoforged.art.Main"
                inputJar = extractedJar

                classpath(remapper)
                minecraftClasspath.setFrom(minecraft)

                outputJar = project.layout.buildDirectory.file("$jarId/$jarId-remapped.jar")
            }

            if (mappingFile != null || mappingUrlOverride != null) {
                val downloadMapping = project.tasks.register("download${jarId.capitalized()}Mappings", DownloadFile::class) {
                    group = TASK_GROUP

                    when {
                        mappingUrlOverride != null -> {
                            url = mappingUrlOverride
                        }
                        mappingFile != null -> {
                            url = mappingFile.url
                            expectedSha1 = mappingFile.sha1
                        }
                    }

                    output = project.layout.buildDirectory.file("$jarId/$jarId-mappings.txt")
                    verbose = isVerbose
                }
                remapJar.configure {
                    mappings = downloadMapping.flatMap { it.output }
                }
            }

            remappedJar = remapJar.flatMap { it.outputJar }
        }

        val decompileJar = project.tasks.register("decompile${jarId.capitalized()}", DecompileJar::class) {
            group = TASK_GROUP
            inputs.property("version", versionId)

            args.putAll(ext.decompilerArguments)

            inputJar = remappedJar
            output = project.layout.projectDirectory.dir("decompiledSources")
            libraries.setFrom(minecraft)

            if (!ext.resolveResources.getOrElse(false) && !preMojMap) { // todo respect the flag for pre mojmap
                allowedPaths = setOf(
                    "com/mojang/",
                    "net/minecraft/",
                    "META-INF/MANIFEST.MF"
                )
            }

            startingMessage = "Decompilation of $versionId"
            stripSignatures = remappedJar == extractedJar // ART already strip the signatures during remapping so no need to do it again

            loggerFactory = factory

            doFirst {
                if (!isVerbose) {
                    return@doFirst
                }
                var version = versionId
                if (customJarSet) { // for custom version, the version id from the manifest might not be accurate anymore
                    val newVersion = findVersionIdFromJar(inputJar.path)
                    if (newVersion == null) {
                        logger.warn("Could not find accurate version id from jar!")
                    } else {
                        version = newVersion
                    }
                }

                logger.lifecycle("Starting to decompile ${inputJar.path.absolutePathString()} ($version) with the following arguments:")
                args.get().forEach { (key, value) ->
                    printFormattedArgument(key, value.toString())
                }
            }
        }

        val unpickDefinitions = project.configurations.getByName(UNPICK_DEFINITIONS_CONFIG)
        if (unpickDefinitions.files.size == 1) {
            val unpickJar by project.tasks.registering(UnpickJar::class) {
                group = TASK_GROUP
                input = remappedJar
                output = project.layout.buildDirectory.file("$jarId/$jarId-unpicked.jar")
                definitions = unpickDefinitions.singleFile
                classpath.setFrom(minecraft)
            }
            decompileJar.configure {
                inputJar = unpickJar.flatMap { it.output }
            }
        }
    }

    private fun findVersionIdFromJar(input: Path): String? {
        FileSystems.newFileSystem(input).use { fs ->
            val root = fs.getPath("/")
            val versionFile = root.resolve("version.json")
            if (versionFile.notExists()) {
                return null
            }

            return json.decodeFromString<VersionFile>(versionFile.readText()).id
        }
    }
}
