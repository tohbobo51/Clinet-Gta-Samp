# Panduan Integrasi CEF / WebView untuk Client GTA SAMP Android (APK)

Client Game GTA SA-MP Android bawaan secara default tidak memiliki browser engine internal untuk merender halaman HTML/CSS/JavaScript maupun menghubungkan event JavaScript (`cef.emit`) ke server Pawn Open.MP.

Dokumen ini menjelaskan **2 Komponen Utama** yang wajib ada di dalam APK Client serta cara penerapannya.

---

## 1. Komponen Utama di Sisi Client (APK)

### A. Plugin CEF / WebView Native (`.so`)
Di dalam folder native library APK Android (`lib/armeabi-v7a/` atau `lib/arm64-v8a/`), harus disematkan library mesin peramban web:
- **`libcef.so`** atau **`libwebview.so`**: Mesin rendering Chromium Embedded Framework / Android WebKit offscreen.
- **`libmain.so`** / **`libsamp.so`**: Library inti GTA SA-MP yang telah di-patch / di-hook agar memanggil layer render WebView di atas frame buffer Direct3D/OpenGL ES game.
- **Fungsi**: Membuka dan menampilkan halaman web autentikasi Google (`https://openmp-gm.vercel.app/auth/google`) serta formulir pendaftaran karakter IC langsung di atas layar permainan tanpa keluar dari aplikasi.

### B. Bridge / Interface Communication (`samp_cef`)
Library bridge komunikasi dua arah antara DOM JavaScript dan gamemode Open.MP:
- **Alur JavaScript ke Pawn**:
  Ketika tombol formulir di web ditekan, script memanggil:
  ```javascript
  cef.emit("OnGoogleLogin", email, googleId, ucpName);
  // atau
  cef.emit("OnGoogleRegister", email, googleId, ucpName, charName, birthplace, birthdate, gender, height, weight);
  ```
  Library native `.so` menangkap pemanggilan fungsi C++ bridge dan mengemasnya ke dalam paket jaringan UDP menuju port CEF Open.MP server (`10127`).
- **Alur Pawn ke JavaScript**:
  Ketika server mengirim event ke browser:
  ```pawn
  CEF_EmitEvent(playerid, browserid, "updateStats", CEF_INT(money), CEF_FLOAT(health), CEF_FLOAT(armour));
  ```
  Client mengeksekusi callback JavaScript `cef.on("updateStats", ...)` untuk memperbarui DOM secara langsung.

---

## 2. Langkah Praktis Penyiapan APK untuk Pemain

1. **Gunakan Base Client SAMP Android yang Sudah Mendukung CEF**:
   - Komunitas modding SA-MP Mobile telah menyediakan base APK yang sudah ter-inject CEF dan bridge event, seperti:
     - **Alyn SAMP Mobile Client (CEF Edition)**
     - **Unisamp Mobile CEF**
     - **Black Russia / Smart RP Client Engine**
2. **Koneksi Server Otomatis Melalui Launcher**:
   - Launcher `Vice Side Mobile` (`MainActivity.java`) secara otomatis mengirimkan parameter koneksi saat meluncurkan game:
     - **Server Host**: `142.132.203.47`
     - **Server Port**: `10125`
     - **CEF Web URL**: `https://openmp-gm.vercel.app/auth/google`
3. **Distribusi ke Pemain**:
   - Paket APK siap pakai dibagikan langsung kepada pemain sehingga pemain cukup memasang satu aplikasi tanpa perlu konfigurasi teknis manual.
