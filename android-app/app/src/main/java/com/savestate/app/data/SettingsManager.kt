package com.savestate.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Manages application settings.
 * Settings are stored in the external folder (via SAF) when configured.
 */
class SettingsManager(
    private val context: Context,
    private val configManager: ConfigManager
) {
    
    companion object {
        private const val TAG = "SettingsManager"
        private const val SETTINGS_FILE = "settings.json"
        private const val KEY_MAX_BACKUPS = "maxBackups"
        private const val DEFAULT_MAX_BACKUPS = 5
        private const val KEY_MAX_SOURCE_SIZE_MB = "maxSourceSizeMB"
        private const val DEFAULT_MAX_SOURCE_SIZE_MB = 500
        private const val KEY_COMPRESSION_LEVEL = "compressionLevel"
        private const val DEFAULT_COMPRESSION_LEVEL = 6
        private const val KEY_ROOT_MODE = "rootModeEnabled"
    }
    
    // Fallback internal file
    private val internalSettingsFile: File
        get() = File(context.filesDir, SETTINGS_FILE)
    
    /**
     * Gets the backup base folder URI from ConfigManager.
     */
    fun getBackupUri(): Uri? {
        return configManager.getBaseUri()
    }
    
    /**
     * Gets the display path for UI.
     */
    fun getBackupPath(): String {
        return configManager.getBasePath() ?: "Not configured"
    }
    
    /**
     * Checks if backup folder is configured.
     */
    fun isBackupConfigured(): Boolean {
        return configManager.isConfigured() && configManager.hasValidPermission()
    }
    
    /**
     * Gets the DocumentFile for the backup folder.
     */
    fun getBackupDocumentFile(): DocumentFile? {
        return configManager.getBaseDocumentFile()
    }
    
    /**
     * Gets max backup count setting.
     */
    fun getMaxBackups(): Int {
        return try {
            val json = readSettingsJson() ?: return DEFAULT_MAX_BACKUPS
            val obj = JSONObject(json)
            obj.optInt(KEY_MAX_BACKUPS, DEFAULT_MAX_BACKUPS)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading max backups: ${e.message}", e)
            DEFAULT_MAX_BACKUPS
        }
    }
    
    /**
     * Sets max backup count.
     */
    fun setMaxBackups(count: Int) {
        try {
            val json = readSettingsJson() ?: "{}"
            val obj = JSONObject(json)
            obj.put(KEY_MAX_BACKUPS, count)
            writeSettingsJson(obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving max backups: ${e.message}", e)
        }
    }
    
    /**
     * Gets maximum source size for backup in MB (0 = unlimited).
     */
    fun getMaxSourceSizeMB(): Int {
        return try {
            val json = readSettingsJson() ?: return DEFAULT_MAX_SOURCE_SIZE_MB
            val obj = JSONObject(json)
            obj.optInt(KEY_MAX_SOURCE_SIZE_MB, DEFAULT_MAX_SOURCE_SIZE_MB)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading max source size: ${e.message}", e)
            DEFAULT_MAX_SOURCE_SIZE_MB
        }
    }
    
    /**
     * Sets maximum source size for backup in MB (0 = unlimited).
     */
    fun setMaxSourceSizeMB(sizeMB: Int) {
        try {
            val json = readSettingsJson() ?: "{}"
            val obj = JSONObject(json)
            obj.put(KEY_MAX_SOURCE_SIZE_MB, sizeMB)
            writeSettingsJson(obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving max source size: ${e.message}", e)
        }
    }
    
    /**
     * Gets compression level: 0 = None, 6 = Standard, 9 = Maximum.
     */
    fun getCompressionLevel(): Int {
        return try {
            val json = readSettingsJson() ?: return DEFAULT_COMPRESSION_LEVEL
            val obj = JSONObject(json)
            obj.optInt(KEY_COMPRESSION_LEVEL, DEFAULT_COMPRESSION_LEVEL)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading compression level: ${e.message}", e)
            DEFAULT_COMPRESSION_LEVEL
        }
    }
    
    /**
     * Sets compression level: 0 = None, 6 = Standard, 9 = Maximum.
     */
    fun setCompressionLevel(level: Int) {
        try {
            val json = readSettingsJson() ?: "{}"
            val obj = JSONObject(json)
            obj.put(KEY_COMPRESSION_LEVEL, level)
            writeSettingsJson(obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving compression level: ${e.message}", e)
        }
    }
    
    /**
     * Gets root mode enabled state.
     */
    fun isRootModeEnabled(): Boolean {
        return try {
            val json = readSettingsJson() ?: return false
            val obj = JSONObject(json)
            obj.optBoolean(KEY_ROOT_MODE, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading root mode: ${e.message}", e)
            false
        }
    }
    
    /**
     * Sets root mode enabled state.
     */
    fun setRootModeEnabled(enabled: Boolean) {
        try {
            val json = readSettingsJson() ?: "{}"
            val obj = JSONObject(json)
            obj.put(KEY_ROOT_MODE, enabled)
            writeSettingsJson(obj.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving root mode: ${e.message}", e)
        }
    }
    
    private fun readSettingsJson(): String? {
        val baseDoc = configManager.getBaseDocumentFile()
        
        if (baseDoc != null && baseDoc.exists()) {
            val settingsDoc = baseDoc.findFile(SETTINGS_FILE)
            if (settingsDoc != null && settingsDoc.exists()) {
                return try {
                    context.contentResolver.openInputStream(settingsDoc.uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading from external: ${e.message}", e)
                    null
                }
            }
        }
        
        // Fallback to internal
        if (internalSettingsFile.exists()) {
            return internalSettingsFile.readText()
        }
        
        return null
    }
    
    private fun writeSettingsJson(json: String): Boolean {
        val baseDoc = configManager.getBaseDocumentFile()
        
        if (baseDoc != null && baseDoc.exists() && baseDoc.canWrite()) {
            return try {
                var settingsDoc = baseDoc.findFile(SETTINGS_FILE)
                if (settingsDoc == null) {
                    settingsDoc = baseDoc.createFile("application/json", SETTINGS_FILE)
                }
                
                if (settingsDoc != null) {
                    context.contentResolver.openOutputStream(settingsDoc.uri, "wt")?.use { output ->
                        output.write(json.toByteArray())
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to external: ${e.message}", e)
                false
            }
        } else {
            return try {
                internalSettingsFile.writeText(json)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to internal: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Migrate all data to new external folder.
     * Called when user selects a new backup folder.
     */
    suspend fun migrateToNewFolder(
        newUri: Uri,
        displayPath: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting migration to: $displayPath")
            
            val newDoc = DocumentFile.fromTreeUri(context, newUri)
            if (newDoc == null || !newDoc.exists() || !newDoc.canWrite()) {
                return@withContext Result.failure(
                    Exception("Cannot access the selected folder")
                )
            }
            
            // Get old data
            val oldBaseDoc = configManager.getBaseDocumentFile()
            var filesMigrated = 0
            var totalFiles = 0
            
            // Count files to migrate
            if (oldBaseDoc != null && oldBaseDoc.exists()) {
                totalFiles = oldBaseDoc.listFiles().count { it.isFile }
            }
            // Also count internal files
            val internalFiles = context.filesDir.listFiles()?.filter { 
                it.name.endsWith(".json") && it.name != "config.json" 
            } ?: emptyList()
            totalFiles += internalFiles.size
            
            Log.d(TAG, "Total files to migrate: $totalFiles")
            
            // Migrate from old external folder
            if (oldBaseDoc != null && oldBaseDoc.exists()) {
                for (file in oldBaseDoc.listFiles()) {
                    if (file.isFile) {
                        try {
                            // Read content
                            val content = context.contentResolver.openInputStream(file.uri)?.use { 
                                it.readBytes() 
                            }
                            
                            if (content != null) {
                                // Create in new location
                                val newFile = newDoc.createFile(
                                    file.type ?: "application/octet-stream",
                                    file.name ?: "unknown"
                                )
                                
                                if (newFile != null) {
                                    context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                        output.write(content)
                                    }
                                    filesMigrated++
                                    onProgress?.invoke(filesMigrated, totalFiles)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error migrating file ${file.name}: ${e.message}")
                        }
                    } else if (file.isDirectory) {
                        // Migrate directory (for backup folders)
                        migrateDirectory(file, newDoc, onProgress) { filesMigrated++; onProgress?.invoke(filesMigrated, totalFiles) }
                    }
                }
            }
            
            // Migrate internal files (if any)
            for (file in internalFiles) {
                try {
                    val content = file.readBytes()
                    val newFile = newDoc.createFile("application/json", file.name)
                    
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            output.write(content)
                        }
                        file.delete() // Remove from internal after migration
                        filesMigrated++
                        onProgress?.invoke(filesMigrated, totalFiles)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error migrating internal file ${file.name}: ${e.message}")
                }
            }
            
            // Save new config
            configManager.takePersistentPermission(newUri)
            configManager.saveBaseUri(newUri, displayPath)
            
            Log.d(TAG, "Migration complete. Moved $filesMigrated files.")
            Result.success(filesMigrated)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun migrateDirectory(
        sourceDir: DocumentFile,
        destParent: DocumentFile,
        onProgress: ((Int, Int) -> Unit)?,
        onFileMigrated: () -> Unit
    ) {
        try {
            // Create directory in destination
            val newDir = destParent.createDirectory(sourceDir.name ?: return) ?: return
            
            for (file in sourceDir.listFiles()) {
                if (file.isFile) {
                    val content = context.contentResolver.openInputStream(file.uri)?.use { 
                        it.readBytes() 
                    }
                    
                    if (content != null) {
                        val newFile = newDir.createFile(
                            file.type ?: "application/octet-stream",
                            file.name ?: "unknown"
                        )
                        
                        if (newFile != null) {
                            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                output.write(content)
                            }
                            onFileMigrated()
                        }
                    }
                } else if (file.isDirectory) {
                    migrateDirectory(file, newDir, onProgress, onFileMigrated)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating directory ${sourceDir.name}: ${e.message}")
        }
    }
    
    /**
     * Gets info about the backup directory.
     */
    fun getBackupDirectoryInfo(): BackupDirectoryInfo {
        val baseDoc = configManager.getBaseDocumentFile()
        val path = configManager.getBasePath() ?: "Not configured"
        
        if (baseDoc == null || !baseDoc.exists()) {
            return BackupDirectoryInfo(
                path = path,
                exists = false,
                fileCount = 0,
                totalSizeBytes = 0
            )
        }
        
        var fileCount = 0
        var totalSize = 0L
        
        fun countFiles(doc: DocumentFile) {
            for (file in doc.listFiles()) {
                if (file.isFile) {
                    fileCount++
                    totalSize += file.length()
                } else if (file.isDirectory) {
                    countFiles(file)
                }
            }
        }
        
        countFiles(baseDoc)
        
        return BackupDirectoryInfo(
            path = path,
            exists = true,
            fileCount = fileCount,
            totalSizeBytes = totalSize
        )
    }
}

/**
 * Information about the backup directory.
 */
data class BackupDirectoryInfo(
    val path: String,
    val exists: Boolean,
    val fileCount: Int,
    val totalSizeBytes: Long
) {
    val formattedSize: String
        get() {
            val kb = totalSizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.2f KB", kb)
                else -> "$totalSizeBytes B"
            }
        }
}
