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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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
            
            // 2. Get backup directory from settings (SAF)
            val backupBaseDoc = settingsManager.getBackupDocumentFile()
            if (backupBaseDoc == null || !backupBaseDoc.exists()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Backup folder not configured.\nPlease select a backup folder in Settings."
                )
            }
            
            // Create or get profile backup folder
            val sanitizedName = sanitizeFolderName(profile.name)
            var profileBackupDoc = backupBaseDoc.findFile(sanitizedName)
            if (profileBackupDoc == null) {
                profileBackupDoc = backupBaseDoc.createDirectory(sanitizedName)
            }
            
            if (profileBackupDoc == null || !profileBackupDoc.exists()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Cannot create backup folder for ${profile.name}"
                )
            }
            
            // 3. Generate backup filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val archiveName = "Backup_${sanitizedName}_${timestamp}.zip"
            
            // Create temp file for ZIP (then copy to SAF)
            val tempFile = File(context.cacheDir, archiveName)
            
            Log.i(TAG, "Creating backup archive: $archiveName")
            
            // 4. Calculate total size for progress
            val totalSize = calculateTotalSize(sourceDocument)
            var bytesWritten = 0L
            
            // 5. Create ZIP archive in temp location
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zipOut ->
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
            
            // 6. Copy temp ZIP to SAF destination
            val destZipDoc = profileBackupDoc.createFile("application/zip", archiveName)
            if (destZipDoc == null) {
                tempFile.delete()
                return@withContext BackupResult(
                    success = false,
                    message = "Cannot create backup file in storage"
                )
            }
            
            context.contentResolver.openOutputStream(destZipDoc.uri)?.use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            
            // Delete temp file
            tempFile.delete()
            
            Log.i(TAG, "Backup archive created successfully: $archiveName")
            
            // 7. Manage old backups (rotation)
            val deletedCount = manageOldBackupsSAF(profileBackupDoc, maxBackups)
            
            val message = buildString {
                append("Backup completed successfully:\n'$archiveName'")
                if (deletedCount > 0) {
                    append("\nDeleted $deletedCount old backup(s).")
                }
            }
            
            BackupResult(
                success = true,
                message = message,
                backupPath = destZipDoc.uri.toString(),
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
        val backupBaseDoc = settingsManager.getBackupDocumentFile() ?: return emptyList()
        val sanitizedName = sanitizeFolderName(profile.name)
        val profileBackupDoc = backupBaseDoc.findFile(sanitizedName) ?: return emptyList()
        
        if (!profileBackupDoc.isDirectory) {
            return emptyList()
        }
        
        return profileBackupDoc.listFiles()
            .filter { it.isFile && (it.name?.startsWith("Backup_") == true) && (it.name?.endsWith(".zip") == true) }
            .map { file ->
                BackupInfo(
                    fileName = file.name ?: "unknown",
                    filePath = file.uri.toString(),
                    createdAt = Date(file.lastModified()),
                    sizeBytes = file.length()
                )
            }
            .sortedByDescending { it.createdAt }
    }
    
    /**
     * Gets backup count and last backup date for a profile.
     */
    fun getBackupStats(profile: GameProfile): Pair<Int, Date?> {
        val backups = listBackups(profile)
        return Pair(backups.size, backups.firstOrNull()?.createdAt)
    }
    
    /**
     * Performs a restore from a backup archive.
     * 
     * @param profile The game profile to restore
     * @param backupInfo The backup to restore from
     * @param onProgress Optional callback for progress updates (filesRestored, totalFiles)
     * @return RestoreResult with success status and details
     */
    suspend fun performRestore(
        profile: GameProfile,
        backupInfo: BackupInfo,
        onProgress: ((Int, Int) -> Unit)? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "Starting restore for profile: '${profile.name}' from: ${backupInfo.fileName}")
        
        try {
            // 1. Validate backup file exists and copy to cache (SAF files need to be copied)
            val backupUri = Uri.parse(backupInfo.filePath)
            val tempBackupFile = File(context.cacheDir, "restore_temp.zip")
            
            try {
                context.contentResolver.openInputStream(backupUri)?.use { input ->
                    tempBackupFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext RestoreResult(
                    success = false,
                    message = "Cannot read backup file"
                )
            } catch (e: Exception) {
                return@withContext RestoreResult(
                    success = false,
                    message = "Backup file not accessible: ${e.message}"
                )
            }
            
            // 2. Get the PARENT folder where we extract to
            // profile.parentPath points to: SAVEDATA (where we have SAF permissions)
            // profile.savePath points to: SAVEDATA/UCUS98653DATA00 (may not exist)
            // We extract to parentPath, and ZIP structure (UCUS98653DATA00/...) is preserved
            
            val extractTarget: DocumentFile?
            
            if (profile.parentPath != null) {
                // Use stored parent path (preferred - we have permissions on this)
                val parentUri = Uri.parse(profile.parentPath)
                extractTarget = DocumentFile.fromTreeUri(context, parentUri)
                Log.d(TAG, "Using stored parentPath: ${profile.parentPath}")
            } else {
                // Fallback: try to get parent from savePath (may fail if folder deleted)
                val destUri = Uri.parse(profile.savePath)
                val saveDocument = DocumentFile.fromTreeUri(context, destUri)
                extractTarget = saveDocument?.parentFile ?: saveDocument
                Log.w(TAG, "No parentPath stored, trying fallback")
            }
            
            if (extractTarget == null || !extractTarget.exists()) {
                return@withContext RestoreResult(
                    success = false,
                    message = "Cannot access save folder.\nPlease delete this profile and re-add it."
                )
            }
            
            Log.d(TAG, "Extracting to: ${extractTarget.name}")
            
            // 3. Open ZIP and count entries (for progress)
            val zipFile = ZipFile(tempBackupFile)
            val entries = zipFile.entries().toList()
            val fileEntries = entries.filter { !it.isDirectory && !it.name.startsWith("savestate/") }
            val totalFiles = fileEntries.size
            var restoredFiles = 0
            
            Log.d(TAG, "ZIP contains ${entries.size} entries, ${fileEntries.size} files to restore")
            
            // 4. Extract files to parent with FULL path structure
            // ZIP has: UCUS98653DATA00/PARAM.SFO
            // We extract to: SAVEDATA/UCUS98653DATA00/PARAM.SFO
            // This recreates the folder if it was deleted!
            for (entry in fileEntries) {
                try {
                    // Skip manifest
                    if (entry.name.startsWith("savestate/")) continue
                    
                    // Security check: prevent path traversal
                    if (entry.name.contains("..")) {
                        Log.w(TAG, "Skipping unsafe path: ${entry.name}")
                        continue
                    }
                    
                    // Keep FULL path - this includes the game folder name
                    val pathParts = entry.name.split("/").filter { it.isNotEmpty() }
                    
                    if (pathParts.isEmpty()) continue
                    
                    // Extract with complete path structure
                    copyZipEntryWithPath(zipFile, entry, extractTarget, pathParts)
                    
                    restoredFiles++
                    onProgress?.invoke(restoredFiles, totalFiles)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring entry ${entry.name}: ${e.message}", e)
                }
            }
            
            zipFile.close()
            
            // Clean up temp file
            tempBackupFile.delete()
            
            Log.i(TAG, "Restore completed: $restoredFiles/$totalFiles files")
            
            RestoreResult(
                success = true,
                message = "Restore completed successfully!\n$restoredFiles file(s) restored from:\n'${backupInfo.fileName}'"
            )
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error during restore: ${e.message}", e)
            RestoreResult(
                success = false,
                message = "Permission denied: Cannot write to save folder.\nPlease re-select the save folder."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during restore: ${e.message}", e)
            RestoreResult(
                success = false,
                message = "Restore failed: ${e.message}"
            )
        }
    }
    
    /**
     * Copies a ZIP entry to a DocumentFile destination.
     */
    private fun copyZipEntryToDocument(
        zipFile: ZipFile,
        entry: java.util.zip.ZipEntry,
        destDocument: DocumentFile,
        fileName: String
    ) {
        // Create or overwrite the file
        var existingFile = destDocument.findFile(fileName)
        if (existingFile != null) {
            existingFile.delete()
        }
        
        val mimeType = getMimeType(fileName)
        val newFile = destDocument.createFile(mimeType, fileName)
            ?: throw Exception("Cannot create file: $fileName")
        
        // Copy content
        zipFile.getInputStream(entry).use { input ->
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        
        Log.d(TAG, "Restored file: $fileName")
    }
    
    /**
     * Copies a ZIP entry to a nested path in DocumentFile, creating folders as needed.
     */
    private fun copyZipEntryWithPath(
        zipFile: ZipFile,
        entry: java.util.zip.ZipEntry,
        destDocument: DocumentFile,
        pathParts: List<String>
    ) {
        if (pathParts.isEmpty()) return
        
        // Navigate/create folders except for the last part (which is the file)
        var currentFolder = destDocument
        for (i in 0 until pathParts.size - 1) {
            val folderName = pathParts[i]
            var childFolder = currentFolder.findFile(folderName)
            
            if (childFolder == null || !childFolder.isDirectory) {
                // Create folder
                childFolder = currentFolder.createDirectory(folderName)
                    ?: throw Exception("Cannot create directory: $folderName")
                Log.d(TAG, "Created directory: $folderName")
            }
            
            currentFolder = childFolder
        }
        
        // Copy the file to the final folder
        val fileName = pathParts.last()
        copyZipEntryToDocument(zipFile, entry, currentFolder, fileName)
    }
    
    /**
     * Deletes a backup file.
     */
    fun deleteBackup(backupInfo: BackupInfo): Boolean {
        return try {
            val uri = Uri.parse(backupInfo.filePath)
            val docFile = DocumentFile.fromSingleUri(context, uri)
            val deleted = docFile?.delete() ?: false
            Log.d(TAG, "Deleted backup ${backupInfo.fileName}: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backup: ${e.message}", e)
            false
        }
    }
    
    /**
     * Gets MIME type for a file based on extension.
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "")
        return when (extension.lowercase()) {
            "sfo" -> "application/octet-stream"
            "bin" -> "application/octet-stream"
            "dat" -> "application/octet-stream"
            "sav" -> "application/octet-stream"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
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
    
    /**
     * SAF version: Manages old backups by deleting oldest ones when exceeding maxBackups.
     */
    private fun manageOldBackupsSAF(profileBackupDoc: DocumentFile, maxBackups: Int): Int {
        if (maxBackups < 0) {
            return 0 // Unlimited backups
        }
        
        val backupFiles = profileBackupDoc.listFiles()
            .filter { it.isFile && (it.name?.startsWith("Backup_") == true) && (it.name?.endsWith(".zip") == true) }
            .sortedByDescending { it.lastModified() }
        
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
