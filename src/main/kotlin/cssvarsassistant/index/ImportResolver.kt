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

    fun resolveImports(
        file: VirtualFile,
        project: Project,
        maxDepth: Int,
        visited: MutableSet<String> = mutableSetOf(),
        currentDepth: Int = 0
    ): Set<VirtualFile> {
        if (currentDepth >= maxDepth) {
            LOG.warn("Max depth $maxDepth reached for ${file.path}")
            return emptySet()
        }
        if (file.path in visited) {
            LOG.debug("Already visited ${file.path}, skipping")
            return emptySet()
        }

        visited.add(file.path)
        val resolvedFiles = mutableSetOf<VirtualFile>()

        try {
            LOG.info("🔍 RESOLVING IMPORTS FOR: ${file.path} (depth: $currentDepth)")
            val content = String(file.contentsToByteArray())
            val imports = extractImportPaths(content)

            LOG.info("📄 File content preview (first 200 chars):")
            LOG.info(content.take(200).replace('\n', ' '))

            LOG.info("📦 Found ${imports.size} @import statements:")
            imports.forEachIndexed { index, import ->
                LOG.info("  ${index + 1}. '$import'")
            }

            for ((index, importPath) in imports.withIndex()) {
                LOG.info("🚀 [${index + 1}/${imports.size}] Resolving: '$importPath'")
                val resolvedFile = resolveImportPath(file, importPath, project)

                if (resolvedFile != null && resolvedFile.exists()) {
                    LOG.info("✅ SUCCESS: '$importPath' -> ${resolvedFile.path}")
                    resolvedFiles.add(resolvedFile)

                    // Recursive resolution
                    val nestedImports = resolveImports(
                        resolvedFile,
                        project,
                        maxDepth,
                        visited,
                        currentDepth + 1
                    )
                    resolvedFiles.addAll(nestedImports)
                    LOG.info("📁 Added ${nestedImports.size} nested imports from ${resolvedFile.name}")
                } else {
                    LOG.warn("❌ FAILED: Could not resolve '$importPath' from ${file.path}")
                }
            }

            LOG.info("🎯 TOTAL RESOLVED: ${resolvedFiles.size} files for ${file.name}")

        } catch (e: Exception) {
            LOG.error("💥 ERROR resolving imports for ${file.path}", e)
        }

        return resolvedFiles
    }

    private fun extractImportPaths(content: String): List<String> {
        val imports = mutableListOf<String>()

        IMPORT_PATTERN.findAll(content).forEach { match ->
            val importPath = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            if (importPath != null) {
                imports.add(importPath.trim())
            }
        }

        return imports
    }

    private fun resolveImportPath(
        currentFile: VirtualFile,
        importPath: String,
        project: Project
    ): VirtualFile? {
        try {
            LOG.info("🔧 Resolving path: '$importPath' from ${currentFile.path}")

            when {
                // Explicit relative paths (./file.css, ../file.css)
                importPath.startsWith("./") || importPath.startsWith("../") -> {
                    LOG.info("📂 Processing as EXPLICIT RELATIVE path: $importPath")
                    return resolveRelativePath(currentFile, importPath)
                }

                // Absolute paths (/file.css)
                importPath.startsWith("/") -> {
                    LOG.info("🏠 Processing as ABSOLUTE path: $importPath")
                    val projectRoot = project.guessProjectDir()
                    return projectRoot?.findFileByRelativePath(importPath.substring(1))
                }

                // Node modules packages (@package/file or package/file)
                importPath.startsWith("@") && importPath.contains("/") -> {
                    LOG.info("📦 Processing as NODE_MODULES PACKAGE: $importPath")
                    return resolveNodeModulesPath(currentFile, importPath, project)
                }

                // Everything else is treated as relative to current file
                else -> {
                    LOG.info("📄 Processing as IMPLICIT RELATIVE path: $importPath")
                    return resolveRelativePath(currentFile, importPath)
                }
            }
        } catch (e: Exception) {
            LOG.error("💥 Error resolving import path: $importPath", e)
            return null
        }
    }

    private fun resolveRelativePath(currentFile: VirtualFile, relativePath: String): VirtualFile? {
        val currentDir = currentFile.parent ?: return null

        // If path already has an extension, use it directly
        if (relativePath.contains('.') && !relativePath.endsWith("/")) {
            LOG.info("📄 Relative path has extension: $relativePath")
            return VfsUtil.findRelativeFile(currentDir, *relativePath.split('/').toTypedArray())
        }

        // Try extensions based on the importing file's extension
        val currentExtension = currentFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "less" -> listOf("less", "css", "scss", "sass")
            "scss" -> listOf("scss", "sass", "css", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            else -> listOf("css", "scss", "sass", "less")
        }

        LOG.info("🔍 Trying extensions for '$relativePath' (from .${currentFile.extension}): $prioritizedExtensions")

        for (ext in prioritizedExtensions) {
            val pathWithExtension = "$relativePath.$ext"
            LOG.info("  🔎 Trying: $pathWithExtension in ${currentDir.path}")
            val resolved = VfsUtil.findRelativeFile(currentDir, *pathWithExtension.split('/').toTypedArray())
            if (resolved != null && resolved.exists()) {
                LOG.info("  ✅ Found: ${resolved.path}")
                return resolved
            } else {
                LOG.info("  ❌ Not found: $pathWithExtension")
            }
        }

        LOG.warn("🚫 No file found for relative path: $relativePath")
        return null
    }

    private fun resolveNodeModulesPath(
        currentFile: VirtualFile,
        packagePath: String,
        project: Project
    ): VirtualFile? {
        LOG.info("🔍 Searching for node_modules from: ${currentFile.path}")

        // Search up the directory tree
        var searchDir = currentFile.parent
        while (searchDir != null) {
            LOG.info("  🔎 Checking directory: ${searchDir.path}")
            val nodeModules = searchDir.findChild("node_modules")
            if (nodeModules != null && nodeModules.isDirectory) {
                LOG.info("  📦 Found node_modules at: ${nodeModules.path}")
                val resolvedFile = resolveInNodeModules(nodeModules, packagePath, currentFile)
                if (resolvedFile != null) return resolvedFile
            }
            searchDir = searchDir.parent
        }

        // Check project root
        val projectRoot = project.guessProjectDir()
        LOG.info("🏠 Checking project root: ${projectRoot?.path}")
        val projectNodeModules = projectRoot?.findChild("node_modules")
        if (projectNodeModules != null && projectNodeModules.isDirectory) {
            LOG.info("📦 Found project node_modules at: ${projectNodeModules.path}")
            return resolveInNodeModules(projectNodeModules, packagePath, currentFile)
        }

        LOG.warn("🚫 No node_modules found for: $packagePath")
        return null
    }

    private fun resolveInNodeModules(
        nodeModules: VirtualFile,
        packagePath: String,
        importingFile: VirtualFile
    ): VirtualFile? {
        LOG.info("🔧 Resolving '$packagePath' in: ${nodeModules.path}")

        // Handle paths with explicit extensions
        if (packagePath.contains('.') && !packagePath.endsWith("/")) {
            LOG.info("📄 Package path has extension: $packagePath")
            val pathParts = packagePath.split('/')
            var current = nodeModules

            for (part in pathParts) {
                LOG.info("  🔎 Looking for: $part in ${current.path}")
                current = current.findChild(part) ?: run {
                    LOG.info("  ❌ Not found: $part")
                    return null
                }
                LOG.info("  ✅ Found: ${current.path}")
            }

            return if (current.isDirectory) {
                LOG.info("  🚫 Resolved to directory, returning null")
                null
            } else {
                LOG.info("  ✅ Resolved to file: ${current.path}")
                current
            }
        }

        // Try different extensions
        val currentExtension = importingFile.extension?.lowercase()
        val prioritizedExtensions = when (currentExtension) {
            "less" -> listOf("less", "css", "scss", "sass")
            "scss" -> listOf("scss", "sass", "css", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            else -> listOf("css", "scss", "sass", "less")
        }

        LOG.info("🔍 Trying extensions for '$packagePath' (from .${importingFile.extension}): $prioritizedExtensions")

        for (ext in prioritizedExtensions) {
            val pathWithExtension = "$packagePath.$ext"
            LOG.info("  🔎 Trying: $pathWithExtension")
            val pathParts = pathWithExtension.split('/')
            var current = nodeModules

            for (part in pathParts) {
                current = current.findChild(part) ?: break
            }

            if (current != nodeModules && !current.isDirectory && current.exists()) {
                LOG.info("  ✅ Found: ${current.path}")
                return current
            } else {
                LOG.info("  ❌ Not found: $pathWithExtension")
            }
        }

        LOG.warn("🚫 No file found in node_modules for: $packagePath")
        return null
    }

    fun isExternalImport(file: VirtualFile, project: Project): Boolean {
        val projectRoot = project.guessProjectDir()?.path ?: return false
        val filePath = file.path
        return filePath.contains("/node_modules/") && !filePath.startsWith(projectRoot)
    }
}