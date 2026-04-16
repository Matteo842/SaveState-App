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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.BackupDirectoryInfo
import com.savestate.app.data.BackupInfo
import com.savestate.app.data.BackupManager
import com.savestate.app.data.ConfigManager
import com.savestate.app.data.EmulatorDetector
import com.savestate.app.data.AzaharManager
import com.savestate.app.data.DolphinManager
import com.savestate.app.data.DraSticManager
import com.savestate.app.data.DuckStationManager
import com.savestate.app.data.EdenManager
import com.savestate.app.data.M64PlusFZManager
import com.savestate.app.data.NetherSX2Manager
import com.savestate.app.data.PPSSPPManager
import com.savestate.app.data.RetroArchManager
import com.savestate.app.data.RootAccessHelper
import com.savestate.app.data.ProfileRepository
import com.savestate.app.data.SettingsManager
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.dialogs.RestoreBackupDialog
import com.savestate.app.ui.dialogs.ManageBackupsDialog
import com.savestate.app.ui.dialogs.SelectEmulatorDialog
import com.savestate.app.ui.dialogs.SelectGameDialog
import com.savestate.app.ui.screens.MainScreen
import com.savestate.app.ui.screens.SettingsScreen
import com.savestate.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// App version from build.gradle.kts - single source of truth
private val APP_VERSION = BuildConfig.VERSION_NAME

class MainActivity : ComponentActivity() {
    
    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var ppssppManager: PPSSPPManager
    private lateinit var retroArchManager: RetroArchManager
    private lateinit var azaharManager: AzaharManager
    private lateinit var dolphinManager: DolphinManager
    private lateinit var draSticManager: DraSticManager
    private lateinit var duckStationManager: DuckStationManager
    private lateinit var edenManager: EdenManager
    private lateinit var m64PlusFZManager: M64PlusFZManager
    private lateinit var netherSX2Manager: NetherSX2Manager
    private lateinit var rootAccessHelper: RootAccessHelper
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
        ppssppManager = PPSSPPManager()
        retroArchManager = RetroArchManager()
        azaharManager = AzaharManager()
        dolphinManager = DolphinManager()
        draSticManager = DraSticManager()
        duckStationManager = DuckStationManager()
        edenManager = EdenManager()
        m64PlusFZManager = M64PlusFZManager()
        netherSX2Manager = NetherSX2Manager()
        rootAccessHelper = RootAccessHelper()
        configManager = ConfigManager(applicationContext)
        settingsManager = SettingsManager(applicationContext, configManager)
        backupManager = BackupManager(applicationContext, settingsManager, rootAccessHelper)
        profileRepository = ProfileRepository(applicationContext, configManager)
        
        // Initialize PSP game database from assets
        PPSSPPManager.initDatabase(applicationContext)

        // Initialize Switch game database from assets (Eden)
        EdenManager.initDatabase(applicationContext)
        
        // Request storage permission at startup (like WiFi FTP Server)
        if (!hasStoragePermission()) {
            requestStoragePermission {
                // Permission granted, continue normally
                Log.d("SaveState", "Storage permission granted at startup")
            }
        }
        
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
                var maxBackupsPerProfile by remember { mutableStateOf(settingsManager.getMaxBackups()) }
                var maxSourceSizeMB by remember { mutableStateOf(settingsManager.getMaxSourceSizeMB()) }
                var compressionLevel by remember { mutableStateOf(settingsManager.getCompressionLevel()) }
                var isRootModeEnabled by remember { mutableStateOf(settingsManager.isRootModeEnabled()) }
                
                // Backup operation state
                var isBackingUp by remember { mutableStateOf(false) }
                var backupErrorMessage by remember { mutableStateOf<String?>(null) }
                
                // Restore dialog state
                var showRestoreDialog by remember { mutableStateOf(false) }
                var showManageDialog by remember { mutableStateOf(false) }
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
                
                // Function to open folder picker or start root scan
                fun openFolderPickerForEmulator(emulator: EmulatorInfo) {
                    showGameDialog = true
                    currentEmulator = emulator
                    detectedGames = emptyList()
                    
                    if (emulator.requiresRoot && isRootModeEnabled) {
                        // Root mode: auto-scan known protected paths
                        isLoadingGames = true
                        coroutineScope.launch {
                            val games = withContext(Dispatchers.IO) {
                                scanRootEmulatorPaths(emulator)
                            }
                            detectedGames = games
                            isLoadingGames = false
                        }
                    } else {
                        // Normal SAF mode: user must select folder
                        isLoadingGames = false
                    }
                }
                
