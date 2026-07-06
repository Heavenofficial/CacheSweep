<div align="center">
<img src="screenshots/banner.png" width="800" alt="CacheSweep banner" />

# 🧹 CacheSweep

**One-tap root cache cleaner for Android — with app whitelisting.**

[![Platform](https://img.shields.io/badge/platform-Android_14-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Root Required](https://img.shields.io/badge/root-required-critical?logo=linux&logoColor=white)](#-requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

---

## 📸 Screenshots

<div align="center">
<img src="screenshots/clean_screen.png" width="260" alt="Clean screen" />
&nbsp;&nbsp;
<img src="screenshots/whitelist_screen.png" width="260" alt="Whitelist screen" />
</div>

<p align="center"><i>Drop your own PNGs into a <code>screenshots/</code> folder at the repo root using the filenames above — or edit the paths to match yours.</i></p>

---

## ✨ Features

- **One-tap clear** — wipes cache for every installed app in a single tap
- **Smart whitelist** — protect specific apps from ever being cleared, with search and sort-by-cache-size
- **Live cache size** — see exactly how much space you're about to reclaim, before and after
- **Cache-only, always** — touches `cache/` and `code_cache/` only, never app data, logins, or settings
- **Zero bloat** — no ads, no network calls, no background services — just a root shell and a Compose UI

---

## 📋 Requirements

| | |
|---|---|
| **OS** | Android 14 (API 34) |
| **Root** | Required — Magisk or KernelSU |
| **Permissions** | `QUERY_ALL_PACKAGES` (to list installed apps) |
| **Network** | None — 100% offline |

---

## 📦 Installation

1. Grab the latest APK from the [Releases](../../releases) page
2. Enable **Install unknown apps** for your browser or file manager
3. Install the APK
4. Open CacheSweep and grant root access when prompted

---

## ⚙️ How It Works

CacheSweep never calls `pm clear` — that wipes an app's full data, not just its cache. Instead, each run opens a single root shell and, for every non-whitelisted app, deletes only:

```bash
/data/data/<package>/cache/*
/data/data/<package>/code_cache/*
/sdcard/Android/data/<package>/cache/*   # if present
```

Your whitelist is saved locally with Jetpack DataStore, so it survives restarts.

---

## ⚠️ Disclaimer

This app requires root and deletes files at the shell level. It only ever touches cache directories — but you're using it at your own risk. The author isn't responsible for data loss or device issues.

---

<div align="center">

Built for personal use on a rooted Android 14 device.

**[MIT License](LICENSE)**

</div>
