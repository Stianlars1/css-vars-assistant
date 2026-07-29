package cssvarsassistant.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

object ImportResolver {
    private val LOG = Logger.getInstance(ImportResolver::class.java)
    private val IMPORT_PATTERN =
        Regex("""@import\s+(?:"([^"]+)"|'([^']+)'|\burl\(\s*(?:"([^"]+)"|'([^']+)'|([^)]+))\s*\))""")
    private val SASS_MODULE_PATTERN =
        Regex("""@(use|forward)\s+(?:"([^"]+)"|'([^']+)')""")

    data class ResolvedImport(
        val requestedPath: String,
        val resolvedFile: VirtualFile?
    )

    private enum class EntrypointKind {
        SASS,
        CSS,
        OTHER
    }

    private data class EntrypointCandidate(
        val path: String,
        val kind: EntrypointKind
    )

    fun resolveDirectImports(
        file: VirtualFile,
        project: Project
    ): List<ResolvedImport> = try {
        val content = String(file.contentsToByteArray())
        extractImportPaths(content).map { importPath ->
            ResolvedImport(importPath, resolveImportPath(file, importPath, project))
        }
    } catch (e: Exception) {
        LOG.debug("Error reading imports for ${file.path}", e)
        emptyList()
    }

    fun collectProjectImports(project: Project, maxDepth: Int): Set<VirtualFile> {
        val projectRoot = project.guessProjectDir() ?: return emptySet()
        val resolvedFiles = linkedSetOf<VirtualFile>()
        val queue = ArrayDeque<VirtualFile>()
        queue.add(projectRoot)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isDirectory) {
                if (current.name == "node_modules") {
                    continue
                }
                current.children.forEach(queue::addLast)
                continue
            }

            if (!isStylesheetFile(current)) {
                continue
            }

