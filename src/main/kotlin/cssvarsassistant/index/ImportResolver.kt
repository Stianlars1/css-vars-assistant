package cssvarsassistant.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

object ImportResolver {
    private val LOG = Logger.getInstance(ImportResolver::class.java)

    // Enhanced pattern to handle LESS import options like (reference), (inline), etc.
    private val IMPORT_PATTERN =
        Regex("""@import\s+(?:\(([^)]+)\)\s+)?(?:"([^"]+)"|'([^']+)'|\burl\(\s*(?:"([^"]+)"|'([^']+)'|([^)]+))\s*\))""")

    /**
     * Resolves @import statements in a CSS/LESS file and returns a set of VirtualFiles
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

            for ((importPath, _) in imports) {
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
     * Extracts @import paths from CSS/LESS content
     * Returns pairs of (path, importOptions)
     */
    private fun extractImportPaths(content: String): List<Pair<String, String?>> {
        val imports = mutableListOf<Pair<String, String?>>()

        IMPORT_PATTERN.findAll(content).forEach { match ->
            // Extract import options (e.g., "reference", "inline", etc.)
            val importOptions = match.groupValues[1].takeIf { it.isNotBlank() }

            // Extract the actual import path from any of the capture groups
            val importPath = match.groupValues.drop(2).firstOrNull { it.isNotBlank() }
            if (importPath != null) {
                imports.add(importPath.trim() to importOptions)
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
            // Handle different types of import paths
            when {
                // Relative paths (./file.css, ../file.css)
                importPath.startsWith("./") || importPath.startsWith("../") -> {
                    return resolveRelativePath(currentFile, importPath)
                }

                // Node modules paths (@package/file, package/file)
                importPath.startsWith("@") || !importPath.startsWith("/") -> {
                    return resolveNodeModulesPath(currentFile, importPath, project)
                }

                // Absolute paths (less common, but handle them)
                importPath.startsWith("/") -> {
                    val projectRoot = project.guessProjectDir()
                    return projectRoot?.findFileByRelativePath(importPath.substring(1))
                }
            }
        } catch (e: Exception) {
            LOG.debug("Error resolving import path: $importPath", e)
            return null
        }
        return null
    }

    /**
     * Resolves relative paths like ./variables.css or ../theme/colors.css
     * Enhanced to handle LESS import conventions
     */
    private fun resolveRelativePath(currentFile: VirtualFile, relativePath: String): VirtualFile? {
        val currentDir = currentFile.parent ?: return null

        // If path already has an extension, use it directly
        if (relativePath.contains('.')) {
            return VfsUtil.findRelativeFile(currentDir, *relativePath.split('/').toTypedArray())
        }

        // For LESS, if no extension, try to find the file with LESS extension first
        // This handles imports like @import "variables"; which should resolve to variables.less
        val currentExtension = currentFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "less" -> listOf("less", "css", "scss", "sass")
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            else -> listOf("css", "scss", "sass", "less")
        }

        // First try exact match with prioritized extensions
        for (ext in prioritizedExtensions) {
            val pathWithExtension = "$relativePath.$ext"
            val resolved = VfsUtil.findRelativeFile(currentDir, *pathWithExtension.split('/').toTypedArray())
            if (resolved != null && resolved.exists()) {
                return resolved
            }
        }

        // If no exact match, try with underscore prefix (partial files)
        val pathParts = relativePath.split('/')
        val fileName = pathParts.last()
        val dirPath = pathParts.dropLast(1)

        for (ext in prioritizedExtensions) {
            val partialFileName = "_$fileName.$ext"
            val partialPath = (dirPath + listOf(partialFileName)).toTypedArray()
            val resolved = VfsUtil.findRelativeFile(currentDir, *partialPath)
            if (resolved != null && resolved.exists()) {
                return resolved
            }
        }

        return null
    }

    /**
     * Resolves node_modules paths like @sb1/ffe-core/less/ffe or bootstrap/dist/css/bootstrap
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
     * Enhanced to handle LESS import conventions
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
            "less" -> listOf("less", "css", "scss", "sass")
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            else -> listOf("css", "scss", "sass", "less")
        }

        // Try exact match with prioritized extensions
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

        // Try with underscore prefix for partial files
        val pathParts = packagePath.split('/')
        val fileName = pathParts.last()
        val dirPath = pathParts.dropLast(1)

        for (ext in prioritizedExtensions) {
            val partialFileName = "_$fileName.$ext"
            val fullPath = (dirPath + listOf(partialFileName))
            var current = nodeModules

            for (part in fullPath) {
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