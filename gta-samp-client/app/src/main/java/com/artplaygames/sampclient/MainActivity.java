package com.artplaygames.sampclient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Vice Side Roleplay - Mobile Client Launcher (Landscape Mode)
 *
 * Alur Real-time:
 *   1. Splash Screen: Memeriksa kelengkapan file game nyata di Android/data/com.viceside.mobile/files/.
 *   2. Jika belum lengkap: Langsung beralih ke UI Unduh Game Data (Screenshot 2) tanpa dialog putih popup.
 *      Hanya ada SATU tombol: "DOWNLOAD SEKARANG" (tanpa tombol batal).
 *   3. Jika sudah lengkap: Membuka layar utama (Screenshot 1) dengan status Ready.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ViceSideClient";
    private static final String PREFS_SETTINGS = "vice_settings";
    private static final String KEY_MUTE_MUSIC = "mute_music";
    private static final String KEY_MUSIC_VOLUME = "music_volume";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";

    // ---- View Splash ----
    private FrameLayout splashContainer;
    private ProgressBar progressSplash;
    private TextView tvSplashFileStatus;

    // ---- View Konten Utama (Screenshot 1) ----
    private ConstraintLayout mainContent;
    private TextView tvWelcomeSubtitle;
    private TextView tvAvatarInitial;
    private TextView tvAccountBadge;
    private TextView tvAccountName;
    private TextView tvAccountEmail;
    private Button btnLogout;
    private Button btnGoogleSignIn;
    private Button btnMain;
    private Button btnSettings;

    // ---- Sub-panel Bawah ----
    private LinearLayout serverInfoContainer;
    private LinearLayout settingsContainer;
    private TextView tvStatus;
    private TextView tvServerAddress;
    private SwitchCompat swMuteMusic;
    private SeekBar sbMusicVolume;
    private SwitchCompat swReduceMotion;

    // ---- View Download Overlay (Screenshot 2) ----
    private ConstraintLayout downloadContainer;
    private TextView tvCardPercent;
    private TextView tvCardFilesCount;
    private ProgressBar progressCardDownload;
    private Button btnCardStartDownload;
    private TextView tvCardDownloadedMb;
    private TextView tvCardSpeed;
    private TextView tvCardRemaining;
    private TextView tvCardTotalSize;
    private TextView tvCardCurrentFile;
    private TextView tvCardTip;

    private UpdateManager.Manifest cachedManifest;
    private List<UpdateManager.PackageEntry> cachedNeededPackages;

    // ---- Lifecycle & Media ----
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private boolean loadingFinished = false;
    private boolean isResumed = false;
    private MediaPlayer bgmPlayer;
    private float currentVolume = 0.70f;
    private boolean isMuted = false;

    // ---- Google Sign-In ----
    private GoogleSignInClient signInClient;
    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult();
                    if (account != null) {
                        onSignInSuccess(account);
                    } else {
                        Toast.makeText(this, R.string.toast_login_failed, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Google Sign-In gagal", e);
                    Toast.makeText(this, R.string.toast_login_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    // ---- Updater Data Game ----
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Memaksimalkan layar ke area Display Cutout / Notch kamera (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        // 2. Set edge-to-edge penuh tanpa padding sistem
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        initViews();
        loadSettingsPreferences();
        setupImmersiveMode();
        setupGoogleSignIn();
        setupButtons();
        setupSettingsControls();

        updateManager = new UpdateManager(this, getString(R.string.version_manifest_url));

        // Tampilkan akun jika sudah ada sesi sebelumnya
        GoogleSignInAccount currentAccount = GoogleSignIn.getLastSignedInAccount(this);
        applyAuthUi(currentAccount);

        // Memulai pemindaian file nyata di splash screen
        startSplashProgressAnimation();
    }

    private void initViews() {
        splashContainer = findViewById(R.id.splashContainer);
        progressSplash = findViewById(R.id.progressLoadingHorizontal);
        tvSplashFileStatus = findViewById(R.id.tvSplashFileStatus);

        mainContent = findViewById(R.id.mainContent);
        tvWelcomeSubtitle = findViewById(R.id.tvWelcomeSubtitle);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        tvAccountBadge = findViewById(R.id.tvAccountBadge);
        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountEmail = findViewById(R.id.tvAccountEmail);
        btnLogout = findViewById(R.id.btnLogout);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        btnMain = findViewById(R.id.btnMain);
        btnSettings = findViewById(R.id.btnSettings);
        serverInfoContainer = findViewById(R.id.serverInfoContainer);
        settingsContainer = findViewById(R.id.settingsContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvServerAddress = findViewById(R.id.tvServerAddress);
        swMuteMusic = findViewById(R.id.swMuteMusic);
        sbMusicVolume = findViewById(R.id.sbMusicVolume);
        swReduceMotion = findViewById(R.id.swReduceMotion);

        // Download Overlay Views (Screenshot 2)
        downloadContainer = findViewById(R.id.downloadContainer);
        tvCardPercent = findViewById(R.id.tvCardPercent);
        tvCardFilesCount = findViewById(R.id.tvCardFilesCount);
        progressCardDownload = findViewById(R.id.progressCardDownload);
        btnCardStartDownload = findViewById(R.id.btnCardStartDownload);
        tvCardDownloadedMb = findViewById(R.id.tvCardDownloadedMb);
        tvCardSpeed = findViewById(R.id.tvCardSpeed);
        tvCardRemaining = findViewById(R.id.tvCardRemaining);
        tvCardTotalSize = findViewById(R.id.tvCardTotalSize);
        tvCardCurrentFile = findViewById(R.id.tvCardCurrentFile);
        tvCardTip = findViewById(R.id.tvCardTip);

        if (btnCardStartDownload != null) {
            btnCardStartDownload.setOnClickListener(v -> {
                if (cachedManifest != null && cachedNeededPackages != null && !cachedNeededPackages.isEmpty()) {
                    startCardDownload(cachedManifest, cachedNeededPackages);
                } else {
                    btnCardStartDownload.setEnabled(false);
                    btnCardStartDownload.setText("MEMERIKSA PAKET…");
                    updateManager.check(new UpdateManager.Listener() {
                        @Override
                        public void onUpdateAvailable(UpdateManager.Manifest manifest, List<UpdateManager.PackageEntry> neededPackages) {
                            cachedManifest = manifest;
                            cachedNeededPackages = neededPackages;
                            startCardDownload(manifest, neededPackages);
                        }
                        @Override
                        public void onNoUpdate() {
                            showMainContentScreen();
                        }
                        @Override
                        public void onCheckFailed(String message) {
                            btnCardStartDownload.setEnabled(true);
                            btnCardStartDownload.setText("COBA LAGI");
                            Toast.makeText(MainActivity.this, "Gagal koneksi: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    private void loadSettingsPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        isMuted = prefs.getBoolean(KEY_MUTE_MUSIC, false);
        int volInt = prefs.getInt(KEY_MUSIC_VOLUME, 70);
        currentVolume = volInt / 100f;
        boolean reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false);

        if (swMuteMusic != null) {
            swMuteMusic.setChecked(isMuted);
        }
        if (sbMusicVolume != null) {
            sbMusicVolume.setProgress(volInt);
        }
        if (swReduceMotion != null) {
            swReduceMotion.setChecked(reduceMotion);
        }
    }

    private void setupImmersiveMode() {
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        setupImmersiveMode();
        if (loadingFinished && !isMuted) {
            startBackgroundMusic();
        }
    }

    @Override
    protected void onPause() {
        isResumed = false;
        pauseBackgroundMusic();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) {
            updateManager.shutdown();
        }
        releaseBackgroundMusic();
        super.onDestroy();
    }

    // ==================================================================
    //  1. Splash Screen & Pemeriksaan File Nyata (Real-time File Scanning)
    // ==================================================================
    private void startSplashProgressAnimation() {
        if (progressSplash == null) return;
        progressSplash.setProgress(5);
        if (tvSplashFileStatus != null) {
            tvSplashFileStatus.setText("Memeriksa data file game…");
        }

        // Jalankan pengecekan file fisik di latar belakang
        new Thread(() -> {
            File gameDir = updateManager.getGameDataDir();
            final int totalTargetFiles = 7152;
            int scannedFiles = 0;

            if (gameDir != null && gameDir.exists()) {
                scannedFiles = countFilesRecursive(gameDir, totalTargetFiles);
            }

            final int finalScanned = scannedFiles;
            final boolean isComplete = updateManager.isGameDataComplete();

            // Animasi transisi progres berbasis scan nyata
            int targetProgress = isComplete ? 100 : Math.min(85, Math.max(15, (finalScanned * 100) / totalTargetFiles));
            for (int p = 15; p <= targetProgress; p += 15) {
                final int currentP = p;
                runOnUiThread(() -> {
                    if (isAlive() && progressSplash != null) {
                        progressSplash.setProgress(currentP);
                    }
                });
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            }

            runOnUiThread(() -> {
                if (!isAlive()) return;
                if (isComplete) {
                    if (progressSplash != null) progressSplash.setProgress(100);
                    if (tvSplashFileStatus != null) {
                        tvSplashFileStatus.setText(R.string.splash_files_done);
                    }
                    splashHandler.postDelayed(() -> finishSplash(true), 400);
                } else {
                    if (tvSplashFileStatus != null) {
                        tvSplashFileStatus.setText("Memeriksa file (" + finalScanned + " / " + totalTargetFiles + "): Belum lengkap");
                    }
                    splashHandler.postDelayed(() -> finishSplash(false), 500);
                }
            });
        }).start();
    }

    private int countFilesRecursive(File dir, int limit) {
        if (dir == null || !dir.exists()) return 0;
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countFilesRecursive(f, limit - count);
            } else {
                count++;
            }
            if (count >= limit) break;
        }
        return count;
    }

    private void finishSplash(boolean isComplete) {
        if (!isAlive()) return;
        loadingFinished = true;

        if (splashContainer != null) {
            splashContainer.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction(() -> splashContainer.setVisibility(View.GONE))
                    .start();
        }

        if (isResumed && !isMuted) {
            startBackgroundMusic();
        }

        if (isComplete) {
            showMainContentScreen();
        } else {
            // Langsung tampilkan UI Unduh Screenshot 2 tanpa dialog popup putih!
            showDownloadScreen();
        }
    }

    private void showMainContentScreen() {
        if (downloadContainer != null) {
            downloadContainer.setVisibility(View.GONE);
        }
        if (mainContent != null) {
            mainContent.setVisibility(View.VISIBLE);
            mainContent.animate().alpha(1f).setDuration(450).start();
        }
    }

    private void showDownloadScreen() {
        if (mainContent != null) {
            mainContent.setVisibility(View.GONE);
        }
        if (downloadContainer != null) {
            downloadContainer.setAlpha(0f);
            downloadContainer.setVisibility(View.VISIBLE);
            downloadContainer.animate().alpha(1f).setDuration(450).start();
        }

        if (btnCardStartDownload != null) {
            btnCardStartDownload.setVisibility(View.VISIBLE);
            btnCardStartDownload.setText("DOWNLOAD SEKARANG");
        }

        // Cek detail paket dari version.json
        updateManager.check(new UpdateManager.Listener() {
            @Override
            public void onUpdateAvailable(UpdateManager.Manifest manifest, List<UpdateManager.PackageEntry> neededPackages) {
                cachedManifest = manifest;
                cachedNeededPackages = neededPackages;
                long totalBytes = 0;
                for (UpdateManager.PackageEntry p : neededPackages) {
                    totalBytes += p.size;
                }
                if (tvCardTotalSize != null) {
                    tvCardTotalSize.setText(String.format(Locale.US, "%.2f GB", totalBytes / (1024f * 1024f * 1024f)));
                }
                if (tvCardCurrentFile != null && !neededPackages.isEmpty()) {
                    tvCardCurrentFile.setText(neededPackages.get(0).name);
                }
            }

            @Override
            public void onNoUpdate() {
                showMainContentScreen();
            }

            @Override
            public void onCheckFailed(String message) {
                if (tvCardCurrentFile != null) {
                    tvCardCurrentFile.setText("Menunggu koneksi internet...");
                }
            }
        });
    }

    private void startCardDownload(final UpdateManager.Manifest manifest, final List<UpdateManager.PackageEntry> queue) {
        if (btnCardStartDownload != null) {
            btnCardStartDownload.setVisibility(View.GONE);
        }

        updateManager.download(manifest, queue, new UpdateManager.Listener() {
            @Override
            public void onProgress(int percent, String detail, String speedText) {
                if (!isAlive()) return;
                if (progressCardDownload != null) {
                    progressCardDownload.setProgress(percent);
                }
                if (tvCardPercent != null) {
                    tvCardPercent.setText(percent + "%");
                }
                if (tvCardCurrentFile != null) {
                    tvCardCurrentFile.setText(detail);
                }
                if (tvCardSpeed != null) {
                    tvCardSpeed.setText(speedText.contains("•") ? speedText.split("•")[0].trim() : speedText);
                }
                if (tvCardDownloadedMb != null && speedText.contains("•")) {
                    String[] parts = speedText.split("•");
                    if (parts.length > 1) {
                        tvCardDownloadedMb.setText(parts[1].trim());
                    }
                }
                if (tvCardFilesCount != null) {
                    int estFiles = (int) ((percent / 100f) * 7152);
                    tvCardFilesCount.setText(estFiles + " / 7152 files");
                }
            }

            @Override
            public void onInstalled(UpdateManager.Manifest manifest) {
                if (!isAlive()) return;
                Toast.makeText(MainActivity.this,
                        "Data game berhasil dipasang & siap dimainkan!",
                        Toast.LENGTH_LONG).show();
                if (tvStatus != null) {
                    tvStatus.setText(R.string.status_ready);
                }
                showMainContentScreen();
            }

            @Override
            public void onFailed(String error) {
                if (!isAlive()) return;
                Toast.makeText(MainActivity.this,
                        "Unduhan gagal: " + error,
                        Toast.LENGTH_LONG).show();
                if (btnCardStartDownload != null) {
                    btnCardStartDownload.setVisibility(View.VISIBLE);
                    btnCardStartDownload.setText("COBA LAGI");
                }
            }
        });
    }

    // ==================================================================
    //  2. Audio / Background Music
    // ==================================================================
    private void startBackgroundMusic() {
        if (isMuted) return;
        if (bgmPlayer == null) {
            int resId = getResources().getIdentifier("bgm", "raw", getPackageName());
            if (resId == 0) return;
            try {
                bgmPlayer = MediaPlayer.create(this, resId);
                if (bgmPlayer != null) {
                    bgmPlayer.setLooping(true);
                    bgmPlayer.setVolume(currentVolume, currentVolume);
                    bgmPlayer.start();
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal memutar musik", e);
            }
        } else if (!bgmPlayer.isPlaying()) {
            bgmPlayer.setVolume(currentVolume, currentVolume);
            bgmPlayer.start();
        }
    }

    private void pauseBackgroundMusic() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    private void releaseBackgroundMusic() {
        if (bgmPlayer != null) {
            try {
                if (bgmPlayer.isPlaying()) bgmPlayer.stop();
                bgmPlayer.release();
            } catch (Exception ignored) {}
            bgmPlayer = null;
        }
    }

    // ==================================================================
    //  3. Google Sign-In & Tampilan Akun
    // ==================================================================
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);
    }

    private void onSignInSuccess(GoogleSignInAccount account) {
        applyAuthUi(account);
        String name = account.getDisplayName() != null ? account.getDisplayName() : "Pemain";
        Toast.makeText(this, getString(R.string.toast_signed_in, name), Toast.LENGTH_SHORT).show();
    }

    private void signOut() {
        if (signInClient != null) {
            signInClient.signOut().addOnCompleteListener(this, task -> {
                applyAuthUi(null);
                Toast.makeText(this, R.string.toast_signed_out, Toast.LENGTH_SHORT).show();
            });
        } else {
            applyAuthUi(null);
        }
    }

    private void applyAuthUi(GoogleSignInAccount account) {
        boolean signedIn = (account != null);
        if (signedIn) {
            String name = account.getDisplayName() != null ? account.getDisplayName() : "Pemain";
            String email = account.getEmail() != null ? account.getEmail() : "-";
            tvAccountName.setText(name);
            tvAccountEmail.setText(email);
            tvAccountBadge.setText(R.string.account_connected);
            tvAccountBadge.setTextColor(getColor(R.color.status_connected_green));
            tvWelcomeSubtitle.setText(getString(R.string.welcome_back, name));
            String initial = name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            tvAvatarInitial.setText(initial);
            btnLogout.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.GONE);
        } else {
            tvAccountBadge.setText(R.string.account_disconnected);
            tvAccountBadge.setTextColor(getColor(R.color.text_muted));
            tvAccountName.setText(R.string.guest_user);
            tvAccountEmail.setText(R.string.guest_email);
            tvWelcomeSubtitle.setText(R.string.welcome_guest);
            tvAvatarInitial.setText("?");
            btnLogout.setVisibility(View.GONE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
        }
    }

    // ==================================================================
    //  4. Tombol Aksi Utama (PLAY, SETTINGS, dll.)
    // ==================================================================
    private void setupButtons() {
        btnMain.setOnClickListener(v -> {
            if (!updateManager.isGameDataComplete()) {
                showDownloadScreen();
                return;
            }
            connectToServer();
        });

        btnSettings.setOnClickListener(v -> {
            if (settingsContainer.getVisibility() == View.VISIBLE) {
                settingsContainer.setVisibility(View.GONE);
                serverInfoContainer.setVisibility(View.VISIBLE);
            } else {
                serverInfoContainer.setVisibility(View.GONE);
                settingsContainer.setVisibility(View.VISIBLE);
            }
        });

        btnLogout.setOnClickListener(v -> signOut());
        btnGoogleSignIn.setOnClickListener(v -> signInLauncher.launch(signInClient.getSignInIntent()));
    }

    private void setupSettingsControls() {
        swMuteMusic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isMuted = isChecked;
            getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_MUTE_MUSIC, isMuted)
                    .apply();
            if (isMuted) {
                pauseBackgroundMusic();
            } else if (isResumed && loadingFinished) {
                startBackgroundMusic();
            }
        });

        sbMusicVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentVolume = progress / 100f;
                if (bgmPlayer != null) {
                    bgmPlayer.setVolume(currentVolume, currentVolume);
                }
                getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_MUSIC_VOLUME, progress)
                        .apply();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        swReduceMotion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_REDUCE_MOTION, isChecked)
                    .apply();
        });
    }

    private void connectToServer() {
        String host = getString(R.string.server_host);
        String port = getString(R.string.server_port);
        tvStatus.setText(R.string.status_connecting);
        Toast.makeText(this, getString(R.string.toast_connecting, host, port), Toast.LENGTH_SHORT).show();

        boolean launched = tryLaunchGameClient(host, port);
        if (!launched) {
            splashHandler.postDelayed(() -> {
                if (isAlive()) {
                    tvStatus.setText(R.string.status_ready);
                }
            }, 1200);
        }
    }

    private boolean tryLaunchGameClient(String host, String port) {
        String[] targetPackages = new String[]{
                "com.russia.game",
                "com.viceside.mobile",
                "com.rockstargames.gtasa",
                "com.artplaygames.sampclient",
                "ru.unisamp_mobile.game",
                "com.samp.mobile"
        };
        for (String pkg : targetPackages) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null && !pkg.equals(getPackageName())) {
                intent.putExtra("server", host);
                intent.putExtra("port", port);
                intent.putExtra("cef_url", getString(R.string.cef_webview_url));
                intent.putExtra("auth_url", "https://openmp-gm.vercel.app/auth/google");
                try {
                    startActivity(intent);
                    return true;
                } catch (Exception e) {
                    Log.w(TAG, "Gagal meluncurkan game pkg: " + pkg, e);
                }
            }
        }
        return false;
    }

    private boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }
}
