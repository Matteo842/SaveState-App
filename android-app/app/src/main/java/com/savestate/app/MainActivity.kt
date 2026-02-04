package com.savestate.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.BackupDirectoryInfo
import com.savestate.app.data.BackupInfo
import com.savestate.app.data.BackupManager
import com.savestate.app.data.ConfigManager
import com.savestate.app.data.EmulatorDetector
import com.savestate.app.data.GameScanner
import com.savestate.app.data.ProfileRepository
import com.savestate.app.data.SettingsManager
import com.savestate.app.data.SfoParser
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.dialogs.RestoreBackupDialog
import com.savestate.app.ui.dialogs.SelectEmulatorDialog
import com.savestate.app.ui.dialogs.SelectGameDialog
import com.savestate.app.ui.screens.MainScreen
import com.savestate.app.ui.screens.SettingsScreen
import com.savestate.app.ui.theme.SaveStateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    
    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var gameScanner: GameScanner
    private lateinit var configManager: ConfigManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var backupManager: BackupManager
    private lateinit var profileRepository: ProfileRepository
    
    // Callback to run after storage permission is granted
    private var onStoragePermissionGranted: (() -> Unit)? = null
    
    // State variables that need to be accessed from callbacks
    private var currentEmulator: EmulatorInfo? = null
    private var onGamesDetected: ((List<DetectedGame>) -> Unit)? = null
    
    // Callback for settings backup path selection
    private var onBackupPathSelected: ((String) -> Unit)? = null
    
    // SAF folder picker launcher (for game folder selection)
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Log.d("SaveState", "User selected folder: $uri")
            
            // Take persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("SaveState", "Failed to take persistent permission: ${e.message}")
            }
            
            // Scan the selected folder
            scanSafFolder(uri)
        }
    }
    
    // SAF folder picker for settings backup path
    private val settingsFolderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Log.d("SaveState", "User selected backup folder: $uri")
            
            // Take persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("SaveState", "Failed to take persistent permission: ${e.message}")
            }
            
            // Get display path for UI
            val displayPath = getPathFromUri(uri) ?: uri.lastPathSegment ?: "Selected Folder"
            
            // Call the migration callback with URI and display path
            onBackupPathSelected?.invoke(uri.toString() + "|" + displayPath)
        }
    }
    
    /**
     * Convert a SAF URI to a file path (works for internal storage)
     */
    private fun getPathFromUri(uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]
                
                if (type == "primary") {
                    return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                }
            }
        } catch (e: Exception) {
            Log.e("SaveState", "Error converting URI to path: ${e.message}")
        }
        return null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize detectors and managers
        emulatorDetector = EmulatorDetector(applicationContext)
        gameScanner = GameScanner()
        configManager = ConfigManager(applicationContext)
        settingsManager = SettingsManager(applicationContext, configManager)
        backupManager = BackupManager(applicationContext, settingsManager)
        profileRepository = ProfileRepository(applicationContext, configManager)
        
        // Initialize PSP game database from assets
        GameScanner.initDatabase(applicationContext)
        
        // Load saved profiles
        val savedProfiles = profileRepository.loadProfiles()
        
        setContent {
            SaveStateTheme {
                // State for emulator selection dialog
                var showEmulatorDialog by remember { mutableStateOf(false) }
                var isLoadingEmulators by remember { mutableStateOf(false) }
                var installedEmulators by remember { mutableStateOf<List<EmulatorInfo>>(emptyList()) }
                var selectedEmulator by remember { mutableStateOf<EmulatorInfo?>(null) }
                
                // State for game selection dialog
                var showGameDialog by remember { mutableStateOf(false) }
                var isLoadingGames by remember { mutableStateOf(false) }
                var detectedGames by remember { mutableStateOf<List<DetectedGame>>(emptyList()) }
                
                // Coroutine scope for background operations
                val coroutineScope = rememberCoroutineScope()
                
                // Profile state - load from disk
                var selectedProfileId by remember { mutableStateOf<String?>(null) }
                var isDarkTheme by remember { mutableStateOf(true) }
                var profiles by remember { mutableStateOf(savedProfiles) }
                
                // Navigation state
                var showSettingsScreen by remember { mutableStateOf(false) }
                
                // Settings state
                var currentBackupPath by remember { mutableStateOf(settingsManager.getBackupPath()) }
                var backupInfo by remember { mutableStateOf<BackupDirectoryInfo?>(null) }
                var isMigrating by remember { mutableStateOf(false) }
                var migrationProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                
                // Backup operation state
                var isBackingUp by remember { mutableStateOf(false) }
                
                // Restore dialog state
                var showRestoreDialog by remember { mutableStateOf(false) }
                var availableBackups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
                var isRestoring by remember { mutableStateOf(false) }
                
                // Load backup info on startup
                LaunchedEffect(currentBackupPath) {
                    backupInfo = withContext(Dispatchers.IO) {
                        settingsManager.getBackupDirectoryInfo()
                    }
                }
                
                // Check if backup folder is configured on startup
                LaunchedEffect(Unit) {
                    if (!configManager.isConfigured()) {
                        // Show message and open settings
                        Toast.makeText(
                            this@MainActivity,
                            "Please select a backup folder in Settings",
                            Toast.LENGTH_LONG
                        ).show()
                        delay(500)
                        showSettingsScreen = true
                    }
                }
                
                // Setup callback for SAF folder scanning
                LaunchedEffect(Unit) {
                    onGamesDetected = { games ->
                        detectedGames = games
                        isLoadingGames = false
                    }
                }
                
                // Function to scan for games
                fun scanForGames(emulator: EmulatorInfo) {
                    showGameDialog = true
                    isLoadingGames = true
                    currentEmulator = emulator
                    
                    coroutineScope.launch {
                        delay(200)
                        
                        // Scan for games on background thread
                        val games = withContext(Dispatchers.IO) {
                            gameScanner.scanPPSSPPGames(emulator)
                        }
                        
                        detectedGames = games
                        isLoadingGames = false
                        
                        Log.d("SaveState", "Found ${games.size} games: ${games.map { it.gameName }}")
                    }
                }
                
                // Navigation: Show either MainScreen or SettingsScreen
                if (showSettingsScreen) {
                    SettingsScreen(
                        currentBackupPath = currentBackupPath,
                        backupInfo = backupInfo,
                        isMigrating = isMigrating,
                        migrationProgress = migrationProgress,
                        onBackClick = { showSettingsScreen = false },
                        onBrowseBackupPath = {
                            // Setup callback for when user selects a folder
                            onBackupPathSelected = { pathData ->
                                // Parse URI and display path (separated by |)
                                val parts = pathData.split("|", limit = 2)
                                val uriString = parts[0]
                                val displayPath = parts.getOrElse(1) { "Selected Folder" }
                                val uri = Uri.parse(uriString)
                                
                                // Save the config FIRST so we know where to look
                                configManager.takePersistentPermission(uri)
                                configManager.saveBaseUri(uri, displayPath)
                                
                                // Now try to load existing profiles from this folder
                                val existingProfiles = profileRepository.loadProfiles()
                                if (existingProfiles.isNotEmpty()) {
                                    // Found existing profiles! Load them
                                    profiles = existingProfiles
                                    profileRepository.saveProfiles(profiles) // Ensure they're saved
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Loaded ${existingProfiles.size} existing profile(s)!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Migrate internal profiles to external (if any)
                                    profileRepository.migrateToExternal()
                                }
                                
                                // Update UI
                                currentBackupPath = displayPath
                                coroutineScope.launch {
                                    backupInfo = withContext(Dispatchers.IO) {
                                        settingsManager.getBackupDirectoryInfo()
                                    }
                                }
                                
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup folder configured successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            
                            // Open folder picker
                            settingsFolderPickerLauncher.launch(null)
                        },
                        onResetToDefault = {
                            // Clear the current config
                            configManager.clearConfig()
                            currentBackupPath = "Not configured"
                            backupInfo = null
                            Toast.makeText(
                                this@MainActivity,
                                "Configuration reset. Please select a new backup folder.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        appVersion = "0.1"
                    )
                } else {
                    MainScreen(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelect = { id -> selectedProfileId = id },
                        onFavoriteToggle = { id ->
                            profiles = profiles.map { 
                                if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it 
                            }
                            profileRepository.saveProfiles(profiles)
                        },
                        onDeleteProfile = { id ->
                            profiles = profiles.filter { it.id != id }
                            profileRepository.saveProfiles(profiles)
                            if (selectedProfileId == id) selectedProfileId = null
                        },
                        onBackupClick = {
                            // Find the selected profile
                            val selectedProfile = profiles.find { it.id == selectedProfileId }
                            if (selectedProfile == null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please select a profile first",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@MainScreen
                            }
                            
                            // Avoid duplicate backups
                            if (isBackingUp) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup already in progress...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@MainScreen
                            }
                            
                            // Start backup
                            isBackingUp = true
                            Toast.makeText(
                                this@MainActivity,
                                "Starting backup for ${selectedProfile.name}...",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            coroutineScope.launch {
                                val result = backupManager.performBackup(
                                    profile = selectedProfile,
                                    maxBackups = 3 // TODO: Get from settings
                                )
                                
                                // Update profile with new backup count/date
                                if (result.success) {
                                    val (count, lastDate) = backupManager.getBackupStats(selectedProfile)
                                    profiles = profiles.map { p ->
                                        if (p.id == selectedProfile.id) {
                                            p.copy(
                                                backupCount = count,
                                                lastBackup = lastDate?.let {
                                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it)
                                                }
                                            )
                                        } else p
                                    }
                                    profileRepository.saveProfiles(profiles)
                                }
                                
                                Toast.makeText(
                                    this@MainActivity,
                                    result.message,
                                    if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                ).show()
                                
                                isBackingUp = false
                            }
                        },
                        onRestoreClick = {
                            // Find the selected profile
                            val selectedProfile = profiles.find { it.id == selectedProfileId }
                            if (selectedProfile == null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please select a profile first",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@MainScreen
                            }
                            
                            // Load available backups for this profile
                            availableBackups = backupManager.listBackups(selectedProfile)
                            showRestoreDialog = true
                        },
                        onManageBackupsClick = {
                            // For now, manage backups just opens the restore dialog
                            // which also allows deleting backups
                            val selectedProfile = profiles.find { it.id == selectedProfileId }
                            if (selectedProfile == null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please select a profile first",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@MainScreen
                            }
                            
                            availableBackups = backupManager.listBackups(selectedProfile)
                            showRestoreDialog = true
                        },
                        onNewProfileClick = { 
                            // Check storage permission first
                            if (!hasStoragePermission()) {
                                requestStoragePermission {
                                    // After permission granted, start emulator detection
                                    showEmulatorDialog = true
                                    isLoadingEmulators = true
                                    
                                    coroutineScope.launch {
                                        delay(300)
                                        val detected = withContext(Dispatchers.IO) {
                                            emulatorDetector.detectInstalledEmulators()
                                        }
                                        installedEmulators = detected
                                        isLoadingEmulators = false
                                        Log.d("SaveState", "Detected ${detected.size} emulators")
                                    }
                                }
                                return@MainScreen
                            }
                            
                            // Start scanning for emulators
                            showEmulatorDialog = true
                            isLoadingEmulators = true
                            
                            coroutineScope.launch {
                                // Small delay for UI feedback
                                delay(300)
                                
                                // Detect emulators on background thread
                                val detected = withContext(Dispatchers.IO) {
                                    emulatorDetector.detectInstalledEmulators()
                                }
                                
                                installedEmulators = detected
                                isLoadingEmulators = false
                                
                                Log.d("SaveState", "Detected ${detected.size} emulators: ${detected.map { it.displayName }}")
                            }
                        },
                        onSettingsClick = { showSettingsScreen = true },
                        onThemeToggle = { isDarkTheme = !isDarkTheme },
                        isDarkTheme = isDarkTheme,
                        appVersion = "0.1"
                    )
                }
                
                // Emulator selection dialog
                if (showEmulatorDialog) {
                    SelectEmulatorDialog(
                        installedEmulators = installedEmulators,
                        isLoading = isLoadingEmulators,
                        onEmulatorSelected = { emulator ->
                            selectedEmulator = emulator
                            showEmulatorDialog = false
                            
                            // Now scan for games with this emulator
                            scanForGames(emulator)
                        },
                        onDismiss = { 
                            showEmulatorDialog = false 
                            isLoadingEmulators = false
                        }
                    )
                }
                
                // Game selection dialog
                if (showGameDialog && selectedEmulator != null) {
                    SelectGameDialog(
                        emulator = selectedEmulator!!,
                        detectedGames = detectedGames,
                        isLoading = isLoadingGames,
                        onGameSelected = { game ->
                            showGameDialog = false
                            
                            // Create a new profile with the selected game
                            val newProfile = GameProfile(
                                id = UUID.randomUUID().toString(),
                                name = game.gameName,
                                emulator = selectedEmulator!!.emulatorType.displayName,
                                savePath = game.savePath,
                                parentPath = game.parentPath, // Store parent for restore
                                backupCount = 0,
                                lastBackup = null,
                                isFavorite = false
                            )
                            
                            // Check if profile already exists
                            val exists = profiles.any { 
                                it.savePath == game.savePath && 
                                it.emulator == selectedEmulator!!.emulatorType.displayName 
                            }
                            
                            if (!exists) {
                                profiles = profiles + newProfile
                                profileRepository.saveProfiles(profiles) // Persist to disk
                                selectedProfileId = newProfile.id
                                Log.d("SaveState", "Created and saved profile: ${game.gameName}")
                            } else {
                                Log.d("SaveState", "Profile already exists for: ${game.gameName}")
                                // Select the existing profile
                                val existingProfile = profiles.find { 
                                    it.savePath == game.savePath 
                                }
                                existingProfile?.let { selectedProfileId = it.id }
                            }
                            
                            selectedEmulator = null
                            detectedGames = emptyList()
                        },
                        onBrowseFolder = {
                            // Open the folder picker
                            openFolderPicker()
                        },
                        onDismiss = { 
                            showGameDialog = false 
                            isLoadingGames = false
                            selectedEmulator = null
                            detectedGames = emptyList()
                        }
                    )
                }
                
                // Restore backup dialog
                if (showRestoreDialog) {
                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                    RestoreBackupDialog(
                        profileName = selectedProfile?.name ?: "",
                        backups = availableBackups,
                        isRestoring = isRestoring,
                        onRestore = { backupInfo ->
                            if (selectedProfile == null) {
                                showRestoreDialog = false
                                return@RestoreBackupDialog
                            }
                            
                            isRestoring = true
                            
                            coroutineScope.launch {
                                val result = backupManager.performRestore(
                                    profile = selectedProfile,
                                    backupInfo = backupInfo
                                )
                                
                                Toast.makeText(
                                    this@MainActivity,
                                    result.message,
                                    if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                ).show()
                                
                                isRestoring = false
                                
                                if (result.success) {
                                    showRestoreDialog = false
                                }
                            }
                        },
                        onDelete = { backupInfo ->
                            val deleted = backupManager.deleteBackup(backupInfo)
                            if (deleted) {
                                // Refresh backup list
                                selectedProfile?.let { profile ->
                                    availableBackups = backupManager.listBackups(profile)
                                    
                                    // Update profile stats
                                    val (count, lastDate) = backupManager.getBackupStats(profile)
                                    profiles = profiles.map { p ->
                                        if (p.id == profile.id) {
                                            p.copy(
                                                backupCount = count,
                                                lastBackup = lastDate?.let {
                                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(it)
                                                }
                                            )
                                        } else p
                                    }
                                }
                                
                                Toast.makeText(
                                    this@MainActivity,
                                    "Backup deleted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to delete backup",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDismiss = {
                            if (!isRestoring) {
                                showRestoreDialog = false
                            }
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Open the SAF folder picker
     */
    private fun openFolderPicker() {
        try {
            // Try to start in Android/data folder
            val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            folderPickerLauncher.launch(initialUri)
        } catch (e: Exception) {
            Log.e("SaveState", "Error opening folder picker: ${e.message}")
            folderPickerLauncher.launch(null)
        }
    }
    
    /**
     * Scan a folder selected via SAF
     */
    private fun scanSafFolder(uri: Uri) {
        Thread {
            try {
                val documentFile = DocumentFile.fromTreeUri(this, uri)
                if (documentFile == null || !documentFile.isDirectory) {
                    Log.e("SaveState", "Selected item is not a directory")
                    runOnUiThread {
                        Toast.makeText(this, "Please select a valid folder", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }
                
                Log.d("SaveState", "Scanning SAF folder: ${documentFile.name}")
                
                // List all items in the folder
                val children = documentFile.listFiles()
                Log.d("SaveState", "Found ${children.size} items in SAVEDATA folder")
                
                // Group games by base ID
                val gamesMap = mutableMapOf<String, DetectedGame>()
                
                for (child in children) {
                    if (!child.isDirectory) continue
                    
                    val folderName = child.name ?: continue
                    
                    // Check if it matches PSP save folder pattern
                    var baseGameId: String? = null
                    
                    if (folderName.endsWith("DATA00")) {
                        baseGameId = folderName.dropLast(6)
                    } else if (folderName.endsWith("PROFILE00")) {
                        baseGameId = folderName.dropLast(9)
                    } else if (folderName.matches(Regex("^[A-Z]{4}\\d{5}.*$"))) {
                        baseGameId = folderName.take(9)
                    }
                    
                    if (baseGameId == null) {
                        continue
                    }
                    
                    Log.d("SaveState", "  Found game ID: $baseGameId")
                    
                    // Skip if we already have this game
                    if (gamesMap.containsKey(baseGameId)) {
                        Log.d("SaveState", "  Already have this game, skipping duplicate")
                        continue
                    }
                    
                    // Try to get game name from PARAM.SFO
                    var gameName: String? = null
                    val sfoFile = child.findFile("PARAM.SFO")
                    if (sfoFile != null && sfoFile.isFile) {
                        try {
                            contentResolver.openInputStream(sfoFile.uri)?.use { inputStream ->
                                val data = inputStream.readBytes()
                                gameName = parseSfoFromBytes(data)
                            }
                        } catch (e: Exception) {
                            Log.w("SaveState", "Error parsing SFO for $folderName: ${e.message}")
                        }
                    }
                    
                    // Fallback to database or ID
                    if (gameName == null) {
                        gameName = GameScanner.getGameNameFromDatabase(baseGameId) ?: baseGameId
                    }
                    
                    // Count files
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
                        emulatorType = currentEmulator?.emulatorType ?: Emulator.PPSSPP,
                        saveCount = saveCount,
                        lastModified = child.lastModified()
                    )
                }
                
                val games = gamesMap.values.toList()
                Log.d("SaveState", "SAF scan complete. Found ${games.size} unique games")
                
                runOnUiThread {
                    onGamesDetected?.invoke(games)
                }
                
            } catch (e: Exception) {
                Log.e("SaveState", "Error scanning SAF folder: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error scanning folder: ${e.message}", Toast.LENGTH_LONG).show()
                    onGamesDetected?.invoke(emptyList())
                }
            }
        }.start()
    }
    
    /**
     * Parse PARAM.SFO from bytes to extract title
     */
    private fun parseSfoFromBytes(data: ByteArray): String? {
        try {
            // Validate magic: \x00PSF
            if (data.size < 20) return null
            if (data[0] != 0x00.toByte() || data[1] != 0x50.toByte() || 
                data[2] != 0x53.toByte() || data[3] != 0x46.toByte()) {
                return null
            }
            
            // Parse header (little endian)
            fun readInt(offset: Int): Int {
                return (data[offset].toInt() and 0xFF) or
                       ((data[offset + 1].toInt() and 0xFF) shl 8) or
                       ((data[offset + 2].toInt() and 0xFF) shl 16) or
                       ((data[offset + 3].toInt() and 0xFF) shl 24)
            }
            
            fun readShort(offset: Int): Int {
                return (data[offset].toInt() and 0xFF) or
                       ((data[offset + 1].toInt() and 0xFF) shl 8)
            }
            
            val keyTableStart = readInt(8)
            val dataTableStart = readInt(12)
            val numEntries = readInt(16)
            
            // Parse index table
            for (i in 0 until numEntries) {
                val entryOffset = 20 + (i * 16)
                if (entryOffset + 16 > data.size) break
                
                val keyOffset = readShort(entryOffset)
                val dataLen = readInt(entryOffset + 4)
                val dataOffset = readInt(entryOffset + 12)
                
                // Read key
                val keyStart = keyTableStart + keyOffset
                var keyEnd = keyStart
                while (keyEnd < data.size && data[keyEnd] != 0.toByte()) {
                    keyEnd++
                }
                val key = String(data, keyStart, keyEnd - keyStart, Charsets.UTF_8)
                
                if (key == "TITLE") {
                    val valueStart = dataTableStart + dataOffset
                    val valueEnd = minOf(valueStart + dataLen, data.size)
                    val rawValue = data.sliceArray(valueStart until valueEnd)
                    
                    val nullPos = rawValue.indexOf(0.toByte())
                    return if (nullPos != -1) {
                        String(rawValue, 0, nullPos, Charsets.UTF_8).trim()
                    } else {
                        String(rawValue, Charsets.UTF_8).trim()
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e("SaveState", "Error parsing SFO bytes: ${e.message}")
            return null
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if returning from permission settings
        if (hasStoragePermission()) {
            onStoragePermissionGranted?.invoke()
            onStoragePermissionGranted = null
        }
    }
    
    /**
     * Check if we have the required storage permissions
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE for /Android/data access
            Environment.isExternalStorageManager()
        } else {
            // Older versions can use regular storage permissions
            true
        }
    }
    
    /**
     * Request storage permissions
     */
    private fun requestStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - need to send user to settings
            Toast.makeText(
                this,
                "SaveState needs storage access to find your save files. Please enable 'All files access' in the next screen.",
                Toast.LENGTH_LONG
            ).show()
            
            onStoragePermissionGranted = onGranted
            
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            // Older Android - just proceed
            onGranted()
        }
    }
}
