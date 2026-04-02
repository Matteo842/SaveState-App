package com.savestate.app.data

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.GameProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Repository for persisting game profiles.
 * Saves to the external folder specified by ConfigManager (via SAF).
 * Falls back to internal storage if external not configured.
 */
class ProfileRepository(
    private val context: Context,
    private val configManager: ConfigManager
) {
    
    companion object {
        private const val TAG = "ProfileRepository"
        private const val PROFILES_FILE = "profiles.json"
    }
    
    // Fallback internal file (used only if external not configured)
    private val internalProfilesFile: File
        get() = File(context.filesDir, PROFILES_FILE)
    
    /**
     * Load all profiles from storage.
     * Tries external folder first, falls back to internal.
     */
    fun loadProfiles(): List<GameProfile> {
        return try {
            val json = readProfilesJson()
            if (json == null) {
                Log.d(TAG, "No profiles file found, returning empty list")
                return emptyList()
            }
            
            val jsonArray = JSONArray(json)
            val profiles = mutableListOf<GameProfile>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                profiles.add(parseProfile(obj))
            }
            
            Log.d(TAG, "Loaded ${profiles.size} profiles")
            profiles
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profiles: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Save all profiles to storage.
     * Saves to external folder if configured, otherwise internal.
     */
    fun saveProfiles(profiles: List<GameProfile>): Boolean {
        return try {
            val jsonArray = JSONArray()
            
            for (profile in profiles) {
                jsonArray.put(profileToJson(profile))
            }
            
            val json = jsonArray.toString(2)
            val success = writeProfilesJson(json)
            
            if (success) {
                Log.d(TAG, "Saved ${profiles.size} profiles")
            } else {
                Log.e(TAG, "Failed to save profiles")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profiles: ${e.message}", e)
            false
        }
    }
    
    /**
     * Read profiles JSON from the appropriate location.
     */
    private fun readProfilesJson(): String? {
        val baseDoc = configManager.getBaseDocumentFile()
        
        if (baseDoc != null && baseDoc.exists()) {
            // Try to read from external folder
            val profilesDoc = baseDoc.findFile(PROFILES_FILE)
            if (profilesDoc != null && profilesDoc.exists()) {
                return try {
                    context.contentResolver.openInputStream(profilesDoc.uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading from external: ${e.message}", e)
                    null
                }
            }
        }
        
        // Fallback to internal
        if (internalProfilesFile.exists()) {
            return internalProfilesFile.readText()
        }
        
        return null
    }
    
    /**
     * Write profiles JSON to the appropriate location.
     */
    private fun writeProfilesJson(json: String): Boolean {
        val baseDoc = configManager.getBaseDocumentFile()
        
        if (baseDoc != null && baseDoc.exists() && baseDoc.canWrite()) {
            // Write to external folder
            return try {
                // Find or create the file
                var profilesDoc = baseDoc.findFile(PROFILES_FILE)
                if (profilesDoc == null) {
                    profilesDoc = baseDoc.createFile("application/json", PROFILES_FILE)
                }
                
                if (profilesDoc != null) {
                    context.contentResolver.openOutputStream(profilesDoc.uri, "wt")?.use { output ->
                        output.write(json.toByteArray())
                    }
                    Log.d(TAG, "Wrote profiles to external folder")
                    true
                } else {
                    Log.e(TAG, "Could not create profiles file in external folder")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to external: ${e.message}", e)
                false
            }
        } else {
            // Write to internal
            return try {
                internalProfilesFile.writeText(json)
                Log.d(TAG, "Wrote profiles to internal storage (external not configured)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to internal: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Migrate profiles from internal to external storage.
     */
    fun migrateToExternal(): Boolean {
        val baseDoc = configManager.getBaseDocumentFile() ?: return false
        
        // Check if internal file exists
        if (!internalProfilesFile.exists()) {
            Log.d(TAG, "No internal profiles to migrate")
            return true
        }
        
        return try {
            val json = internalProfilesFile.readText()
            
            // Create file in external
            var profilesDoc = baseDoc.findFile(PROFILES_FILE)
            if (profilesDoc == null) {
                profilesDoc = baseDoc.createFile("application/json", PROFILES_FILE)
            }
            
            if (profilesDoc != null) {
                context.contentResolver.openOutputStream(profilesDoc.uri, "wt")?.use { output ->
                    output.write(json.toByteArray())
                }
                
                // Delete internal file after successful migration
                internalProfilesFile.delete()
                Log.d(TAG, "Migrated profiles to external storage")
                true
            } else {
                Log.e(TAG, "Could not create profiles file for migration")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating profiles: ${e.message}", e)
            false
        }
    }
    
    private fun parseProfile(obj: JSONObject): GameProfile {
        return GameProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            emulator = obj.getString("emulator"),
            savePath = obj.getString("savePath"),
            parentPath = obj.optString("parentPath", null),
            backupCount = obj.optInt("backupCount", 0),
            lastBackup = obj.optString("lastBackup", null),
            isFavorite = obj.optBoolean("isFavorite", false),
            iconResId = if (obj.has("iconResId")) obj.getInt("iconResId") else null,
            gameFilePrefix = obj.optString("gameFilePrefix", null),
            requiresRoot = obj.optBoolean("requiresRoot", false)
        )
    }
    
    private fun profileToJson(profile: GameProfile): JSONObject {
        return JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("emulator", profile.emulator)
            put("savePath", profile.savePath)
            put("parentPath", profile.parentPath)
            put("backupCount", profile.backupCount)
            put("lastBackup", profile.lastBackup)
            put("isFavorite", profile.isFavorite)
            profile.iconResId?.let { put("iconResId", it) }
            profile.gameFilePrefix?.let { put("gameFilePrefix", it) }
            if (profile.requiresRoot) put("requiresRoot", true)
        }
    }
}
