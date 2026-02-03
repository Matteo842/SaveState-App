package com.savestate.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Manages backup and restore operations for game save profiles.
 * Handles both local file paths and SAF (Storage Access Framework) URIs.
 */
class BackupManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val MANIFEST_PATH = "savestate/manifest.json"
        private const val BUFFER_SIZE = 8192 // 8KB buffer for file operations
        private const val APP_VERSION = "1.0" // TODO: Get from BuildConfig
    }
    
    /**
     * Result of a backup operation
     */
    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backupPath: String? = null,
        val deletedOldBackups: Int = 0
    )
    
    /**
     * Result of a restore operation
     */
    data class RestoreResult(
        val success: Boolean,
        val message: String
    )
    
    /**
     * Performs a backup for the given profile.
     * 
     * @param profile The game profile to backup
     * @param maxBackups Maximum number of backups to keep (-1 for unlimited)
     * @param onProgress Optional callback for progress updates (bytesWritten, totalBytes)
     * @return BackupResult with success status and details
     */
    suspend fun performBackup(
        profile: GameProfile,
        maxBackups: Int = 3,
        onProgress: ((Long, Long) -> Unit)? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "Starting backup for profile: '${profile.name}'")
        
        try {
            // 1. Validate source path
            val sourceUri = Uri.parse(profile.savePath)
            val sourceDocument = DocumentFile.fromTreeUri(context, sourceUri)
            
            if (sourceDocument == null || !sourceDocument.exists()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Source path does not exist: ${profile.savePath}"
                )
            }
            
            // 2. Create backup directory for this profile
            val backupBaseDir = settingsManager.getBackupPath()
            val sanitizedName = sanitizeFolderName(profile.name)
            val profileBackupDir = File(backupBaseDir, sanitizedName)
            
            if (!profileBackupDir.exists()) {
                if (!profileBackupDir.mkdirs()) {
                    return@withContext BackupResult(
                        success = false,
                        message = "Cannot create backup directory: ${profileBackupDir.absolutePath}"
                    )
                }
            }
            
            // 3. Generate backup filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val archiveName = "Backup_${sanitizedName}_${timestamp}.zip"
            val archivePath = File(profileBackupDir, archiveName)
            
            Log.i(TAG, "Creating backup archive: ${archivePath.absolutePath}")
            
            // 4. Calculate total size for progress
            val totalSize = calculateTotalSize(sourceDocument)
            var bytesWritten = 0L
            
            // 5. Create ZIP archive
            ZipOutputStream(BufferedOutputStream(FileOutputStream(archivePath))).use { zipOut ->
                // Set compression level (standard)
                zipOut.setLevel(Deflater.DEFAULT_COMPRESSION)
                
                // Write manifest
                writeManifest(zipOut, profile, sourceDocument)
                
                // Add all files from source
                val baseFolderName = sourceDocument.name ?: sanitizedName
                addDocumentToZip(zipOut, sourceDocument, baseFolderName) { bytes ->
                    bytesWritten += bytes
                    onProgress?.invoke(bytesWritten, totalSize)
                }
            }
            
            Log.i(TAG, "Backup archive created successfully: ${archivePath.absolutePath}")
            
            // 6. Manage old backups (rotation)
            val deletedCount = manageOldBackups(profileBackupDir, maxBackups)
            
            val message = buildString {
                append("Backup completed successfully:\n'$archiveName'")
                if (deletedCount > 0) {
                    append("\nDeleted $deletedCount old backup(s).")
                }
            }
            
            BackupResult(
                success = true,
                message = message,
                backupPath = archivePath.absolutePath,
                deletedOldBackups = deletedCount
            )
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error during backup: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Permission denied: Cannot access save files.\nPlease re-select the save folder."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during backup: ${e.message}", e)
            BackupResult(
                success = false,
                message = "Backup failed: ${e.message}"
            )
        }
    }
    
    /**
     * Lists available backups for a profile.
     * 
     * @param profile The profile to list backups for
     * @return List of backup files sorted by date (newest first)
     */
    fun listBackups(profile: GameProfile): List<BackupInfo> {
        val backupBaseDir = settingsManager.getBackupPath()
        val sanitizedName = sanitizeFolderName(profile.name)
        val profileBackupDir = File(backupBaseDir, sanitizedName)
        
        if (!profileBackupDir.exists() || !profileBackupDir.isDirectory) {
            return emptyList()
        }
        
        return profileBackupDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("Backup_") && it.name.endsWith(".zip") }
            ?.map { file ->
                BackupInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    createdAt = Date(file.lastModified()),
                    sizeBytes = file.length()
                )
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }
    
    /**
     * Gets backup count and last backup date for a profile.
     */
    fun getBackupStats(profile: GameProfile): Pair<Int, Date?> {
        val backups = listBackups(profile)
        return Pair(backups.size, backups.firstOrNull()?.createdAt)
    }
    
    /**
     * Sanitizes a folder name by removing/replacing invalid characters.
     */
    private fun sanitizeFolderName(name: String): String {
        // Replace invalid characters with underscore
        val invalidChars = Regex("[<>:\"/\\\\|?*]")
        var sanitized = name.replace(invalidChars, "_")
        
        // Remove leading/trailing dots and spaces
        sanitized = sanitized.trim('.', ' ')
        
        // Limit length
        if (sanitized.length > 100) {
            sanitized = sanitized.take(100)
        }
        
        // Fallback if empty
        if (sanitized.isEmpty()) {
            sanitized = "profile_backup"
        }
        
        return sanitized
    }
    
    /**
     * Writes the manifest file to the ZIP archive.
     */
    private fun writeManifest(
        zipOut: ZipOutputStream,
        profile: GameProfile,
        sourceDocument: DocumentFile
    ) {
        val manifest = JSONObject().apply {
            put("schema", 1)
            put("app_version", APP_VERSION)
            put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            put("profile_name", profile.name)
            put("profile_id", profile.id)
            put("emulator", profile.emulator)
            put("save_path", profile.savePath)
            put("platform", "android")
        }
        
        val manifestBytes = manifest.toString(2).toByteArray(Charsets.UTF_8)
        
        val entry = ZipEntry(MANIFEST_PATH)
        zipOut.putNextEntry(entry)
        zipOut.write(manifestBytes)
        zipOut.closeEntry()
        
        Log.d(TAG, "Added manifest to backup archive")
    }
    
    /**
     * Recursively adds a DocumentFile (file or directory) to the ZIP archive.
     */
    private fun addDocumentToZip(
        zipOut: ZipOutputStream,
        document: DocumentFile,
        arcPath: String,
        onBytesWritten: (Long) -> Unit
    ) {
        if (document.isDirectory) {
            // Add directory entry
            val dirPath = if (arcPath.endsWith("/")) arcPath else "$arcPath/"
            val dirEntry = ZipEntry(dirPath)
            zipOut.putNextEntry(dirEntry)
            zipOut.closeEntry()
            
            // Process children
            document.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                addDocumentToZip(zipOut, child, "$arcPath/$childName", onBytesWritten)
            }
        } else if (document.isFile) {
            // Add file entry
            val fileEntry = ZipEntry(arcPath)
            zipOut.putNextEntry(fileEntry)
            
            try {
                context.contentResolver.openInputStream(document.uri)?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        zipOut.write(buffer, 0, bytesRead)
                        onBytesWritten(bytesRead.toLong())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error adding file to zip: ${document.uri}: ${e.message}")
            }
            
            zipOut.closeEntry()
            Log.d(TAG, "Added file to archive: $arcPath")
        }
    }
    
    /**
     * Calculates total size of all files in a DocumentFile (recursively).
     */
    private fun calculateTotalSize(document: DocumentFile): Long {
        if (document.isFile) {
            return document.length()
        }
        
        var total = 0L
        document.listFiles().forEach { child ->
            total += calculateTotalSize(child)
        }
        return total
    }
    
    /**
     * Manages old backups by deleting oldest ones when exceeding maxBackups.
     * 
     * @param profileBackupDir Directory containing backups for the profile
     * @param maxBackups Maximum number of backups to keep (-1 for unlimited)
     * @return Number of deleted backups
     */
    private fun manageOldBackups(profileBackupDir: File, maxBackups: Int): Int {
        if (maxBackups < 0) {
            return 0 // Unlimited backups
        }
        
        val backupFiles = profileBackupDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("Backup_") && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?: return 0
        
        if (backupFiles.size <= maxBackups) {
            return 0
        }
        
        var deletedCount = 0
        backupFiles.drop(maxBackups).forEach { fileToDelete ->
            try {
                if (fileToDelete.delete()) {
                    Log.d(TAG, "Deleted old backup: ${fileToDelete.name}")
                    deletedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete old backup: ${fileToDelete.name}: ${e.message}")
            }
        }
        
        return deletedCount
    }
}

/**
 * Information about a backup file.
 */
data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val createdAt: Date,
    val sizeBytes: Long
) {
    /**
     * Formatted size string (e.g., "1.5 MB")
     */
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            
            return when {
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.2f KB", kb)
                else -> "$sizeBytes B"
            }
        }
    
    /**
     * Formatted date string.
     */
    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(createdAt)
}
