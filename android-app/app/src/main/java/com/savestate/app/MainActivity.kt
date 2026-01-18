package com.savestate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.savestate.app.data.model.GameProfile
import com.savestate.app.ui.screens.MainScreen
import com.savestate.app.ui.theme.SaveStateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            SaveStateTheme {
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
                    onNewProfileClick = { /* TODO: Show new profile dialog */ },
                    onSettingsClick = { /* TODO: Navigate to settings */ },
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    isDarkTheme = isDarkTheme,
                    appVersion = "1.0"
                )
            }
        }
    }
}
