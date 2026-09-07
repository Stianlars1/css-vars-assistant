package cssvarsassistant.index

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.util.StylesheetStatements

object ImportResolver {
    private val LOG = Logger.getInstance(ImportResolver::class.java)
    private val DIRECTIVE = Regex("^@(import|use|forward)\\s+")
    private val PATH = Regex("""^(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)'|url\(\s*(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)'|([^)]*?))\s*\))""", RegexOption.IGNORE_CASE)
    private val ALIAS = Regex("""^as\s+(\*|[\p{L}\p{N}_-]+)(?=\s|$)""")

    data class ResolvedImport(
        val requestedPath: String,
        val resolvedFile: VirtualFile?,
        val kind: String = "import",
        val namespace: String? = null,
        val offset: Int = 0,
        val resolvedFiles: List<VirtualFile> = listOfNotNull(resolvedFile),
        val suffix: String = ""
    )

    private enum class EntrypointKind { SASS, CSS, OTHER }
    private data class EntrypointCandidate(val path: String, val kind: EntrypointKind)

    private fun readText(file: VirtualFile): String =
        FileDocumentManager.getInstance().getCachedDocument(file)?.text
            ?: String(file.contentsToByteArray(), file.charset)

    fun resolveDirectImports(file: VirtualFile, project: Project): List<ResolvedImport> = try {
        ProgressManager.checkCanceled()
        buildList {
            for (statement in StylesheetStatements.parse(readText(file), file.extension?.lowercase())) {
                ProgressManager.checkCanceled()
                val directive = DIRECTIVE.find(statement.text) ?: continue
                val kind = directive.groupValues[1]
                var remaining = statement.text.substring(directive.range.last + 1).trimStart()
                if (kind == "import" && file.extension.equals("less", true) && remaining.startsWith('(')) {
                    val optionsEnd = remaining.indexOf(')')
                    if (optionsEnd < 0) continue
                    remaining = remaining.substring(optionsEnd + 1).trimStart()
                }
                do {
                    val pathMatch = PATH.find(remaining) ?: break
                    val path = pathMatch.groups.drop(1).firstNotNullOfOrNull { it?.value }?.trim() ?: break
                    val suffix = remaining.substring(pathMatch.range.last + 1).trim()
                    val resolved = resolveImportPaths(file, path, project)
                    val namespace = if (kind == "use") ALIAS.find(suffix)?.groupValues?.get(1)
                        ?: path.substringAfterLast('/').substringBeforeLast('.').removePrefix("_") else null
                    add(ResolvedImport(path, resolved.firstOrNull(), kind, namespace, statement.offset, resolved, suffix))
                    remaining = if (kind == "import" && suffix.startsWith(',')) suffix.drop(1).trimStart() else ""
                } while (remaining.isNotEmpty())
            }
        }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.debug("Error reading imports for ${file.path}", e)
        emptyList()
    }

