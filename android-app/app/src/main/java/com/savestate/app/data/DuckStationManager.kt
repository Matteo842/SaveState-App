package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles DuckStation (PlayStation 1) save detection and scanning.
 *
 * DuckStation stores saves in Android/data/com.github.stenzek.duckstation/files/:
 *   memcards/   -> shared_card_1.mcd, shared_card_2.mcd, or per-game .mcd files
 *   savestates/ -> GAMEID_N.sav save state files
 *
 * Memory cards are backed up as a whole folder; save states use file-prefix grouping.
 */
class DuckStationManager {

    companion object {
        private const val TAG = "DuckStationManager"

        // Save state filename: GAMEID_N.sav  (e.g., SLUS-00594_1.sav)
        private val STATE_PATTERN = Regex("^(.+)_(\\d+)\\.sav$")

        // Memory card files
        private val MEMCARD_PATTERN = Regex("^.*\\.mcd$", RegexOption.IGNORE_CASE)

        /**
         * Extract game ID from a DuckStation save state filename.
         * e.g. "SLUS-00594_1.sav" -> "SLUS-00594"
         */
        fun getStateGameId(fileName: String): String? =
            STATE_PATTERN.matchEntire(fileName)?.groupValues?.get(1)
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a folder selected via SAF for DuckStation saves.
     * Auto-detects whether the folder contains memcards or savestates.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("memcards", true) -> scanMemcardsSaf(documentFile)
            folderName.equals("savestates", true) -> scanStatesSaf(documentFile)
            folderName.equals("files", true) || folderName.contains("duckstation", true) ->
                scanDuckStationRootSaf(documentFile)
            else -> scanAutoDetectSaf(documentFile)
        }
    }

    private fun scanDuckStationRootSaf(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("memcards")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanMemcardsSaf(it))
        }
        root.findFile("savestates")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }

        return results
    }

    private fun scanMemcardsSaf(memcardsDir: DocumentFile): List<DetectedGame> {
        val mcdFiles = memcardsDir.listFiles().filter {
            it.isFile && MEMCARD_PATTERN.matches(it.name ?: "")
        }
        if (mcdFiles.isEmpty()) return emptyList()

        val folderUri = memcardsDir.uri.toString()
        val fileNames = mcdFiles.mapNotNull { it.name }

        return listOf(
            DetectedGame(
                gameId = "DuckStation_memcards",
                gameName = "DuckStation Memory Cards (${fileNames.joinToString(", ")})",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DUCKSTATION,
                saveCount = mcdFiles.size,
                lastModified = mcdFiles.maxOfOrNull { it.lastModified() } ?: 0L
            )
        )
    }

    private fun scanStatesSaf(statesDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in statesDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val gameId = getStateGameId(name) ?: continue
            gamesMap.getOrPut(gameId) { mutableListOf() }.add(child)
        }

        val folderUri = statesDir.uri.toString()

        return gamesMap.map { (gameId, files) ->
            DetectedGame(
                gameId = gameId,
                gameName = "$gameId (Save States)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DUCKSTATION,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = gameId
            )
        }
    }

    private fun scanAutoDetectSaf(documentFile: DocumentFile): List<DetectedGame> {
        val children = documentFile.listFiles()

        if (children.any { it.isFile && MEMCARD_PATTERN.matches(it.name ?: "") }) {
            return scanMemcardsSaf(documentFile)
        }
        if (children.any { it.isFile && getStateGameId(it.name ?: "") != null }) {
            return scanStatesSaf(documentFile)
        }

        val fileCount = children.count { it.isFile }
        if (fileCount > 0) {
            val folderUri = documentFile.uri.toString()
            return listOf(
                DetectedGame(
                    gameId = documentFile.name ?: "duckstation_unknown",
                    gameName = documentFile.name ?: "DuckStation Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.DUCKSTATION,
                    saveCount = fileCount,
                    lastModified = children.maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return emptyList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan all known root save paths for DuckStation saves.
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            val memcardsPath = "$basePath/memcards"
            if (rootHelper.directoryExists(memcardsPath)) {
                results.addAll(rootScanMemcards(rootHelper, memcardsPath))
            }

            val statesPath = "$basePath/savestates"
            if (rootHelper.directoryExists(statesPath)) {
                results.addAll(rootScanStates(rootHelper, statesPath))
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    private fun rootScanMemcards(
        root: RootAccessHelper,
        memcardsPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(memcardsPath)
        val mcdNames = fileNames.filter { MEMCARD_PATTERN.matches(it) }
        if (mcdNames.isEmpty()) return emptyList()

        return listOf(
            DetectedGame(
                gameId = "DuckStation_memcards",
                gameName = "DuckStation Memory Cards (${mcdNames.joinToString(", ")})",
                savePath = memcardsPath,
                parentPath = memcardsPath,
                emulatorType = Emulator.DUCKSTATION,
                saveCount = mcdNames.size,
                lastModified = root.getLastModified(memcardsPath)
            )
        )
    }

    private fun rootScanStates(
        root: RootAccessHelper,
        statesPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(statesPath)
        val gamesMap = mutableMapOf<String, Int>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val gameId = getStateGameId(name) ?: continue
            gamesMap[gameId] = (gamesMap[gameId] ?: 0) + 1
        }

        return gamesMap.map { (gameId, count) ->
            DetectedGame(
                gameId = gameId,
                gameName = "$gameId (Save States)",
                savePath = statesPath,
                parentPath = statesPath,
                emulatorType = Emulator.DUCKSTATION,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = gameId
            )
        }
    }
}
