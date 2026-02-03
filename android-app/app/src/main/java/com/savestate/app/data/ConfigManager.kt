package com.savestate.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File

/**
 * Manages the base configuration - specifically the external storage path.
 * This is the ONLY file stored in the app's internal directory.
 * Everything else (profiles, settings, backups) goes in the external folder.
 */
class ConfigManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ConfigManager"
        private const val CONFIG_FILE = "config.json"
        private const val KEY_BASE_URI = "baseUri"
        private const val KEY_BASE_PATH = "basePath" // Display path for UI
    }
    
    private val configFile: File
        get() = File(context.filesDir, CONFIG_FILE)
    
    /**
     * Gets the SAF URI for the external storage folder.
     * Returns null if not configured (first run).
     */
    fun getBaseUri(): Uri? {
        return try {
            if (!configFile.exists()) {
                Log.d(TAG, "No config file, base URI not set")
                return null
            }
            
            val json = configFile.readText()
            val obj = JSONObject(json)
            val uriString = obj.optString(KEY_BASE_URI, null)
            
            if (uriString != null) {
                Uri.parse(uriString)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading config: ${e.message}", e)
            null
        }
    }
    
    /**
     * Gets the display path for UI (human readable).
     */
    fun getBasePath(): String? {
        return try {
            if (!configFile.exists()) return null
            
            val json = configFile.readText()
            val obj = JSONObject(json)
            obj.optString(KEY_BASE_PATH, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading base path: ${e.message}", e)
            null
        }
    }
    
    /**
     * Saves the base URI (after user selects folder via SAF).
     */
    fun saveBaseUri(uri: Uri, displayPath: String) {
        try {
            val obj = JSONObject().apply {
                put(KEY_BASE_URI, uri.toString())
                put(KEY_BASE_PATH, displayPath)
            }
            configFile.writeText(obj.toString(2))
            Log.d(TAG, "Saved base URI: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${e.message}", e)
        }
    }
    
    /**
     * Checks if a base folder has been configured.
     */
    fun isConfigured(): Boolean {
        return getBaseUri() != null
    }
    
    /**
     * Takes persistent permission for the URI.
     */
    fun takePersistentPermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.d(TAG, "Took persistent permission for: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistent permission: ${e.message}", e)
        }
    }
    
    /**
     * Checks if we have valid permission for the stored URI.
     */
    fun hasValidPermission(): Boolean {
        val uri = getBaseUri() ?: return false
        
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile != null && docFile.exists() && docFile.canWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission: ${e.message}", e)
            false
        }
    }
    
    /**
     * Gets or creates a DocumentFile for writing files in the base folder.
     */
    fun getBaseDocumentFile(): DocumentFile? {
        val uri = getBaseUri() ?: return null
        return try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting DocumentFile: ${e.message}", e)
            null
        }
    }
    
    /**
     * Clears the configuration (for reset).
     */
    fun clearConfig() {
        try {
            if (configFile.exists()) {
                configFile.delete()
            }
            Log.d(TAG, "Config cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing config: ${e.message}", e)
        }
    }
}
