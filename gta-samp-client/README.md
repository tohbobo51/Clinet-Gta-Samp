# GTA SA-MP Client (Native Android)

Launcher / client UI native Android (Java + XML) untuk GTA San Andreas Multiplayer.

Proyek ini **berdiri sendiri** — proyek Flutter di folder `artplay_launcher-main/` tidak diubah.

```
gta-samp-client/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── app/
│   ├── build.gradle                  # dependensi: AppCompat, ConstraintLayout, play-services-auth
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/artplaygames/sampclient/
│       │   ├── MainActivity.java     # MediaPlayer, Google Sign-In, animasi, splash, update, aksi cepat
│       │   └── UpdateManager.java    # cek manifest + unduh aset game (background)
│       └── res/
│           ├── layout/activity_main.xml      # utama: ConstraintLayout
│           ├── layout/dialog_update_progress.xml
│           ├── layout/dialog_quick_action.xml
│           ├── values/{strings,colors,themes}.xml
│           ├── drawable/                     # bg_gradient, panel_bg, btn_google/logout/main/update/quick
│           └── mipmap-*/ic_launcher.png
```

## Spesifikasi yang diimplementasikan

| # | Fitur | Lokasi |
|---|-------|--------|
| 1 | Splash screen + loading animation | `splashContainer` di `activity_main.xml`, `finishSplash()` |
| 2 | Musik latar otomatis setelah loading + lifecycle `onResume`/`onPause` | `startBackgroundMusic()` / `pauseBackgroundMusic()` / `releaseBackgroundMusic()` |
| 3 | Login Google → Logout → tombol MAIN (pojok kiri atas) | `applyAuthUi()`, `authGroup` (LinearLayout vertikal) |
| 4 | Nama server tengah atas + animasi floating infinite | `setupServerNameFloatingAnimation()` |

## Cara menjalankan

1. Buka folder `gta-samp-client/` di Android Studio (Studio akan membuat Gradle Wrapper otomatis).
2. Tunggu sync Gradle selesai, lalu Run ▶.

## Setup yang perlu kamu lakukan

### 1. Musik latar (wajib untuk fitur #2)
Taruh file audio di `app/src/main/res/raw/bgm.mp3` (nama resource harus **`bgm`**).
Kalau belum ada, aplikasi tetap jalan — hanya musik yang tidak diputar dan ada warning di Logcat.

### 2. Background image UI utama (fitur #2)
Sekarang masih memakai gradient placeholder `bg_gradient.xml`.
Taruh gambar kamu di `app/src/main/res/drawable-nodpi/bg_main.png`, lalu ubah
`android:src="@drawable/bg_gradient"` pada `ivBackground` di `activity_main.xml`
menjadi `@drawable/bg_main`.