                // Navigation: Show either MainScreen or SettingsScreen
                if (showSettingsScreen) {
                    SettingsScreen(
                        currentBackupPath = currentBackupPath,
                        backupInfo = backupInfo,
                        isMigrating = isMigrating,
                        migrationProgress = migrationProgress,
                        maxBackupsPerProfile = maxBackupsPerProfile,
                        onMaxBackupsChange = { newValue ->
                            maxBackupsPerProfile = newValue
                            settingsManager.setMaxBackups(newValue)
                        },
                        maxSourceSizeMB = maxSourceSizeMB,
                        onMaxSourceSizeChange = { newValue ->
                            maxSourceSizeMB = newValue
                            settingsManager.setMaxSourceSizeMB(newValue)
                        },
                        compressionLevel = compressionLevel,
                        onCompressionLevelChange = { newValue ->
                            compressionLevel = newValue
                            settingsManager.setCompressionLevel(newValue)
                        },
                        isRootModeEnabled = isRootModeEnabled,
                        onRootModeChange = { enabled ->
                            if (enabled) {
                                coroutineScope.launch {
                                    val hasRoot = withContext(Dispatchers.IO) {
                                        rootAccessHelper.isRootAvailable()
                                    }
                                    if (hasRoot) {
                                        isRootModeEnabled = true
                                        settingsManager.setRootModeEnabled(true)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Root access granted!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Root access denied. Is your device rooted?",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } else {
                                isRootModeEnabled = false
                                settingsManager.setRootModeEnabled(false)
                            }
                        },
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
                        appVersion = APP_VERSION
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
                                    maxBackups = maxBackupsPerProfile,
                                    maxSourceSizeMB = maxSourceSizeMB,
                                    compressionLevel = compressionLevel
                                )
                                
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
                                    
                                    Toast.makeText(
                                        this@MainActivity,
                                        result.message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    backupErrorMessage = result.message
                                }
                                
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
                            // Open manage backups dialog
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
                            showManageDialog = true
                        },
                        onNewProfileClick = { 
                            // Start scanning for emulators (permission already granted at startup)
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
                        appVersion = APP_VERSION
                    )
                }
                
                // Emulator selection dialog
                if (showEmulatorDialog) {
                    SelectEmulatorDialog(
                        installedEmulators = installedEmulators,
                        isLoading = isLoadingEmulators,
                        isRootModeEnabled = isRootModeEnabled,
                        onEmulatorSelected = { emulator ->
                            selectedEmulator = emulator
                            showEmulatorDialog = false
                            
                            // Open folder picker directly - no automatic scanning
                            openFolderPickerForEmulator(emulator)
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
                        isRootMode = selectedEmulator!!.requiresRoot && isRootModeEnabled,
                        onGameSelected = { game ->
                            showGameDialog = false
                            
                            // File-prefix backup for emulators that store saves as flat files
                            val filePrefix = game.gameFilePrefix
                            
                            // Create a new profile with the selected game
                            val isRoot = selectedEmulator!!.requiresRoot && isRootModeEnabled
                            val newProfile = GameProfile(
                                id = UUID.randomUUID().toString(),
                                name = game.gameName,
                                emulator = selectedEmulator!!.emulatorType.displayName,
                                savePath = game.savePath,
                                parentPath = game.parentPath,
                                backupCount = 0,
                                lastBackup = null,
                                isFavorite = false,
                                gameFilePrefix = filePrefix,
                                requiresRoot = isRoot
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
                        onDismiss = {
                            if (!isRestoring) {
                                showRestoreDialog = false
                            }
                        }
                    )
                }
                
                // Manage backups dialog
                if (showManageDialog) {
                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                    ManageBackupsDialog(
                        profileName = selectedProfile?.name ?: "",
                        backups = availableBackups,
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
                            showManageDialog = false
                        }
                    )
                }
                
                // Backup error dialog
                if (backupErrorMessage != null) {
                    val profileName = profiles.find { it.id == selectedProfileId }?.name ?: "Unknown"
                    AlertDialog(
                        onDismissRequest = { backupErrorMessage = null },
                        containerColor = DarkSurface,
                        titleContentColor = SaveStateRed,
                        textContentColor = TextPrimary,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SaveStateRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Backup Failed",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            Column {
                                Text(
                                    text = "Profile: $profileName",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = backupErrorMessage ?: "",
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "The source save folder may have been moved, deleted, or permissions were revoked. Try re-selecting the emulator folder and re-adding the profile.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { backupErrorMessage = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = SaveStateRed)
                            ) {
                                Text("OK")
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
                
                val emulatorType = currentEmulator?.emulatorType
                val games = when (emulatorType) {
                    Emulator.RETROARCH -> retroArchManager.scanSafFolder(documentFile)
                    Emulator.DOLPHIN -> dolphinManager.scanSafFolder(documentFile)
                    Emulator.AZAHAR -> azaharManager.scanSafFolder(documentFile)
                    Emulator.DRASTIC -> draSticManager.scanSafFolder(documentFile)
                    Emulator.DUCKSTATION -> duckStationManager.scanSafFolder(documentFile)
                    Emulator.M64PLUS_FZ -> m64PlusFZManager.scanSafFolder(documentFile)
                    Emulator.NETHERSX2 -> netherSX2Manager.scanSafFolder(documentFile)
                    Emulator.EDEN -> edenManager.scanSafFolder(documentFile)
                    else -> ppssppManager.scanSafFolder(
                        documentFile, contentResolver,
                        emulatorType ?: Emulator.PPSSPP
                    )
                }
                
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
     * Scan root-protected save paths for a given emulator via su.
     * Called when root mode is enabled and a root-only emulator is selected.
     */
    private fun scanRootEmulatorPaths(emulator: EmulatorInfo): List<DetectedGame> {
        val rootPaths = emulator.rootSavePaths
        if (rootPaths.isEmpty()) return emptyList()
        
        return when (emulator.emulatorType) {
            Emulator.DOLPHIN -> dolphinManager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.AZAHAR -> azaharManager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.DRASTIC -> draSticManager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.DUCKSTATION -> duckStationManager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.M64PLUS_FZ -> m64PlusFZManager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.NETHERSX2 -> netherSX2Manager.scanRootPaths(rootAccessHelper, rootPaths)
            Emulator.EDEN -> edenManager.scanRootPaths(rootAccessHelper, rootPaths)
            else -> emptyList()
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
                "SaveState needs storage access to manage your save files. Please enable 'All files access' permission.",
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
