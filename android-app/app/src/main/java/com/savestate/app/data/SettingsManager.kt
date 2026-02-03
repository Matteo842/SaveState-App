package com.savestate.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages application settings including backup path.
 * Uses SharedPreferences for persistence.
 */
class SettingsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "savestate_settings"
        private const val KEY_BACKUP_PATH = "backup_path"
        
        // Default backup folder name inside app's private directory
        private const val DEFAULT_BACKUP_FOLDER = "GameSaveBackups"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Gets the default backup path (app's private external files directory).
     * This path doesn't require any special permissions.
     */
    fun getDefaultBackupPath(): String {
        // Use external files directory if available, otherwise internal
        val externalDir = context.getExternalFilesDir(null)
        val baseDir = externalDir ?: context.filesDir
        return File(baseDir, DEFAULT_BACKUP_FOLDER).absolutePath
    }
    
    /**
     * Gets the current backup path.
     * If not set, returns and initializes the default path.
     */
    fun getBackupPath(): String {
        val savedPath = prefs.getString(KEY_BACKUP_PATH, null)
        
        if (savedPath != null) {
            return savedPath
        }
        
        // Initialize with default path
        val defaultPath = getDefaultBackupPath()
        ensureBackupDirectoryExists(defaultPath)
        saveBackupPath(defaultPath)
        return defaultPath
    }
    
    /**
     * Saves a new backup path to preferences.
     */
    fun saveBackupPath(path: String) {
        prefs.edit().putString(KEY_BACKUP_PATH, path).apply()
        Log.d(TAG, "Saved backup path: $path")
    }
    
    /**
     * Ensures the backup directory exists.
     * Creates it if necessary.
     */
    fun ensureBackupDirectoryExists(path: String = getBackupPath()): Boolean {
        val dir = File(path)
        return if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.d(TAG, "Created backup directory: $path, success: $created")
            created
        } else {
            true
        }
    }
    
    /**
     * Changes the backup path and migrates all existing files.
     * @param newPath The new backup path
     * @param onProgress Callback for progress updates (copied, total)
     * @return Result with number of files migrated or error
     */
    suspend fun changeBackupPath(
        newPath: String,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val oldPath = getBackupPath()
            
            // Same path, nothing to do
            if (oldPath == newPath) {
                return@withContext Result.success(0)
            }
            
            val oldDir = File(oldPath)
            val newDir = File(newPath)
            
            // Create new directory if needed
            if (!newDir.exists()) {
                if (!newDir.mkdirs()) {
                    return@withContext Result.failure(
                        Exception("Cannot create directory: $newPath")
                    )
                }
            }
            
            // Check if new directory is writable
            if (!newDir.canWrite()) {
                return@withContext Result.failure(
                    Exception("Cannot write to directory: $newPath")
                )
            }
            
            // Get all files to migrate
            val filesToMigrate = if (oldDir.exists()) {
                oldDir.walkTopDown().filter { it.isFile }.toList()
            } else {
                emptyList()
            }
            
            val totalFiles = filesToMigrate.size
            var copiedFiles = 0
            
            Log.d(TAG, "Migrating $totalFiles files from $oldPath to $newPath")
            
            // Copy each file maintaining directory structure
            for (file in filesToMigrate) {
                val relativePath = file.relativeTo(oldDir).path
                val destFile = File(newDir, relativePath)
                
                // Create parent directories if needed
                destFile.parentFile?.mkdirs()
                
                // Copy file
                file.copyTo(destFile, overwrite = true)
                copiedFiles++
                
                onProgress?.invoke(copiedFiles, totalFiles)
            }
            
            // Delete old files after successful copy
            if (copiedFiles == totalFiles && oldDir.exists()) {
                oldDir.deleteRecursively()
                Log.d(TAG, "Deleted old backup directory: $oldPath")
            }
            
            // Save new path
            saveBackupPath(newPath)
            
            Log.d(TAG, "Migration complete. Moved $copiedFiles files.")
            Result.success(copiedFiles)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating backup path: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets info about the current backup directory.
     */
    fun getBackupDirectoryInfo(): BackupDirectoryInfo {
        val path = getBackupPath()
        val dir = File(path)
        
        if (!dir.exists()) {
            return BackupDirectoryInfo(
                path = path,
                exists = false,
                fileCount = 0,
                totalSizeBytes = 0
            )
        }
        
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val totalSize = files.sumOf { it.length() }
        
        return BackupDirectoryInfo(
            path = path,
            exists = true,
            fileCount = files.size,
            totalSizeBytes = totalSize
        )
    }
    
    /**
     * Resets to default backup path and migrates files.
     */
    suspend fun resetToDefaultPath(
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<Int> {
        val defaultPath = getDefaultBackupPath()
        return changeBackupPath(defaultPath, onProgress)
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
    /**
     * Formatted size string (e.g., "1.5 MB")
     */
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
