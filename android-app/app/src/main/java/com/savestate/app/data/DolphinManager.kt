package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles Dolphin emulator save detection and scanning for GameCube/Wii.
 *
 * Dolphin organizes saves in three main structures:
 *   GC/<Region>/Card A/   -> *.gci files (individual GameCube saves) and MemoryCard*.raw
 *   Wii/title/<type>/<id>/data/ -> Wii NAND save data per title
 *   StateSaves/            -> <GameID>.s<N> save state files
 *
 * The scanner auto-detects which folder type the user selected and scans accordingly.
 * GC cards and Wii titles use folder-level backup; save states use file-prefix backup.
 */
class DolphinManager {

    companion object {
        private const val TAG = "DolphinManager"

        private val GC_REGIONS = setOf("USA", "EUR", "JAP")

        // GCI filename: <slot>-<gameID>-<description>.gci
        private val GCI_PATTERN = Regex("^\\d{2}-([A-Za-z0-9]{4,6})-.+\\.gci$")

        // Save state: <GameID>.s<N>
        private val STATE_PATTERN = Regex("^(.+)\\.s(\\d+)$")

        // Raw memory card files
        private val MEMCARD_PATTERN = Regex("^MemoryCard.*\\.(raw|gcp)$", RegexOption.IGNORE_CASE)

        // 8-char hex directory (Wii title type or title ID)
        private val HEX_DIR_PATTERN = Regex("^[0-9a-fA-F]{8}$")

        /**
         * Extract game ID from a GCI filename.
         * e.g. "01-GMSE-SuperMarioSunshine.gci" -> "GMSE"
         */
        fun getGciGameId(fileName: String): String? =
            GCI_PATTERN.matchEntire(fileName)?.groupValues?.get(1)

        /**
         * Extract game ID prefix from a Dolphin save state filename.
         * e.g. "GMSE01.s01" -> "GMSE01"
         */
        fun getStateGameId(fileName: String): String? =
            STATE_PATTERN.matchEntire(fileName)?.groupValues?.get(1)

        /**
         * Convert an 8-char hex string to 4-char ASCII game code (for Wii title IDs).
         * e.g. "524d4745" -> "RMGE" (Super Mario Galaxy US)
         * Returns null if the result contains non-printable characters.
         */
        fun hexToGameCode(hex: String): String? {
            if (hex.length != 8) return null
            try {
                val chars = (0 until 4).map { i ->
                    val byte = hex.substring(i * 2, i * 2 + 2).toInt(16)
                    if (byte < 0x20 || byte > 0x7E) return null
                    byte.toChar()
                }
                return String(chars.toCharArray())
            } catch (e: Exception) {
                return null
            }
        }
    }

