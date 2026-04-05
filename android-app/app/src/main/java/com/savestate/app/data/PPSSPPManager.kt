package com.savestate.app.data

import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorInfo
import org.json.JSONObject
import java.io.File

/**
 * Handles PPSSPP (PSP) save detection, scanning, and game database lookup.
 *
 * PPSSPP stores saves in PSP/SAVEDATA/<GameID><Suffix>/ folders where each
 * subfolder contains PARAM.SFO (metadata) plus the actual save files.
 * Common suffixes: DATA00, PROFILE00, or raw GameID folders matching [A-Z]{4}\d{5}.
 *
 * Save locations (checked in priority order):
 *   /storage/emulated/0/PSP/SAVEDATA                           (user-created, recommended)
 *   /storage/emulated/0/Android/data/org.ppsspp.ppsspp/...      (free version)
 *   /storage/emulated/0/Android/data/org.ppsspp.ppssppgold/...  (gold version)
 */
class PPSSPPManager {

    companion object {
        private const val TAG = "PPSSPPManager"

        private val PSP_SAVE_SUFFIXES = listOf("DATA00", "PROFILE00")

        val savePaths = listOf(
            "/storage/emulated/0/PSP/SAVEDATA",
            "/storage/emulated/0/Android/data/org.ppsspp.ppsspp/files/PSP/SAVEDATA",
            "/storage/emulated/0/Android/data/org.ppsspp.ppssppgold/files/PSP/SAVEDATA"
        )

        val statePaths = listOf(
            "/storage/emulated/0/PSP/PPSSPP_STATE",
            "/storage/emulated/0/Android/data/org.ppsspp.ppsspp/files/PSP/PPSSPP_STATE",
            "/storage/emulated/0/Android/data/org.ppsspp.ppssppgold/files/PSP/PPSSPP_STATE"
        )

        // ── PSP game-title database ─────────────────────────────────────

        private var pspGameDatabase: Map<String, String>? = null

        fun initDatabase(context: Context) {
            if (pspGameDatabase != null) return

            try {
                val jsonString = context.assets.open("psp_game_database.json")
                    .bufferedReader()
                    .use { it.readText() }

                val jsonObject = JSONObject(jsonString)
                val database = mutableMapOf<String, String>()

                jsonObject.keys().forEach { key ->
                    database[key] = jsonObject.getString(key)
                }

                pspGameDatabase = database
                Log.d(TAG, "Loaded PSP game database with ${database.size} entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load PSP game database: ${e.message}")
                pspGameDatabase = emptyMap()
            }
        }

        fun getGameNameFromDatabase(gameId: String): String? {
            return pspGameDatabase?.get(gameId)
        }

        fun isDatabaseLoaded(): Boolean = pspGameDatabase != null
    }

    // ══ Direct file-system scanning ═════════════════════════════════════

    /**
     * Scan all known PPSSPP save locations for games.
     */
    fun scanGames(emulatorInfo: EmulatorInfo): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, DetectedGame>()

        for (savePath in savePaths) {
            val saveDir = File(savePath)
            Log.d(TAG, "Checking path: $savePath (exists=${saveDir.exists()})")

            if (saveDir.exists() && saveDir.isDirectory) {
                val foundGames = scanDirectory(saveDir)

                for (game in foundGames) {
                    val existing = gamesMap[game.gameId]
                    if (existing == null) {
                        gamesMap[game.gameId] = game
                    } else if (game.saveCount > existing.saveCount ||
                        (existing.gameName == existing.gameId && game.gameName != game.gameId)
                    ) {
                        gamesMap[game.gameId] = game
                    }
                }

                Log.d(TAG, "Found ${foundGames.size} save folders in $savePath")
            }
        }

