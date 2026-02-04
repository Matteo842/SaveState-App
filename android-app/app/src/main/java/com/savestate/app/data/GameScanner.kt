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
 * Scans emulator directories to find saves/games
 */
class GameScanner {
    
    companion object {
        private const val TAG = "GameScanner"
        
        // PSP save folder suffixes (from ppsspp_manager.py)
        private val PSP_SAVE_SUFFIXES = listOf("DATA00", "PROFILE00")
        
        // Cached game database (loaded from assets/psp_game_database.json)
        private var pspGameDatabase: Map<String, String>? = null
        
        /**
         * Initialize the PSP game database from assets
         * Should be called once during app startup
         */
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
        
        /**
         * Get game name from the database
         */
        fun getGameNameFromDatabase(gameId: String): String? {
            return pspGameDatabase?.get(gameId)
        }
        
        /**
         * Check if database is loaded
         */
        fun isDatabaseLoaded(): Boolean = pspGameDatabase != null
    }
    
    /**
     * Scan for PPSSPP games in all known save locations
     */
    fun scanPPSSPPGames(emulatorInfo: EmulatorInfo): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, DetectedGame>() // Group by base game ID
        
        // Check all possible PPSSPP save paths
        for (savePath in EmulatorConfig.ppssppSavePaths) {
            val saveDir = File(savePath)
            Log.d(TAG, "Checking PPSSPP path: $savePath")
            Log.d(TAG, "  exists: ${saveDir.exists()}, isDirectory: ${saveDir.isDirectory}")
            
            if (saveDir.exists() && saveDir.isDirectory) {
                Log.d(TAG, "  Scanning directory...")
                val foundGames = scanPPSSPPDirectory(saveDir)
                
                // Merge found games by base ID
                for (game in foundGames) {
                    val existingGame = gamesMap[game.gameId]
                    if (existingGame == null) {
                        gamesMap[game.gameId] = game
                    } else {
                        // Keep the one with more saves or a better name
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
    
    /**
     * Scan a PPSSPP SAVEDATA directory for game saves
     * Looks for folders ending with DATA00 or PROFILE00
     */
    private fun scanPPSSPPDirectory(saveDataDir: File): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<File>>() // Group folders by base ID
        
        try {
            val allItems = saveDataDir.listFiles() ?: return emptyList()
            Log.d(TAG, "  Found ${allItems.size} items in directory")
            
            for (item in allItems) {
                if (!item.isDirectory || item.name.startsWith(".")) {
                    continue
                }
                
                val folderName = item.name
                Log.d(TAG, "    Checking folder: $folderName")
                
                // Extract base game ID by checking for known suffixes
                var baseGameId: String? = null
                for (suffix in PSP_SAVE_SUFFIXES) {
                    if (folderName.endsWith(suffix)) {
                        baseGameId = folderName.dropLast(suffix.length)
                        Log.d(TAG, "      Matched suffix '$suffix', base ID: $baseGameId")
                        break
                    }
                }
                
                if (baseGameId == null) {
                    // If no known suffix, but looks like a PSP game ID format
                    // (4 letters + 5 digits), use the whole folder name
                    if (folderName.matches(Regex("^[A-Z]{4}\\d{5}.*$"))) {
                        baseGameId = folderName.take(9) // Just the ID part
                        Log.d(TAG, "      Looks like PSP ID, using: $baseGameId")
                    } else {
                        Log.d(TAG, "      Skipping - doesn't match known patterns")
                        continue
                    }
                }
                
                // Add to the group
                gamesMap.getOrPut(baseGameId) { mutableListOf() }.add(item)
            }
            
            // Process each group
            val games = mutableListOf<DetectedGame>()
            for ((baseId, folders) in gamesMap) {
                // Try to get game name from PARAM.SFO in any of the folders
                var gameName: String? = null
                var totalSaves = 0
                var latestModified = 0L
                var primaryPath: String = folders.first().absolutePath
                
                for (folder in folders) {
                    // Check for PARAM.SFO
                    val sfoFile = File(folder, "PARAM.SFO")
                    if (sfoFile.exists() && gameName == null) {
                        gameName = SfoParser.parseParamSfo(sfoFile.absolutePath)
                        Log.d(TAG, "      Parsed PARAM.SFO: $gameName")
                        primaryPath = folder.absolutePath
                    }
                    
                    // Count save files in this folder
                    val saveCount = folder.listFiles()?.count { it.isFile } ?: 0
                    totalSaves += saveCount
                    
                    // Track latest modification
                    if (folder.lastModified() > latestModified) {
                        latestModified = folder.lastModified()
                    }
                }
                
                // Fallback to database or ID if no SFO name found
                if (gameName == null) {
                    gameName = getGameNameFromDatabase(baseId) ?: baseId
                    Log.d(TAG, "      Using fallback name: $gameName")
                }
                
                games.add(
                    DetectedGame(
                        gameId = baseId,
                        gameName = gameName,
                        savePath = primaryPath,
                        parentPath = saveDataDir.absolutePath, // Store parent folder for restore
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
    
    /**
     * Check if any PPSSPP save data exists on the device
     */
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
    
    /**
     * Get the first valid PPSSPP save path
     */
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
