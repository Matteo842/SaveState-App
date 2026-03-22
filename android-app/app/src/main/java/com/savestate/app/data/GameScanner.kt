package com.savestate.app.data

import android.content.Context
import android.util.Log
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorConfig
import com.savestate.app.data.model.EmulatorInfo
import org.json.JSONObject
import java.io.File

/**
 * Scans emulator directories to find saves/games.
 * PPSSPP logic lives here; RetroArch logic is in RetroArchManager.
 */
class GameScanner {
    
    companion object {
        private const val TAG = "GameScanner"
        
        private val PSP_SAVE_SUFFIXES = listOf("DATA00", "PROFILE00")
        
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
    
    /**
     * Scan for PPSSPP games in all known save locations
     */
    fun scanPPSSPPGames(emulatorInfo: EmulatorInfo): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, DetectedGame>()
        
        for (savePath in EmulatorConfig.ppssppSavePaths) {
            val saveDir = File(savePath)
            Log.d(TAG, "Checking PPSSPP path: $savePath")
            Log.d(TAG, "  exists: ${saveDir.exists()}, isDirectory: ${saveDir.isDirectory}")
            
            if (saveDir.exists() && saveDir.isDirectory) {
                Log.d(TAG, "  Scanning directory...")
                val foundGames = scanPPSSPPDirectory(saveDir)
                
                for (game in foundGames) {
                    val existingGame = gamesMap[game.gameId]
                    if (existingGame == null) {
                        gamesMap[game.gameId] = game
                    } else {
                        if (game.saveCount > existingGame.saveCount || 
                            (existingGame.gameName == existingGame.gameId && game.gameName != game.gameId)) {
                            gamesMap[game.gameId] = game
                        }
                    }
                }
                
                Log.d(TAG, "Found ${foundGames.size} save folders in $savePath")
            } else if (!saveDir.exists()) {
                Log.d(TAG, "  Path does not exist")
            }
        }
        
        val result = gamesMap.values.toList()
        Log.d(TAG, "Total unique games found: ${result.size}")
        return result
    }
    
    private fun scanPPSSPPDirectory(saveDataDir: File): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<File>>()
        
        try {
            val allItems = saveDataDir.listFiles() ?: return emptyList()
            Log.d(TAG, "  Found ${allItems.size} items in directory")
            
            for (item in allItems) {
                if (!item.isDirectory || item.name.startsWith(".")) {
                    continue
                }
                
                val folderName = item.name
                Log.d(TAG, "    Checking folder: $folderName")
                
                var baseGameId: String? = null
                for (suffix in PSP_SAVE_SUFFIXES) {
                    if (folderName.endsWith(suffix)) {
                        baseGameId = folderName.dropLast(suffix.length)
                        Log.d(TAG, "      Matched suffix '$suffix', base ID: $baseGameId")
                        break
                    }
                }
                
                if (baseGameId == null) {
                    if (folderName.matches(Regex("^[A-Z]{4}\\d{5}.*$"))) {
                        baseGameId = folderName.take(9)
                        Log.d(TAG, "      Looks like PSP ID, using: $baseGameId")
                    } else {
                        Log.d(TAG, "      Skipping - doesn't match known patterns")
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
                        Log.d(TAG, "      Parsed PARAM.SFO: $gameName")
                        primaryPath = folder.absolutePath
                    }
                    
                    val saveCount = folder.listFiles()?.count { it.isFile } ?: 0
                    totalSaves += saveCount
                    
                    if (folder.lastModified() > latestModified) {
                        latestModified = folder.lastModified()
                    }
                }
                
                if (gameName == null) {
                    gameName = getGameNameFromDatabase(baseId) ?: baseId
                    Log.d(TAG, "      Using fallback name: $gameName")
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
                
                Log.d(TAG, "    Created game: $gameName (ID: $baseId)")
            }
            
            return games
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning PPSSPP directory: ${e.message}", e)
            return emptyList()
        }
    }
    
    fun hasPPSSPPSaves(): Boolean {
        for (savePath in EmulatorConfig.ppssppSavePaths) {
            val saveDir = File(savePath)
            if (saveDir.exists() && saveDir.isDirectory) {
                val files = saveDir.listFiles()
                if (!files.isNullOrEmpty()) {
                    return true
                }
            }
        }
        return false
    }
    
    fun getValidPPSSPPSavePath(): String? {
        for (savePath in EmulatorConfig.ppssppSavePaths) {
            val saveDir = File(savePath)
            if (saveDir.exists() && saveDir.isDirectory) {
                return savePath
            }
        }
        return null
    }
}