    fun collectProjectImports(project: Project, maxDepth: Int): Set<VirtualFile> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val queue = ArrayDeque(ProjectRootManager.getInstance(project).contentRoots.toList())
        val visited = hashSetOf<String>()
        val resolved = linkedSetOf<VirtualFile>()
        while (queue.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val current = queue.removeFirst()
            if (!visited.add(current.url) || current.`is`(VFileProperty.SYMLINK) || !fileIndex.isInContent(current)) continue
            if (current.isDirectory) {
                if (current.name !in setOf("node_modules", ".git")) current.children.forEach(queue::addLast)
            } else if (isStylesheetFile(current)) {
                resolved.addAll(resolveImports(current, project, maxDepth))
            }
        }
        return resolved
    }

    /** Dependencies precede the importing file so its local declarations override them. */
    fun resolveImports(
        file: VirtualFile,
        project: Project,
        maxDepth: Int,
        visited: MutableSet<String> = mutableSetOf(),
        currentDepth: Int = 0
    ): Set<VirtualFile> {
        ProgressManager.checkCanceled()
        if (currentDepth >= maxDepth || !visited.add(file.path)) return emptySet()
        val resolved = linkedSetOf<VirtualFile>()
        for (directive in resolveDirectImports(file, project)) {
            for (dependency in directive.resolvedFiles) {
                ProgressManager.checkCanceled()
                if (!dependency.exists()) continue
                resolved.addAll(resolveImports(dependency, project, maxDepth, visited, currentDepth + 1))
                resolved.remove(dependency)
                resolved.add(dependency)
            }
        }
        return resolved
    }

    fun resolveImportPath(currentFile: VirtualFile, importPath: String, project: Project): VirtualFile? =
        resolveImportPaths(currentFile, importPath, project).firstOrNull()

    private fun resolveImportPaths(currentFile: VirtualFile, importPath: String, project: Project): List<VirtualFile> = try {
        ProgressManager.checkCanceled()
        val extensions = prioritizedExtensions(currentFile)
        when {
            importPath.startsWith("//") || Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""").containsMatchIn(importPath) -> emptyList()
            importPath.startsWith('/') -> listOfNotNull(project.guessProjectDir()?.let {
                resolveEntrypointCandidate(it, importPath.removePrefix("/"), extensions)
            })
            importPath.startsWith("./") || importPath.startsWith("../") ->
                listOfNotNull(currentFile.parent?.let { resolveEntrypointCandidate(it, importPath, extensions) })
            else -> {
                val local = currentFile.parent?.let { resolveEntrypointCandidate(it, importPath, extensions) }
                if (local != null) listOf(local) else resolveNodeModulesPaths(currentFile, importPath, project)
            }
        }
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.debug("Error resolving import path: $importPath", e)
        emptyList()
    }

    private fun prioritizedExtensions(file: VirtualFile): List<String> = when (file.extension?.lowercase()) {
        "scss" -> listOf("scss", "css", "sass", "less")
        "sass" -> listOf("sass", "scss", "css", "less")
        "less" -> listOf("less", "css", "scss", "sass")
        else -> listOf("css", "scss", "sass", "less")
    }

    private fun resolveNodeModulesPaths(currentFile: VirtualFile, packagePath: String, project: Project): List<VirtualFile> {
        var searchDir = currentFile.parent
        while (searchDir != null) {
            ProgressManager.checkCanceled()
            searchDir.findChild("node_modules")?.takeIf { it.isDirectory }?.let {
                val resolved = resolveInNodeModules(it, packagePath, currentFile)
                if (resolved.isNotEmpty()) return resolved
            }
            searchDir = searchDir.parent
        }
        val root = project.guessProjectDir()?.findChild("node_modules") ?: return emptyList()
        return resolveInNodeModules(root, packagePath, currentFile)
    }

    private fun resolveInNodeModules(nodeModules: VirtualFile, packagePath: String, importingFile: VirtualFile): List<VirtualFile> {
        val segments = packagePath.split('/').filter { it.isNotEmpty() }
        val extensions = prioritizedExtensions(importingFile)
        val packageDir = resolvePackageDirectory(nodeModules, segments)
        if (packageDir != null) {
            val subpath = segments.drop(packageRootSegments(segments).size).joinToString("/")
            val entrypoints = resolvePackageJsonEntrypoints(packageDir, subpath, extensions)
            if (entrypoints.isNotEmpty()) return entrypoints
        }
        return listOfNotNull(resolveEntrypointCandidate(nodeModules, packagePath, extensions))
    }

    private fun packageRootSegments(pathSegments: List<String>): List<String> {
        if (pathSegments.isEmpty()) return emptyList()
        return when {
            pathSegments.first().startsWith("@") && pathSegments.size >= 2 -> pathSegments.take(2)
            else -> pathSegments.take(1)
        }
    }

    private fun resolvePackageDirectory(nodeModules: VirtualFile, pathSegments: List<String>): VirtualFile? {
        if (pathSegments.isEmpty()) return null
        val packageRootSegments = packageRootSegments(pathSegments)

        var current = nodeModules
        for (segment in packageRootSegments) {
            current = current.findChild(segment) ?: return null
        }
        return if (current.isDirectory) current else null
    }

    private fun resolvePackageJsonEntrypoints(
        packageDir: VirtualFile,
        packageSubPath: String,
        prioritizedExtensions: List<String>
    ): List<VirtualFile> {
        val packageJson = packageDir.findChild("package.json")
            ?.takeIf { !it.isDirectory && it.exists() }
            ?: return emptyList()
        val json = try {
            JsonParser.parseString(readText(packageJson)).takeIf { it.isJsonObject }?.asJsonObject
                ?: return emptyList()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            return emptyList()
        }
        val exports = json.get("exports")
        if (packageSubPath.isNotBlank()) {
            return resolvePackageCandidates(packageDir,
                extractExportCandidates(exports, "./$packageSubPath"), prioritizedExtensions)
        }
        val rootExportCandidates = extractExportCandidates(exports, ".")
        val explicitCssSubpathCandidates = extractExportCandidates(exports, "./css")
        val topLevelCandidates = listOf("sass", "css", "style", "main").mapNotNull { key ->
            json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let {
                EntrypointCandidate(it, classifyEntrypointKind(key, it))
            }
        }

        val candidates = rootExportCandidates + topLevelCandidates +
            explicitCssSubpathCandidates.filter { it.kind == EntrypointKind.CSS }
        return resolvePackageCandidates(packageDir, candidates, prioritizedExtensions)
    }

    private fun resolvePackageCandidates(
        packageDir: VirtualFile,
        candidates: List<EntrypointCandidate>,
        prioritizedExtensions: List<String>
    ): List<VirtualFile> {
        val sassCandidates = candidates.filter { it.kind == EntrypointKind.SASS }
        var firstResolvedSass: VirtualFile? = null
        for (candidate in sassCandidates) {
            val resolved = resolveEntrypointCandidate(packageDir, candidate.path, prioritizedExtensions) ?: continue
            if (firstResolvedSass == null) {
                firstResolvedSass = resolved
            }
            if (hasConcreteCssVariableDeclarations(resolved)) {
                return listOf(resolved)
            }
        }

        val cssCandidates = candidates.filter { it.kind == EntrypointKind.CSS }
        resolveFirstCandidate(packageDir, cssCandidates, prioritizedExtensions)?.let { cssEntrypoint ->
            return listOfNotNull(firstResolvedSass, cssEntrypoint).distinct()
        }

        val otherCandidates = candidates.filter { it.kind == EntrypointKind.OTHER }
        resolveFirstCandidate(packageDir, otherCandidates, prioritizedExtensions)?.let {
            return listOf(it)
        }

        firstResolvedSass?.let { return listOf(it) }

        return emptyList()
    }

    private fun resolveFirstCandidate(
        packageDir: VirtualFile,
        candidates: List<EntrypointCandidate>,
        prioritizedExtensions: List<String>
    ): VirtualFile? {
        val seen = linkedSetOf<String>()
        for (candidate in candidates) {
            if (!seen.add(candidate.path)) continue
            resolveEntrypointCandidate(packageDir, candidate.path, prioritizedExtensions)?.let { return it }
        }
        return null
    }

    private fun resolveEntrypointCandidate(
        packageDir: VirtualFile,
        entry: String,
        prioritizedExtensions: List<String>
    ): VirtualFile? {
        val segments = entry.trim().removePrefix("./").split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) return null
        val direct = VfsUtil.findRelativeFile(packageDir, *segments.toTypedArray())
        if (direct != null && !direct.isDirectory && isStylesheetFile(direct)) return direct
        val filename = segments.last()
        val candidates = if (filename.contains('.')) {
            if (filename.substringAfterLast('.') in setOf("scss", "sass") && !filename.startsWith('_'))
                listOf(segments.dropLast(1) + "_$filename") else emptyList()
        } else prioritizedExtensions.flatMap { stylesheetImportCandidates(segments, it) }
        for (candidate in candidates) {
            ProgressManager.checkCanceled()
            val resolved = VfsUtil.findRelativeFile(packageDir, *candidate.toTypedArray())
            if (resolved != null && !resolved.isDirectory && isStylesheetFile(resolved)) return resolved
        }
        if (direct?.isDirectory == true) {
            for (extension in prioritizedExtensions) {
                for (candidate in stylesheetImportCandidates(listOf("index"), extension)) {
                    val resolved = VfsUtil.findRelativeFile(direct, *candidate.toTypedArray())
                    if (resolved != null && !resolved.isDirectory && isStylesheetFile(resolved)) return resolved
                }
            }
        }
        return null
    }

    private fun extractExportCandidates(exports: JsonElement?, key: String): List<EntrypointCandidate> {
        if (exports == null) return emptyList()
        val value = if (exports.isJsonObject) {
            val objectValue = exports.asJsonObject
            objectValue.get(key) ?: if (key == "." && objectValue.keySet().none { it.startsWith('.') }) exports else null
        } else if (key == ".") exports else null
        return exportCandidates(value, key)
    }

    private fun exportCandidates(value: JsonElement?, condition: String): List<EntrypointCandidate> {
        ProgressManager.checkCanceled()
        return when {
            value == null || value.isJsonNull -> emptyList()
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> listOf(
                EntrypointCandidate(value.asString, classifyEntrypointKind(condition, value.asString)))
            value.isJsonArray -> value.asJsonArray.flatMap { exportCandidates(it, condition) }
            value.isJsonObject -> listOf("sass", "css", "style", "default", "import", "require", "main").flatMap { key ->
                exportCandidates(value.asJsonObject.get(key), if (key in setOf("default", "import", "require")) condition else key)
            }
            else -> emptyList()
        }
    }

    private fun classifyEntrypointKind(key: String, value: String): EntrypointKind {
        val normalizedKey = key.lowercase()
        val normalizedValue = value.lowercase()
        return when {
            normalizedKey == "css" || normalizedKey == "style" -> EntrypointKind.CSS
            normalizedKey == "sass" -> EntrypointKind.SASS
            normalizedValue.endsWith(".css") -> EntrypointKind.CSS
            normalizedValue.endsWith(".scss") || normalizedValue.endsWith(".sass") -> EntrypointKind.SASS
            else -> EntrypointKind.OTHER
        }
    }

    private fun hasConcreteCssVariableDeclarations(file: VirtualFile): Boolean = try {
        CssVariableEntryParser.parse(readText(file), file.extension?.lowercase()).isNotEmpty()
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private fun stylesheetImportCandidates(pathSegments: List<String>, extension: String): List<List<String>> {
        val fileName = pathSegments.lastOrNull() ?: return emptyList()
        val regular = pathSegments.dropLast(1) + "$fileName.$extension"
        if (extension !in setOf("scss", "sass")) {
            return listOf(regular)
        }

        val partial = pathSegments.dropLast(1) + "_$fileName.$extension"
        return listOf(regular, partial)
    }

    private fun isStylesheetFile(file: VirtualFile): Boolean =
        file.extension?.lowercase() in setOf("css", "scss", "sass", "less")
}
