package com.savestate.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.savestate.app.data.model.EmulatorConfig
import com.savestate.app.data.model.EmulatorInfo

/**
 * Detects installed emulators on the Android device
 * Uses PackageManager to query installed applications
 */
class EmulatorDetector(private val context: Context) {
    
    private val packageManager: PackageManager = context.packageManager
    
    /**
     * Detect all known emulators installed on the device
     * @return List of EmulatorInfo for installed emulators
     */
    fun detectInstalledEmulators(): List<EmulatorInfo> {
        val installedEmulators = mutableListOf<EmulatorInfo>()
        
        for (definition in EmulatorConfig.knownEmulators) {
            // Check each possible package name for this emulator
            for (packageName in definition.packageNames) {
                try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(packageName, 0)
                    }
                    
                    // Get the app label (display name)
                    val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                    
                    // Get the app icon
                    val appIcon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }
                    
                    installedEmulators.add(
                        EmulatorInfo(
                            packageName = packageName,
                            displayName = appLabel,
                            emulatorType = definition.emulatorType,
                            icon = appIcon,
                            isInstalled = true,
                            defaultSavePaths = definition.defaultSavePaths
                        )
                    )
                    
                    // Found this emulator, don't check other package names
                    break
                    
                } catch (e: PackageManager.NameNotFoundException) {
                    // This package is not installed, continue checking
                }
            }
        }
        
        return installedEmulators.sortedBy { it.displayName }
    }
    
    /**
     * Check if a specific emulator package is installed
     */
    fun isEmulatorInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Get all installed applications that could potentially be emulators
     * This is a more aggressive search that returns any app containing "emu" in the name
     */
    fun detectPotentialEmulators(): List<EmulatorInfo> {
        val potentialEmulators = mutableListOf<EmulatorInfo>()
        
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }
        
        for (appInfo in installedApps) {
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()
            val packageName = appInfo.packageName
            
            // Check if it's a known emulator
            val knownDefinition = EmulatorConfig.findByPackageName(packageName)
            
            if (knownDefinition != null) {
                val appIcon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }
                
                potentialEmulators.add(
                    EmulatorInfo(
                        packageName = packageName,
                        displayName = appLabel,
                        emulatorType = knownDefinition.emulatorType,
                        icon = appIcon,
                        isInstalled = true,
                        defaultSavePaths = knownDefinition.defaultSavePaths
                    )
                )
            }
        }
        
        return potentialEmulators.sortedBy { it.displayName }
    }
}
