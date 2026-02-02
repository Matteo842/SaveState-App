package com.savestate.app.data.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed emulator detected on the device
 */
data class EmulatorInfo(
    val packageName: String,
    val displayName: String,
    val emulatorType: Emulator,
    val icon: Drawable?,
    val isInstalled: Boolean = true,
    val defaultSavePaths: List<String> = emptyList()
)

/**
 * Configuration for known emulators with their package names and default save paths
 */
object EmulatorConfig {
    
    data class EmulatorDefinition(
        val emulatorType: Emulator,
        val packageNames: List<String>,
        val displayName: String,
        val defaultSavePaths: List<String>
    )
    
    val knownEmulators = listOf(
        // PPSSPP - PSP Emulator (Primary focus)
        EmulatorDefinition(
            emulatorType = Emulator.PPSSPP,
            packageNames = listOf(
                "org.ppsspp.ppsspp",        // Free version
                "org.ppsspp.ppssppgold"     // Gold version
            ),
            displayName = "PPSSPP",
            defaultSavePaths = listOf(
                "/storage/emulated/0/PSP/SAVEDATA",      // Game saves
                "/storage/emulated/0/PSP/PPSSPP_STATE"   // Save states
            )
        ),
        
        // RetroArch - Multi-system emulator
        EmulatorDefinition(
            emulatorType = Emulator.RETROARCH,
            packageNames = listOf(
                "com.retroarch",
                "com.retroarch.aarch64",
                "com.retroarch.ra32"
            ),
            displayName = "RetroArch",
            defaultSavePaths = listOf(
                "/storage/emulated/0/RetroArch/saves",
                "/storage/emulated/0/RetroArch/states"
            )
        ),
        
        // Dolphin - GameCube/Wii emulator
        EmulatorDefinition(
            emulatorType = Emulator.DOLPHIN,
            packageNames = listOf(
                "org.dolphinemu.dolphinemu",
                "org.dolphinemu.dolphinemu.mmjr",
                "org.dolphinemu.dolphinemu.mmjr2"
            ),
            displayName = "Dolphin",
            defaultSavePaths = listOf(
                "/storage/emulated/0/dolphin-emu/Wii/title",
                "/storage/emulated/0/dolphin-emu/GC"
            )
        ),
        
        // DuckStation - PlayStation emulator
        EmulatorDefinition(
            emulatorType = Emulator.DUCKSTATION,
            packageNames = listOf(
                "com.github.stenzek.duckstation"
            ),
            displayName = "DuckStation",
            defaultSavePaths = listOf(
                "/storage/emulated/0/duckstation/memcards",
                "/storage/emulated/0/duckstation/savestates"
            )
        ),
        
        // Citra - Nintendo 3DS emulator
        EmulatorDefinition(
            emulatorType = Emulator.CITRA,
            packageNames = listOf(
                "org.citra.citra_emu",
                "org.citra.citra_emu.canary"
            ),
            displayName = "Citra",
            defaultSavePaths = listOf(
                "/storage/emulated/0/citra-emu/sdmc",
                "/storage/emulated/0/citra-emu/nand"
            )
        ),
        
        // Azahar - 3DS emulator fork
        EmulatorDefinition(
            emulatorType = Emulator.AZAHAR,
            packageNames = listOf(
                "org.azahar.azahar_emu"
            ),
            displayName = "Azahar",
            defaultSavePaths = listOf(
                "/storage/emulated/0/azahar-emu/sdmc"
            )
        ),
        
        // DraStic - Nintendo DS emulator
        EmulatorDefinition(
            emulatorType = Emulator.DRASTIC,
            packageNames = listOf(
                "com.dsemu.drastic"
            ),
            displayName = "DraStic",
            defaultSavePaths = listOf(
                "/storage/emulated/0/DraStic/backup",
                "/storage/emulated/0/DraStic/savestates"
            )
        ),
        
        // Flycast - Dreamcast emulator
        EmulatorDefinition(
            emulatorType = Emulator.FLYCAST,
            packageNames = listOf(
                "com.flycast.emulator",
                "com.reicast.emulator"
            ),
            displayName = "Flycast",
            defaultSavePaths = listOf(
                "/storage/emulated/0/flycast/data"
            )
        ),
        
        // mGBA - GBA emulator
        EmulatorDefinition(
            emulatorType = Emulator.MGBA,
            packageNames = listOf(
                "io.mgba"
            ),
            displayName = "mGBA",
            defaultSavePaths = listOf(
                "/storage/emulated/0/mGBA/saves"
            )
        ),
        
        // Lemuroid - Multi-system emulator based on RetroArch
        EmulatorDefinition(
            emulatorType = Emulator.LEMUROID,
            packageNames = listOf(
                "com.swordfish.lemuroid"
            ),
            displayName = "Lemuroid",
            defaultSavePaths = listOf(
                "/storage/emulated/0/Lemuroid/saves",
                "/storage/emulated/0/Lemuroid/states"
            )
        ),
        
        // Pizza Boy - GBA/GBC emulator
        EmulatorDefinition(
            emulatorType = Emulator.PIZZA_BOY,
            packageNames = listOf(
                "it.dbtecno.pizzaboygba",
                "it.dbtecno.pizzaboygbapro",
                "it.dbtecno.pizzaboygbc",
                "it.dbtecno.pizzaboygbcpro"
            ),
            displayName = "Pizza Boy",
            defaultSavePaths = listOf(
                "/storage/emulated/0/PizzaBoy/saves"
            )
        ),
        
        // AetherSX2 - PlayStation 2 emulator
        EmulatorDefinition(
            emulatorType = Emulator.AETHERSX2,
            packageNames = listOf(
                "xyz.aethersx2.android"
            ),
            displayName = "AetherSX2",
            defaultSavePaths = listOf(
                "/storage/emulated/0/AetherSX2/memcards",
                "/storage/emulated/0/AetherSX2/sstates"
            )
        ),
        
        // Vita3K - PlayStation Vita emulator
        EmulatorDefinition(
            emulatorType = Emulator.VITA3K,
            packageNames = listOf(
                "org.vita3k.emulator"
            ),
            displayName = "Vita3K",
            defaultSavePaths = listOf(
                "/storage/emulated/0/Vita3K/ux0/user"
            )
        ),
        
        // Yuzu / Citron - Nintendo Switch emulators
        EmulatorDefinition(
            emulatorType = Emulator.YUZU,
            packageNames = listOf(
                "org.yuzu.yuzu_emu",
                "org.yuzu.yuzu_emu.ea"
            ),
            displayName = "Yuzu",
            defaultSavePaths = listOf(
                "/storage/emulated/0/yuzu/nand/user/save"
            )
        ),
        EmulatorDefinition(
            emulatorType = Emulator.CITRON,
            packageNames = listOf(
                "org.citron.citron_emu"
            ),
            displayName = "Citron",
            defaultSavePaths = listOf(
                "/storage/emulated/0/citron/nand/user/save"
            )
        )
    )
    
    /**
     * Get all known package names for quick lookup
     */
    val allPackageNames: Set<String> = knownEmulators
        .flatMap { it.packageNames }
        .toSet()
    
    /**
     * Find emulator definition by package name
     */
    fun findByPackageName(packageName: String): EmulatorDefinition? {
        return knownEmulators.find { definition ->
            definition.packageNames.any { it.equals(packageName, ignoreCase = true) }
        }
    }
}
