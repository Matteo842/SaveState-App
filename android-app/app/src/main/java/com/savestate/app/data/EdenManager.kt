package com.savestate.app.data

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import org.json.JSONObject

/**
 * Handles Eden (Nintendo Switch) save detection and scanning.
 *
 * Eden is a Yuzu fork. Per `src/core/file_sys/savedata_factory.cpp`:
 *
 * OLD layout (still default for existing installs):
 *   <root>/nand/user/save/<space>/<account_id>/<title_id>/
 *     space:      "0000000000000000" (16-hex, masked save_id == 0)
 *     account_id: 32-hex string (concat of user_id[1] + user_id[0], UPPERCASE)
 *     title_id:   16-hex string (UPPERCASE)
 *
 * NEW "future" layout (preferred when present):
 *   <root>/nand/user/save/account/<uuid_raw>/<title_id>/0/    (Account saves)
 *   <root>/nand/user/save/device/<title_id>/0/                (Device saves)
 *     uuid_raw:   32-hex string (lowercase, no hyphens — Common::UUID::RawString())
 *     title_id:   16-hex string (UPPERCASE)
 *
 * User-accessible path (when Eden's user directory is set to internal storage):
 *   /storage/emulated/0/Eden/nand/user/save/...
 *
 * Root-only path:
 *   /storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save/...
 *   /storage/emulated/0/Android/data/dev.eden.eden_nightly/files/nand/user/save/...
 */
class EdenManager {

    companion object {
        private const val TAG = "EdenManager"

        /** Switch title-IDs and save-space folders are 16-char hex. */
        private val HEX16 = Regex("^[0-9a-fA-F]{16}$")

        /** Account folders (both legacy concat-u128 and new UUID raw) are 32-char hex. */
        private val HEX32 = Regex("^[0-9a-fA-F]{32}$")

        /** Folder names walked through when looking for title-id folders. */
        private val WALK_FOLDER_NAMES = setOf(
            "eden", "yuzu", "citron", "nand", "user", "save", "sdmc", "data", "account", "device"
        )

        // ── Switch game-title database ──────────────────────────────────

        private var switchGameDatabase: Map<String, String>? = null

        /**
         * Load the bundled Switch title-ID → game-name database from assets.
         * Safe to call multiple times; only the first call parses the file.
         */
        fun initDatabase(context: Context) {
            if (switchGameDatabase != null) return

            try {
                val jsonString = context.assets.open("switch_game_database.json")
                    .bufferedReader()
                    .use { it.readText() }

                val root = JSONObject(jsonString)
                // Database may be flat {id: name} or nested {"games": {...}}.
                val gamesObj = if (root.has("games")) root.getJSONObject("games") else root

                val database = mutableMapOf<String, String>()
                gamesObj.keys().forEach { key ->
                    database[key.uppercase()] = gamesObj.getString(key)
                }

                switchGameDatabase = database
                Log.d(TAG, "Loaded Switch game database with ${database.size} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Switch game database: ${e.message}")
                switchGameDatabase = emptyMap()
            }
        }

        /**
         * Look up a game name by 16-char hex title ID. Case-insensitive.
         */
        fun getGameNameFromDatabase(titleId: String): String? {
            return switchGameDatabase?.get(titleId.uppercase())
        }

        fun isDatabaseLoaded(): Boolean = switchGameDatabase != null
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a user-selected SAF folder for Eden/Yuzu/Citron saves.
     * Auto-detects which level of the nand/user/save tree was selected.
     */
    fun scanSafFolder(documentFile: DocumentFile, emulatorType: Emulator): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName' for ${emulatorType.displayName}")

        val titleDirs = mutableListOf<DocumentFile>()
        collectTitleDirsSaf(documentFile, titleDirs, depth = 0)

        if (titleDirs.isEmpty()) {
            // Fallback: if folder looks like a single title-id save dir, wrap it.
            if (HEX16.matches(folderName)) {
                titleDirs.add(documentFile)
            } else {
                return emptyList()
            }
        }

        return titleDirs.map { dir -> buildGameFromSafTitleDir(dir, emulatorType) }
    }

