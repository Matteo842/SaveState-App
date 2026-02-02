package com.savestate.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.savestate.app.data.EmulatorDetector
import com.savestate.app.data.model.EmulatorInfo
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.dialogs.SelectEmulatorDialog
import com.savestate.app.ui.screens.MainScreen
import com.savestate.app.ui.theme.SaveStateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    
    private lateinit var emulatorDetector: EmulatorDetector
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize emulator detector
        emulatorDetector = EmulatorDetector(applicationContext)
        
        setContent {
            SaveStateTheme {
                // State for emulator selection dialog
                var showEmulatorDialog by remember { mutableStateOf(false) }
                var isLoadingEmulators by remember { mutableStateOf(false) }
                var installedEmulators by remember { mutableStateOf<List<EmulatorInfo>>(emptyList()) }
                var selectedEmulator by remember { mutableStateOf<EmulatorInfo?>(null) }
                
                // Coroutine scope for background operations
                val coroutineScope = rememberCoroutineScope()
                
                // Demo data for preview - will be replaced with actual data later
                var selectedProfileId by remember { mutableStateOf<String?>(null) }
                var isDarkTheme by remember { mutableStateOf(true) }
                
                val demoProfiles = remember {
                    listOf(
                        GameProfile(
                            id = "1",
                            name = "Luigi's Mansion™",
                            emulator = "Citra",
                            savePath = "/storage/emulated/0/Citra/states/Luigi's Mansion",
                            backupCount = 3,
                            lastBackup = "14/05/2025 18:52",
                            isFavorite = true
                        ),
                        GameProfile(
                            id = "2",
                            name = "SUPER MARIO GALAXY",
                            emulator = "Dolphin",
                            savePath = "/storage/emulated/0/Dolphin/Wii/title",
                            backupCount = 3,
                            lastBackup = "12/01/2026 07:15",
                            isFavorite = true
                        ),
                        GameProfile(
                            id = "3",
                            name = "Crash Bandicoot",
                            emulator = "DuckStation",
                            savePath = "/storage/emulated/0/DuckStation/memcards",
                            backupCount = 1,
                            lastBackup = "13/01/2026 13:52",
                            isFavorite = true
                        ),
                        GameProfile(
                            id = "4",
                            name = "Super Mario Odyssey™",
                            emulator = "Eden",
                            savePath = "/storage/emulated/0/Eden/nand/user/save",
                            backupCount = 1,
                            lastBackup = "13/01/2026 13:52",
                            isFavorite = false
                        ),
                        GameProfile(
                            id = "5",
                            name = "SONICADV_INT",
                            emulator = "Flycast",
                            savePath = "/storage/emulated/0/Flycast/data",
                            backupCount = 1,
                            lastBackup = "10/05/2025 01:50",
                            isFavorite = false
                        ),
                        GameProfile(
                            id = "6",
                            name = "PAPER MARIO",
                            emulator = "Gopher64",
                            savePath = "/storage/emulated/0/Gopher64/saves",
                            backupCount = 0,
                            lastBackup = null,
                            isFavorite = false
                        ),
                        GameProfile(
                            id = "7",
                            name = "Hytale",
                            emulator = "Other",
                            savePath = "/storage/emulated/0/Android/data/com.hytale",
                            backupCount = 0,
                            lastBackup = null,
                            isFavorite = false
                        )
                    )
                }
                
                var profiles by remember { mutableStateOf(demoProfiles) }
                
                MainScreen(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelect = { id -> selectedProfileId = id },
                    onFavoriteToggle = { id ->
                        profiles = profiles.map { 
                            if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it 
                        }
                    },
                    onDeleteProfile = { id ->
                        profiles = profiles.filter { it.id != id }
                        if (selectedProfileId == id) selectedProfileId = null
                    },
                    onBackupClick = { /* TODO: Implement backup */ },
                    onRestoreClick = { /* TODO: Implement restore */ },
                    onManageBackupsClick = { /* TODO: Show manage backups dialog */ },
                    onNewProfileClick = { 
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
                    onSettingsClick = { /* TODO: Navigate to settings */ },
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    isDarkTheme = isDarkTheme,
                    appVersion = "1.0"
                )
                
                // Emulator selection dialog
                if (showEmulatorDialog) {
                    SelectEmulatorDialog(
                        installedEmulators = installedEmulators,
                        isLoading = isLoadingEmulators,
                        onEmulatorSelected = { emulator ->
                            selectedEmulator = emulator
                            showEmulatorDialog = false
                            
                            // Create a new profile with the selected emulator
                            val newProfile = GameProfile(
                                id = UUID.randomUUID().toString(),
                                name = "New ${emulator.emulatorType.displayName} Game",
                                emulator = emulator.emulatorType.displayName,
                                savePath = emulator.defaultSavePaths.firstOrNull() ?: "",
                                backupCount = 0,
                                lastBackup = null,
                                isFavorite = false
                            )
                            
                            profiles = profiles + newProfile
                            selectedProfileId = newProfile.id
                            
                            Log.d("SaveState", "Created new profile for ${emulator.displayName}")
                            
                            // TODO: Show profile editor dialog to set game name and custom path
                        },
                        onDismiss = { 
                            showEmulatorDialog = false 
                            isLoadingEmulators = false
                        }
                    )
                }
            }
        }
    }
}
