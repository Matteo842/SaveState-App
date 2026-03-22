package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorInfo
import java.io.File

/**
 * Handles all RetroArch-specific save detection and scanning logic.
 *
 * RetroArch stores saves as flat files named after the ROM:
 *   saves/  -> Game Name.srm, Game Name.sav, Game Name.rtc, ...
 *   states/ -> Game Name.state, Game Name.state1, Game Name.state.auto, ...
 *
 * Files may also be organized into core-specific subdirectories (e.g., saves/snes9x/).
 */
class RetroArchManager {

    companion object {
        private const val TAG = "RetroArchManager"

        // Known save paths on Android (checked in priority order)
        val savePaths = listOf(
            "/storage/emulated/0/RetroArch/saves",
            "/storage/emulated/0/Android/data/com.retroarch/files/saves",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/saves",
            "/storage/emulated/0/Android/data/com.retroarch.ra32/files/saves"
        )

        val statePaths = listOf(
            "/storage/emulated/0/RetroArch/states",
            "/storage/emulated/0/Android/data/com.retroarch/files/states",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/states",
            "/storage/emulated/0/Android/data/com.retroarch.ra32/files/states"
        )

        // Extensions that are NOT save data (skip these when scanning the saves folder)
        private val IGNORED_EXTENSIONS = setOf(
            "cfg", "opt", "lpl", "png", "jpg", "jpeg", "bmp",
            "log", "txt", "xml", "json", "ini", "bak"
        )

        // State file pattern: .state, .state1, .state2, ..., .state.auto, .state.png (screenshot)
        private val STATE_FILE_PATTERN = Regex("^(.+)\\.state(\\d+|\\.auto|\\.png)?$")

        /**
         * Extract the base ROM name from a file in the saves/ directory.
         * Accepts any file with an extension, excluding known non-save types.
         * Returns null if the file should be skipped.
         */
        fun getSaveBaseName(fileName: String): String? {
            val dotIndex = fileName.lastIndexOf('.')
            if (dotIndex <= 0) return null

            val ext = fileName.substring(dotIndex + 1).lowercase()
            if (ext in IGNORED_EXTENSIONS) return null

            return fileName.substring(0, dotIndex)
        }

        /**
         * Extract the base ROM name from a file in the states/ directory.
         * Matches .state, .state1, .state2, ..., .state.auto, .state.png
         */
        fun getStateBaseName(fileName: String): String? {
            val match = STATE_FILE_PATTERN.matchEntire(fileName) ?: return null
            return match.groupValues[1]
        }

        /**
         * Determine if a folder name looks like RetroArch saves or states.
         * Returns true for saves, false for states, null for unknown.
         */
        fun isSavesOrStatesDir(folderName: String?): Boolean? {
            return when (folderName?.lowercase()) {
                "saves" -> true
                "states" -> false
                else -> null
            }
        }

        /**
         * Extract the base name from a file in either saves/ or states/.
         */
        fun getBaseName(fileName: String, isSaveDir: Boolean): String? {
            return if (isSaveDir) getSaveBaseName(fileName) else getStateBaseName(fileName)
        }
    }

    /**
     * Scan a folder selected via SAF for RetroArch game saves.
     * The user should select either "saves" or "states".
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name
        val isSaveDir = isSavesOrStatesDir(folderName)

        if (isSaveDir == null) {
            Log.w(TAG, "Folder '$folderName' is not 'saves' or 'states' — scanning as saves")
        }
        val scanAsSaves = isSaveDir ?: true

        val children = documentFile.listFiles()
        Log.d(TAG, "Scanning SAF folder '$folderName': ${children.size} items")

        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in children) {
            val childName = child.name ?: continue

            if (child.isDirectory) {
                if (childName.startsWith(".")) continue
                Log.d(TAG, "  Entering subdirectory: $childName")
                val subChildren = child.listFiles()
                for (subChild in subChildren) {
                    processFileItem(subChild, scanAsSaves, gamesMap)
                }
                continue
            }

            processFileItem(child, scanAsSaves, gamesMap)
        }

        val folderUri = documentFile.uri.toString()

        val games = gamesMap.map { (baseName, files) ->
            val latestModified = files.maxOfOrNull { it.lastModified() } ?: 0L
            DetectedGame(
                gameId = baseName,
                gameName = baseName,
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.RETROARCH,
                saveCount = files.size,
                lastModified = latestModified
            )
        }

        Log.d(TAG, "SAF scan complete: ${games.size} games found from ${gamesMap.values.sumOf { it.size }} files")
        return games
    }

    /**
     * Scan for RetroArch games using direct file system access.
     */
    fun scanGames(emulatorInfo: EmulatorInfo): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, DetectedGame>()

        for (path in savePaths) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue
            Log.d(TAG, "Scanning saves path: $path")

            val found = scanDirectory(dir, isSaveDir = true)
            for (game in found) {
                val existing = gamesMap[game.gameId]
                if (existing == null || game.saveCount > existing.saveCount) {
                    gamesMap[game.gameId] = game
                }
            }
        }

        for (path in statePaths) {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) continue
            Log.d(TAG, "Scanning states path: $path")

            val found = scanDirectory(dir, isSaveDir = false)
            for (game in found) {
                val existing = gamesMap[game.gameId]
                if (existing == null) {
                    gamesMap[game.gameId] = game
                } else {
                    gamesMap[game.gameId] = existing.copy(
                        saveCount = existing.saveCount + game.saveCount,
                        lastModified = maxOf(existing.lastModified, game.lastModified)
                    )
                }
            }
        }

        Log.d(TAG, "Direct scan complete: ${gamesMap.size} unique games")
        return gamesMap.values.toList()
    }

    private fun scanDirectory(directory: File, isSaveDir: Boolean): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<File>>()

        try {
            val items = directory.listFiles() ?: return emptyList()

            for (item in items) {
                if (item.isDirectory && !item.name.startsWith(".")) {
                    val subResults = scanDirectory(item, isSaveDir)
                    for (game in subResults) {
                        gamesMap.getOrPut(game.gameId) { mutableListOf() }
                    }
                    continue
                }

                if (!item.isFile || item.name.startsWith(".")) continue

                val baseName = getBaseName(item.name, isSaveDir) ?: continue
                gamesMap.getOrPut(baseName) { mutableListOf() }.add(item)
            }

            return gamesMap.map { (baseName, files) ->
                DetectedGame(
                    gameId = baseName,
                    gameName = baseName,
                    savePath = directory.absolutePath,
                    parentPath = directory.absolutePath,
                    emulatorType = Emulator.RETROARCH,
                    saveCount = files.size,
                    lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory ${directory.absolutePath}: ${e.message}", e)
            return emptyList()
        }
    }

    private fun processFileItem(
        item: DocumentFile,
        isSaveDir: Boolean,
        gamesMap: MutableMap<String, MutableList<DocumentFile>>
    ) {
        if (!item.isFile) return
        val name = item.name ?: return
        if (name.startsWith(".")) return

        val baseName = getBaseName(name, isSaveDir)
        if (baseName == null) {
            Log.d(TAG, "  Skipped: $name (no matching pattern)")
            return
        }

        Log.d(TAG, "  Matched: $name -> game '$baseName'")
        gamesMap.getOrPut(baseName) { mutableListOf() }.add(item)
    }
}