    /**
     * Walk at most 7 levels deep looking for title-id folders.
     * Possible paths from a top-level Eden root:
     *   <root>/nand/user/save/<space16>/<account32>/<title16>          (OLD)
     *   <root>/nand/user/save/account/<uuid32>/<title16>/0/...         (NEW)
     *   <root>/nand/user/save/device/<title16>/0/...                   (NEW device)
     */
    private fun collectTitleDirsSaf(
        dir: DocumentFile,
        results: MutableList<DocumentFile>,
        depth: Int
    ) {
        if (depth > 7) return
        val children = dir.listFiles()
        val parentName = dir.name ?: ""

        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            if (HEX16.matches(name)) {
                // A 16-hex folder is a title-id when its parent is:
                //   - a 32-hex account folder (covers OLD account, NEW account/uuid)
                //   - "device" literal        (covers NEW save/device/<title>/0/)
                val isTitleId = HEX32.matches(parentName) ||
                    parentName.equals("device", ignoreCase = true)

                if (isTitleId) {
                    results.add(child)
                    continue
                }

                // Otherwise it is most likely the save-space folder
                // ("0000000000000000"); recurse to discover account/title.
                collectTitleDirsSaf(child, results, depth + 1)
                continue
            }

            // 32-hex account folder → recurse to find title-ids inside.
            if (HEX32.matches(name)) {
                collectTitleDirsSaf(child, results, depth + 1)
                continue
            }

            if (name.lowercase() in WALK_FOLDER_NAMES || depth < 4) {
                collectTitleDirsSaf(child, results, depth + 1)
            }
        }
    }

    private fun buildGameFromSafTitleDir(titleDir: DocumentFile, emulatorType: Emulator): DetectedGame {
        val titleId = titleDir.name ?: "unknown"
        val saveCount = try {
            countFilesRecursivelySaf(titleDir)
        } catch (e: Exception) {
            0
        }
        val lastModified = try {
            maxLastModifiedSaf(titleDir)
        } catch (e: Exception) {
            titleDir.lastModified()
        }
        val parentUri = titleDir.parentFile?.uri?.toString() ?: titleDir.uri.toString()
        val displayName = getGameNameFromDatabase(titleId) ?: titleId

        return DetectedGame(
            gameId = "switch_$titleId",
            gameName = displayName,
            savePath = titleDir.uri.toString(),
            parentPath = parentUri,
            emulatorType = emulatorType,
            saveCount = saveCount,
            lastModified = lastModified
        )
    }

    private fun countFilesRecursivelySaf(dir: DocumentFile): Int {
        var count = 0
        for (child in dir.listFiles()) {
            if (child.isFile) count++
            else if (child.isDirectory) count += countFilesRecursivelySaf(child)
        }
        return count
    }

    private fun maxLastModifiedSaf(dir: DocumentFile): Long {
        var max = dir.lastModified()
        for (child in dir.listFiles()) {
            val childTime = if (child.isDirectory) maxLastModifiedSaf(child)
                            else child.lastModified()
            if (childTime > max) max = childTime
        }
        return max
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan root-protected save paths for Eden saves.
     * Expected layout under each basePath: nand/user/save/<space>/<account>/<title>/
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>,
        emulatorType: Emulator
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            // Typical structure: <basePath>/nand/user/save/<space>/<account>/<title>
            val savePath = "$basePath/nand/user/save"
            if (rootHelper.directoryExists(savePath)) {
                results.addAll(rootScanSaveTree(rootHelper, savePath, emulatorType))
            } else {
                // Fallback: try to discover nested save folder.
                val discovered = findSaveDirRoot(rootHelper, basePath, 0)
                if (discovered != null) {
                    results.addAll(rootScanSaveTree(rootHelper, discovered, emulatorType))
                }
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    private fun findSaveDirRoot(
        root: RootAccessHelper,
        dir: String,
        depth: Int
    ): String? {
        if (depth > 4) return null
        val subdirs = root.listDirectories(dir)
        if (subdirs.any { it.equals("save", true) } &&
            subdirs.any { it.equals("user", true) || it.equals("nand", true) }) {
            // Could be the nand/user parent; build path.
            val save = "$dir/save"
            if (root.directoryExists(save)) return save
        }
        for (sub in subdirs) {
            if (sub.equals("save", true)) {
                val candidate = "$dir/$sub"
                // Treat as the save dir if it holds any of:
                //   - a 16-hex space folder (OLD layout)
                //   - "account" / "device" literal (NEW layout)
                val children = root.listDirectories(candidate)
                val looksLikeSave = children.any {
                    HEX16.matches(it) ||
                        it.equals("account", ignoreCase = true) ||
                        it.equals("device", ignoreCase = true)
                }
                if (looksLikeSave) return candidate
            }
            val nested = findSaveDirRoot(root, "$dir/$sub", depth + 1)
            if (nested != null) return nested
        }
        return null
    }

    /**
     * Mirrors Eden's title-ID masking: `title_id & ~(0xFFULL)` clears the
     * lower 8 bits (= the last 2 hex chars).  Eden uses this masked value as
     * the folder name in the NEW save-data layout
     * (`nand/user/save/account/<uuid>/<masked_title>/0/`).
     *
     * Example: 0100A94014A4C00E → 0100A94014A4C000
     */
    private fun maskTitleId(titleId: String): String =
        if (titleId.length >= 2)
            titleId.uppercase().dropLast(2) + "00"
        else
            titleId.uppercase()

    /**
     * Scan [savePath] (= `nand/user/save/`) for all title save directories.
     *
     * Eden maintains **two** on-disk layouts and always prefers the NEW one
     * when both exist (see `GetFullPath` in savedata_factory.cpp):
     *
     *   NEW:  save/account/<uuid32>/<title_masked>/0/
     *         save/device/<title_masked>/0/
     *   OLD:  save/<space16>/<account32>/<title16>/
     *
     * We perform two passes so that any title already found in a NEW-layout
     * path is **not** duplicated from its OLD-layout path.  Without this,
     * users could accidentally back up the stale OLD path while Eden reads
     * from the NEW path, making the restore invisible to the emulator.
     */
    private fun rootScanSaveTree(
        root: RootAccessHelper,
        savePath: String,
        emulatorType: Emulator
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()
        val seenTitlePaths = mutableSetOf<String>()

        // Collect the masked title IDs that already have a NEW-layout directory so
        // we can skip their OLD-layout counterparts in the second pass.
        val maskedTitleIdsInNewPaths = mutableSetOf<String>()

        val topDirs = root.listDirectories(savePath)

        // ── Pass 1: NEW layout (account/ and device/) ──────────────────────────
        for (entry in topDirs) {
            val entryPath = "$savePath/$entry"
            when {
                // NEW: save/account/<uuid32>/<title16_masked>/0/
                entry.equals("account", ignoreCase = true) -> {
                    for (uuid in root.listDirectories(entryPath)) {
                        if (!HEX32.matches(uuid)) continue
                        val uuidPath = "$entryPath/$uuid"
                        for (titleId in root.listDirectories(uuidPath)) {
                            if (!HEX16.matches(titleId)) continue
                            val titlePath = "$uuidPath/$titleId"
                            if (seenTitlePaths.add(titlePath)) {
                                maskedTitleIdsInNewPaths.add(maskTitleId(titleId))
                                results.add(buildRootGame(root, titleId, titlePath, uuidPath, emulatorType))
                            }
                        }
                    }
                }

                // NEW: save/device/<title16_masked>/0/
                entry.equals("device", ignoreCase = true) -> {
                    for (titleId in root.listDirectories(entryPath)) {
                        if (!HEX16.matches(titleId)) continue
                        val titlePath = "$entryPath/$titleId"
                        if (seenTitlePaths.add(titlePath)) {
                            maskedTitleIdsInNewPaths.add(maskTitleId(titleId))
                            results.add(buildRootGame(root, titleId, titlePath, entryPath, emulatorType))
                        }
                    }
                }
            }
        }

        // ── Pass 2: OLD layout (<space16>/<account32>/<title16>/) ──────────────
        // Skip any title whose masked ID was already collected from a NEW path.
        // This mirrors Eden's own logic: if `future_dir != nullptr`, use NEW path.
        for (entry in topDirs) {
            if (!HEX16.matches(entry)) continue   // Only OLD-style space-ID folders
            val spacePath = "$savePath/$entry"
            for (account in root.listDirectories(spacePath)) {
                if (!HEX32.matches(account)) continue
                val accountPath = "$spacePath/$account"
                for (titleId in root.listDirectories(accountPath)) {
                    if (!HEX16.matches(titleId)) continue
                    // Skip if the NEW layout already covers this title (possibly
                    // with a masked ID — e.g. OLD has "…C00E", NEW has "…C000").
                    if (maskTitleId(titleId) in maskedTitleIdsInNewPaths) {
                        Log.d(TAG, "rootScanSaveTree: skipping OLD-layout title '$titleId' " +
                                   "(covered by NEW-layout path)")
                        continue
                    }
                    val titlePath = "$accountPath/$titleId"
                    if (seenTitlePaths.add(titlePath)) {
                        results.add(buildRootGame(root, titleId, titlePath, accountPath, emulatorType))
                    }
                }
            }
        }

        Log.d(TAG, "rootScanSaveTree($savePath): ${results.size} title(s) found")
        return results
    }

    private fun buildRootGame(
        root: RootAccessHelper,
        titleId: String,
        titlePath: String,
        parentPath: String,
        emulatorType: Emulator
    ): DetectedGame {
        val displayName = getGameNameFromDatabase(titleId) ?: titleId
        return DetectedGame(
            gameId = "switch_$titleId",
            gameName = displayName,
            savePath = titlePath,
            parentPath = parentPath,
            emulatorType = emulatorType,
            saveCount = root.countFiles(titlePath),
            lastModified = root.getLastModified(titlePath)
        )
    }
}