### 3. Google Sign-In
1. Buat project di [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → **OAuth client ID** (Android).
2. Isi package name `com.artplaygames.sampclient` + **SHA-1** (Android Studio → Gradle → `app` → Tasks → `android` → `signingReport`).
3. Taruh `google-services.json` di `app/` bila kamu memakai plugin `com.google.android.gms.google-services`.
4. Untuk login dasar (email saja) tidak ada konfigurasi tambahan — langsung jalan.
5. Untuk verifikasi di server, aktifkan `.requestIdToken(...)` di `setupGoogleSignIn()`.

> Catatan: API `GoogleSignIn` sudah ditandai deprecated oleh Google (arahnya ke
> Credential Manager), tapi masih berfungsi penuh di play-services-auth 20.x.

### 4. Nama & alamat server (fitur #3)
Ubah di `app/src/main/res/values/strings.xml`:
`server_name`, `server_host`, `server_port`.

### 5. Tombol MAIN → koneksi server
Ganti blok `// TODO` di `connectToServer()` (`MainActivity.java`) dengan handshake
SA-MP / SDK multipemain yang kamu pakai.

## Perilaku tombol

- **Belum login** → hanya tampil tombol *Login dengan Google*.
- **Sudah login** → tombol login disembunyikan; tampil *Logout* dan, **tepat di bawahnya**, tombol *MAIN*.

Jika kamu ingin tombol MAIN selalu tampil tanpa login, ganti baris
`btnMain.setVisibility(signedIn ? View.VISIBLE : View.GONE);` di `applyAuthUi()`
menjadi `btnMain.setVisibility(View.VISIBLE);`.

---

## Update data game (cek otomatis + unduh aset)

Fitur ini **tidak** memperbarui APK — hanya data/aset game, dan disimpan di penyimpanan
khusus aplikasi ("kesimpan di apknya"):

- Utama: `Android/data/com.artplaygames.sampclient/files/game_data/`
- Cadangan (jika external tidak tersedia): `files/game_data/`

### Alur

1. Setelah splash selesai, client otomatis `checkForUpdates(false)` (senyap — hanya log bila gagal).
   Tombol **UPDATE DATA** di panel kiri atas memanggil `checkForUpdates(true)` (selalu menampilkan hasil).
2. `UpdateManager` mengambil `update_manifest_url` (lihat `strings.xml`).
3. Jika angka `version` lebih besar dari versi terpasang → dialog **UNDUH / NANTI**.
4. Unduhan berjalan di background thread; tiap file divalidasi ukuran dan SHA-256 (jika ada di
   manifest), disimpan sementara sebagai `*.part` lalu di-rename ke tujuan akhir. Ada progres
   per-file + total, dan tombol **BATAL** untuk membatalkan.
5. Versi & judul update disimpan di `SharedPreferences` (`game_update`) → label "Data: …" ikut berubah.

Keamanan: path `path` dari manifest diperiksa dengan `getCanonicalPath()` — file yang mencoba
keluar dari folder data game ditolak (anti path traversal).

### Format `update.json`

```json
{
  "version": 2,
  "title": "Update Roleplay Oktober",
  "description": "Objek interior baru + perbaikan peta",
  "files": [
    {
      "path": "stream/objects.dff",
      "url": "https://cdn-kamu.vercel.app/assets/objects.dff",
      "size": 1048576,
      "sha256": "2c7c1e…"
    }
  ]
}
```

- `version` (wajib, integer, harus naik tiap rilis)
- `files[].path` (wajib) — lokasi relatif di folder data game
- `files[].url` (wajib) — URL unduh langsung (HTTPS)
- `files[].size` & `files[].sha256` (opsional, tapi disarankan)

### Cara merilis update

1. Letakkan file aset + `update.json` di hosting statis (mis. Vercel, Cloudflare Pages, GitHub Pages).
2. Set `update_manifest_url` di `strings.xml` ke URL `update.json` itu.
3. Untuk rilis berikutnya: ganti file aset, naikkan `version`, perbarui `size`/`sha256`, deploy ulang.

---

## Aksi cepat roleplay

Panel **AKSI CEPAT ROLEPLAY** di bagian bawah layar utama (scroll horizontal) berisi tombol:
`/me`, `/do`, `/ooc`, `/b`, `/report`, `/stats`, `/inv` — semua teks bisa diubah di `strings.xml`.

Cara kerja:

1. Ketuk tombol → dialog input muncul (terisi teks terakhir untuk perintah itu).
2. Isi teks (opsional) → ketuk **SALIN**.
3. Perintah jadi satu kalimat utuh (`/me berjalan ke bar`) dan disalin ke clipboard → tinggal tempel
   di chat game. Kalau teks kosong, hanya perintahnya yang disalin (`/stats`).

Teks terakhir tiap perintah disimpan di `SharedPreferences` (`quick_actions`) sebagai modal awal.

> Belum ada koneksi langsung ke dalam game (launcher terpisah dari proses SA-MP).
> Kalau nanti mau mengirim otomatis, tambahkan integrasinya di `showQuickActionDialog()` —
> saat ini perilakunya adalah salin-ke-clipboard.
