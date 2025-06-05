package cssvarsassistant.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

object ImportResolver {
    private val LOG = Logger.getInstance(ImportResolver::class.java)
    private val IMPORT_PATTERN =
        Regex(
            """@import\s*(?:\([^)]*\)\s*)?(?:"([^"]+)"|'([^']+)'|url\(\s*(?:"([^"]+)"|'([^']+)'|([^)]+))\s*\))"""
        )


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

        return imports
    }

    /**
     * Resolves a single import path relative to the current file
     */
    private fun resolveImportPath(currentFile: VirtualFile, importPath: String, project: Project): VirtualFile? {
        try {
            return when {
                importPath.startsWith("./") || importPath.startsWith("../") -> {
                    // Explicit relative path
                    resolveRelativePath(currentFile, importPath)
                }

                importPath.startsWith("/") -> {
                    // Absolute path: resolve from project root
                    project.guessProjectDir()?.findFileByRelativePath(importPath.removePrefix("/"))
                }

                importPath.startsWith("@") -> {
                    // Scoped or package path (node_modules)
                    resolveNodeModulesPath(currentFile, importPath, project)
                }

                else -> {
                    // Bare path (no ./, no /, no @) – likely a relative import in same dir
                    val localFile = resolveRelativePath(currentFile, importPath)
                    if (localFile != null && localFile.exists()) {
                        localFile  // Found e.g. "colors-semantic.less" in current directory
                    } else {
                        // Not a file in current folder, treat as a node_modules package path
                        resolveNodeModulesPath(currentFile, importPath, project)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error resolving import path: $importPath", e)
            return null
        }
    }

    /**
     * Resolves relative paths like ./variables.css or ../theme/colors.css
     * Tries multiple extensions if no extension is specified, prioritizing based on current file type
     */
    private fun resolveRelativePath(currentFile: VirtualFile, relativePath: String): VirtualFile? {
        val currentDir = currentFile.parent ?: return null

        // If path already has an extension, use it directly
        if (relativePath.contains('.')) {
            return VfsUtil.findRelativeFile(currentDir, *relativePath.split('/').toTypedArray())
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
            val pathWithExtension = "$relativePath.$ext"
            val resolved = VfsUtil.findRelativeFile(currentDir, *pathWithExtension.split('/').toTypedArray())
            if (resolved != null && resolved.exists()) {
                return resolved
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
     * Tries multiple extensions if no extension is specified, prioritizing based on importing file type
     */
    private fun resolveInNodeModules(
        nodeModules: VirtualFile,
        packagePath: String,
        importingFile: VirtualFile
    ): VirtualFile? {
        // If path already has an extension, use it directly
        if (packagePath.contains('.')) {
            val pathParts = packagePath.split('/')
            var current = nodeModules

            for (part in pathParts) {
                current = current.findChild(part) ?: return null
            }

            return if (current.isDirectory) null else current
        }

        // Prioritize extensions based on the importing file's extension
        val currentExtension = importingFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }

        for (ext in prioritizedExtensions) {
            val pathWithExtension = "$packagePath.$ext"
            val pathParts = pathWithExtension.split('/')
            var current = nodeModules

            for (part in pathParts) {
                current = current.findChild(part) ?: break
            }

            if (current != nodeModules && !current.isDirectory && current.exists()) {
                return current
            }
        }

        return null
    }

    /**
     * Checks if a file should be considered for import resolution based on its location
     */
    fun isExternalImport(file: VirtualFile, project: Project): Boolean {
        val projectRoot = project.guessProjectDir()?.path ?: return false
        val filePath = file.path

        // Check if file is in node_modules
        return filePath.contains("/node_modules/") && !filePath.startsWith(projectRoot)
    }
}