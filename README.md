# SaveState - Android App

<div align="center">

**Backup & Restore your emulator save files on Android**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

</div>

> 🖥️ **Looking for the Desktop/PC version?** Check out [SaveState for Windows](https://github.com/Matteo842/SaveState)

---

## 📱 About

**SaveState App** is an Android application designed to backup and restore save files from **emulators**. Unlike the desktop version which targets PC games in general, this mobile app is specifically built for the Android emulator ecosystem.

### Current Status: `Pre-Release Alpha (v0.4)`

The app is now **functional** and can be used to:
- ✅ Detect installed emulators
- ✅ Scan for game saves
- ✅ Create compressed backups (.zip)
- ✅ Restore saves from backups
- ✅ Manage multiple game profiles
- ✅ Persist data across app reinstalls (when using external storage)

## 🎮 Supported Emulators

| Emulator | Platform | Status |
|----------|----------|--------|
| **PPSSPP** | PSP | ✅ Supported |
| **RetroArch** | Multi-system | ✅ Supported |
| Citra | 3DS | 🔜 Coming Soon |
| Dolphin | GameCube/Wii | 🔜 Coming Soon |
| DuckStation | PS1 | 🔜 Coming Soon |
| AetherSX2 | PS2 | 🔜 Coming Soon |
| Flycast | Dreamcast | 🔜 Coming Soon |
| ePSXe | PS1 | 🔜 Coming Soon |

> More emulators will be added in future releases!

## 📸 Screenshots

<p align="center">
  <img src="images/Screenshot.png" alt="SaveState App Screenshot" width="300">
</p>

## 🚀 Getting Started

1. **Install the APK** from the Releases page
2. **Select a backup folder** when prompted (this is where your backups will be stored)
3. **Add a profile** by clicking the + button
4. **Select your emulator** (PPSSPP)
5. **Choose a game** from the detected saves
6. **Backup** your saves with one tap!

### Data Persistence
Your profiles and backups are saved to the folder you select. This means:
- ✅ Backups survive app uninstallation
- ✅ Reinstall the app and select the same folder to restore everything
- ✅ You control where your data is stored

## 🗺️ Roadmap

- [ ] Support for more emulators (Citra, Dolphin, DuckStation, etc.)
- [ ] Auto-backup scheduling
- [ ] Cloud backup integration (Google Drive, OneDrive)
- [ ] Import/Export profiles
- [ ] Backup compression options
- [ ] Multi-language support

## 🤝 Contributing

Feedback is welcome! Feel free to:
- Report bugs via [Issues](https://github.com/Matteo842/SaveState-App/issues)
- Suggest new features or emulator support
- Share your experience with the app

> ⚠️ **Note:** This project is developed with AI assistance. Due to this, I'm currently **not accepting pull requests** for code changes, as reviewing external code contributions is beyond my capacity. Thank you for understanding!

## 📄 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Created by [Matteo842](https://github.com/Matteo842)**

</div>
