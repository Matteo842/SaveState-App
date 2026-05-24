package com.savestate.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.BuildConfig
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
    private val settingsManager: SettingsManager,
    private val rootAccessHelper: RootAccessHelper? = null
) {
    companion object {
        private const val TAG = "BackupManager"
        private const val MANIFEST_PATH = "savestate/manifest.json"
        private const val BUFFER_SIZE = 8192 // 8KB buffer for file operations
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
     * @param maxSourceSizeMB Maximum source size in MB (0 = unlimited)
     * @param compressionLevel ZIP compression level (0 = none, 6 = standard, 9 = maximum)
     * @param onProgress Optional callback for progress updates (bytesWritten, totalBytes)
     * @return BackupResult with success status and details
     */
    suspend fun performBackup(
        profile: GameProfile,
        maxBackups: Int = 3,
        maxSourceSizeMB: Int = 0,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
        onProgress: ((Long, Long) -> Unit)? = null
    ): BackupResult = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "Starting backup for profile: '${profile.name}'")
        
        // Temp directory used when backing up root-protected paths
        var rootTempDir: File? = null
        
        try {
            // 1. Validate source path
            var sourceDocument: DocumentFile?
            
            if (profile.requiresRoot && rootAccessHelper != null) {
                // Root mode: copy from protected path to cache, then work with cache copy
                Log.i(TAG, "Root backup: copying from ${profile.savePath}")
                val tempDir = File(context.cacheDir, "root_backup_temp")
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                
                val copyOk = rootAccessHelper.copyToCache(profile.savePath, tempDir)
                if (!copyOk || !tempDir.exists() || (tempDir.listFiles()?.isEmpty() != false)) {
                    tempDir.deleteRecursively()
                    return@withContext BackupResult(
                        success = false,
                        message = "Root: failed to copy saves from protected path.\nCheck root permissions."
                    )
                }
                
                rootTempDir = tempDir
                sourceDocument = DocumentFile.fromFile(tempDir)
                Log.i(TAG, "Root copy complete, temp dir has ${tempDir.listFiles()?.size} items")
            } else {
                // Normal SAF mode
                val sourceUri = Uri.parse(profile.savePath)
                sourceDocument = DocumentFile.fromTreeUri(context, sourceUri)
                
                // Fallback: if tree URI fails, try navigating from parent path
                if ((sourceDocument == null || !sourceDocument.exists()) && profile.parentPath != null) {
                    Log.w(TAG, "fromTreeUri failed for savePath, trying via parentPath")
                    val parentUri = Uri.parse(profile.parentPath)
                    val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                    if (parentDoc != null && parentDoc.exists()) {
                        val folderName = sourceUri.lastPathSegment
                            ?.substringAfterLast("/")
                            ?.substringAfterLast("%2F")
                        if (folderName != null) {
                            sourceDocument = parentDoc.findFile(folderName)
                        }
                        if (sourceDocument == null || !sourceDocument.exists()) {
                            sourceDocument = parentDoc
                        }
                    }
                }
                
                // Fallback: try as single document URI
                if (sourceDocument == null || !sourceDocument.exists()) {
                    Log.w(TAG, "Tree URI failed, trying fromSingleUri")
                    sourceDocument = DocumentFile.fromSingleUri(context, sourceUri)
                }
            }
            
            if (sourceDocument == null || !sourceDocument.exists()) {
                return@withContext BackupResult(
                    success = false,
                    message = "Source path does not exist: ${profile.savePath}"
                )
            }
            
            Log.i(TAG, "Source validated: ${sourceDocument.name} (uri: ${sourceDocument.uri})")
            
            // 1b. Check source size against limit
            if (maxSourceSizeMB > 0) {
                val sourceSize = if (profile.gameFilePrefix != null) {
                    calculateFilteredSize(sourceDocument, profile.gameFilePrefix)
                } else {
                    calculateTotalSize(sourceDocument)
                }
                val maxBytes = maxSourceSizeMB.toLong() * 1024L * 1024L
                if (sourceSize > maxBytes) {
                    val sizeMB = String.format("%.1f", sourceSize / (1024.0 * 1024.0))
                    return@withContext BackupResult(
                        success = false,
                        message = "Source size (${sizeMB} MB) exceeds the maximum allowed (${maxSourceSizeMB} MB).\nYou can increase this limit in Settings."
                    )
                }
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
            
            Log.i(TAG, "Creating backup archive: $archiveName (compression level: $compressionLevel)")
            
            // 4. Calculate total size for progress
            var bytesWritten = 0L
            
            // 5. Create ZIP archive in temp location
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { zipOut ->
                zipOut.setLevel(compressionLevel)
                
                writeManifest(zipOut, profile, sourceDocument)
                
                if (profile.gameFilePrefix != null) {
                    // File-level filtering (RetroArch): only back up files matching the game prefix
                    val totalSize = calculateFilteredSize(sourceDocument, profile.gameFilePrefix)
                    addFilteredFilesToZip(zipOut, sourceDocument, profile.gameFilePrefix) { bytes ->
                        bytesWritten += bytes
                        onProgress?.invoke(bytesWritten, totalSize)
                    }
                } else if (profile.requiresRoot && rootAccessHelper != null) {
                    // Root directory backup: add children directly to ZIP root
                    // WITHOUT a wrapper folder. The temp cache dir name (root_backup_temp)
                    // must NOT leak into the archive – root restore copies flat content
                    // straight into savePath.
                    val totalSize = calculateTotalSize(sourceDocument)
                    Log.d(TAG, "Root backup: adding ${sourceDocument.listFiles().size} items without wrapper folder")
                    sourceDocument.listFiles().forEach { child ->
                        val childName = child.name ?: return@forEach
                        addDocumentToZip(zipOut, child, childName) { bytes ->
                            bytesWritten += bytes
                            onProgress?.invoke(bytesWritten, totalSize)
                        }
                    }
                } else {
                    // Directory-level backup (PPSSPP, etc.): back up entire folder
                    val totalSize = calculateTotalSize(sourceDocument)
                    val baseFolderName = sourceDocument.name ?: sanitizedName
                    addDocumentToZip(zipOut, sourceDocument, baseFolderName) { bytes ->
                        bytesWritten += bytes
                        onProgress?.invoke(bytesWritten, totalSize)
                    }
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
            
            val archiveBytes = tempFile.length()
            val archiveSizeFormatted = when {
                archiveBytes >= 1024 * 1024 -> String.format("%.2f MB", archiveBytes / (1024.0 * 1024.0))
                archiveBytes >= 1024 -> String.format("%.2f KB", archiveBytes / 1024.0)
                else -> "$archiveBytes B"
            }
            Log.i(TAG, "Backup archive created: $archiveName (size: $archiveSizeFormatted, compression: $compressionLevel)")
            
            // Delete temp file
            tempFile.delete()
            
            // Clean up root temp directory
            rootTempDir?.deleteRecursively()
            
            // 7. Manage old backups (rotation)
            val deletedCount = manageOldBackupsSAF(profileBackupDoc, maxBackups)
            
            val compressionLabel = when (compressionLevel) {
                0 -> "None"
                9 -> "Maximum"
                else -> "Standard"
            }
            val message = buildString {
                append("Backup completed! ($archiveSizeFormatted, $compressionLabel)")
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
            rootTempDir?.deleteRecursively()
            BackupResult(
                success = false,
                message = "Permission denied: Cannot access save files.\nPlease re-select the save folder."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during backup: ${e.message}", e)
            rootTempDir?.deleteRecursively()
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
            
            // 2. Determine extract target
            val isRootRestore = profile.requiresRoot && rootAccessHelper != null
            val extractTarget: DocumentFile?
            var rootRestoreTempDir: File? = null
            
            if (isRootRestore) {
                // Root mode: extract to cache, then copy to protected path via root
                val tempDir = File(context.cacheDir, "root_restore_temp")
                tempDir.deleteRecursively()
                tempDir.mkdirs()
                rootRestoreTempDir = tempDir
                extractTarget = DocumentFile.fromFile(tempDir)
                Log.d(TAG, "Root restore: extracting to cache first")
            } else {
                // SAF mode: we ALWAYS extract directly into the savePath (the actual folder where files live).
                // This guarantees we have the necessary write permissions granted by the user.
                val saveUri = Uri.parse(profile.savePath)
                extractTarget = DocumentFile.fromTreeUri(context, saveUri)
                Log.d(TAG, "SAF restore: extracting directly to savePath: ${profile.savePath}")
            }
            
            if (extractTarget == null || !extractTarget.exists()) {
                return@withContext RestoreResult(
                    success = false,
                    message = "Cannot access save folder.\nPlease check folder permissions or re-select the save folder."
                )
            }
            
            Log.d(TAG, "Extracting to: ${extractTarget.name}")
            
            // 3. Open ZIP and count entries (for progress)
            val zipFile = ZipFile(tempBackupFile)
            val entries = zipFile.entries().toList()
            val fileEntries = entries.filter { !it.isDirectory && !it.name.startsWith("savestate/") }
            val totalFiles = fileEntries.size
            var restoredFiles = 0
            val errors = mutableListOf<String>()

            Log.d(TAG, "ZIP contains ${entries.size} entries, ${fileEntries.size} files to restore")

            // 3b. Pre-restore cleanup: remove stale files in the destination whose
            // base name (without extension) matches a file in the backup.
            // This covers the SAF quirk where a file was originally stored without
            // an extension (e.g. "file0") but the backup recorded it as "file0.bin",
            // which would otherwise leave a dangling "file0" alongside the new "file0.bin".
            if (!isRootRestore && profile.gameFilePrefix == null) {
                // Collect base names (no-extension) for all files we're about to restore
                val backupBaseNames: Set<String> = fileEntries.mapNotNull { e ->
                    val leaf = e.name.substringAfterLast("/").ifEmpty { null }
                    leaf?.substringBeforeLast(".", leaf)
                }.toSet()

                val existingDocs = extractTarget.listFiles()
                for (doc in existingDocs) {
                    if (!doc.isFile) continue
                    val docName = doc.name ?: continue
                    val docBase = docName.substringBeforeLast(".", docName)
                    if (docBase in backupBaseNames) {
                        doc.delete()
                        Log.d(TAG, "Pre-restore: removed stale file '$docName'")
                    }
                }
            }
            
            // 4. Extract files
            for (entry in fileEntries) {
                try {
                    if (entry.name.startsWith("savestate/")) continue
                    if (entry.name.contains("..")) {
                        Log.w(TAG, "Skipping unsafe path: ${entry.name}")
                        continue
                    }
                    
                    val pathParts = entry.name.split("/").filter { it.isNotEmpty() }.toMutableList()
                    if (pathParts.isEmpty()) continue
                    
                    // Normalize path segments to restore directly inside the save directory.
                    // If the first folder in the ZIP matches the game's save folder name,
                    // the legacy "root_backup_temp", or the profile name, strip it so files
                    // land in the correct root or nested folders of the save directory.
                    val lastSegment = try {
                        val uri = Uri.parse(profile.savePath)
                        uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast("%2F")
                    } catch (e: Exception) {
                        null
                    }
                    val sanitizedProfileName = sanitizeFolderName(profile.name)
                    
                    val firstPart = pathParts.firstOrNull()
                    if (firstPart != null && (
                        firstPart == "root_backup_temp" || 
                        firstPart == lastSegment || 
                        firstPart == sanitizedProfileName
                    )) {
                        Log.d(TAG, "Stripping wrapper folder '$firstPart' from path '${entry.name}'")
                        pathParts.removeAt(0)
                    }
                    
                    if (pathParts.isEmpty()) continue
                    
                    if (isRootRestore && rootRestoreTempDir != null) {
                        // Root mode: extract using standard java.io.File to preserve names verbatim!
                        var currentLocalDir = rootRestoreTempDir
                        for (i in 0 until pathParts.size - 1) {
                            currentLocalDir = File(currentLocalDir, pathParts[i])
                            currentLocalDir.mkdirs()
                        }
                        
                        val localFile = File(currentLocalDir, pathParts.last())
                        if (localFile.exists()) {
                            localFile.delete()
                        }
                        
                        zipFile.getInputStream(entry).use { input ->
                            localFile.outputStream().use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    } else {
                        // SAF mode: extract using DocumentFile (may map to RawDocumentFile or TreeDocumentFile)
                        copyZipEntryWithPath(zipFile, entry, extractTarget!!, pathParts)
                    }
                    
                    restoredFiles++
                    onProgress?.invoke(restoredFiles, totalFiles)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring entry ${entry.name}: ${e.message}", e)
                    errors.add("${entry.name}: ${e.message}")
                }
            }
            
            zipFile.close()
            tempBackupFile.delete()
            
            // 5. For root profiles, copy from cache to protected path
            if (isRootRestore && rootRestoreTempDir != null) {
                Log.i(TAG, "Root restore: copying from cache to ${profile.savePath}")
                
                // Determine the expected save folder name from the destination path
                // (e.g. for Eden: "0100A..." title ID folder).
                val savePathBasename = profile.savePath.substringAfterLast("/")

                // Unwrap the source directory if files were accidentally wrapped
                // inside a single subdirectory (legacy "root_backup_temp" or the
                // title-ID folder itself e.g. "0100A...").
                val actualSourceDir = rootRestoreTempDir.listFiles()?.let { children ->
                    val directFiles = children.filter { it.isFile }
                    val singleWrapperDir = children.singleOrNull { it.isDirectory }
                    when {
                        // Old format: every file lives under root_backup_temp/
                        singleWrapperDir?.name == "root_backup_temp" && directFiles.isEmpty() -> {
                            Log.w(TAG, "Detected legacy root_backup_temp wrapper in backup – unwrapping")
                            singleWrapperDir
                        }
                        // ZIP was rooted at the save folder itself (e.g. title-ID or
                        // any single directory whose name matches the destination basename).
                        singleWrapperDir != null && directFiles.isEmpty() &&
                            singleWrapperDir.name == savePathBasename -> {
                            Log.w(TAG, "Detected save-folder wrapper '${singleWrapperDir.name}' in backup – unwrapping")
                            singleWrapperDir
                        }
                        // New format (or mixed): files already at root level
                        else -> rootRestoreTempDir
                    }
                } ?: rootRestoreTempDir

                // Pre-restore cleanup via root: remove stale files at the destination
                // before copying. Without this, old save files may linger alongside
                // freshly-restored ones and confuse the emulator.
                // SAFETY: only wipe destination if we actually have files to copy.
                // If extraction failed and temp dir is empty, skipping deleteContents
                // avoids leaving the destination empty (which makes Eden create a fresh
                // save, overwriting any previously-existing data).
                val tempHasContent = actualSourceDir.listFiles()?.isNotEmpty() == true
                if (tempHasContent) {
                    Log.d(TAG, "Root restore: cleaning destination '${profile.savePath}' before copy")
                    rootAccessHelper!!.deleteContents(profile.savePath)
                } else {
                    Log.w(TAG, "Root restore: temp dir is empty – skipping deleteContents to preserve existing saves")
                }

                val copyOk = rootAccessHelper.copyFromCache(actualSourceDir, profile.savePath)
                rootRestoreTempDir.deleteRecursively()
                
                if (!copyOk) {
                    return@withContext RestoreResult(
                        success = false,
                        message = "Root: failed to copy restored files to protected path."
                    )
                }
            }
            
            Log.i(TAG, "Restore completed: $restoredFiles/$totalFiles files")
            
            if (restoredFiles == 0) {
                val errorDetails = if (errors.isNotEmpty()) {
                    "\n\nDetails:\n" + errors.take(3).joinToString("\n") + if (errors.size > 3) "\n..." else ""
                } else {
                    "\n\nMake sure the backup file contains valid save files."
                }
                return@withContext RestoreResult(
                    success = false,
                    message = "Restore failed: No files could be restored.$errorDetails"
                )
            }
            
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
     *
     * SAF's createFile() can silently append or mangle extensions based on the
     * MIME type (e.g. "file0.bin" → "file0.bin.bin") and never overwrites an
     * existing document – it always creates a new one with a disambiguated name.
     * To avoid duplicates we:
     *  1. Delete any existing document whose display-name matches fileName.
     *  2. Create a fresh document with MIME "application/octet-stream" so the
     *     name is stored verbatim without any extension transformation.
     */
    private fun copyZipEntryToDocument(
        zipFile: ZipFile,
        entry: java.util.zip.ZipEntry,
        destDocument: DocumentFile,
        fileName: String
    ) {
        // Delete existing file first to avoid SAF creating a duplicate
        val existingFile = destDocument.findFile(fileName)
        if (existingFile != null && existingFile.isFile) {
            existingFile.delete()
            Log.d(TAG, "Deleted existing file before restore: $fileName")
        }

        // Always use application/octet-stream so SAF stores the name verbatim
        val targetFile = destDocument.createFile("application/octet-stream", fileName)
            ?: throw Exception("Cannot create file: $fileName")

        // Copy content from ZIP entry
        zipFile.getInputStream(entry).use { input ->
            context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
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
            put("app_version", BuildConfig.VERSION_NAME)
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
     * Returns the true display name of a document as stored in the provider,
     * bypassing DocumentFile.name which can silently append a MIME-type
     * extension (e.g. turns "file0" into "file0.bin") for extensionless files.
     *
     * Falls back to [DocumentFile.name] if the cursor query fails.
     */
    private fun getRealDisplayName(document: DocumentFile): String? {
        return try {
            context.contentResolver.query(
                document.uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            } ?: document.name
        } catch (e: Exception) {
            Log.w(TAG, "getRealDisplayName fallback for ${document.uri}: ${e.message}")
            document.name
        }
    }

    /**
     * Recursively adds a DocumentFile (file or directory) to the ZIP archive.
     *
     * Uses [getRealDisplayName] instead of [DocumentFile.name] to avoid the
     * SAF quirk where extensionless files (like Eden's "file0", "system") get
     * their name silently suffixed with ".bin" by the DocumentsProvider.
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

            // Process children — use real display name to avoid SAF extension mangling
            document.listFiles().forEach { child ->
                val childName = getRealDisplayName(child) ?: return@forEach
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
     * Calculates total size of files matching a prefix in a directory.
     */
    private fun calculateFilteredSize(directory: DocumentFile, prefix: String): Long {
        var total = 0L
        directory.listFiles().forEach { child ->
            if (child.isFile) {
                val name = child.name ?: return@forEach
                if (name.startsWith("$prefix.")) {
                    total += child.length()
                }
            }
        }
        return total
    }
    
    /**
     * Adds only files matching a game prefix to the ZIP (for RetroArch file-level backups).
     * Files like "GameName.srm", "GameName.rtc", "GameName.state", etc.
     */
    private fun addFilteredFilesToZip(
        zipOut: ZipOutputStream,
        directory: DocumentFile,
        prefix: String,
        onBytesWritten: (Long) -> Unit
    ) {
        directory.listFiles().forEach { child ->
            if (!child.isFile) return@forEach
            val name = child.name ?: return@forEach
            
            if (!name.startsWith("$prefix.")) return@forEach
            
            val fileEntry = ZipEntry(name)
            zipOut.putNextEntry(fileEntry)
            
            try {
                context.contentResolver.openInputStream(child.uri)?.use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        zipOut.write(buffer, 0, bytesRead)
                        onBytesWritten(bytesRead.toLong())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error adding file to zip: ${child.uri}: ${e.message}")
            }
            
            zipOut.closeEntry()
            Log.d(TAG, "Added file to archive: $name")
        }
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
