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
 * Eden is a Yuzu fork, so it reuses the same on-disk layout:
 *   <root>/nand/user/save/<save_data_space>/<account_id>/<title_id>/
 *
 *   save_data_space: "0000000000000000" for user saves (16 hex chars)
 *   account_id:      16-char hex, user profile UUID (short form)
 *   title_id:        16-char hex, Nintendo Switch game ID
 *
 * Each <title_id> folder represents a single game's save.
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

        /** Switch IDs are 16-character hex strings. */
        private val HEX16 = Regex("^[0-9a-fA-F]{16}$")

        /** The default save-data space (user saves) folder name. */
        private const val USER_SAVE_SPACE = "0000000000000000"

        /** Folder names walked through when looking for title-id folders. */
        private val WALK_FOLDER_NAMES = setOf(
            "eden", "nand", "user", "save", "sdmc", "data"
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
     * Scan a user-selected SAF folder for Eden saves.
     * Auto-detects which level of the nand/user/save tree was selected.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

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

        return titleDirs.map { dir -> buildGameFromSafTitleDir(dir) }
    }

    /**
     * Walk at most 6 levels deep looking for title-id folders.
     * The Switch path is: save/<space>/<account>/<title>, so a top-level
     * Eden root folder may sit 3–5 levels above the title dir.
     */
    private fun collectTitleDirsSaf(
        dir: DocumentFile,
        results: MutableList<DocumentFile>,
        depth: Int
    ) {
        if (depth > 6) return
        val children = dir.listFiles()

        for (child in children) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            if (HEX16.matches(name)) {
                // A 16-hex folder at this depth is a title-id when we are
                // inside an <account_id> folder. We use heuristics: if the
                // parent looks like an account folder (also 16-hex) and the
                // title folder contains files, treat it as a game.
                val parentName = dir.name ?: ""
                val looksLikeAccountChild = HEX16.matches(parentName) &&
                    !parentName.equals(USER_SAVE_SPACE, true)

                if (looksLikeAccountChild) {
                    results.add(child)
                    continue
                }

                // Otherwise keep walking: could be the save-space or account
                // folder itself. Recurse one deeper.
                collectTitleDirsSaf(child, results, depth + 1)
                continue
            }

            if (name.lowercase() in WALK_FOLDER_NAMES || depth < 3) {
                collectTitleDirsSaf(child, results, depth + 1)
            }
        }
    }

    private fun buildGameFromSafTitleDir(titleDir: DocumentFile): DetectedGame {
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
            emulatorType = Emulator.EDEN,
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
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            // Typical structure: <basePath>/nand/user/save/<space>/<account>/<title>
            val savePath = "$basePath/nand/user/save"
            if (rootHelper.directoryExists(savePath)) {
                results.addAll(rootScanSaveTree(rootHelper, savePath))
            } else {
                // Fallback: try to discover nested save folder.
                val discovered = findSaveDirRoot(rootHelper, basePath, 0)
                if (discovered != null) {
                    results.addAll(rootScanSaveTree(rootHelper, discovered))
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
                // Only treat as the save dir if it holds a 16-hex child.
                if (root.listDirectories(candidate).any { HEX16.matches(it) }) {
                    return candidate
                }
            }
            val nested = findSaveDirRoot(root, "$dir/$sub", depth + 1)
            if (nested != null) return nested
        }
        return null
    }

    private fun rootScanSaveTree(
        root: RootAccessHelper,
        savePath: String
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (space in root.listDirectories(savePath)) {
            if (!HEX16.matches(space)) continue
            val spacePath = "$savePath/$space"

            for (account in root.listDirectories(spacePath)) {
                if (!HEX16.matches(account)) continue
                val accountPath = "$spacePath/$account"

                for (titleId in root.listDirectories(accountPath)) {
                    if (!HEX16.matches(titleId)) continue
                    val titlePath = "$accountPath/$titleId"

                    val displayName = getGameNameFromDatabase(titleId) ?: titleId
                    results.add(
                        DetectedGame(
                            gameId = "switch_$titleId",
                            gameName = displayName,
                            savePath = titlePath,
                            parentPath = accountPath,
                            emulatorType = Emulator.EDEN,
                            saveCount = root.countFiles(titlePath),
                            lastModified = root.getLastModified(titlePath)
                        )
                    )
                }
            }
        }

        return results
    }
}
