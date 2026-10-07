package com.artplaygames.sampclient;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Vice Side Roleplay - Mobile Client Launcher (Landscape Mode)
 *
 * Alur Kerja:
 *   1. Splash Screen: Memeriksa kelengkapan file game secara case-insensitive.
 *   2. Unduh Game Data: Menghilangkan download loop berulang, file otomatis di-unzip ke data folder.
 *   3. Login Google: Menghubungkan akun Google dan menyelaraskan nickname SA-MP roleplay.
 *   4. Tombol PLAY: Menulis konfigurasi SAMP/settings.ini (IP, Port, Nickname) dan meluncurkan
 *      game SA-MP APK (com.russia.game / com.rockstargames.gtasa / com.viceside.mobile).
 *   5. Jika APK Game belum terpasang di HP, launcher menyediakan tombol 1-klik untuk mengunduh APK.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ViceSideClient";
    private static final String PREFS_SETTINGS = "vice_settings";
    private static final String KEY_MUTE_MUSIC = "mute_music";
    private static final String KEY_MUSIC_VOLUME = "music_volume";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";
    private static final String KEY_PLAYER_NICKNAME = "player_nickname";

    // ---- View Splash ----
    private FrameLayout splashContainer;
    private ProgressBar progressSplash;
    private TextView tvSplashFileStatus;

    // ---- View Konten Utama ----
    private ConstraintLayout mainContent;
    private View accountBox;
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

    // ---- View Download Overlay ----
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
    private UpdateManager updateManager;

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
                    Log.w(TAG, "Login Google gagal/batal: " + e.getMessage());
                    Toast.makeText(this, R.string.toast_login_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

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

        // Memulai pemindaian file game di splash screen
        startSplashProgressAnimation();
    }

    private void initViews() {
        splashContainer = findViewById(R.id.splashContainer);
        progressSplash = findViewById(R.id.progressLoadingHorizontal);
        tvSplashFileStatus = findViewById(R.id.tvSplashFileStatus);

        mainContent = findViewById(R.id.mainContent);
        accountBox = findViewById(R.id.accountBox);
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

        // Download Overlay Views
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
                            Toast.makeText(MainActivity.this, getString(R.string.update_check_failed, message), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    private void loadSettingsPreferences() {
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        isMuted = sp.getBoolean(KEY_MUTE_MUSIC, false);
        int vol = sp.getInt(KEY_MUSIC_VOLUME, 70);
        currentVolume = vol / 100f;
        boolean reduceMotion = sp.getBoolean(KEY_REDUCE_MOTION, false);

        if (swMuteMusic != null) swMuteMusic.setChecked(isMuted);
        if (sbMusicVolume != null) sbMusicVolume.setProgress(vol);
        if (swReduceMotion != null) swReduceMotion.setChecked(reduceMotion);
    }

    private void setupImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
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
        if (loadingFinished && !isMuted) {
            startBackgroundMusic();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        pauseBackgroundMusic();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseBackgroundMusic();
        if (updateManager != null) {
            updateManager.shutdown();
        }
    }

    // ==================================================================
    //  1. Splash & Scanning File Fisik
    // ==================================================================

    private void startSplashProgressAnimation() {
        if (progressSplash == null) return;
        progressSplash.setProgress(10);
        if (tvSplashFileStatus != null) {
            tvSplashFileStatus.setText("Memeriksa data file game…");
        }

        new Thread(() -> {
            File gameDir = updateManager.getGameDataDir();
            final int totalTargetFiles = 7152;
            int scannedFiles = 0;
            if (gameDir != null && gameDir.exists()) {
                scannedFiles = countFilesRecursive(gameDir, totalTargetFiles);
            }
            final int finalScanned = scannedFiles;
            final boolean isComplete = updateManager.isGameDataComplete();

            int targetProgress = isComplete ? 100 : Math.min(85, Math.max(25, (finalScanned * 100) / totalTargetFiles));

            for (int p = 15; p <= targetProgress; p += 15) {
                final int currentP = p;
                runOnUiThread(() -> {
                    if (isAlive() && progressSplash != null) {
                        progressSplash.setProgress(currentP);
                    }
                });
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }

            runOnUiThread(() -> {
                if (!isAlive()) return;
                if (isComplete) {
                    if (progressSplash != null) progressSplash.setProgress(100);
                    if (tvSplashFileStatus != null) {
                        tvSplashFileStatus.setText(R.string.splash_files_done);
                    }
                    splashHandler.postDelayed(() -> finishSplash(true), 350);
                } else {
                    if (tvSplashFileStatus != null) {
                        tvSplashFileStatus.setText("Memeriksa file (" + finalScanned + " / " + totalTargetFiles + "): Siap unduh");
                    }
                    splashHandler.postDelayed(() -> finishSplash(false), 450);
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

    public static String sanitizeNickname(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "ViceSide_Player";
        String s = raw.trim().replace(" ", "_");
        if (s.contains("@")) {
            s = s.substring(0, s.indexOf("@"));
        }
        s = s.replaceAll("[^a-zA-Z0-9_]", "");
        if (s.length() > 20) s = s.substring(0, 20);
        if (s.length() < 3) s = "Player_" + s;
        return s;
    }

    private void onSignInSuccess(GoogleSignInAccount account) {
        applyAuthUi(account);
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getEmail();
        String cleanNick = sanitizeNickname(name);

        getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
                .edit()
                .putString(KEY_PLAYER_NICKNAME, cleanNick)
                .apply();

        writeSampSettings(updateManager.getGameDataDir(),
                getString(R.string.server_host),
                getString(R.string.server_port),
                cleanNick);

        Toast.makeText(this, getString(R.string.toast_signed_in, cleanNick), Toast.LENGTH_SHORT).show();
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
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String savedNick = sp.getString(KEY_PLAYER_NICKNAME, "");

        if (signedIn) {
            String name = account.getDisplayName() != null ? account.getDisplayName() : "Pemain";
            String email = account.getEmail() != null ? account.getEmail() : "-";
            String nick = savedNick.isEmpty() ? sanitizeNickname(name) : savedNick;

            tvAccountName.setText(nick);
            tvAccountEmail.setText(email);
            tvAccountBadge.setText(R.string.account_connected);
            tvAccountBadge.setTextColor(getColor(R.color.status_connected_green));
            tvWelcomeSubtitle.setText(getString(R.string.welcome_back, nick));
            String initial = nick.isEmpty() ? "P" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
            tvAvatarInitial.setText(initial);
            btnLogout.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.GONE);
        } else {
            String nick = savedNick.isEmpty() ? getString(R.string.guest_user) : savedNick;
            tvAccountBadge.setText(R.string.account_disconnected);
            tvAccountBadge.setTextColor(getColor(R.color.text_muted));
            tvAccountName.setText(nick);
            tvAccountEmail.setText(R.string.guest_email);
            tvWelcomeSubtitle.setText(R.string.welcome_guest);
            tvAvatarInitial.setText("?");
            btnLogout.setVisibility(View.GONE);
            btnGoogleSignIn.setVisibility(View.VISIBLE);
        }
    }

    private void showChangeNicknameDialog() {
        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String current = sp.getString(KEY_PLAYER_NICKNAME, "ViceSide_Player");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(current);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_change_nickname_title)
                .setMessage("Format SA-MP: Huruf, angka, garis bawah (cth: Nama_Karakter)")
                .setView(input)
                .setPositiveButton("SIMPAN", (d, which) -> {
                    String clean = sanitizeNickname(input.getText().toString());
                    sp.edit().putString(KEY_PLAYER_NICKNAME, clean).apply();
                    applyAuthUi(GoogleSignIn.getLastSignedInAccount(this));
                    writeSampSettings(updateManager.getGameDataDir(),
                            getString(R.string.server_host),
                            getString(R.string.server_port),
                            clean);
                    Toast.makeText(this, "Nickname diubah: " + clean, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("BATAL", null)
                .show();
    }

    // ==================================================================
    //  4. Tombol Aksi Utama (PLAY, SETTINGS, & Koneksi Server)
    // ==================================================================

    private void setupButtons() {
        btnMain.setOnClickListener(v -> {
            if (!updateManager.isGameDataComplete()) {
                showDownloadScreen();
                return;
            }
            connectToServer();
        });

        if (accountBox != null) {
            accountBox.setOnClickListener(v -> showChangeNicknameDialog());
        }

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

    /**
     * Menulis konfigurasi SAMP/settings.ini agar mesin C++ SA-MP dapat membaca IP, Port, dan Nickname.
     */
    public static void writeSampSettings(File gameDir, String host, String port, String nickname) {
        if (gameDir == null) return;
        try {
            File sampDir = new File(gameDir, "SAMP");
            if (!sampDir.exists()) sampDir.mkdirs();
            File settingsFile = new File(sampDir, "settings.ini");

            String safeName = (nickname != null && !nickname.trim().isEmpty())
                    ? sanitizeNickname(nickname) : "ViceSide_Player";

            String content = "[client]\n"
                    + "ip=" + host + "\n"
                    + "port=" + port + "\n"
                    + "name=" + safeName + "\n"
                    + "password=\n"
                    + "autologin=0\n"
                    + "server=0\n"
                    + "debug=0\n"
                    + "[gui]\n"
                    + "Font=visby-round-cf-extra-bold.ttf\n"
                    + "fps=60\n";

            try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            // Upayakan sinkronisasi ke folder game com.russia.game jika dapat diakses
            try {
                File russiaDir = new File("/storage/emulated/0/Android/data/com.russia.game/files/SAMP");
                if (russiaDir.exists() || russiaDir.mkdirs()) {
                    File rSettings = new File(russiaDir, "settings.ini");
                    try (FileOutputStream rfos = new FileOutputStream(rSettings)) {
                        rfos.write(content.getBytes(StandardCharsets.UTF_8));
                        rfos.flush();
                    }
                }
            } catch (Throwable ignored) {}

            // Sync fallback ke /sdcard/SAMP/settings.ini
            try {
                File sdcardSamp = new File(Environment.getExternalStorageDirectory(), "SAMP");
                if (sdcardSamp.exists() || sdcardSamp.mkdirs()) {
                    File sdSettings = new File(sdcardSamp, "settings.ini");
                    try (FileOutputStream sdfos = new FileOutputStream(sdSettings)) {
                        sdfos.write(content.getBytes(StandardCharsets.UTF_8));
                        sdfos.flush();
                    }
                }
            } catch (Throwable ignored) {}

        } catch (Exception e) {
            Log.w(TAG, "Gagal menulis SAMP/settings.ini: " + e.getMessage());
        }
    }

    private void connectToServer() {
        String host = getString(R.string.server_host);
        String port = getString(R.string.server_port);

        SharedPreferences sp = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        String nickname = sp.getString(KEY_PLAYER_NICKNAME, "ViceSide_Player");

        tvStatus.setText(R.string.status_connecting);
        Toast.makeText(this, getString(R.string.toast_connecting, host, port), Toast.LENGTH_SHORT).show();

        // 1. Tulis settings.ini sebelum meluncurkan game
        writeSampSettings(updateManager.getGameDataDir(), host, port, nickname);

        // 2. Luncurkan game
        boolean launched = tryLaunchGameClient(host, port, nickname);
        if (!launched) {
            tvStatus.setText(R.string.status_ready);
            showGameNotInstalledDialog();
        } else {
            tvStatus.setText(R.string.status_connected);
        }
    }

    private boolean tryLaunchGameClient(String host, String port, String nickname) {
        String[] targetPackages = new String[]{
                "com.russia.game",
                "com.rockstargames.gtasa",
                "ru.unisamp_mobile.game",
                "com.samp.mobile",
                "com.artplaygames.sampclient"
        };

        // 1. Prioritaskan Component eksplisit ke com.russia.game.core.Samp (mesin game C++ GTA SA 2.10)
        try {
            Intent directIntent = new Intent(Intent.ACTION_VIEW);
            directIntent.setComponent(new ComponentName("com.russia.game", "com.russia.game.core.Samp"));
            directIntent.putExtra("server", host);
            directIntent.putExtra("ip", host);
            directIntent.putExtra("port", port);
            directIntent.putExtra("port_str", port);
            directIntent.putExtra("nick", nickname);
            directIntent.putExtra("name", nickname);
            directIntent.putExtra("cef_url", getString(R.string.cef_webview_url));
            directIntent.putExtra("auth_url", "https://openmp-gm.vercel.app/auth/google");
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(directIntent);
            return true;
        } catch (Exception ignored) {
            // Lanjut ke metode launch intent jika component eksplisit belum tersedia
        }

        // 2. Coba getLaunchIntentForPackage pada paket game yang terpasang
        for (String pkg : targetPackages) {
            if (pkg.equals(getPackageName())) continue;
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.putExtra("server", host);
                    intent.putExtra("port", port);
                    intent.putExtra("name", nickname);
                    intent.putExtra("cef_url", getString(R.string.cef_webview_url));
                    intent.putExtra("auth_url", "https://openmp-gm.vercel.app/auth/google");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal meluncurkan game pkg: " + pkg, e);
            }
        }
        return false;
    }

    private void showGameNotInstalledDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.game_not_installed_title)
                .setMessage(R.string.game_not_installed_msg)
                .setPositiveButton(R.string.btn_download_apk, (dialog, which) -> {
                    String apkUrl = getString(R.string.game_apk_download_url);
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                    startActivity(browserIntent);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }
}
