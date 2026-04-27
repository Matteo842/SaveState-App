# SaveState - Android App

<div align="center">

**Backup & Restore your emulator save files on Android**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Discord](https://img.shields.io/badge/Discord-Server-5865F2?logo=discord&logoColor=white)](https://discord.gg/6d7Qv4hAky)

</div>

> 🖥️ **Looking for the Desktop/PC version?** Check out [SaveState for Windows](https://github.com/Matteo842/SaveState)

---

## 📱 About

**SaveState App** is an Android application designed to backup and restore save files from **emulators**. Unlike the desktop version which targets PC games in general, this mobile app is specifically built for the Android emulator ecosystem.

### Current Status: `Pre-Release Alpha (v0.8)`

The app is **functional** and can be used to:
- ✅ Detect installed emulators
- ✅ Scan for game saves (folder- and file-level where needed)
- ✅ Create compressed backups (.zip)
- ✅ Restore saves from backups
- ✅ Manage multiple game profiles
- ✅ Persist data across app reinstalls (when using external storage)
- ✅ Optional **root mode** for saves under protected `Android/data/` paths

## 🧪 Join the Play Store Beta

SaveState App needs Android beta testers before the public Play Store release. If you use emulators on Android and want to help test backup/restore workflows, you can join the beta in a few minutes.

### How to become a beta tester

1. **Request access** through the [beta tester form](https://docs.google.com/forms/d/e/1FAIpQLScLShJjru50_oY3Of_wNSeZVs27coTnzcxUZaWegzsRcDRCfg/viewform?usp=header).
2. **Join the Google Group** with the same Google account you use on Play Store: [SaveState Beta Test group](https://groups.google.com/g/savestate-beta-test/members).
3. **Open the Play Store testing page** and opt in: [SaveState App beta](https://play.google.com/apps/testing/com.savestate.app).
4. **Install or update the app** from the [Play Store listing](https://play.google.com/store/apps/details?id=com.savestate.app).

### What to test

- Emulator detection on your device
- Backup and restore for real game saves
- Root mode behavior, if you use a rooted Android device
- Problems with permissions, storage folders, missing games, or failed restores

Please report what device, Android version, emulator, and game you tested when sharing feedback. This makes bugs much easier to reproduce and fix.

### What’s new in v0.8

- **First-launch tutorial**: interactive coach-marks (spotlight + tooltips) that walk through storage permission, backup folder in Settings, profile creation, Backup / Restore / Manage, and favorites—shown once per install.

### Earlier: v0.7

- **Eden** (Nintendo Switch): detection and backup/restore for Yuzu-style save layout; optional **Root mode** for `Android/data/` paths; game titles resolved via bundled Switch title-ID database.

### Earlier: v0.6

- **Dolphin** (GameCube / Wii): backup and restore including **MMJR / MMJR2** variants; public paths under `dolphin-emu/` and, with **Root mode**, access to app-private storage.
- **DuckStation** (PS1): memcards and save states; **Root mode** for private app data when needed.
- **Root mode** (Settings): toggle to use root (via [libsu](https://github.com/topjohnwu/libsu)) for emulators that keep saves in inaccessible `Android/data/` locations. Requires a rooted device and granting superuser access.
- **Settings**: max backups per profile, maximum source size (MB) for a single backup, and ZIP compression level.

## 🎮 Supported Emulators

Paths are preconfigured per emulator in the app (detection + default save locations). Some titles only expose saves in **private app storage** on modern Android; for those, enable **Root mode** where indicated.

| Emulator | Platform | Notes |
|----------|----------|--------|
| **AetherSX2** | PS2 | Memory cards + save states |
| **Azahar** | 3DS | Citra-style `sdmc` layout; folder scan |
| **Citra** | 3DS | SDMC / NAND paths |
| **Citron** | Switch | User saves under `nand/user/save` |
| **Dolphin** | GameCube / Wii | Public `dolphin-emu/`; MMJR / MMJR2; optional **root** for `Android/data/` |
| **DraStic** | DS | Backup folder + save states |
| **DuckStation** | PS1 | Memcards + save states; optional **root** for app data |
| **Eden** | Switch | Yuzu-style layout; bundled title-ID DB; optional **root** |
| **Flycast** | Dreamcast | `flycast/data` |
| **Lemuroid** | Multi-system | Saves + states |
| **mGBA** | GBA | Battery saves |
| **M64Plus FZ** | N64 | Default public path; optional **root** for app-private storage |
| **NetherSX2** | PS2 | AetherSX2 fork; memcards + states; optional **root** |
| **Pizza Boy** | GBA / GBC | Saves folder |
| **PPSSPP** | PSP | SAVEDATA + save states |
| **RetroArch** | Multi-system | Per-core saves + states |
| **Vita3K** | PS Vita | `ux0/user` layout |
| **Yuzu** | Switch | `yuzu/nand/user/save` |

> Compatibility depends on where each emulator stores files on your device. If a game does not appear in the list, check the emulator’s save directory settings or use Root mode if saves are under `Android/data/`.

## 📸 Screenshots

<p align="center">
  <img src="images/Screenshot.png" alt="SaveState App Screenshot" width="300">
</p>

## 🚀 Getting Started

1. **Install the APK** from the Releases page
2. **Select a backup folder** when prompted (this is where your backups will be stored)
3. **Add a profile** with the + button
4. **Select your emulator** from the detected list
5. **Choose a game** from the detected saves
6. **Backup** with one tap; use **Restore** to write saves back

If you use **Dolphin** or **DuckStation** and saves live only in private storage, open **Settings**, enable **Root mode**, and grant superuser when asked.

### Data Persistence

Your profiles and backups are saved to the folder you select. This means:
- ✅ Backups survive app uninstallation
- ✅ Reinstall the app and select the same folder to restore everything
- ✅ You control where your data is stored

## 🗺️ Roadmap

- [x] Broad emulator coverage — see **Supported Emulators** (detection + default paths; root-capable emulators noted in the table)
- [ ] Additional emulators (dedicated scanning and backup flows)
- [ ] Auto-backup scheduling
- [ ] Cloud backup integration (Google Drive, OneDrive)
- [ ] Import/Export profiles
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
