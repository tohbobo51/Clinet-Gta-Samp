package com.artplaygames.sampclient;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;

/**
 * Launcher / client UI untuk GTA SA-MP.
 *
 * Alur:
 *   1. Splash screen + loading animation (splashContainer di activity_main.xml)
 *   2. Loading selesai  -> tampilkan background + konten utama, lalu putar musik latar
 *   3. Login Google di pojok kiri atas -> Logout -> tombol MAIN di bawahnya
 *   4. Nama server di tengah atas dengan animasi floating tak berujung
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SampClient";

    /** Durasi splash screen (ms). */
    private static final long SPLASH_DURATION_MS = 2500L;

    /** Konfigurasi server SA-MP — ganti sesuai server kamu (lihat strings.xml). */
    private static final int SERVER_PORT_DEFAULT = 7777;

    /** Menyimpan teks terakhir untuk tiap tombol aksi cepat roleplay. */
    private static final String PREF_QUICK_ACTIONS = "quick_actions";

    // ---- View ----
    private FrameLayout splashContainer;
    private ConstraintLayout mainContent;
    private TextView tvServerName;
    private TextView tvStatus;
    private Button btnGoogleSignIn;
    private Button btnLogout;
    private Button btnMain;

    // ---- Lifecycle state ----
    private final Handler splashHandler = new Handler(Looper.getMainLooper());
    private final Runnable splashRunnable = this::finishSplash;
    private boolean loadingFinished = false;
    private boolean isResumed = false;

    // ---- Media ----
    private MediaPlayer bgmPlayer;

    // ---- Google Sign-In ----
    private GoogleSignInClient signInClient;

    // ---- Update data game ----
    private UpdateManager updateManager;
    private Button btnUpdate;
    private TextView tvDataVersion;
    private AlertDialog updateProgressDialog;
    private ProgressBar updateProgressBar;
    private TextView updateProgressText;

    /**
     * Harus didaftarkan saat inisialisasi (sebelum onCreate selesai),
     * tidak boleh dipanggil secara kondisional.
     */
    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            Toast.makeText(this, R.string.toast_login_cancelled,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        task.addOnSuccessListener(this::onSignInSuccess)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Google Sign-In gagal", e);
                                    Toast.makeText(this, R.string.toast_login_failed,
                                            Toast.LENGTH_SHORT).show();
                                });
                    });

    // ==================================================================
    //  Lifecycle
    // ==================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashContainer = findViewById(R.id.splashContainer);
        mainContent = findViewById(R.id.mainContent);
        tvServerName = findViewById(R.id.tvServerName);
        tvStatus = findViewById(R.id.tvStatus);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnLogout = findViewById(R.id.btnLogout);
        btnMain = findViewById(R.id.btnMain);
        btnUpdate = findViewById(R.id.btnUpdate);
        tvDataVersion = findViewById(R.id.tvDataVersion);

        setupGoogleSignIn();
        setupServerNameFloatingAnimation();
        applyAuthUi(GoogleSignIn.getLastSignedInAccount(this) != null);

        btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        btnLogout.setOnClickListener(v -> signOut());
        btnMain.setOnClickListener(v -> connectToServer());
        btnUpdate.setOnClickListener(v -> checkForUpdates(true));

        updateManager = new UpdateManager(this, getString(R.string.update_manifest_url));
        refreshDataVersionLabel();
        setupQuickActions();

        // Mulai splash + loading animation
        splashHandler.postDelayed(splashRunnable, SPLASH_DURATION_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        // Musik hanya diputar kembali setelah loading selesai
        if (loadingFinished) {
            startBackgroundMusic();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Aplikasi ditutup / diminimalkan -> musik berhenti
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
    //  1. Splash screen & loading animation
    // ==================================================================

    private void finishSplash() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        loadingFinished = true;

        // Konten utama muncul dengan fade-in
        mainContent.setVisibility(View.VISIBLE);
        mainContent.animate()
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Splash screen fade-out lalu disembunyikan
        splashContainer.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> splashContainer.setVisibility(View.GONE))
                .start();

        // 2. Musik latar mulai diputar SETELAH loading selesai
        if (isResumed) {
            startBackgroundMusic();
        }

        // Client otomatis mengecek update data game
        checkForUpdates(false);
    }

    // ==================================================================
    //  2. Audio / media player + lifecycle
    // ==================================================================

    /**
     * Memutar musik latar (looping).
     * Letakkan file audio di: app/src/main/res/raw/bgm.mp3  (nama resource: bgm)
     */
    private void startBackgroundMusic() {
        if (bgmPlayer == null) {
            int resId = getResources().getIdentifier("bgm", "raw", getPackageName());
            if (resId == 0) {
                Log.w(TAG, "Musik latar tidak ditemukan — tambahkan res/raw/bgm.mp3");
                return;
            }
            try {
                bgmPlayer = MediaPlayer.create(this, resId);
                if (bgmPlayer == null) {
                    Log.w(TAG, "MediaPlayer gagal dibuat untuk raw/bgm");
                    return;
                }
                bgmPlayer.setLooping(true);
                bgmPlayer.setVolume(0.65f, 0.65f);
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
            } catch (IllegalStateException e) {
                Log.w(TAG, "stop() MediaPlayer gagal", e);
            }
            bgmPlayer.release();
            bgmPlayer = null;
        }
    }

    // ==================================================================
    //  3. Autentikasi Google (pojok kiri atas)
    // ==================================================================

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                // Aktifkan bila server kamu memverifikasi ID token:
                // .requestIdToken(getString(R.string.default_web_client_id))
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);
    }

    private void launchGoogleSignIn() {
        signInLauncher.launch(signInClient.getSignInIntent());
    }

    private void onSignInSuccess(GoogleSignInAccount account) {
        String name = account.getDisplayName() != null
                ? account.getDisplayName()
                : (account.getEmail() != null ? account.getEmail() : "Pemain");
        Toast.makeText(this, getString(R.string.toast_signed_in, name),
                Toast.LENGTH_SHORT).show();
        applyAuthUi(true);
        tvStatus.setText(R.string.status_ready);
    }

    private void signOut() {
        signInClient.signOut().addOnCompleteListener(this, task -> {
            applyAuthUi(false);
            tvStatus.setText(R.string.status_login_required);
            Toast.makeText(this, R.string.toast_signed_out, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Belum login  -> tampil "Login dengan Google".
     * Sudah login  -> tombol login disembunyikan, tampil "Logout" dan tombol MAIN
     *                 (MAIN tepat di bawah Logout, karena keduanya ada di LinearLayout
     *                  yang sama sehingga urutannya vertikal).
     */
    private void applyAuthUi(boolean signedIn) {
        btnGoogleSignIn.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        btnLogout.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        btnMain.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (signedIn) {
            btnMain.setAlpha(0f);
            btnMain.animate().alpha(1f).setDuration(300).start();
        } else {
            tvStatus.setText(R.string.status_login_required);
        }
    }

    // ==================================================================
    //  Tombol MAIN — logika koneksi server
    // ==================================================================

    private void connectToServer() {
        if (GoogleSignIn.getLastSignedInAccount(this) == null) {
            Toast.makeText(this, R.string.toast_need_login, Toast.LENGTH_SHORT).show();
            return;
        }

        String host = getString(R.string.server_host);
        int port = readServerPort();
        tvStatus.setText(getString(R.string.status_connecting, host, port));
        Toast.makeText(this, getString(R.string.toast_connecting, host, port),
                Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Menghubungkan ke server SA-MP " + host + ":" + port);

        // TODO: ganti blok ini dengan handshake sesungguhnya
        //       (konektivitas SA-MP / SDK multipemain / intent ke activity game).
        splashHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            tvStatus.setText(getString(R.string.status_connected, host));
        }, 1200);
    }

    /**
     * Membaca port dari strings.xml (server_port) supaya konfigurasi cukup diubah di satu tempat.
     * Jika nilainya tidak valid, jatuh kembali ke {@link #SERVER_PORT_DEFAULT}.
     */
    private int readServerPort() {
        try {
            return Integer.parseInt(getString(R.string.server_port).trim());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Nilai server_port di strings.xml tidak valid", e);
            return SERVER_PORT_DEFAULT;
        }
    }

    // ==================================================================
    //  4. Animasi nama server (tengah atas) — floating tak berujung
    // ==================================================================

    private void setupServerNameFloatingAnimation() {
        float distance = dp(12f);

        // Geser naik-turun perlahan: 0 -> +12dp, lalu berbalik terus-menerus.
        TranslateAnimation floating = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.ABSOLUTE, 0f,
                Animation.ABSOLUTE, distance);

        floating.setDuration(1800);
        floating.setRepeatCount(Animation.INFINITE);
        floating.setRepeatMode(Animation.REVERSE);
        floating.setInterpolator(new AccelerateDecelerateInterpolator());

        tvServerName.startAnimation(floating);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    // ==================================================================
    //  Update data game (cek + unduh aset, tersimpan di aplikasi)
    // ==================================================================

    /**
     * Mengecek manifest update.
     *
     * @param userInitiated true bila dipicu tombol UPDATE DATA (selalu menampilkan hasilnya)
     */
    private void checkForUpdates(final boolean userInitiated) {
        btnUpdate.setEnabled(false);
        updateManager.check(new UpdateManager.Listener() {
            @Override
            public void onNoUpdate() {
                if (!isAlive()) {
                    return;
                }
                btnUpdate.setEnabled(true);
                if (userInitiated) {
                    Toast.makeText(MainActivity.this, R.string.update_none,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onUpdateAvailable(UpdateManager.Manifest manifest) {
                if (!isAlive()) {
                    return;
                }
                btnUpdate.setEnabled(true);
                showUpdateDialog(manifest);
            }

            @Override
            public void onCheckFailed(String message) {
                if (!isAlive()) {
                    return;
                }
                btnUpdate.setEnabled(true);
                if (userInitiated) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.update_check_failed, message),
                            Toast.LENGTH_LONG).show();
                } else {
                    Log.w(TAG, "Cek update otomatis gagal: " + message);
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
                .setPositiveButton(R.string.update_btn_download,
                        (dialog, which) -> startDownload(manifest))
                .setNegativeButton(R.string.update_btn_later, null)
                .show();
    }

    private void startDownload(final UpdateManager.Manifest manifest) {
        showProgressDialog();
        updateManager.download(manifest, new UpdateManager.Listener() {
            @Override
            public void onProgress(int percent, String detail) {
                if (updateProgressBar == null || updateProgressText == null) {
                    return;
                }
                updateProgressBar.setProgress(percent);
                updateProgressText.setText(detail);
            }

            @Override
            public void onInstalled(UpdateManager.Manifest installed) {
                if (!isAlive()) {
                    return;
                }
                dismissProgressDialog();
                refreshDataVersionLabel();
                Toast.makeText(MainActivity.this,
                        getString(R.string.update_installed, installed.title),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailed(String message) {
                if (!isAlive()) {
                    return;
                }
                dismissProgressDialog();
                Toast.makeText(MainActivity.this,
                        getString(R.string.update_failed, message),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showProgressDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_update_progress, null);
        updateProgressBar = view.findViewById(R.id.progressUpdate);
        updateProgressText = view.findViewById(R.id.tvUpdateDetail);
        updateProgressBar.setProgress(0);
        updateProgressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(R.string.update_btn_cancel,
                        (dialog, which) -> updateManager.cancel())
                .show();
    }

    private void dismissProgressDialog() {
        if (updateProgressDialog != null && updateProgressDialog.isShowing()) {
            updateProgressDialog.dismiss();
        }
        updateProgressDialog = null;
        updateProgressBar = null;
        updateProgressText = null;
    }

    private void refreshDataVersionLabel() {
        String installed = updateManager.getInstalledTitle();
        tvDataVersion.setText(installed.isEmpty()
                ? getString(R.string.update_data_none)
                : getString(R.string.update_data_version, installed));
    }

    // ==================================================================
    //  Aksi cepat roleplay
    // ==================================================================

    private void setupQuickActions() {
        int[] actionIds = {
                R.id.btnActionMe, R.id.btnActionDo, R.id.btnActionOoc, R.id.btnActionB,
                R.id.btnActionReport, R.id.btnActionStats, R.id.btnActionInventory
        };
        for (int id : actionIds) {
            View button = findViewById(id);
            button.setOnClickListener(this::onQuickAction);
        }
    }

    private void onQuickAction(View view) {
        showQuickActionDialog(((Button) view).getText().toString());
    }

    /**
     * Dialog untuk menyusun perintah roleplay lalu menyalinnya ke clipboard,
     * sehingga tinggal ditempel di chat game.
     */
    private void showQuickActionDialog(final String command) {
        View view = getLayoutInflater().inflate(R.layout.dialog_quick_action, null);
        final EditText input = view.findViewById(R.id.inputAction);
        input.setText(loadLastAction(command));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.quick_action_dialog, command))
                .setView(view)
                .setPositiveButton(R.string.quick_action_copy, (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    String full = text.isEmpty() ? command : command + " " + text;
                    copyToClipboard(full);
                    saveLastAction(command, text);
                    Toast.makeText(MainActivity.this,
                            getString(R.string.quick_action_copied, full),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.quick_action_cancel, null)
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("roleplay", text));
        }
    }

    private String loadLastAction(String command) {
        return getSharedPreferences(PREF_QUICK_ACTIONS, MODE_PRIVATE)
                .getString(quickActionKey(command), "");
    }

    private void saveLastAction(String command, String text) {
        getSharedPreferences(PREF_QUICK_ACTIONS, MODE_PRIVATE)
                .edit()
                .putString(quickActionKey(command), text)
                .apply();
    }

    private static String quickActionKey(String command) {
        return "last_" + command.replaceAll("[^A-Za-z0-9]", "");
    }
}
