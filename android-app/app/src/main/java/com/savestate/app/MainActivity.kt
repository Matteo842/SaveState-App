package com.savestate.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.savestate.app.controller.GamepadManager
import com.savestate.app.controller.GamepadAction

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
import com.savestate.app.data.TutorialPreferences
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.data.model.GameProfile
import com.savestate.app.security.LicenseGate
import com.savestate.app.security.LicenseGuard
import com.savestate.app.security.LicenseGuardLoader
import com.savestate.app.security.LicenseStatus
import com.savestate.app.security.ui.LicenseBlockScreen
import com.savestate.app.security.ui.LicenseCheckSplash
import com.savestate.app.ui.dialogs.RestoreBackupDialog
import com.savestate.app.ui.dialogs.ManageBackupsDialog
import com.savestate.app.ui.dialogs.SelectEmulatorDialog
import com.savestate.app.ui.dialogs.SelectGameDialog
import com.savestate.app.ui.screens.ControllerSettingsScreen
import com.savestate.app.ui.screens.EditProfileScreen
import com.savestate.app.ui.screens.MainScreen
import com.savestate.app.ui.screens.SettingsScreen
import com.savestate.app.ui.theme.*
import com.savestate.app.ui.tutorial.TutorialCallbacks
import com.savestate.app.ui.tutorial.TutorialOverlay
import com.savestate.app.ui.tutorial.TutorialScreen
import com.savestate.app.ui.tutorial.TutorialState
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
    private lateinit var tutorialPrefs: TutorialPreferences
    private lateinit var licenseGuard: LicenseGuard
    private lateinit var gamepadManager: GamepadManager
    
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
        tutorialPrefs = TutorialPreferences(applicationContext)
        licenseGuard = LicenseGuardLoader.load(applicationContext)
        gamepadManager = GamepadManager(applicationContext)
        val savedMappings = settingsManager.getControllerMappings()
        gamepadManager.updateMappings(savedMappings)


        // Kick off license verification once per process. Subsequent
        // Activity recreations (rotation, theme/locale changes) reuse the
        // cached result via [LicenseGate] instead of re-running verify and
        // flashing the splash screen.
        LicenseGate.ensureStarted(licenseGuard)

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
        val initialDarkTheme = settingsManager.isDarkThemeEnabled()

        setContent {
            // Theme state: persisted via SettingsManager so the choice
            // survives process death and matches whatever the user last set.
            var isDarkTheme by remember { mutableStateOf(initialDarkTheme) }

            SaveStateTheme(darkTheme = isDarkTheme) {
                // License gate — backed by a process-level cache
                // ([LicenseGate]) so configuration changes (e.g. rotation)
                // do NOT re-trigger verify() and do NOT flash the splash.
                // The verify call itself was kicked off in onCreate via
                // [LicenseGate.ensureStarted]; here we only render based
                // on the latest emitted status.
                val licenseStatus by LicenseGate.status.collectAsState()

                when (val status = licenseStatus) {
                    null -> {
                        LicenseCheckSplash()
                        return@SaveStateTheme
                    }
                    is LicenseStatus.Blocked -> {
                        LicenseBlockScreen(
                            reason = status.reason,
                            message = status.message,
                            onOpenPlayStore = { openPlayStore() },
                            onRetry = { LicenseGate.retry(licenseGuard) }
                        )
                        return@SaveStateTheme
                    }
                    LicenseStatus.Ok -> Unit
                }

                // State for controller settings screen
                var showControllerSettingsScreen by remember { mutableStateOf(false) }

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
                var profiles by remember { mutableStateOf(savedProfiles) }
                
                // Navigation state
                var showSettingsScreen by remember { mutableStateOf(false) }
                var editingProfileId by remember { mutableStateOf<String?>(null) }
                
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
                var profileToDeleteConfirm by remember { mutableStateOf<GameProfile?>(null) }

                // Observe controller manager states
                val controllerMode by gamepadManager.controllerMode.collectAsState()
                val focusedProfileIndex by gamepadManager.focusedProfileIndex.collectAsState()
                val controllerConnected by gamepadManager.controllerConnected.collectAsState()

                // Stable sorted profiles list for consistent gamepad navigation matching the UI display
                val displayProfiles = remember(profiles) {
                    profiles.sortedWith(compareByDescending { it.isFavorite })
                }

                LaunchedEffect(displayProfiles.size) {
                    gamepadManager.setProfileCount(displayProfiles.size)
                }

                LaunchedEffect(focusedProfileIndex, controllerMode) {
                    if (controllerMode && focusedProfileIndex in displayProfiles.indices) {
                        selectedProfileId = displayProfiles[focusedProfileIndex].id
                    }
                }

                LaunchedEffect(showEmulatorDialog, showGameDialog, showRestoreDialog, showManageDialog, backupErrorMessage, profileToDeleteConfirm) {
                    gamepadManager.dialogOpen = showEmulatorDialog || showGameDialog || showRestoreDialog || showManageDialog || (backupErrorMessage != null) || (profileToDeleteConfirm != null)
                }


                // Define actions as reusable lambdas so they can be triggered via both UI buttons and gamepad actions
                val performBackupAction = {
                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                    if (selectedProfile == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please select a profile first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (isBackingUp) {
                        Toast.makeText(
                            this@MainActivity,
                            "Backup already in progress...",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
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
                    }
                }

                val performRestoreAction = {
                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                    if (selectedProfile == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please select a profile first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        availableBackups = backupManager.listBackups(selectedProfile)
                        showRestoreDialog = true
                    }
                }

                val performManageBackupsAction = {
                    val selectedProfile = profiles.find { it.id == selectedProfileId }
                    if (selectedProfile == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please select a profile first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        availableBackups = backupManager.listBackups(selectedProfile)
                        showManageDialog = true
                    }
                }

                val performNewProfileAction = {
                    showEmulatorDialog = true
                    isLoadingEmulators = true
                    
                    coroutineScope.launch {
                        delay(300)
                        val detected = withContext(Dispatchers.IO) {
                            emulatorDetector.detectInstalledEmulators()
                        }
                        installedEmulators = detected
                        isLoadingEmulators = false
                        Log.d("SaveState", "Detected ${detected.size} emulators: ${detected.map { it.displayName }}")
                    }
                }

                val performDeleteAction = {
                    selectedProfileId?.let { id ->
                        val profileToDelete = profiles.find { it.id == id }
                        if (profileToDelete != null) {
                            profileToDeleteConfirm = profileToDelete
                        }
                    }
                }

                LaunchedEffect(displayProfiles, selectedProfileId, isBackingUp) {
                    gamepadManager.setActionCallback { action ->
                        when (action) {
                            GamepadAction.BACKUP -> performBackupAction()
                            GamepadAction.RESTORE -> performRestoreAction()
                            GamepadAction.MANAGE_BACKUPS -> performManageBackupsAction()
                            GamepadAction.DELETE -> performDeleteAction()
                            GamepadAction.NEW_PROFILE -> performNewProfileAction()
                            GamepadAction.SETTINGS -> { showSettingsScreen = true }
                            else -> {}
                        }
                    }
                }

                
                // Load backup info on startup
                LaunchedEffect(currentBackupPath) {
                    backupInfo = withContext(Dispatchers.IO) {
                        settingsManager.getBackupDirectoryInfo()
                    }
                }
                
                // Start the first-launch tutorial before any other nav side-effect
                // so it can drive the user into Settings itself.
                LaunchedEffect(Unit) {
                    if (!tutorialPrefs.hasCompletedTutorial() && !TutorialState.isActive) {
                        TutorialState.start(tutorialPrefs.getLastStep())
                    }
                }

                // Persist the current tutorial step so process-death mid-tour can
                // resume roughly where the user was.
                LaunchedEffect(TutorialState.currentStepIndex, TutorialState.isActive) {
                    if (TutorialState.isActive) {
                        tutorialPrefs.setLastStep(TutorialState.currentStepIndex)
                    }
                }

                // Check if backup folder is configured on startup. Suppress this
                // auto-redirect while the tutorial is running — the tutorial will
                // walk the user into Settings itself.
                LaunchedEffect(Unit) {
                    if (!configManager.isConfigured() && !TutorialState.isActive &&
                        tutorialPrefs.hasCompletedTutorial()
                    ) {
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
                
                // Navigation: Show either MainScreen, SettingsScreen, or
                // EditProfileScreen. Wrapped in a Box so the tutorial overlay
                // can draw on top.
                val editingProfile = profiles.find { it.id == editingProfileId }
                Box(modifier = Modifier.fillMaxSize()) {
                if (editingProfile != null) {
                    EditProfileScreen(
                        profile = editingProfile,
                        onSave = { updated ->
                            profiles = profiles.map { p ->
                                if (p.id == updated.id) updated else p
                            }
                            profileRepository.saveProfiles(profiles)
                            editingProfileId = null
                            Toast.makeText(
                                this@MainActivity,
                                "Profile updated",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onCancel = { editingProfileId = null },
                        appVersion = APP_VERSION
                    )
                } else if (showSettingsScreen) {
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
                } else if (showControllerSettingsScreen) {
                    ControllerSettingsScreen(
                        initialMappings = settingsManager.getControllerMappings(),
                        controllerConnected = controllerConnected,
                        onSave = { newMappings ->
                            settingsManager.setControllerMappings(newMappings)
                            gamepadManager.updateMappings(newMappings)
                            showControllerSettingsScreen = false
                            Toast.makeText(
                                this@MainActivity,
                                "Controller settings saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onBackClick = { showControllerSettingsScreen = false },
                        appVersion = APP_VERSION
                    )
                } else {
                    MainScreen(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelect = { id -> 
                            selectedProfileId = id
                            // Sync focus index back to gamepad manager if user taps physically
                            val index = displayProfiles.indexOfFirst { it.id == id }
                            if (index != -1) {
                                gamepadManager.setProfileCount(displayProfiles.size)
                                // We don't want to trigger a LaunchedEffect loop, but since GamepadManager will only set if different:
                                // gamepadManager.onKeyEvent/onTouchEvent will naturally handle it
                            }
                        },
                        onFavoriteToggle = { id ->
                            profiles = profiles.map { 
                                if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it 
                            }
                            profileRepository.saveProfiles(profiles)
                        },
                        onDeleteProfile = { id ->
                            val profileToDelete = profiles.find { it.id == id }
                            if (profileToDelete != null) {
                                profileToDeleteConfirm = profileToDelete
                            }
                        },
                        onEditProfile = { id ->
                            editingProfileId = id
                        },
                        onBackupClick = {
                            performBackupAction()
                        },
                        onRestoreClick = {
                            performRestoreAction()
                        },
                        onManageBackupsClick = {
                            performManageBackupsAction()
                        },
                        onNewProfileClick = { 
                            performNewProfileAction()
                        },
                        onSettingsClick = { showSettingsScreen = true },
                        onThemeToggle = {
                            isDarkTheme = !isDarkTheme
                            settingsManager.setDarkThemeEnabled(isDarkTheme)
                        },
                        onControllerClick = { showControllerSettingsScreen = true },
                        isDarkTheme = isDarkTheme,
                        appVersion = APP_VERSION,
                        controllerMode = controllerMode,
                        focusedProfileIndex = focusedProfileIndex,
                        controllerConnected = controllerConnected
                    )
                }

                // First-launch tutorial overlay — always last sibling so it
                // draws on top of whichever screen is currently visible.
                val tutorialCallbacks = remember {
                    TutorialCallbacks(
                        openSettings = { showSettingsScreen = true },
                        closeSettings = { showSettingsScreen = false },
                        finish = {
                            tutorialPrefs.setCompleted()
                            TutorialState.finish()
                        }
                    )
                }
                TutorialOverlay(
                    currentScreen = if (showSettingsScreen) TutorialScreen.SETTINGS else TutorialScreen.MAIN,
                    hasProfiles = profiles.isNotEmpty(),
                    callbacks = tutorialCallbacks
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
                            
                            // Determine root vs SAF from the actual save path
                            // returned by the scanner (root scans return plain
                            // filesystem paths, SAF scans return content:// URIs).
                            // This is important for emulators that support both
                            // modes (e.g. Eden), where a manual SAF fallback may
                            // happen even with root mode enabled.
                            val isRoot = !game.savePath.startsWith("content://") &&
                                selectedEmulator!!.requiresRoot &&
                                isRootModeEnabled
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
                        gamepadManager = gamepadManager,
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
                        gamepadManager = gamepadManager,
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

                // Delete profile confirmation dialog
                if (profileToDeleteConfirm != null) {
                    AlertDialog(
                        onDismissRequest = { profileToDeleteConfirm = null },
                        containerColor = DarkSurface,
                        titleContentColor = SaveStateRed,
                        textContentColor = TextPrimary,
                        title = {
                            Text(
                                text = "Delete Profile",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "Are you sure you want to delete the profile \"${profileToDeleteConfirm?.name}\"? This action cannot be undone.",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    profileToDeleteConfirm?.let { profile ->
                                        val id = profile.id
                                        profiles = profiles.filter { it.id != id }
                                        profileRepository.saveProfiles(profiles)
                                        if (selectedProfileId == id) selectedProfileId = null
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Profile \"${profile.name}\" deleted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    profileToDeleteConfirm = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = SaveStateRed)
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { profileToDeleteConfirm = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Open the official Play Store listing for this app, falling back to the
     * web URL if the Play Store app is not installed.
     */
    private fun openPlayStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(market)
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.e("SaveState", "Cannot open Play Store: ${e2.message}")
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
                    Emulator.EDEN, Emulator.YUZU, Emulator.CITRON -> edenManager.scanSafFolder(documentFile, emulatorType)
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
            Emulator.EDEN, Emulator.YUZU, Emulator.CITRON -> edenManager.scanRootPaths(rootAccessHelper, rootPaths, emulator.emulatorType)
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::gamepadManager.isInitialized && gamepadManager.onKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gamepadManager.isInitialized) {
            gamepadManager.onTouchEvent()
        }
        return super.dispatchTouchEvent(ev)
    }
}

