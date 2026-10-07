package com.russia.launcher.storage;

import static com.russia.launcher.config.Config.NATIVE_SETTINGS_FILE_PATH;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import org.ini4j.Wini;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class NativeStorage {
    private static final String TAG = "NativeStorage";
    private static final String CLIENT_SECTION_NAME = "client";
    public static final String DEFAULT_HOST = "142.132.203.47";
    public static final String DEFAULT_PORT = "10125";
    public static final String DEFAULT_NAME = "ViceSide_Player";

    private static File getSettingsFile(Context context) {
        try {
            File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                return new File(ext, NATIVE_SETTINGS_FILE_PATH);
            }
        } catch (Exception ignored) {}
        return new File(new File(Environment.getExternalStorageDirectory(), "SAMP"), "settings.ini");
    }

    public static void ensureSettingsExist(Context context) {
        try {
            File[] targetDirs = new File[] {
                new File(context.getExternalFilesDir(null), "SAMP"),
                new File("/storage/emulated/0/Android/data/com.russia.game/files/SAMP"),
                new File(Environment.getExternalStorageDirectory(), "SAMP"),
                new File(context.getFilesDir(), "SAMP")
            };

            String defaultContent = "[client]\n" +
                    "ip=" + DEFAULT_HOST + "\n" +
                    "port=" + DEFAULT_PORT + "\n" +
                    "name=" + DEFAULT_NAME + "\n" +
                    "password=\n" +
                    "autologin=0\n" +
                    "server=0\n" +
                    "debug=0\n" +
                    "[gui]\n" +
                    "Font=visby-round-cf-extra-bold.ttf\n" +
                    "fps=60\n";

            for (File dir : targetDirs) {
                try {
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, "settings.ini");
                    if (!f.exists() || f.length() == 0) {
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(defaultContent.getBytes(StandardCharsets.UTF_8));
                            fos.flush();
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "ensureSettingsExist error: " + e.getMessage());
        }
    }

    public static void addClientProperty(String propertyName, String value, Context context) {
        try {
            ensureSettingsExist(context);
            File f = getSettingsFile(context);
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            Wini w = new Wini(f);
            w.put(CLIENT_SECTION_NAME, propertyName, value);
            w.store();
        } catch (Exception e) {
            Log.w(TAG, "addClientProperty error: " + e.getMessage());
        }
    }

    public static String getClientProperty(String property, Context context) {
        try {
            ensureSettingsExist(context);
            File f = getSettingsFile(context);
            if (f.exists()) {
                Wini w = new Wini(f);
                String value = w.get(CLIENT_SECTION_NAME, property);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {}

        if ("name".equalsIgnoreCase(property)) return DEFAULT_NAME;
        if ("ip".equalsIgnoreCase(property)) return DEFAULT_HOST;
        if ("port".equalsIgnoreCase(property)) return DEFAULT_PORT;
        return null;
    }
}
