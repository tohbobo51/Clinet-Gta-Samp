package com.artplaygames.sampclient;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

/**
 * Vice Side Roleplay - Mobile Client Launcher (Landscape Mode)
 *
 * Alur:
 *   1. Splash Screen Landscape: Logo, bar progres horizontal, pengecekan data game.
 *   2. Main Screen: Kartu panel kiri modern, info akun Google, tombol PLAY & SETTINGS.
 *   3. Panel SETTINGS: Toggle matikan musik, volume slider, kurangi animasi.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ViceSideClient";

    private static final long SPLASH_DURATION_MS = 2400L;
    private static final String PREFS_SETTINGS = "vice_settings";
    private static final String KEY_MUTE_MUSIC = "mute_music";
    private static final String KEY_MUSIC_VOLUME = "music_volume";
    private static final String KEY_REDUCE_MOTION = "reduce_motion";

    // ---- View Splash ----
    private FrameLayout splashContainer;
    private ProgressBar progressSplash;
    private TextView tvSplashFileStatus;

    // ---- View Konten Utama ----
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

    // ---- Lifecycle & Media ----
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable splashRunnable = this::finishSplash;
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

    // ---- Update data game ----
    private UpdateManager updateManager;
    private AlertDialog updateProgressDialog;
    private ProgressBar updateProgressBar;
    private TextView updateProgressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadSettingsPreferences();
        setupImmersiveMode();
        setupGoogleSignIn();
        setupButtons();
        setupSettingsControls();

        updateManager = new UpdateManager(this, getString(R.string.update_manifest_url));

        // Tampilkan akun jika sudah ada sesi sebelumnya
        GoogleSignInAccount currentAccount = GoogleSignIn.getLastSignedInAccount(this);
        applyAuthUi(currentAccount);

        // Memulai simulasi progres loading file game di splash screen
        startSplashProgressAnimation();
        splashHandler.postDelayed(splashRunnable, SPLASH_DURATION_MS);
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
        setupImmersiveMode();
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
        splashHandler.removeCallbacks(splashRunnable);
        dismissProgressDialog();
        if (updateManager != null) {
            updateManager.shutdown();
        }
        releaseBackgroundMusic();
        super.onDestroy();
    }

    // ==================================================================
    //  1. Splash Screen & Progres Pengecekan Berkas
    // ==================================================================
    private void startSplashProgressAnimation() {
        if (progressSplash == null) return;
        progressSplash.setProgress(15);

        splashHandler.postDelayed(() -> {
            if (isAlive()) {
                progressSplash.setProgress(55);
            }
        }, 700);

        splashHandler.postDelayed(() -> {
            if (isAlive()) {
                progressSplash.setProgress(88);
            }
        }, 1500);

        splashHandler.postDelayed(() -> {
            if (isAlive()) {
                progressSplash.setProgress(100);
                if (tvSplashFileStatus != null) {
                    tvSplashFileStatus.setText(R.string.splash_files_done);
                }
            }
        }, 2000);
    }

    private void finishSplash() {
        if (!isAlive()) {
            return;
        }
        loadingFinished = true;

        mainContent.setVisibility(View.VISIBLE);
        mainContent.animate()
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        splashContainer.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> splashContainer.setVisibility(View.GONE))
                .start();

        if (isResumed && !isMuted) {
            startBackgroundMusic();
        }

        // Cek update aset game di latar belakang
        checkForUpdates(false);
    }

    // ==================================================================
    //  2. Audio / Background Music
    // ==================================================================
    private void startBackgroundMusic() {
        if (isMuted) {
            return;
        }
        if (bgmPlayer == null) {
            int resId = getResources().getIdentifier("bgm", "raw", getPackageName());
            if (resId == 0) {
                Log.w(TAG, "Musik latar tidak ditemukan — letakkan file di res/raw/bgm.mp3");
                return;
            }
            try {
                bgmPlayer = MediaPlayer.create(this, resId);
                if (bgmPlayer == null) {
                    Log.w(TAG, "MediaPlayer gagal dibuat untuk raw/bgm");
                    return;
                }
                bgmPlayer.setLooping(true);
                bgmPlayer.setVolume(currentVolume, currentVolume);
            } catch (Exception e) {
                Log.e(TAG, "Gagal memuat musik latar", e);
                bgmPlayer = null;
                return;
            }
        }
        if (!bgmPlayer.isPlaying()) {
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
                bgmPlayer.stop();
            } catch (Exception e) {
                Log.w(TAG, "stop() MediaPlayer gagal", e);
            }
            bgmPlayer.release();
            bgmPlayer = null;
        }
    }

    // ==================================================================
    //  3. Autentikasi Google
    // ==================================================================
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);
    }

    private void onSignInSuccess(GoogleSignInAccount account) {
        String name = account.getDisplayName() != null ? account.getDisplayName() : "Pemain";
        Toast.makeText(this, getString(R.string.toast_signed_in, name), Toast.LENGTH_SHORT).show();
        applyAuthUi(account);
        if (tvStatus != null) {
            tvStatus.setText(R.string.status_ready);
        }
    }

    private void signOut() {
        signInClient.signOut().addOnCompleteListener(this, task -> {
            applyAuthUi(null);
            Toast.makeText(this, R.string.toast_signed_out, Toast.LENGTH_SHORT).show();
        });
    }

    private void applyAuthUi(GoogleSignInAccount account) {
        boolean signedIn = (account != null);
        if (signedIn) {
            String name = account.getDisplayName() != null ? account.getDisplayName() : "Gtasamp01212";
            String email = account.getEmail() != null ? account.getEmail() : "gtasamp01212@gmail.com";

            tvAccountBadge.setText(R.string.account_connected);
            tvAccountBadge.setTextColor(getResources().getColor(R.color.status_connected_green));
            tvAccountName.setText(name);
            tvAccountEmail.setText(email);
            tvWelcomeSubtitle.setText(getString(R.string.welcome_back, name));

            String initial = !name.isEmpty() ? name.substring(0, 1).toUpperCase() : "G";
            tvAvatarInitial.setText(initial);

            btnLogout.setVisibility(View.VISIBLE);
            btnGoogleSignIn.setVisibility(View.GONE);
        } else {
            tvAccountBadge.setText(R.string.account_disconnected);
            tvAccountBadge.setTextColor(getResources().getColor(R.color.text_muted));
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
        btnMain.setOnClickListener(v -> connectToServer());

        btnSettings.setOnClickListener(v -> {
            // Toggle antara Tampilan Server Info dan Panel Pengaturan
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

        // Coba buka package game SA-MP jika terpasang
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

    // ==================================================================
    //  5. Update Data Game
    // ==================================================================
    private void checkForUpdates(final boolean userInitiated) {
        updateManager.check(new UpdateManager.Listener() {
            @Override
            public void onNoUpdate() {
                if (!isAlive()) return;
                if (userInitiated) {
                    Toast.makeText(MainActivity.this, R.string.update_none, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onUpdateAvailable(UpdateManager.Manifest manifest) {
                if (!isAlive()) return;
                showUpdateDialog(manifest);
            }

            @Override
            public void onCheckFailed(String message) {
                if (!isAlive()) return;
                if (userInitiated) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_check_failed, message),
                            Toast.LENGTH_LONG).show();
                } else {
                    Log.w(TAG, "Cek update otomatis: " + message);
                }
            }
        });
    }

    private void showUpdateDialog(final UpdateManager.Manifest manifest) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setMessage(getString(R.string.update_available,
                        manifest.title, manifest.files.size(), manifest.version,
                        manifest.description))
                .setPositiveButton(R.string.update_btn_download, (dialog, which) -> startDownload(manifest))
                .setNegativeButton(R.string.update_btn_later, null)
                .show();
    }

    private void startDownload(final UpdateManager.Manifest manifest) {
        showProgressDialog();
        updateManager.download(manifest, new UpdateManager.Listener() {
            @Override
            public void onProgress(int percent, String detail) {
                if (updateProgressBar == null || updateProgressText == null) return;
                updateProgressBar.setProgress(percent);
                updateProgressText.setText(detail);
            }

            @Override
            public void onInstalled(UpdateManager.Manifest manifest) {
                dismissProgressDialog();
                if (!isAlive()) return;
                Toast.makeText(MainActivity.this,
                        getString(R.string.update_installed, manifest.title),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed(String error) {
                dismissProgressDialog();
                if (!isAlive()) return;
                Toast.makeText(MainActivity.this,
                        getString(R.string.update_failed, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showProgressDialog() {
        dismissProgressDialog();
        View view = getLayoutInflater().inflate(R.layout.dialog_update_progress, null);
        updateProgressBar = view.findViewById(R.id.progressUpdate);
        updateProgressText = view.findViewById(R.id.tvUpdateDetail);

        updateProgressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.update_downloading)
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(R.string.update_btn_cancel, (dialog, which) -> {
                    if (updateManager != null) {
                        updateManager.cancel();
                    }
                })
                .create();
        updateProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (updateProgressDialog != null && updateProgressDialog.isShowing()) {
            updateProgressDialog.dismiss();
        }
        updateProgressDialog = null;
        updateProgressBar = null;
        updateProgressText = null;
    }
}
