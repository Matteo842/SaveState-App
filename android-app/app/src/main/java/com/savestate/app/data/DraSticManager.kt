package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles DraStic (Nintendo DS) save detection and scanning.
 *
 * DraStic stores saves under /storage/emulated/0/DraStic/:
 *   backup/      -> per-game battery saves (.dsv) and quick-save states (.dss)
 *   savestates/  -> per-game numbered save states (.ds0 … .ds9, .dst)
 *
 * Root-only path:
 *   /storage/emulated/0/Android/data/com.dsemu.drastic/files/
 */
class DraSticManager {

    companion object {
        private const val TAG = "DraSticManager"

        /** Battery-save / quick-save extensions stored in the backup folder. */
        private val BACKUP_EXTENSIONS = setOf("dsv", "dss")

        /** Save-state extensions stored in the savestates folder. */
        private val STATE_EXTENSIONS = setOf("dst")
        private val STATE_SLOT_PATTERN = Regex("^(.+)\\.ds(\\d)$")

        private val IGNORE_EXTENSIONS = setOf("cfg", "ini", "png", "jpg", "txt", "log", "bak")

        /**
         * Extract ROM base name from a DraStic save file.
         * "game.dsv"  -> "game"
         * "game.dss"  -> "game"
         * "game.ds2"  -> "game"
         * "game.dst"  -> "game"
         */
        fun romBaseName(fileName: String): String? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext in BACKUP_EXTENSIONS || ext in STATE_EXTENSIONS) {
                return fileName.substringBeforeLast('.')
            }
            STATE_SLOT_PATTERN.matchEntire(fileName)?.let {
                return it.groupValues[1]
            }
            return null
        }
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a user-selected SAF folder for DraStic saves.
     * Auto-detects whether the folder is the top-level DraStic dir,
     * backup, savestates, or a flat folder containing save files.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("backup", true) -> scanBackupSaf(documentFile)
            folderName.equals("savestates", true) -> scanStatesSaf(documentFile)
            folderName.contains("drastic", true) ||
                hasKnownSubfolders(documentFile) -> scanTopLevelSaf(documentFile)
            else -> scanAutoDetectSaf(documentFile)
        }
    }

    private fun hasKnownSubfolders(dir: DocumentFile): Boolean {
        return dir.listFiles().any {
            it.isDirectory && it.name?.let { n ->
                n.equals("backup", true) ||
                n.equals("savestates", true)
            } == true
        }
    }

    private fun scanTopLevelSaf(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("backup")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanBackupSaf(it))
        }
        root.findFile("savestates")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }

        if (results.isEmpty()) {
            results.addAll(scanAutoDetectSaf(root))
        }

        return mergeByRomName(results)
    }

    /**
     * Scan the backup folder: .dsv (battery) and .dss (quick save) files.
     */
    private fun scanBackupSaf(backupDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in backupDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in IGNORE_EXTENSIONS) continue

            if (ext in BACKUP_EXTENSIONS) {
                val romName = name.substringBeforeLast('.')
                gamesMap.getOrPut(romName) { mutableListOf() }.add(child)
            }
        }

        val folderUri = backupDir.uri.toString()

        return gamesMap.map { (romName, files) ->
            val saveTypes = files.mapNotNull { f ->
                f.name?.substringAfterLast('.')?.uppercase()
            }.distinct().joinToString(", ")

            DetectedGame(
                gameId = "nds_save_$romName",
                gameName = "$romName ($saveTypes)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DRASTIC,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = romName
            )
        }
    }

    /**
     * Scan the savestates folder: .ds0–.ds9 / .dst save-state files.
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

        return gamesMap.map { (romName, files) ->
            DetectedGame(
                gameId = "nds_state_$romName",
                gameName = "$romName (Save States)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DRASTIC,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = romName
            )
        }
    }

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
                    gameId = "nds_$romName",
                    gameName = romName,
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.DRASTIC,
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
                    gameId = documentFile.name ?: "drastic_unknown",
                    gameName = documentFile.name ?: "DraStic Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.DRASTIC,
                    saveCount = fileCount,
                    lastModified = documentFile.listFiles().maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return emptyList()
    }

    /**
     * Merge results from backup/ and savestates/ that share the same ROM name
     * into a single entry with combined save count.
     */
    private fun mergeByRomName(games: List<DetectedGame>): List<DetectedGame> {
        if (games.size <= 1) return games

        val merged = mutableMapOf<String, DetectedGame>()
        for (game in games) {
            val key = game.gameFilePrefix ?: game.gameId
            val existing = merged[key]
            if (existing == null) {
                merged[key] = game
            } else {
                merged[key] = existing.copy(
                    gameName = key,
                    saveCount = existing.saveCount + game.saveCount,
                    lastModified = maxOf(existing.lastModified, game.lastModified)
                )
            }
        }
        return merged.values.toList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan root-protected save paths for DraStic saves.
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            val backupPath = "$basePath/backup"
            if (rootHelper.directoryExists(backupPath)) {
                results.addAll(rootScanBackup(rootHelper, backupPath))
            }

            val statesPath = "$basePath/savestates"
            if (rootHelper.directoryExists(statesPath)) {
                results.addAll(rootScanStates(rootHelper, statesPath))
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return mergeByRomName(results)
    }

    private fun rootScanBackup(
        root: RootAccessHelper,
        backupPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(backupPath)
        val gamesMap = mutableMapOf<String, MutableList<String>>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in IGNORE_EXTENSIONS) continue
            if (ext in BACKUP_EXTENSIONS) {
                val romName = name.substringBeforeLast('.')
                gamesMap.getOrPut(romName) { mutableListOf() }.add(name)
            }
        }

        return gamesMap.map { (romName, files) ->
            val saveTypes = files.map { it.substringAfterLast('.').uppercase() }
                .distinct().joinToString(", ")

            DetectedGame(
                gameId = "nds_save_$romName",
                gameName = "$romName ($saveTypes)",
                savePath = backupPath,
                parentPath = backupPath,
                emulatorType = Emulator.DRASTIC,
                saveCount = files.size,
                lastModified = root.getLastModified(backupPath),
                gameFilePrefix = romName
            )
        }
    }

    private fun rootScanStates(
        root: RootAccessHelper,
        statesPath: String
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
                gameId = "nds_state_$romName",
                gameName = "$romName (Save States)",
                savePath = statesPath,
                parentPath = statesPath,
                emulatorType = Emulator.DRASTIC,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = romName
            )
        }
    }
}
