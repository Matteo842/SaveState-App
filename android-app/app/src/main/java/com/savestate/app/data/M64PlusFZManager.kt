package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles M64Plus FZ (Nintendo 64) save detection and scanning.
 *
 * M64Plus FZ stores data either in Android/data/<pkg>/files/ (requires root)
 * or in an external folder chosen by the user (Settings → Data → External).
 *
 * Typical folder layout inside the data directory:
 *   GameData/         -> per-ROM save files (.sra, .eep, .fla, .mpk)
 *   SlotSaves/        -> save states (.st0 … .st9, .fzs)
 *   AutoSaves/        -> automatic save states
 *
 * When the user points at the top-level folder, we auto-detect sub-folders.
 * When pointing at GameData or SlotSaves directly, we scan that single folder.
 */
class M64PlusFZManager {

    companion object {
        private const val TAG = "M64PlusFZManager"

        /** Native N64 game-save extensions (battery/memory). */
        private val GAME_SAVE_EXTENSIONS = setOf("sra", "eep", "fla", "mpk")

        /** Save-state extensions produced by M64Plus FZ. */
        private val STATE_EXTENSIONS = setOf("fzs", "fzs.bak")
        private val STATE_SLOT_PATTERN = Regex("^(.+)\\.st(\\d+)$")

        /** Files to ignore while scanning. */
        private val IGNORE_EXTENSIONS = setOf("cfg", "ini", "png", "jpg", "txt", "log")

        fun romBaseName(fileName: String): String? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext in GAME_SAVE_EXTENSIONS) {
                return fileName.substringBeforeLast('.')
            }
            STATE_SLOT_PATTERN.matchEntire(fileName)?.let {
                return it.groupValues[1]
            }
            if (ext == "fzs") {
                return fileName.substringBeforeLast('.')
            }
            return null
        }
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a user-selected SAF folder for M64Plus FZ saves.
     * Auto-detects whether the folder is the top-level data dir, GameData,
     * SlotSaves, AutoSaves, or a flat folder containing save files.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("GameData", true) -> scanGameDataSaf(documentFile)
            folderName.equals("SlotSaves", true) ||
            folderName.equals("AutoSaves", true) -> scanStatesSaf(documentFile)
            hasKnownSubfolders(documentFile) -> scanTopLevelSaf(documentFile)
            else -> scanAutoDetectSaf(documentFile)
        }
    }

    private fun hasKnownSubfolders(dir: DocumentFile): Boolean {
        return dir.listFiles().any {
            it.isDirectory && it.name?.let { n ->
                n.equals("GameData", true) ||
                n.equals("SlotSaves", true) ||
                n.equals("AutoSaves", true)
            } == true
        }
    }

    /**
     * User selected the top-level M64Plus FZ data folder; dive into each
     * known subfolder.
     */
    private fun scanTopLevelSaf(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("GameData")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanGameDataSaf(it))
        }
        root.findFile("SlotSaves")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }
        root.findFile("AutoSaves")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }

        if (results.isEmpty()) {
            results.addAll(scanAutoDetectSaf(root))
        }

        return results
    }

    /**
     * Scan GameData folder: each save file corresponds to a ROM.
     * Files are grouped by the ROM base name.
     */
    private fun scanGameDataSaf(gameDataDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in gameDataDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in IGNORE_EXTENSIONS) continue

            if (ext in GAME_SAVE_EXTENSIONS) {
                val romName = name.substringBeforeLast('.')
                gamesMap.getOrPut(romName) { mutableListOf() }.add(child)
            }
        }

        val folderUri = gameDataDir.uri.toString()

        return gamesMap.map { (romName, files) ->
            val saveTypes = files.mapNotNull { f ->
                f.name?.substringAfterLast('.')?.uppercase()
            }.distinct().joinToString(", ")

            DetectedGame(
                gameId = "n64_save_$romName",
                gameName = "$romName ($saveTypes)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.M64PLUS_FZ,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = romName
            )
        }
    }

    /**
     * Scan SlotSaves / AutoSaves folder: save-state files grouped by ROM name.
     */
    private fun scanStatesSaf(statesDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in statesDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val romName = romBaseName(name) ?: continue
            gamesMap.getOrPut(romName) { mutableListOf() }.add(child)
        }

        val folderUri = statesDir.uri.toString()
        val label = statesDir.name ?: "Save States"

        return gamesMap.map { (romName, files) ->
            DetectedGame(
                gameId = "n64_state_$romName",
                gameName = "$romName ($label)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.M64PLUS_FZ,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = romName
            )
        }
    }

    /**
     * Fallback: auto-detect file types and group by ROM name.
     */
    private fun scanAutoDetectSaf(documentFile: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in documentFile.listFiles()) {
            if (child.isDirectory) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in IGNORE_EXTENSIONS) continue

            val romName = romBaseName(name)
            if (romName != null) {
                gamesMap.getOrPut(romName) { mutableListOf() }.add(child)
            }
        }

        if (gamesMap.isNotEmpty()) {
            val folderUri = documentFile.uri.toString()
            return gamesMap.map { (romName, files) ->
                DetectedGame(
                    gameId = "n64_$romName",
                    gameName = romName,
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.M64PLUS_FZ,
                    saveCount = files.size,
                    lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                    gameFilePrefix = romName
                )
            }
        }

        val fileCount = documentFile.listFiles().count { it.isFile }
        if (fileCount > 0) {
            val folderUri = documentFile.uri.toString()
            return listOf(
                DetectedGame(
                    gameId = documentFile.name ?: "m64plus_unknown",
                    gameName = documentFile.name ?: "M64Plus FZ Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.M64PLUS_FZ,
                    saveCount = fileCount,
                    lastModified = documentFile.listFiles().maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return emptyList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan root-protected save paths for M64Plus FZ saves.
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            val gameDataPath = "$basePath/GameData"
            if (rootHelper.directoryExists(gameDataPath)) {
                results.addAll(rootScanGameData(rootHelper, gameDataPath))
            }

            val slotSavesPath = "$basePath/SlotSaves"
            if (rootHelper.directoryExists(slotSavesPath)) {
                results.addAll(rootScanStates(rootHelper, slotSavesPath, "SlotSaves"))
            }

            val autoSavesPath = "$basePath/AutoSaves"
            if (rootHelper.directoryExists(autoSavesPath)) {
                results.addAll(rootScanStates(rootHelper, autoSavesPath, "AutoSaves"))
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    private fun rootScanGameData(
        root: RootAccessHelper,
        gameDataPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(gameDataPath)
        val gamesMap = mutableMapOf<String, MutableList<String>>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in IGNORE_EXTENSIONS) continue
            if (ext in GAME_SAVE_EXTENSIONS) {
                val romName = name.substringBeforeLast('.')
                gamesMap.getOrPut(romName) { mutableListOf() }.add(name)
            }
        }

        return gamesMap.map { (romName, files) ->
            val saveTypes = files.map { it.substringAfterLast('.').uppercase() }
                .distinct().joinToString(", ")

            DetectedGame(
                gameId = "n64_save_$romName",
                gameName = "$romName ($saveTypes)",
                savePath = gameDataPath,
                parentPath = gameDataPath,
                emulatorType = Emulator.M64PLUS_FZ,
                saveCount = files.size,
                lastModified = root.getLastModified(gameDataPath),
                gameFilePrefix = romName
            )
        }
    }

    private fun rootScanStates(
        root: RootAccessHelper,
        statesPath: String,
        label: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(statesPath)
        val gamesMap = mutableMapOf<String, Int>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val romName = romBaseName(name) ?: continue
            gamesMap[romName] = (gamesMap[romName] ?: 0) + 1
        }

        return gamesMap.map { (romName, count) ->
            DetectedGame(
                gameId = "n64_state_$romName",
                gameName = "$romName ($label)",
                savePath = statesPath,
                parentPath = statesPath,
                emulatorType = Emulator.M64PLUS_FZ,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = romName
            )
        }
    }
}