    /**
     * Scan a folder selected via SAF for Dolphin game saves.
     * Auto-detects folder type (GC, Wii, StateSaves, root) and scans accordingly.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("dolphin-emu", true) -> scanDolphinRoot(documentFile)
            folderName.equals("GC", true) -> scanGcRoot(documentFile)
            folderName.uppercase() in GC_REGIONS -> scanGcRegion(documentFile)
            folderName.startsWith("Card", true) -> scanGcCard(documentFile)
            folderName.equals("StateSaves", true) -> scanStateSaves(documentFile)
            folderName.equals("Wii", true) -> scanWiiFolder(documentFile)
            folderName.equals("title", true) -> scanWiiTitleRoot(documentFile)
            HEX_DIR_PATTERN.matches(folderName) -> scanWiiTitleType(documentFile)
            else -> scanAutoDetect(documentFile)
        }
    }

    // ── Dolphin root ────────────────────────────────────────────────────

    /**
     * Scan dolphin-emu root: check for GC, Wii, StateSaves subdirectories.
     */
    private fun scanDolphinRoot(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("GC")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanGcRoot(it))
        }
        root.findFile("Wii")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanWiiFolder(it))
        }
        root.findFile("StateSaves")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStateSaves(it))
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    // ── GameCube scanning ───────────────────────────────────────────────

    /**
     * Scan GC root: iterate region subdirectories (USA, EUR, JAP).
     */
    private fun scanGcRoot(gcDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (child in gcDir.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name?.uppercase() ?: continue
            if (name in GC_REGIONS) {
                results.addAll(scanGcRegion(child))
            }
        }

        // Non-standard layout: GCI files directly in GC folder
        if (results.isEmpty()) {
            results.addAll(scanGcCard(gcDir))
        }

        Log.d(TAG, "GC root scan: ${results.size} items")
        return results
    }

    /**
     * Scan a GC region folder (e.g. USA) for Card A/B subdirectories.
     */
    private fun scanGcRegion(regionDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()
        val regionName = regionDir.name?.uppercase() ?: "Unknown"

        for (child in regionDir.listFiles()) {
            if (!child.isDirectory) continue
            val name = child.name ?: continue
            if (name.startsWith("Card", true)) {
                results.addAll(scanGcCardWithLabel(child, regionName, name))
            }
        }

        // Non-standard: files directly in region folder
        if (results.isEmpty()) {
            results.addAll(scanGcCardWithLabel(regionDir, regionName, "saves"))
        }

        return results
    }

    private fun scanGcCard(cardDir: DocumentFile): List<DetectedGame> {
        return scanGcCardWithLabel(cardDir, null, cardDir.name ?: "Card")
    }

    /**
     * Scan a GC card folder for .gci / MemoryCard files.
     * The entire card folder is treated as one backup unit.
     */
    private fun scanGcCardWithLabel(
        cardDir: DocumentFile,
        regionName: String?,
        cardName: String
    ): List<DetectedGame> {
        val children = cardDir.listFiles()
        val gciFiles = children.filter {
            it.isFile && it.name?.endsWith(".gci", true) == true
        }
        val memcardFiles = children.filter {
            it.isFile && MEMCARD_PATTERN.matches(it.name ?: "")
        }

        if (gciFiles.isEmpty() && memcardFiles.isEmpty()) return emptyList()

        val gameIds = gciFiles.mapNotNull { getGciGameId(it.name ?: "") }.distinct()

        val regionPrefix = regionName?.let { "$it " } ?: ""
        val gameSummary = when {
            gameIds.size > 3 -> "${gameIds.take(3).joinToString(", ")} +${gameIds.size - 3} more"
            gameIds.isNotEmpty() -> gameIds.joinToString(", ")
            memcardFiles.isNotEmpty() -> "Memory Card"
            else -> "saves"
        }

        val allSaveFiles = gciFiles + memcardFiles
        val folderUri = cardDir.uri.toString()

        return listOf(
            DetectedGame(
                gameId = "GC_${regionPrefix.trim()}_${cardName.replace(" ", "")}",
                gameName = "GameCube ${regionPrefix}${cardName} ($gameSummary)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DOLPHIN,
                saveCount = allSaveFiles.size,
                lastModified = allSaveFiles.maxOfOrNull { it.lastModified() } ?: 0L
            )
        )
    }

    // ── Wii scanning ────────────────────────────────────────────────────

    /**
     * Scan Wii folder: navigate into title/ subdirectory.
     */
    private fun scanWiiFolder(wiiDir: DocumentFile): List<DetectedGame> {
        val titleDir = wiiDir.findFile("title")
        if (titleDir != null && titleDir.isDirectory) {
            return scanWiiTitleRoot(titleDir)
        }
        return emptyList()
    }

    /**
     * Scan Wii/title root: iterate title type subdirectories (e.g. 00010000).
     */
    private fun scanWiiTitleRoot(titleRoot: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (typeDir in titleRoot.listFiles()) {
            if (!typeDir.isDirectory) continue
            val typeName = typeDir.name ?: continue
            if (!HEX_DIR_PATTERN.matches(typeName)) continue
            results.addAll(scanWiiTitleType(typeDir))
        }

        Log.d(TAG, "Wii title scan: ${results.size} titles found")
        return results
    }

    /**
     * Scan a Wii title type directory for individual title save data.
     * Each title with a data/ subfolder containing files becomes a DetectedGame.
     * The hex title ID is converted to a 4-char ASCII game code when possible.
     */
    private fun scanWiiTitleType(typeDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (idDir in typeDir.listFiles()) {
            if (!idDir.isDirectory) continue
            val idName = idDir.name ?: continue
            if (!HEX_DIR_PATTERN.matches(idName)) continue

            val dataDir = idDir.findFile("data")
            if (dataDir == null || !dataDir.isDirectory) continue

            val dataFiles = dataDir.listFiles().filter { it.isFile }
            if (dataFiles.isEmpty()) continue

            val gameCode = hexToGameCode(idName) ?: idName
            val folderUri = dataDir.uri.toString()
            val parentUri = idDir.uri.toString()

            results.add(
                DetectedGame(
                    gameId = "Wii_${typeDir.name}_${idName}",
                    gameName = "Wii - $gameCode",
                    savePath = folderUri,
                    parentPath = parentUri,
                    emulatorType = Emulator.DOLPHIN,
                    saveCount = dataFiles.size,
                    lastModified = dataFiles.maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return results
    }

    // ── Save states scanning ────────────────────────────────────────────

    /**
     * Scan StateSaves folder for .s<N> files, grouped by game ID prefix.
     * Uses file-prefix backup (gameFilePrefix is set on each result).
     */
    private fun scanStateSaves(statesDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in statesDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            val gameId = getStateGameId(name) ?: continue
            gamesMap.getOrPut(gameId) { mutableListOf() }.add(child)
        }

        val folderUri = statesDir.uri.toString()

        val games = gamesMap.map { (gameId, files) ->
            DetectedGame(
                gameId = gameId,
                gameName = "$gameId (Save States)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.DOLPHIN,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = gameId
            )
        }

        Log.d(TAG, "StateSaves scan: ${games.size} games from ${gamesMap.values.sumOf { it.size }} files")
        return games
    }

    // ── Auto-detection ──────────────────────────────────────────────────

    /**
     * Attempt to auto-detect folder contents when the folder name is not recognized.
     * Checks for GCI files, state files, hex directories (Wii), and GC regions.
     */
    private fun scanAutoDetect(documentFile: DocumentFile): List<DetectedGame> {
        Log.w(TAG, "Unknown folder '${documentFile.name}', attempting auto-detection")

        val children = documentFile.listFiles()

        // Check for GCI files → GC card
        if (children.any { it.isFile && it.name?.endsWith(".gci", true) == true }) {
            return scanGcCard(documentFile)
        }

        // Check for state files → StateSaves
        if (children.any { it.isFile && getStateGameId(it.name ?: "") != null }) {
            return scanStateSaves(documentFile)
        }

        // Check for hex directories → Wii structure
        val hexDirs = children.filter { it.isDirectory && HEX_DIR_PATTERN.matches(it.name ?: "") }
        if (hexDirs.isNotEmpty()) {
            for (hexDir in hexDirs) {
                if (hexDir.findFile("data")?.isDirectory == true) {
                    return scanWiiTitleType(documentFile)
                }
                val sub = hexDir.listFiles()
                if (sub.any { it.isDirectory && HEX_DIR_PATTERN.matches(it.name ?: "") }) {
                    return scanWiiTitleRoot(documentFile)
                }
            }
        }

        // Check for GC region directories
        if (children.any { it.isDirectory && it.name?.uppercase() in GC_REGIONS }) {
            return scanGcRoot(documentFile)
        }

        // Fallback: treat the entire folder as a single backup unit
        val fileCount = children.count { it.isFile }
        if (fileCount > 0) {
            val folderUri = documentFile.uri.toString()
            return listOf(
                DetectedGame(
                    gameId = documentFile.name ?: "dolphin_unknown",
                    gameName = documentFile.name ?: "Dolphin Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.DOLPHIN,
                    saveCount = fileCount,
                    lastModified = children.maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        Log.w(TAG, "No recognizable Dolphin content found")
        return emptyList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════
    // Uses RootAccessHelper to read protected Android/data/ paths.

    /**
     * Scan all known root save paths for Dolphin saves.
     * Called when root mode is enabled and the user selects Dolphin.
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            val gcPath = "$basePath/GC"
            if (rootHelper.directoryExists(gcPath)) {
                results.addAll(rootScanGcRoot(rootHelper, gcPath))
            }

            val wiiPath = "$basePath/Wii"
            if (rootHelper.directoryExists(wiiPath)) {
                results.addAll(rootScanWiiFolder(rootHelper, wiiPath))
            }

            val statesPath = "$basePath/StateSaves"
            if (rootHelper.directoryExists(statesPath)) {
                results.addAll(rootScanStateSaves(rootHelper, statesPath))
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    private fun rootScanGcRoot(root: RootAccessHelper, gcPath: String): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (regionName in GC_REGIONS) {
            val regionPath = "$gcPath/$regionName"
            if (!root.directoryExists(regionPath)) continue
            results.addAll(rootScanGcRegion(root, regionPath, regionName))
        }

        if (results.isEmpty()) {
            results.addAll(rootScanGcCardAtPath(root, gcPath, null, "GC"))
        }

        return results
    }

    private fun rootScanGcRegion(
        root: RootAccessHelper,
        regionPath: String,
        regionName: String
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()
        val dirs = root.listDirectories(regionPath)

        for (dirName in dirs) {
            if (dirName.startsWith("Card", true)) {
                results.addAll(
                    rootScanGcCardAtPath(root, "$regionPath/$dirName", regionName, dirName)
                )
            }
        }

        if (results.isEmpty()) {
            results.addAll(rootScanGcCardAtPath(root, regionPath, regionName, "saves"))
        }

        return results
    }

    private fun rootScanGcCardAtPath(
        root: RootAccessHelper,
        cardPath: String,
        regionName: String?,
        cardName: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(cardPath)
        val gciNames = fileNames.filter { it.endsWith(".gci", true) }
        val memcardNames = fileNames.filter { MEMCARD_PATTERN.matches(it) }

        if (gciNames.isEmpty() && memcardNames.isEmpty()) return emptyList()

        val gameIds = gciNames.mapNotNull { getGciGameId(it) }.distinct()
        val regionPrefix = regionName?.let { "$it " } ?: ""
        val gameSummary = when {
            gameIds.size > 3 -> "${gameIds.take(3).joinToString(", ")} +${gameIds.size - 3} more"
            gameIds.isNotEmpty() -> gameIds.joinToString(", ")
            memcardNames.isNotEmpty() -> "Memory Card"
            else -> "saves"
        }

        return listOf(
            DetectedGame(
                gameId = "GC_${regionPrefix.trim()}_${cardName.replace(" ", "")}",
                gameName = "GameCube ${regionPrefix}${cardName} ($gameSummary)",
                savePath = cardPath,
                parentPath = cardPath,
                emulatorType = Emulator.DOLPHIN,
                saveCount = gciNames.size + memcardNames.size,
                lastModified = root.getLastModified(cardPath)
            )
        )
    }

    private fun rootScanWiiFolder(root: RootAccessHelper, wiiPath: String): List<DetectedGame> {
        val titlePath = "$wiiPath/title"
        if (!root.directoryExists(titlePath)) return emptyList()
        return rootScanWiiTitleRoot(root, titlePath)
    }

    private fun rootScanWiiTitleRoot(
        root: RootAccessHelper,
        titlePath: String
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (typeDirName in root.listDirectories(titlePath)) {
            if (!HEX_DIR_PATTERN.matches(typeDirName)) continue
            val typePath = "$titlePath/$typeDirName"

            for (idDirName in root.listDirectories(typePath)) {
                if (!HEX_DIR_PATTERN.matches(idDirName)) continue
                val dataPath = "$typePath/$idDirName/data"
                if (!root.directoryExists(dataPath)) continue

                val fileCount = root.countFiles(dataPath)
                if (fileCount == 0) continue

                val gameCode = hexToGameCode(idDirName) ?: idDirName

                results.add(
                    DetectedGame(
                        gameId = "Wii_${typeDirName}_${idDirName}",
                        gameName = "Wii - $gameCode",
                        savePath = dataPath,
                        parentPath = "$typePath/$idDirName",
                        emulatorType = Emulator.DOLPHIN,
                        saveCount = fileCount,
                        lastModified = root.getLastModified(dataPath)
                    )
                )
            }
        }

        Log.d(TAG, "Wii root scan: ${results.size} titles found")
        return results
    }

    private fun rootScanStateSaves(
        root: RootAccessHelper,
        statesPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(statesPath)
        val gamesMap = mutableMapOf<String, Int>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val gameId = getStateGameId(name) ?: continue
            gamesMap[gameId] = (gamesMap[gameId] ?: 0) + 1
        }

        val games = gamesMap.map { (gameId, count) ->
            DetectedGame(
                gameId = gameId,
                gameName = "$gameId (Save States)",
                savePath = statesPath,
                parentPath = statesPath,
                emulatorType = Emulator.DOLPHIN,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = gameId
            )
        }

        Log.d(TAG, "StateSaves root scan: ${games.size} games")
        return games
    }
}
