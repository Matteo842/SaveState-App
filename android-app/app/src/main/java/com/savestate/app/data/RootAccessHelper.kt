package com.savestate.app.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Provides root-privileged file access via libsu for emulators that store
 * saves in protected Android/data/ paths (Dolphin, DuckStation, etc.).
 *
 * All operations run shell commands through `su` and should be called
 * from a background thread.
 */
class RootAccessHelper {

    companion object {
        private const val TAG = "RootAccessHelper"
    }

    /**
     * Metadata for a file or directory discovered via root shell.
     */
    data class FileInfo(
        val name: String,
        val isDirectory: Boolean,
        val size: Long = 0,
        val lastModified: Long = 0
    )

    /**
     * Check whether the device has root and the user has granted access.
     * Safe to call from any thread; the first call may trigger the
     * Magisk/SuperSU grant dialog.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val result = Shell.cmd("id").exec()
            val isRoot = result.isSuccess && result.out.any { it.contains("uid=0") }
            Log.d(TAG, "Root check: $isRoot (out=${result.out})")
            isRoot
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed: ${e.message}", e)
            false
        }
    }

    /**
     * Test if a directory exists at [path].
     */
    fun directoryExists(path: String): Boolean {
        val result = Shell.cmd("[ -d '${escapePath(path)}' ] && echo YES").exec()
        return result.isSuccess && result.out.any { it.trim() == "YES" }
    }

    /**
     * List immediate children of [dirPath].
     * Returns empty list if the directory does not exist or is not readable.
     */
    fun listFiles(dirPath: String): List<FileInfo> {
        val escaped = escapePath(dirPath)
        val result = Shell.cmd(
            "ls -la '$escaped' 2>/dev/null"
        ).exec()

        if (!result.isSuccess) {
            Log.w(TAG, "listFiles failed for $dirPath (code=${result.code})")
            return emptyList()
        }

        return parseLsOutput(result.out)
    }

    /**
     * List only directory names inside [dirPath] (non-recursive).
     */
    fun listDirectories(dirPath: String): List<String> {
        val escaped = escapePath(dirPath)
        val result = Shell.cmd(
            "ls -1 '$escaped' 2>/dev/null"
        ).exec()

        if (!result.isSuccess) return emptyList()

        val dirs = mutableListOf<String>()
        for (name in result.out) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") continue
            if (directoryExists("$dirPath/$trimmed")) {
                dirs.add(trimmed)
            }
        }
        return dirs
    }

    /**
     * List only file names inside [dirPath] (non-recursive).
     */
    fun listFileNames(dirPath: String): List<String> {
        val escaped = escapePath(dirPath)
        val result = Shell.cmd(
            "find '$escaped' -maxdepth 1 -type f -printf '%f\\n' 2>/dev/null"
        ).exec()

        if (!result.isSuccess) {
            // Fallback for devices without GNU find -printf
            val fallback = Shell.cmd(
                "ls -1 '$escaped' 2>/dev/null"
            ).exec()
            if (!fallback.isSuccess) return emptyList()
            return fallback.out
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
                .filter { !directoryExists("$dirPath/$it") }
        }

        return result.out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Count files in a directory (non-recursive).
     */
    fun countFiles(dirPath: String): Int {
        val escaped = escapePath(dirPath)
        val result = Shell.cmd(
            "find '$escaped' -maxdepth 1 -type f 2>/dev/null | wc -l"
        ).exec()
        return result.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Recursively copy [sourcePath] into [destDir].
     * The destination directory must be writable by the app (e.g. cache dir).
     * Uses `su` to read from the protected source and writes to an
     * app-accessible destination.
     */
    fun copyToCache(sourcePath: String, destDir: File): Boolean {
        destDir.mkdirs()
        val src = escapePath(sourcePath)
        val dst = escapePath(destDir.absolutePath)

        val result = Shell.cmd("cp -rf '$src'/. '$dst/'").exec()
        if (!result.isSuccess) {
            Log.e(TAG, "copyToCache failed: ${result.out}")
        }
        return result.isSuccess
    }

    /**
     * Copy the contents of [sourceDir] (app-writable) back to the
     * protected [destPath] via root.
     */
    fun copyFromCache(sourceDir: File, destPath: String): Boolean {
        val src = escapePath(sourceDir.absolutePath)
        val dst = escapePath(destPath)

        val result = Shell.cmd(
            "cp -rf '$src'/. '$dst/'"
        ).exec()
        if (!result.isSuccess) {
            Log.e(TAG, "copyFromCache failed: ${result.out}")
        }
        return result.isSuccess
    }

    /**
     * Get the last-modified timestamp (epoch millis) for a path.
     */
    fun getLastModified(path: String): Long {
        val escaped = escapePath(path)
        val result = Shell.cmd("stat -c %Y '$escaped' 2>/dev/null").exec()
        val seconds = result.out.firstOrNull()?.trim()?.toLongOrNull() ?: return 0L
        return seconds * 1000L
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Escape single quotes in a file path for safe shell interpolation.
     */
    private fun escapePath(path: String): String =
        path.replace("'", "'\\''")

    /**
     * Parse typical `ls -la` output into [FileInfo] entries.
     * Expected format per line:
     *   drwxrwx--x  3 u0_a123 u0_a123  4096 2025-01-15 10:30 Card A
     *   -rw-rw----  1 u0_a123 u0_a123 12345 2025-01-15 10:30 save.gci
     * The first line ("total N") is skipped.
     */
    private fun parseLsOutput(lines: List<String>): List<FileInfo> {
        val results = mutableListOf<FileInfo>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total ")) continue

            val isDir = trimmed.startsWith('d')
            // ls -la columns: perms, links, owner, group, size, date, time, name...
            // Name may contain spaces, so we split up to 8 tokens and join the rest.
            val parts = trimmed.split(Regex("\\s+"), limit = 8)
            if (parts.size < 8) continue

            val name = parts[7]
            if (name == "." || name == "..") continue

            val size = parts[4].toLongOrNull() ?: 0L

            results.add(FileInfo(name = name, isDirectory = isDir, size = size))
        }

        return results
    }
}
