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
     *
     * Uses `cp -a` (archive) to preserve timestamps so that the extracted
     * cache copy retains the original file modification times — important when
     * these are later copied back to the emulator via [copyFromCache].
     */
    fun copyToCache(sourcePath: String, destDir: File): Boolean {
        destDir.mkdirs()
        val src = escapePath(sourcePath)
        val dst = escapePath(destDir.absolutePath)

        // Prefer cp -a (archive: preserves timestamps, permissions, symlinks).
        // Fall back to cp -rf for older Android shells that lack -a.
        var result = Shell.cmd("cp -a '$src'/. '$dst/'").exec()
        if (!result.isSuccess) {
            Log.w(TAG, "copyToCache: cp -a failed, retrying with cp -rf")
            result = Shell.cmd("cp -rf '$src'/. '$dst/'").exec()
        }
        if (!result.isSuccess) {
            Log.e(TAG, "copyToCache failed: ${result.out}")
        }
        return result.isSuccess
    }

    /**
     * Delete all files and subdirectories **inside** [dirPath] via root,
     * without removing [dirPath] itself. Used to clear stale save files from
     * a protected emulator directory before a restore operation.
     *
     * Safe to call even if the directory is empty; a non-existent path is a
     * no-op (returns true).
     */
    fun deleteContents(dirPath: String): Boolean {
        if (!directoryExists(dirPath)) return true // nothing to delete

        val escaped = escapePath(dirPath)
        // Delete everything inside the directory but keep the directory itself.
        // `find -mindepth 1 -delete` works bottom-up so nested items are removed first.
        val result = Shell.cmd(
            "find '$escaped' -mindepth 1 -delete 2>/dev/null"
        ).exec()
        if (!result.isSuccess) {
            // Fallback: rm -rf on the contents (slightly less safe but widely supported)
            val fallback = Shell.cmd("rm -rf '$escaped'/* '$escaped'/.[!.]* 2>/dev/null").exec()
            if (!fallback.isSuccess) {
                Log.w(TAG, "deleteContents: could not clear '$dirPath' (non-fatal): ${fallback.out}")
                return false
            }
        }
        Log.d(TAG, "deleteContents: cleared '$dirPath'")
        return true
    }

    /**
     * Copy the contents of [sourceDir] (app-writable) back to the
     * protected [destPath] via root, then fix ownership and SELinux
     * context so the target app can read its own save files.
     *
     * Root restore permission strategy:
     *  1. Resolve numeric UID/GID by walking UP the path tree to avoid issues
     *     with symbolic name resolution on different Android versions.
     *  2. Use `cp -a` (archive mode) to preserve file timestamps — emulators
     *     like Eden validate timestamp coherence and may discard saves with
     *     future/zero timestamps produced by a plain `cp -rf`.
     *  3. Apply Android-standard permissions: 0755 for directories and 0644
     *     for files. Using 770/660 (no world-read) can break emulators that
     *     read their own data through FUSE with a helper process running as a
     *     different user in the same app's group.
     *  4. Run `restorecon -RF` to fix SELinux labels.
     */
    fun copyFromCache(sourceDir: File, destPath: String): Boolean {
        val src = escapePath(sourceDir.absolutePath)
        val dst = escapePath(destPath)

        // Ensure destination directory exists (Eden may have deleted it when
        // the user cleared saves through the emulator UI).
        Shell.cmd("mkdir -p '$dst'").exec()

        // Resolve owner BEFORE copying.
        // We prefer numeric uid:gid (e.g. "10123:10123") because symbolic
        // names like "u0_a123" may not be resolvable in all shell environments.
        // Fallback chain:
        //   1. Numeric uid:gid from ancestor directory walk
        //   2. Symbolic owner from ancestor directory walk
        //   3. Package UID from `dumpsys package <pkg>` (when all ancestors are root-owned)
        val numericOwner = findAppOwnerNumeric(destPath)
        val symbolicOwner = if (numericOwner == null) findAppOwner(destPath) else null
        val packageOwner = if (numericOwner == null && symbolicOwner == null)
            getPackageUidFromPath(destPath)
        else null
        val resolvedOwner = numericOwner ?: symbolicOwner ?: packageOwner
        Log.d(TAG, "copyFromCache: resolved owner='$resolvedOwner' " +
                   "(numeric=$numericOwner, symbolic=$symbolicOwner, pkg=$packageOwner) for '$destPath'")

        // Use cp -a (archive) to preserve timestamps — critical for emulators
        // that validate save-file timestamps against internal NAND metadata.
        // Fall back to cp -rf if -a is not supported.
        var copyResult = Shell.cmd("cp -a '$src'/. '$dst/'").exec()
        if (!copyResult.isSuccess) {
            Log.w(TAG, "cp -a failed (${copyResult.out}), falling back to cp -rf")
            copyResult = Shell.cmd("cp -rf '$src'/. '$dst/'").exec()
        }
        if (!copyResult.isSuccess) {
            Log.e(TAG, "copyFromCache cp failed: ${copyResult.out}")
            return false
        }

        // Restore ownership so the emulator app can read/write its saves.
        if (resolvedOwner != null) {
            val chownResult = Shell.cmd("chown -R '$resolvedOwner' '$dst'").exec()
            if (!chownResult.isSuccess) {
                Log.w(TAG, "chown after restore failed (non-fatal): ${chownResult.out}")
            } else {
                Log.d(TAG, "Restored ownership '$resolvedOwner' on '$dst'")
            }
        } else {
            Log.w(TAG, "findAppOwner returned null for '$destPath' – skipping chown")
        }

        // Fix file permissions using Eden's exact native values:
        //   - Directories: 0770 (rwxrwx---) so the emulator process and its
        //     helper threads can traverse subdirectories.
        //   - Files:       0660 (rw-rw----) so the emulator can read/write.
        Shell.cmd(
            "find '$dst' -type d -exec chmod 770 {} \\; 2>/dev/null ; " +
            "find '$dst' -type f -exec chmod 660 {} \\; 2>/dev/null"
        ).exec()

        // Restore SELinux context so Android's security layer allows access.
        // restorecon is a no-op on non-SELinux devices, so safe to always run.
        val seResult = Shell.cmd("restorecon -RF '$dst' 2>/dev/null").exec()
        if (!seResult.isSuccess) {
            Log.d(TAG, "restorecon not available or failed (non-fatal)")
        } else {
            Log.d(TAG, "SELinux context restored on '$dst'")
        }

        Log.i(TAG, "copyFromCache complete: src=$src dst=$dst owner=$resolvedOwner")
        return true
    }

    /**
     * Walk UP the ancestor chain of [path] until we find a directory that is
     * NOT owned by root. Returns the owner as a **numeric** "uid:gid" string
     * (e.g. "10123:10123") which is more reliably usable in `chown` across
     * all Android shell environments.
     *
     * Example walk for an Eden save:
     *   /…/Android/data/dev.eden.eden_emulator/files/nand/user/save/<space>/<acct>/<title>/
     *   → <title>  root (just created)
     *   → …        root (just created)
     *   → files    uid=10123 gid=10123  ← FOUND ✓
     *
     * Returns "uid:gid" string, or null if no non-root ancestor is found.
     */
    private fun findAppOwnerNumeric(path: String): String? {
        var current = path
        repeat(15) {
            val escaped = escapePath(current)
            // stat -c '%u:%g' returns numeric uid:gid (e.g. "10123:10123")
            val result = Shell.cmd("stat -c '%u:%g' '$escaped' 2>/dev/null").exec()
            val owner = result.out.firstOrNull()?.trim()
            if (!owner.isNullOrBlank() && !owner.startsWith("0:") && owner != "0:0") {
                Log.d(TAG, "findAppOwnerNumeric: found '$owner' at '$current'")
                return owner
            }
            val parent = current.substringBeforeLast("/")
            if (parent.isEmpty() || parent == current) return null
            current = parent
        }
        return null
    }

    /**
     * Walk UP the ancestor chain of [path] until we find a directory that is
     * NOT owned by root. Returns symbolic "user:group" string as fallback when
     * numeric resolution is unavailable.
     *
     * Returns "user:group" string, or null if no non-root ancestor exists.
     */
    private fun findAppOwner(path: String): String? {
        var current = path
        repeat(15) {
            val escaped = escapePath(current)
            val result = Shell.cmd("stat -c '%U:%G' '$escaped' 2>/dev/null").exec()
            val owner = result.out.firstOrNull()?.trim()
            if (!owner.isNullOrBlank() && !owner.startsWith("root")) {
                Log.d(TAG, "findAppOwner: found '$owner' at '$current'")
                return owner
            }
            val parent = current.substringBeforeLast("/")
            if (parent.isEmpty() || parent == current) return null
            current = parent
        }
        return null
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
     * Last-resort UID resolution: extract the package name from a path of the
     * form `.../Android/data/<package>/...` and query the system package
     * manager for its installed UID.
     *
     * This is needed when the entire NAND directory tree was created by root
     * (all ancestors are uid=0) so [findAppOwnerNumeric] / [findAppOwner]
     * return null.
     *
     * Returns "uid:uid" string (e.g. "10123:10123") or null on failure.
     */
    private fun getPackageUidFromPath(path: String): String? {
        val pkg = extractPackageName(path) ?: return null
        // `dumpsys package <pkg>` outputs a line like: "    userId=10123"
        val result = Shell.cmd("dumpsys package '$pkg' 2>/dev/null | grep 'userId='").exec()
        val line = result.out.firstOrNull { it.contains("userId=") } ?: return null
        val uid = line.substringAfter("userId=").trim().split(Regex("\\s+")).firstOrNull() ?: return null
        if (uid.toLongOrNull() == null) return null
        Log.d(TAG, "getPackageUidFromPath: pkg='$pkg' uid=$uid")
        return "$uid:$uid"
    }

    /**
     * Extracts an Android package name from a path that contains
     * `/Android/data/<package>/` (case-insensitive).
     *
     * Returns null if the path does not match the expected pattern.
     */
    private fun extractPackageName(path: String): String? {
        val regex = Regex("/Android/data/([^/]+)/", RegexOption.IGNORE_CASE)
        return regex.find(path)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }

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
