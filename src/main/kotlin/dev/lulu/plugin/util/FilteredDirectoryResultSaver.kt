package dev.lulu.plugin.util

import org.jetbrains.java.decompiler.main.decompiler.DirectoryResultSaver
import java.io.File
import java.nio.file.Files
import java.util.jar.Manifest
import kotlin.io.path.createParentDirectories

class FilteredDirectoryResultSaver(
    val root: File,
    private val allowedPaths: Set<String>,
    private val stripSignatures: Boolean
) : DirectoryResultSaver(root) {

    var strippedManifest: Manifest? = null

    companion object {
        const val DIGEST_SUFFIX: String = "-digest"
        const val DIGEST_SUFFIX_LENGTH: Int = DIGEST_SUFFIX.length
    }

    private fun isExcluded(name: String, checkDirs: Boolean = false): Boolean {
        if (allowedPaths.isEmpty()) { // todo better state null?
            return false
        }

        for (path in allowedPaths) {
            if (name.startsWith(path)) {
                return false
            }

            if (checkDirs && !(path.endsWith('/'))) { // assume a file so allow all parent dirs as well
                var lastSlash = path.lastIndexOf('/')
                while (lastSlash != -1) {
                    if (path.substring(0, lastSlash + 1) == name) {
                        return false
                    }
                    lastSlash = path.lastIndexOf('/', lastSlash - 1)
                }
            }
        }

        return true
    }

    override fun saveDirEntry(path: String, archiveName: String, entryName: String) {
        if (isExcluded(entryName.removeSuffix("/") + '/', true)) { // normalize entry
            return
        }
        super.saveDirEntry(path, archiveName, entryName)
    }

    override fun saveClassEntry(
        path: String,
        archiveName: String,
        qualifiedName: String,
        entryName: String,
        content: String
    ) {
        if (isExcluded(entryName)) {
            return
        }
        super.saveClassEntry(path, archiveName, qualifiedName, entryName, content)
    }

    override fun saveClassFile(
        path: String,
        qualifiedName: String,
        entryName: String,
        content: String,
        mapping: IntArray
    ) {
        if (isExcluded(entryName)) {
            return
        }
        super.saveClassFile(path, qualifiedName, entryName, content, mapping)
    }

    override fun copyFile(source: String, path: String, entryName: String) {
        if (isExcluded(entryName)) {
            return
        }
        super.copyFile(source, path, entryName)
    }

    override fun createArchive(path: String, archiveName: String, manifest: Manifest?) {
        if (manifest != null && stripSignatures) {
            val it = manifest.entries.values.iterator()
            var foundSignatures = false
            while (it.hasNext()) {
                val entryAttributes = it.next()
                if (entryAttributes.keys.removeIf(this::isDigestAttribute)) {
                    foundSignatures = true
                    if (entryAttributes.isEmpty()) {
                        it.remove()
                    }
                }
            }

            if (foundSignatures) {
                strippedManifest = manifest
            }
        }
    }

    private fun isDigestAttribute(key: Any): Boolean {
        val attributeName = key.toString()
        if (attributeName.length <= DIGEST_SUFFIX_LENGTH) {
            return false
        }
        return attributeName.regionMatches(attributeName.length - DIGEST_SUFFIX_LENGTH, DIGEST_SUFFIX, 0, DIGEST_SUFFIX_LENGTH, true)
    }

    override fun copyEntry(source: String, path: String, archiveName: String, entryName: String) {
        if (isExcluded(entryName)) {
            return
        }
        if (strippedManifest != null && entryName == MANIFEST_PATH) {
            val absoluteManifest = root.toPath().resolve(MANIFEST_PATH)
            absoluteManifest.createParentDirectories()
            strippedManifest!!.write(Files.newOutputStream(absoluteManifest))
            return
        }
        super.copyEntry(source, path, archiveName, entryName)
    }
}
