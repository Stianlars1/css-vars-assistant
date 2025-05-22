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
                val resolvedFile = resolveImportPath(file, importPath, project)
                if (resolvedFile != null && resolvedFile.exists()) {
                    resolvedFiles.add(resolvedFile)

                    // Recursively resolve imports in the resolved file
                    val nestedImports = resolveImports(
                        resolvedFile,
                        project,
                        maxDepth,
                        visited.toMutableSet(), // Create copy to avoid modifying parent's visited set
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
        val lines = content.lines()

        for (line in lines) {
            val trimmedLine = line.trim()

            // Skip commented lines
            if (trimmedLine.startsWith("//") || trimmedLine.startsWith("/*")) continue

            // Handle conditional imports like @import "file.css" screen;
            IMPORT_PATTERN.findAll(trimmedLine).forEach { match ->
                val importPath = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                if (importPath != null && !importPath.startsWith("http")) {
                    imports.add(importPath.trim())
                }
            }
        }

        return imports
    }

    /**
     * Resolves a single import path relative to the current file
     */
    private fun resolveImportPath(
        currentFile: VirtualFile,
        importPath: String,
        project: Project
    ): VirtualFile? {
        try {
            return when {
                // Relative paths
                importPath.startsWith("./") || importPath.startsWith("../") -> {
                    resolveRelativePath(currentFile, importPath)
                }

                // Node modules or scoped packages
                importPath.startsWith("@") || !importPath.startsWith("/") -> {
                    resolveNodeModulesPath(currentFile, importPath, project)
                }

                // Absolute paths from project root
                importPath.startsWith("/") -> {
                    val projectRoot = project.guessProjectDir()
                    projectRoot?.findFileByRelativePath(importPath.substring(1))
                }

                else -> null
            }
        } catch (e: Exception) {
            LOG.debug("Error resolving import path: $importPath from ${currentFile.path}", e)
            return null
        }
    }

    /**
     * Resolves relative paths like ./variables.css or ../theme/colors.css
     */
    private fun resolveRelativePath(currentFile: VirtualFile, relativePath: String): VirtualFile? {
        val currentDir = currentFile.parent ?: return null

        // If path already has an extension, try it first
        if (relativePath.contains('.') && !relativePath.endsWith('.')) {
            VfsUtil.findRelativeFile(currentDir, *relativePath.split('/').toTypedArray())?.let {
                if (it.exists()) return it
            }
        }

        // Prioritize extensions based on the importing file's extension
        val currentExtension = currentFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }

        // Try each extension
        for (ext in prioritizedExtensions) {
            val pathWithExtension = if (relativePath.contains('.')) {
                relativePath.substringBeforeLast('.') + ".$ext"
            } else {
                "$relativePath.$ext"
            }

            val resolved = VfsUtil.findRelativeFile(currentDir, *pathWithExtension.split('/').toTypedArray())
            if (resolved != null && resolved.exists()) {
                return resolved
            }
        }

        // Try index files (like index.scss, index.css)
        if (!relativePath.contains('.')) {
            for (ext in prioritizedExtensions) {
                val indexPath = "$relativePath/index.$ext"
                val resolved = VfsUtil.findRelativeFile(currentDir, *indexPath.split('/').toTypedArray())
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
    private fun resolveNodeModulesPath(
        currentFile: VirtualFile,
        packagePath: String,
        project: Project
    ): VirtualFile? {
        // Find node_modules directory by traversing up from current file
        var searchDir = currentFile.parent

        while (searchDir != null) {
            val nodeModules = searchDir.findChild("node_modules")
            if (nodeModules != null && nodeModules.isDirectory) {
                val resolvedFile = resolveInNodeModules(nodeModules, packagePath, currentFile)
                if (resolvedFile != null) return resolvedFile
            }
            searchDir = searchDir.parent
        }

        // Also check project root
        val projectNodeModules = project.guessProjectDir()?.findChild("node_modules")
        if (projectNodeModules != null && projectNodeModules.isDirectory) {
            return resolveInNodeModules(projectNodeModules, packagePath, currentFile)
        }

        return null
    }

    /**
     * Resolves a package path within a node_modules directory
     */
    private fun resolveInNodeModules(
        nodeModules: VirtualFile,
        packagePath: String,
        importingFile: VirtualFile
    ): VirtualFile? {
        val pathParts = packagePath.split('/')

        // Handle scoped packages (@scope/package)
        val packageName = if (packagePath.startsWith("@") && pathParts.size >= 2) {
            "${pathParts[0]}/${pathParts[1]}"
        } else {
            pathParts[0]
        }

        val packageDir = if (packageName.contains("/")) {
            // Scoped package
            val scopeParts = packageName.split("/")
            nodeModules.findChild(scopeParts[0])?.findChild(scopeParts[1])
        } else {
            nodeModules.findChild(packageName)
        }

        if (packageDir == null || !packageDir.isDirectory) return null

        // If it's just the package name, try to find main CSS file
        if (pathParts.size <= (if (packagePath.startsWith("@")) 2 else 1)) {
            return findMainCssFile(packageDir, importingFile)
        }

        // Navigate to the specific file within the package
        val remainingPath = pathParts.drop(if (packagePath.startsWith("@")) 2 else 1).joinToString("/")
        return resolveFileInPackage(packageDir, remainingPath, importingFile)
    }

    private fun findMainCssFile(packageDir: VirtualFile, importingFile: VirtualFile): VirtualFile? {
        // Try common CSS entry points
        val commonEntries = listOf("style", "main", "index", "dist/index", "css/index")
        val extensions = getExtensionPriority(importingFile)

        for (entry in commonEntries) {
            for (ext in extensions) {
                val cssFile = packageDir.findFileByRelativePath("$entry.$ext")
                if (cssFile != null && cssFile.exists()) return cssFile
            }
        }

        return null
    }

    private fun resolveFileInPackage(
        packageDir: VirtualFile,
        filePath: String,
        importingFile: VirtualFile
    ): VirtualFile? {
        // Try exact path first
        if (filePath.contains('.')) {
            val exact = packageDir.findFileByRelativePath(filePath)
            if (exact != null && exact.exists()) return exact
        }

        // Try with extensions
        val extensions = getExtensionPriority(importingFile)
        for (ext in extensions) {
            val pathWithExt = if (filePath.contains('.')) {
                filePath.substringBeforeLast('.') + ".$ext"
            } else {
                "$filePath.$ext"
            }

            val resolved = packageDir.findFileByRelativePath(pathWithExt)
            if (resolved != null && resolved.exists()) return resolved
        }

        return null
    }

    private fun getExtensionPriority(importingFile: VirtualFile): List<String> {
        return when (importingFile.extension?.lowercase()) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }
    }

    /**
     * Checks if a file should be considered for import resolution based on its location
     */
    fun isExternalImport(file: VirtualFile, project: Project): Boolean {
        val projectRoot = project.guessProjectDir()?.path ?: return false
        val filePath = file.path
        return filePath.contains("/node_modules/") && !filePath.startsWith(projectRoot)
    }

    /**
     * Debug utility to trace import resolution
     */
    fun debugImportChain(file: VirtualFile, project: Project, maxDepth: Int = 3): String {
        val result = StringBuilder()
        result.appendLine("Import resolution debug for: ${file.path}")
        result.appendLine("Max depth: $maxDepth")

        fun traceImports(currentFile: VirtualFile, depth: Int, prefix: String = "") {
            if (depth >= maxDepth) return

            try {
                val content = String(currentFile.contentsToByteArray())
                val imports = extractImportPaths(content)

                result.appendLine("${prefix}├─ ${currentFile.name} (${imports.size} imports)")

                imports.forEachIndexed { index, importPath ->
                    val isLast = index == imports.size - 1
                    val newPrefix = prefix + if (isLast) "   " else "│  "

                    val resolved = resolveImportPath(currentFile, importPath, project)
                    if (resolved != null && resolved.exists()) {
                        result.appendLine("${prefix}${if (isLast) "└─" else "├─"} $importPath -> ${resolved.name} ✓")
                        traceImports(resolved, depth + 1, newPrefix)
                    } else {
                        result.appendLine("${prefix}${if (isLast) "└─" else "├─"} $importPath -> NOT FOUND ✗")
                    }
                }
            } catch (e: Exception) {
                result.appendLine("${prefix}└─ ERROR: ${e.message}")
            }
        }

        traceImports(file, 0)
        return result.toString()
    }
}