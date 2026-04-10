package com.savestate.app.data

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.savestate.app.data.model.DetectedGame
import com.savestate.app.data.model.Emulator

/**
 * Handles Azahar (Nintendo 3DS) save detection and scanning.
 *
 * Azahar is a fork of Citra; it stores game saves under a nested SDMC path:
 *   azahar-emu/sdmc/Nintendo 3DS/<ID0>/<ID1>/title/<category>/<titleId>/data/00000001/
 *
 * Category constants:
 *   00040000 = retail games
 *   00040002 = DLC
 *   00040010 = system applets
 *
 * Save states (if present) are stored as .cst files inside a states folder.
 *
 * The user can point at:
 *   • azahar-emu           -> top-level, we dive into sdmc automatically
 *   • sdmc                 -> we look for "Nintendo 3DS" inside
 *   • Nintendo 3DS         -> we traverse <ID0>/<ID1>/title/…
 *   • title                -> we scan category/title folders directly
 *   • states               -> save-state .cst files
 *   • any arbitrary folder -> auto-detect save files
 */
class AzaharManager {

    companion object {
        private const val TAG = "AzaharManager"

        /** 3DS title-ID category for retail games. */
        private const val CATEGORY_GAME = "00040000"

        /** Common categories that contain user-relevant data. */
        private val INTERESTING_CATEGORIES = setOf(
            "00040000",  // retail games
            "00040002",  // add-on content (DLC)
            "0004000e",  // patches / updates
            "00040010"   // system applets (rare but possible)
        )

        /** Save-state extension produced by Citra / Azahar. */
        private const val STATE_EXTENSION = "cst"

        private val IGNORE_EXTENSIONS = setOf("ini", "txt", "log", "png", "jpg")

        /**
         * Build a human-readable title from the hex title-ID.
         * Falls back to the raw hex string if nothing better is available.
         */
        fun formatTitleId(titleId: String): String =
            titleId.uppercase().let { "3DS-$it" }
    }

    // ══ SAF scanning ════════════════════════════════════════════════════

    /**
     * Entry point: scan a user-selected SAF folder for Azahar saves.
     * Delegates to the right sub-scanner based on folder name.
     */
    fun scanSafFolder(documentFile: DocumentFile): List<DetectedGame> {
        val folderName = documentFile.name ?: ""
        Log.d(TAG, "Scanning SAF folder: '$folderName'")

        return when {
            folderName.equals("states", true) ->
                scanStatesSaf(documentFile)
            folderName.equals("sdmc", true) ->
                scanSdmcSaf(documentFile)
            folderName.equals("Nintendo 3DS", true) ->
                scanNintendo3DSSaf(documentFile)
            folderName.equals("title", true) ->
                scanTitleRootSaf(documentFile)
            folderName.contains("azahar", true) ||
                hasKnownSubfolders(documentFile) ->
                scanTopLevelSaf(documentFile)
            else -> scanAutoDetectSaf(documentFile)
        }
    }

    private fun hasKnownSubfolders(dir: DocumentFile): Boolean {
        return dir.listFiles().any {
            it.isDirectory && it.name?.let { n ->
                n.equals("sdmc", true) ||
                n.equals("nand", true) ||
                n.equals("states", true)
            } == true
        }
    }