            resolvedFiles.addAll(resolveImports(current, project, maxDepth))
        }

        return resolvedFiles
    }

    /**
     * Resolves @import statements in a CSS file and returns a set of VirtualFiles
     * that should be indexed based on the current settings.
     */
    fun resolveImports(
        file: VirtualFile,
        project: Project,
        maxDepth: Int,
        visited: MutableSet<String> = mutableSetOf(),
        currentDepth: Int = 0
    ): Set<VirtualFile> {
        if (currentDepth >= maxDepth) return emptySet()
        if (file.path in visited) return emptySet()

        visited.add(file.path)
        val resolvedFiles = mutableSetOf<VirtualFile>()

        try {
            val content = String(file.contentsToByteArray())
            val imports = extractImportPaths(content)

            for (importPath in imports) {
                for (resolvedFile in resolveImportPaths(file, importPath, project)) {
                    if (!resolvedFile.exists()) continue
                    resolvedFiles.add(resolvedFile)

                    // Recursively resolve imports in the resolved file
                    val nestedImports = resolveImports(
                        resolvedFile,
                        project,
                        maxDepth,
                        visited,
                        currentDepth + 1
                    )
                    resolvedFiles.addAll(nestedImports)
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error resolving imports for ${file.path}", e)
        }

        return resolvedFiles
    }

    /**
     * Extracts @import paths from CSS content
     */
    private fun extractImportPaths(content: String): List<String> {
        val imports = mutableListOf<String>()

        IMPORT_PATTERN.findAll(content).forEach { match ->
            // Extract the actual import path from any of the capture groups
            val importPath = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (importPath != null) {
                imports.add(importPath.trim())
            }
        }

        SASS_MODULE_PATTERN.findAll(content).forEach { match ->
            val importPath = match.groupValues.drop(2).firstOrNull { it.isNotBlank() }
            if (importPath != null) {
                imports.add(importPath.trim())
            }
        }

        return imports
    }

    /**
     * Resolves a single import path relative to the current file
     */
    fun resolveImportPath(currentFile: VirtualFile, importPath: String, project: Project): VirtualFile? =
        resolveImportPaths(currentFile, importPath, project).firstOrNull()

    /**
     * Resolves every file needed for one import.
     *
     * Most imports resolve to one file. A package-root Sass import can resolve
     * to both its Sass API entrypoint and a CSS fallback: keeping both retains
     * imported Sass variables while still exposing concrete custom-property
     * declarations from the compiled CSS distribution.
     */
    private fun resolveImportPaths(
        currentFile: VirtualFile,
        importPath: String,
        project: Project
    ): List<VirtualFile> {
        try {
            return when {
                importPath.startsWith("./") || importPath.startsWith("../") -> {
                    // Explicit relative path
                    listOfNotNull(resolveRelativePath(currentFile, importPath))
                }

                importPath.startsWith("/") -> {
                    // Absolute path: resolve from project root
                    listOfNotNull(
                        project.guessProjectDir()?.findFileByRelativePath(importPath.removePrefix("/"))
                    )
                }

                importPath.startsWith("@") -> {
                    // Scoped or package path (node_modules)
                    resolveNodeModulesPaths(currentFile, importPath, project)
                }

                else -> {
                    // Bare path (no ./, no /, no @) – likely a relative import in same dir
                    val localFile = resolveRelativePath(currentFile, importPath)
                    if (localFile != null && localFile.exists()) {
                        listOf(localFile) // Found e.g. "colors-semantic.less" in current directory
                    } else {
                        // Not a file in current folder, treat as a node_modules package path
                        resolveNodeModulesPaths(currentFile, importPath, project)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error resolving import path: $importPath", e)
            return emptyList()
        }
    }

    /**
     * Resolves relative paths like ./variables.css or ../theme/colors.css
     * Tries multiple extensions if no extension is specified, prioritizing based on current file type
     */
    private fun resolveRelativePath(currentFile: VirtualFile, relativePath: String): VirtualFile? {
        val currentDir = currentFile.parent ?: return null
        val pathSegments = relativePath
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
        if (pathSegments.isEmpty()) {
            return null
        }

        // If the final path segment already has an extension, use it directly.
        // The whole import string may contain "." from "./" or "../".
        if (pathSegments.last().contains('.')) {
            return VfsUtil.findRelativeFile(currentDir, *pathSegments.toTypedArray())
        }

        // Prioritize extensions based on the importing file's extension
        val currentExtension = currentFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }

        for (ext in prioritizedExtensions) {
            for (candidate in stylesheetImportCandidates(pathSegments, ext)) {
                val resolved = VfsUtil.findRelativeFile(currentDir, *candidate.toTypedArray())
                if (resolved != null && resolved.exists()) {
                    return resolved
                }
            }
        }

        return null
    }

    /**
     * Resolves node_modules paths like @sb1/ffe-core/css/ffe or bootstrap/dist/css/bootstrap
     */
    private fun resolveNodeModulesPaths(
        currentFile: VirtualFile,
        packagePath: String,
        project: Project
    ): List<VirtualFile> {
        // Find node_modules directory by traversing up from current file
        var searchDir = currentFile.parent

        while (searchDir != null) {
            val nodeModules = searchDir.findChild("node_modules")
            if (nodeModules != null && nodeModules.isDirectory) {
                val resolvedFiles = resolveInNodeModules(nodeModules, packagePath, currentFile)
                if (resolvedFiles.isNotEmpty()) return resolvedFiles
            }
            searchDir = searchDir.parent
        }

        // Also check project root
        val projectNodeModules = project.guessProjectDir()?.findChild("node_modules")
        if (projectNodeModules != null && projectNodeModules.isDirectory) {
            return resolveInNodeModules(projectNodeModules, packagePath, currentFile)
        }

        return emptyList()
    }

    /**
     * Resolves a package path within a node_modules directory
     * Tries multiple extensions if no extension is specified, prioritizing based on importing file type
     */
    private fun resolveInNodeModules(
        nodeModules: VirtualFile,
        packagePath: String,
        importingFile: VirtualFile
    ): List<VirtualFile> {
        // If the final path segment already has an extension, use it directly.
        // Scoped packages may contain "." in directory names.
        val rawPathParts = packagePath.split('/').filter { it.isNotEmpty() }
        if (rawPathParts.lastOrNull()?.contains('.') == true) {
            val pathParts = packagePath.split('/')
            var current = nodeModules

            for (part in pathParts) {
                current = current.findChild(part) ?: return emptyList()
            }

            return if (current.isDirectory) emptyList() else listOf(current)
        }

        // Prioritize extensions based on the importing file's extension
        val currentExtension = importingFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }

        val pathSegments = packagePath.split('/').filter { it.isNotEmpty() }
        for (ext in prioritizedExtensions) {
            for (candidate in stylesheetImportCandidates(pathSegments, ext)) {
                var current = nodeModules

                for (part in candidate) {
                    current = current.findChild(part) ?: break
                }

                if (current != nodeModules && !current.isDirectory && current.exists()) {
                    return listOf(current)
                }
            }
        }

        resolvePackageDirectory(nodeModules, pathSegments)?.let { packageDir ->
            val packageRootSegments = packageRootSegments(pathSegments)
            val packageSubPath = pathSegments.drop(packageRootSegments.size).joinToString("/")
            val packageEntrypoints =
                resolvePackageJsonEntrypoints(packageDir, packageSubPath, prioritizedExtensions)
            if (packageEntrypoints.isNotEmpty()) return packageEntrypoints
        }

        // Sass resolves a directory import to index.scss / _index.scss (and
        // their indented-Sass equivalents). This also covers package roots
        // that do not declare a Sass entrypoint in package.json.
        resolveDirectory(nodeModules, pathSegments)?.let { directory ->
            resolveEntrypointCandidate(directory, "index", prioritizedExtensions)?.let {
                return listOf(it)
            }
        }

        return emptyList()
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

    private fun resolveDirectory(root: VirtualFile, pathSegments: List<String>): VirtualFile? {
        var current = root
        for (segment in pathSegments) {
            current = current.findChild(segment) ?: return null
        }
        return current.takeIf { it.isDirectory }
    }

    private fun resolvePackageJsonEntrypoints(
        packageDir: VirtualFile,
        packageSubPath: String,
        prioritizedExtensions: List<String>
    ): List<VirtualFile> {
        val packageJson = packageDir.findChild("package.json")
            ?.takeIf { !it.isDirectory && it.exists() }
            ?: return emptyList()
        val packageJsonContent = try {
            String(packageJson.contentsToByteArray())
        } catch (_: Exception) {
            return emptyList()
        }

        val exportsObject = extractObjectField(packageJsonContent, "exports")
        if (packageSubPath.isNotBlank()) {
            val subpathKey = "./$packageSubPath"
            val subpathCandidates = extractExportCandidates(exportsObject, subpathKey)
            return listOfNotNull(
                resolveFirstCandidate(packageDir, subpathCandidates, prioritizedExtensions)
            )
        }

        val topLevelJson = removeObjectField(packageJsonContent, "exports")
        val rootExportCandidates = extractExportCandidates(exportsObject, ".")
        val explicitCssSubpathCandidates = extractExportCandidates(exportsObject, "./css")

        val topLevelCandidates = mutableListOf<EntrypointCandidate>()
        extractTopLevelStringField(topLevelJson, "sass")?.let {
            topLevelCandidates += EntrypointCandidate(it, EntrypointKind.SASS)
        }
        extractTopLevelStringField(topLevelJson, "css")?.let {
            topLevelCandidates += EntrypointCandidate(it, EntrypointKind.CSS)
        }
        extractTopLevelStringField(topLevelJson, "style")?.let {
            topLevelCandidates += EntrypointCandidate(it, EntrypointKind.CSS)
        }
        extractTopLevelStringField(topLevelJson, "main")?.let {
            topLevelCandidates += EntrypointCandidate(it, EntrypointKind.OTHER)
        }

        val sassCandidates = (rootExportCandidates + topLevelCandidates)
            .filter { it.kind == EntrypointKind.SASS }
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

        val cssCandidates = (rootExportCandidates + topLevelCandidates + explicitCssSubpathCandidates)
            .filter { it.kind == EntrypointKind.CSS }
        resolveFirstCandidate(packageDir, cssCandidates, prioritizedExtensions)?.let { cssEntrypoint ->
            return listOfNotNull(firstResolvedSass, cssEntrypoint).distinct()
        }

        val otherCandidates = (rootExportCandidates + topLevelCandidates)
            .filter { it.kind == EntrypointKind.OTHER }
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
        val normalized = entry.removePrefix("./").trim()
        if (normalized.isEmpty()) return null
        val pathSegments = normalized.split('/').filter { it.isNotEmpty() }
        if (pathSegments.isEmpty()) return null

        if (pathSegments.last().contains('.')) {
            var current = packageDir
            for (segment in pathSegments) {
                current = current.findChild(segment) ?: return null
            }
            return if (current.exists() && !current.isDirectory && isStylesheetFile(current)) current else null
        }

        for (ext in prioritizedExtensions) {
            for (candidate in stylesheetImportCandidates(pathSegments, ext)) {
                var current = packageDir
                for (segment in candidate) {
                    current = current.findChild(segment) ?: break
                }
                if (current.exists() && !current.isDirectory && isStylesheetFile(current)) {
                    return current
                }
            }
        }

        return null
    }

    private fun extractTopLevelStringField(json: String, field: String): String? =
        Regex("""\"${Regex.escape(field)}\"\s*:\s*\"([^\"]+)\"""")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)

    private fun extractExportCandidates(exportsObject: String?, exportKey: String): List<EntrypointCandidate> {
        if (exportsObject.isNullOrBlank()) return emptyList()
        val candidates = mutableListOf<EntrypointCandidate>()

        extractTopLevelStringField(exportsObject, exportKey)?.let { exportValue ->
            candidates += EntrypointCandidate(exportValue, classifyEntrypointKind(exportKey, exportValue))
        }

        extractObjectField(exportsObject, exportKey)?.let { exportValueObject ->
            listOf("css", "style", "sass", "default", "import", "require", "main").forEach { key ->
                extractTopLevelStringField(exportValueObject, key)?.let { value ->
                    candidates += EntrypointCandidate(value, classifyEntrypointKind(key, value))
                }
            }
        }

        return candidates
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

    private fun hasConcreteCssVariableDeclarations(file: VirtualFile): Boolean =
        try {
            val content = String(file.contentsToByteArray())
            CssVariableEntryParser.parse(content, file.extension?.lowercase()).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private fun extractObjectField(json: String, field: String): String? {
        val keyPattern = Regex("""\"${Regex.escape(field)}\"\s*:\s*\{""")
        val match = keyPattern.find(json) ?: return null
        val openBrace = json.indexOf('{', match.range.first)
        if (openBrace < 0) return null
        val closeBrace = findMatchingBrace(json, openBrace) ?: return null
        return json.substring(openBrace, closeBrace + 1)
    }

    private fun removeObjectField(json: String, field: String): String {
        val keyPattern = Regex("""\"${Regex.escape(field)}\"\s*:\s*\{""")
        val match = keyPattern.find(json) ?: return json
        val openBrace = json.indexOf('{', match.range.first)
        if (openBrace < 0) return json
        val closeBrace = findMatchingBrace(json, openBrace) ?: return json
        return json.removeRange(match.range.first, closeBrace + 1)
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (idx in openIndex until text.length) {
            val ch = text[idx]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return idx
                }
            }
        }
        return null
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
