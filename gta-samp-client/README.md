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
│       │   └── MainActivity.java     # MediaPlayer, Google Sign-In, animasi, splash
│       └── res/
│           ├── layout/activity_main.xml      # utama: ConstraintLayout
│           ├── values/{strings,colors,themes}.xml
│           ├── drawable/                     # bg_gradient, panel_bg, btn_google/logout/main
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