    /**
     * User pointed at the top-level azahar-emu folder.
     * Dive into sdmc/ and states/ if they exist.
     */
    private fun scanTopLevelSaf(root: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        root.findFile("sdmc")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanSdmcSaf(it))
        }
        root.findFile("nand")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanSdmcSaf(it))
        }
        root.findFile("states")?.takeIf { it.isDirectory }?.let {
            results.addAll(scanStatesSaf(it))
        }

        if (results.isEmpty()) {
            results.addAll(scanAutoDetectSaf(root))
        }

        return results
    }

    /**
     * Scan the sdmc folder: expects Nintendo 3DS/<ID0>/<ID1>/title/…
     */
    private fun scanSdmcSaf(sdmcDir: DocumentFile): List<DetectedGame> {
        val nintendo3DS = sdmcDir.findFile("Nintendo 3DS")
            ?: sdmcDir.listFiles().firstOrNull {
                it.isDirectory && it.name?.startsWith("Nintendo 3DS") == true
            }

        if (nintendo3DS != null && nintendo3DS.isDirectory) {
            return scanNintendo3DSSaf(nintendo3DS)
        }

        return scanAutoDetectSaf(sdmcDir)
    }

    /**
     * Scan Nintendo 3DS/<ID0>/<ID1>/title/…
     * ID0 and ID1 are long hex-string folder names.
     */
    private fun scanNintendo3DSSaf(n3dsDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (id0Dir in n3dsDir.listFiles()) {
            if (!id0Dir.isDirectory) continue
            val id0Name = id0Dir.name ?: continue
            if (!id0Name.matches(Regex("^[a-fA-F0-9]{32}$"))) continue

            for (id1Dir in id0Dir.listFiles()) {
                if (!id1Dir.isDirectory) continue
                val id1Name = id1Dir.name ?: continue
                if (!id1Name.matches(Regex("^[a-fA-F0-9]{32}$"))) continue

                val titleDir = id1Dir.findFile("title")
                    ?: id1Dir.listFiles().firstOrNull {
                        it.isDirectory && it.name.equals("title", true)
                    }

                if (titleDir != null && titleDir.isDirectory) {
                    results.addAll(scanTitleRootSaf(titleDir))
                }

                val extdataDir = id1Dir.findFile("extdata")
                if (extdataDir != null && extdataDir.isDirectory) {
                    results.addAll(scanExtdataSaf(extdataDir))
                }
            }
        }

        return results
    }

    /**
     * Scan title/<category>/<titleId>/data/00000001/ for save files.
     */
    private fun scanTitleRootSaf(titleDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (categoryDir in titleDir.listFiles()) {
            if (!categoryDir.isDirectory) continue
            val catName = categoryDir.name?.lowercase() ?: continue
            if (catName !in INTERESTING_CATEGORIES) continue

            for (titleIdDir in categoryDir.listFiles()) {
                if (!titleIdDir.isDirectory) continue
                val titleId = titleIdDir.name ?: continue

                val saveFiles = collectSaveFiles(titleIdDir)
                if (saveFiles.isEmpty()) continue

                val folderUri = titleIdDir.uri.toString()
                val parentUri = categoryDir.uri.toString()

                results.add(
                    DetectedGame(
                        gameId = "3ds_${catName}_$titleId",
                        gameName = formatTitleId(titleId),
                        savePath = folderUri,
                        parentPath = parentUri,
                        emulatorType = Emulator.AZAHAR,
                        saveCount = saveFiles.size,
                        lastModified = saveFiles.maxOfOrNull { it.lastModified() } ?: 0L
                    )
                )
            }
        }

        return results
    }

    /**
     * Scan extdata/<category>/<extdataId>/ for extra save data.
     */
    private fun scanExtdataSaf(extdataDir: DocumentFile): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (categoryDir in extdataDir.listFiles()) {
            if (!categoryDir.isDirectory) continue
            val catName = categoryDir.name ?: continue

            for (extIdDir in categoryDir.listFiles()) {
                if (!extIdDir.isDirectory) continue
                val extId = extIdDir.name ?: continue

                val files = collectAllFiles(extIdDir)
                if (files.isEmpty()) continue

                val folderUri = extIdDir.uri.toString()
                results.add(
                    DetectedGame(
                        gameId = "3ds_ext_${catName}_$extId",
                        gameName = "${formatTitleId(extId)} (ExtData)",
                        savePath = folderUri,
                        parentPath = categoryDir.uri.toString(),
                        emulatorType = Emulator.AZAHAR,
                        saveCount = files.size,
                        lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L
                    )
                )
            }
        }

        return results
    }

    /**
     * Recursively collect save files under a title-ID folder.
     * Navigates into data/00000001/ if present.
     */
    private fun collectSaveFiles(titleIdDir: DocumentFile): List<DocumentFile> {
        val dataDir = titleIdDir.findFile("data")
        if (dataDir != null && dataDir.isDirectory) {
            val slot = dataDir.findFile("00000001")
            if (slot != null && slot.isDirectory) {
                return collectAllFiles(slot)
            }
            return collectAllFiles(dataDir)
        }
        return collectAllFiles(titleIdDir)
    }

    /**
     * Recursively collect all files in a directory tree.
     */
    private fun collectAllFiles(dir: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        for (child in dir.listFiles()) {
            if (child.isFile) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in IGNORE_EXTENSIONS) continue
                result.add(child)
            } else if (child.isDirectory) {
                result.addAll(collectAllFiles(child))
            }
        }
        return result
    }

    /**
     * Scan a states folder for .cst save-state files.
     */
    private fun scanStatesSaf(statesDir: DocumentFile): List<DetectedGame> {
        val gamesMap = mutableMapOf<String, MutableList<DocumentFile>>()

        for (child in statesDir.listFiles()) {
            if (!child.isFile) continue
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()

            if (ext == STATE_EXTENSION) {
                val baseName = name.substringBeforeLast('.')
                gamesMap.getOrPut(baseName) { mutableListOf() }.add(child)
            }
        }

        val folderUri = statesDir.uri.toString()

        return gamesMap.map { (baseName, files) ->
            DetectedGame(
                gameId = "3ds_state_$baseName",
                gameName = "$baseName (Save State)",
                savePath = folderUri,
                parentPath = folderUri,
                emulatorType = Emulator.AZAHAR,
                saveCount = files.size,
                lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
                gameFilePrefix = baseName
            )
        }
    }

    /**
     * Fallback: scan any folder for recognisable save files.
     */
    private fun scanAutoDetectSaf(documentFile: DocumentFile): List<DetectedGame> {
        val children = documentFile.listFiles()

        val stateFiles = children.filter {
            it.isFile && (it.name?.substringAfterLast('.', "")?.lowercase() == STATE_EXTENSION)
        }
        if (stateFiles.isNotEmpty()) {
            return scanStatesSaf(documentFile)
        }

        val subDirs = children.filter { it.isDirectory }
        if (subDirs.isNotEmpty()) {
            val results = mutableListOf<DetectedGame>()
            for (sub in subDirs) {
                val name = sub.name ?: continue
                if (name.startsWith(".")) continue
                val files = collectAllFiles(sub)
                if (files.isEmpty()) continue

                results.add(
                    DetectedGame(
                        gameId = "3ds_$name",
                        gameName = name,
                        savePath = sub.uri.toString(),
                        parentPath = documentFile.uri.toString(),
                        emulatorType = Emulator.AZAHAR,
                        saveCount = files.size,
                        lastModified = files.maxOfOrNull { it.lastModified() } ?: 0L
                    )
                )
            }
            if (results.isNotEmpty()) return results
        }

        val fileCount = children.count { it.isFile }
        if (fileCount > 0) {
            val folderUri = documentFile.uri.toString()
            return listOf(
                DetectedGame(
                    gameId = documentFile.name ?: "azahar_unknown",
                    gameName = documentFile.name ?: "Azahar Saves",
                    savePath = folderUri,
                    parentPath = folderUri,
                    emulatorType = Emulator.AZAHAR,
                    saveCount = fileCount,
                    lastModified = children.maxOfOrNull { it.lastModified() } ?: 0L
                )
            )
        }

        return emptyList()
    }

    // ══ Root-based scanning ═════════════════════════════════════════════

    /**
     * Scan root-protected paths for Azahar saves.
     */
    fun scanRootPaths(
        rootHelper: RootAccessHelper,
        basePaths: List<String>
    ): List<DetectedGame> {
        val results = mutableListOf<DetectedGame>()

        for (basePath in basePaths) {
            if (!rootHelper.directoryExists(basePath)) continue
            Log.d(TAG, "Root scanning: $basePath")

            val sdmcPath = "$basePath/sdmc"
            if (rootHelper.directoryExists(sdmcPath)) {
                results.addAll(rootScanSdmc(rootHelper, sdmcPath))
            }

            val nandPath = "$basePath/nand"
            if (rootHelper.directoryExists(nandPath)) {
                results.addAll(rootScanSdmc(rootHelper, nandPath))
            }

            val statesPath = "$basePath/states"
            if (rootHelper.directoryExists(statesPath)) {
                results.addAll(rootScanStates(rootHelper, statesPath))
            }
        }

        Log.d(TAG, "Root scan complete: ${results.size} items")
        return results
    }

    private fun rootScanSdmc(
        root: RootAccessHelper,
        sdmcPath: String
    ): List<DetectedGame> {
        val n3dsPath = "$sdmcPath/Nintendo 3DS"
        if (!root.directoryExists(n3dsPath)) return emptyList()

        val results = mutableListOf<DetectedGame>()
        val id0Dirs = root.listFileNames(n3dsPath)

        for (id0 in id0Dirs) {
            if (!id0.matches(Regex("^[a-fA-F0-9]{32}$"))) continue
            val id0Path = "$n3dsPath/$id0"
            val id1Dirs = root.listFileNames(id0Path)

            for (id1 in id1Dirs) {
                if (!id1.matches(Regex("^[a-fA-F0-9]{32}$"))) continue
                val titlePath = "$id0Path/$id1/title"
                if (!root.directoryExists(titlePath)) continue

                val categories = root.listFileNames(titlePath)
                for (cat in categories) {
                    if (cat.lowercase() !in INTERESTING_CATEGORIES) continue
                    val catPath = "$titlePath/$cat"
                    val titleIds = root.listFileNames(catPath)

                    for (titleId in titleIds) {
                        val tidPath = "$catPath/$titleId"
                        val dataPath = "$tidPath/data/00000001"
                        val scanPath = if (root.directoryExists(dataPath)) dataPath
                            else if (root.directoryExists("$tidPath/data")) "$tidPath/data"
                            else tidPath

                        val files = root.listFileNames(scanPath)
                        val validFiles = files.filter { name ->
                            !name.startsWith(".") &&
                                name.substringAfterLast('.', "").lowercase() !in IGNORE_EXTENSIONS
                        }
                        if (validFiles.isEmpty()) continue

                        results.add(
                            DetectedGame(
                                gameId = "3ds_${cat}_$titleId",
                                gameName = formatTitleId(titleId),
                                savePath = tidPath,
                                parentPath = catPath,
                                emulatorType = Emulator.AZAHAR,
                                saveCount = validFiles.size,
                                lastModified = root.getLastModified(scanPath)
                            )
                        )
                    }
                }
            }
        }

        return results
    }

    private fun rootScanStates(
        root: RootAccessHelper,
        statesPath: String
    ): List<DetectedGame> {
        val fileNames = root.listFileNames(statesPath)
        val gamesMap = mutableMapOf<String, Int>()

        for (name in fileNames) {
            if (name.startsWith(".")) continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext == STATE_EXTENSION) {
                val baseName = name.substringBeforeLast('.')
                gamesMap[baseName] = (gamesMap[baseName] ?: 0) + 1
            }
        }

        return gamesMap.map { (baseName, count) ->
            DetectedGame(
                gameId = "3ds_state_$baseName",
                gameName = "$baseName (Save State)",
                savePath = statesPath,
                parentPath = statesPath,
                emulatorType = Emulator.AZAHAR,
                saveCount = count,
                lastModified = root.getLastModified(statesPath),
                gameFilePrefix = baseName
            )
        }
    }
}
