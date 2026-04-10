package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles NetherSX2 (PlayStation 2) save detection and scanning.
 *
 * NetherSX2 is a community-patched fork of AetherSX2 and shares the same
 * package name (xyz.aethersx2.android) and folder layout:
 *
 *   memcards/   -> Mcd001.ps2, Mcd002.ps2 (shared), or per-game .ps2 files
 *   sstates/    -> GAMEID (Title).NN.p2s save-state files
 *
 * External paths (user-chosen):
 *   /storage/emulated/0/NetherSX2/memcards
 *   /storage/emulated/0/NetherSX2/sstates
 *
 * Root-only path:
 *   /storage/emulated/0/Android/data/xyz.aethersx2.android/files/
 */
class NetherSX2Manager {

    companion object {
        private const val TAG = "NetherSX2Manager"

        private val MEMCARD_PATTERN = Regex("^.*\\.ps2$", RegexOption.IGNORE_CASE)

        // Save-state filename: "GAMEID (Title).NN.p2s" or "GAMEID.NN.p2s"
        private val STATE_PATTERN = Regex("^(.+)\\.(\\d{2})\\.p2s$")

        /**
         * Extract game ID from a NetherSX2 save-state filename.
         * e.g. "SLUS-20062 (Shadow of the Colossus).01.p2s" -> "SLUS-20062 (Shadow of the Colossus)"
         */
        fun getStateGameId(fileName: String): String? =
            STATE_PATTERN.matchEntire(fileName)?.groupValues?.get(1)
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a folder selected via SAF for NetherSX2 saves.
     * Auto-detects whether the folder contains memcards or sstates.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("memcards", true) -> scanMemcardsSaf(documentFile)
            folderName.equals("sstates", true) -> scanStatesSaf(documentFile)
            folderName.equals("files", true) ||
                folderName.contains("nethersx2", true) ||
                folderName.contains("aethersx2", true) ->
                scanRootSaf(documentFile)
            else -> scanAutoDetectSaf(documentFile)
        }
    }

    private fun scanRootSaf(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("memcards")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanMemcardsSaf(it))
        }
        root.findFile("sstates")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }

        if (results.isEmpty()) {
            results.addAll(scanAutoDetectSaf(root))
        }

        return results
    }

    private fun scanMemcardsSaf(memcardsDir: DocumentFile): List<DetectedGame> {
        val ps2Files = memcardsDir.listFiles().filter {
            it.isFile && MEMCARD_PATTERN.matches(it.name ?: "")
        }
        if (ps2Files.isEmpty()) return emptyList()

        val folderUri = memcardsDir.uri.toString()
        val fileNames = ps2Files.mapNotNull { it.name }

        return listOf(
            DetectedGame(
                gameId = "NetherSX2_memcards",
                gameName = "NetherSX2 Memory Cards (${fileNames.joinToString(", ")})",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.NETHERSX2,
                saveCount = ps2Files.size,
                lastModified = ps2Files.maxOfOrNull { it.lastModified() } ?: 0L
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
                emulatorType = Emulator.NETHERSX2,
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
                    gameId = documentFile.name ?: "nethersx2_unknown",
                    gameName = documentFile.name ?: "NetherSX2 Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.NETHERSX2,
                    saveCount = fileCount,
                    lastModified = children.maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return emptyList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan all known root save paths for NetherSX2 saves.
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

            val statesPath = "$basePath/sstates"
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
        val ps2Names = fileNames.filter { MEMCARD_PATTERN.matches(it) }
        if (ps2Names.isEmpty()) return emptyList()

        return listOf(
            DetectedGame(
                gameId = "NetherSX2_memcards",
                gameName = "NetherSX2 Memory Cards (${ps2Names.joinToString(", ")})",
                savePath = memcardsPath,
                parentPath = memcardsPath,
                emulatorType = Emulator.NETHERSX2,
                saveCount = ps2Names.size,
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
                emulatorType = Emulator.NETHERSX2,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = gameId
            )
        }
    }
}