        val result = gamesMap.values.toList()
        Log.d(TAG, "Total unique games found: ${result.size}")
        return result
    }

    private fun scanDirectory(saveDataDir: File): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<File>>()

        try {
            val allItems = saveDataDir.listFiles() ?: return emptyList()

            for (item in allItems) {
                if (!item.isDirectory || item.name.startsWith(".")) continue

                val folderName = item.name
                var baseGameId: String? = null

                for (suffix in PSP_SAVE_SUFFIXES) {
                    if (folderName.endsWith(suffix)) {
                        baseGameId = folderName.dropLast(suffix.length)
                        break
                    }
                }

                if (baseGameId == null) {
                    if (folderName.matches(Regex("^[A-Z]{4}\\d{5}.*$"))) {
                        baseGameId = folderName.take(9)
                    } else {
                        continue
                    }
                }

                gamesMap.getOrPut(baseGameId) { mutableListOf() }.add(item)
            }

            val games = mutableListOf<DetectedGame>()
            for ((baseId, folders) in gamesMap) {
                var gameName: String? = null
                var totalSaves = 0
                var latestModified = 0L
                var primaryPath: String = folders.first().absolutePath

                for (folder in folders) {
                    val sfoFile = File(folder, "PARAM.SFO")
                    if (sfoFile.exists() && gameName == null) {
                        gameName = SfoParser.parseParamSfo(sfoFile.absolutePath)
                        primaryPath = folder.absolutePath
                    }

                    totalSaves += folder.listFiles()?.count { it.isFile } ?: 0

                    if (folder.lastModified() > latestModified) {
                        latestModified = folder.lastModified()
                    }
                }

                if (gameName == null) {
                    gameName = getGameNameFromDatabase(baseId) ?: baseId
                }

                games.add(
                    DetectedGame(
                        gameId = baseId,
                        gameName = gameName,
                        savePath = primaryPath,
                        parentPath = saveDataDir.absolutePath,
                        emulatorType = Emulator.PPSSPP,
                        saveCount = totalSaves,
                        lastModified = latestModified
                    )
                )
            }

            return games
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory: ${e.message}", e)
            return emptyList()
        }
    }

    fun hasSaves(): Boolean {
        for (savePath in savePaths) {
            val saveDir = File(savePath)
            if (saveDir.exists() && saveDir.isDirectory) {
                val files = saveDir.listFiles()
                if (!files.isNullOrEmpty()) return true
            }
        }
        return false
    }

    fun getValidSavePath(): String? {
        for (savePath in savePaths) {
            val saveDir = File(savePath)
            if (saveDir.exists() && saveDir.isDirectory) return savePath
        }
        return null
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Scan a folder selected via SAF for PPSSPP saves (folder-based: each
     * game has its own subfolder containing PARAM.SFO + save files).
     *
     * @param contentResolver needed to open PARAM.SFO via SAF URIs
     * @param emulatorType    defaults to PPSSPP; pass another value when this
     *                        scanner is used as a fallback for other emulators
     */
    fun scanSafFolder(
        documentFile: DocumentFile,
        contentResolver: ContentResolver,
        emulatorType: Emulator = Emulator.PPSSPP
    ): List<DetectedGame> {
        val children = documentFile.listFiles()
        Log.d(TAG, "SAF: found ${children.size} items in folder")

        val gamesMap = mutableMapOf<String, DetectedGame>()

        for (child in children) {
            if (!child.isDirectory) continue

            val folderName = child.name ?: continue
            var baseGameId: String? = null

            if (folderName.endsWith("DATA00")) {
                baseGameId = folderName.dropLast(6)
            } else if (folderName.endsWith("PROFILE00")) {
                baseGameId = folderName.dropLast(9)
            } else if (folderName.matches(Regex("^[A-Z]{4}\\d{5}.*$"))) {
                baseGameId = folderName.take(9)
            }

            if (baseGameId == null) continue
            if (gamesMap.containsKey(baseGameId)) continue

            var gameName: String? = null
            val sfoFile = child.findFile("PARAM.SFO")
            if (sfoFile != null && sfoFile.isFile) {
                try {
                    contentResolver.openInputStream(sfoFile.uri)?.use { inputStream ->
                        gameName = SfoParser.parseFromBytes(inputStream.readBytes())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SFO for $folderName: ${e.message}")
                }
            }

            if (gameName == null) {
                gameName = getGameNameFromDatabase(baseGameId) ?: baseGameId
            }

            val saveCount = try {
                child.listFiles().count { it.isFile }
            } catch (e: Exception) {
                0
            }

            gamesMap[baseGameId] = DetectedGame(
                gameId = baseGameId,
                gameName = gameName ?: baseGameId,
                savePath = child.uri.toString(),
                parentPath = documentFile.uri.toString(),
                emulatorType = emulatorType,
                saveCount = saveCount,
                lastModified = child.lastModified()
            )
        }

        return gamesMap.values.toList()
    }
}
