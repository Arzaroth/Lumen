package com.arzaroth.lumen;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Asks GitHub for Lumen's latest release and installs it over this one. */
final class Updater {
    enum State { IDLE, CHECKING, CURRENT, AVAILABLE, BLOCKED, DOWNLOADING, INSTALLING, CONFIRMING, FAILED }

    static String releases = "https://api.github.com/repos/Arzaroth/Lumen/releases/latest";

    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor worker = Executors.newSingleThreadExecutor();
    private State state = State.IDLE;
    private String latest, apk;
    private int progress;

    /** Where the install session reports back; not exported, so only the system's PendingIntent reaches it. */
    public static final class Result extends BroadcastReceiver {
        @Override
        @SuppressWarnings("deprecation")
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                if (live != null) live.set(State.CONFIRMING);
                return;
            }
            if (status == PackageInstaller.STATUS_SUCCESS) return;
            c.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().remove(Prefs.RELAUNCH).apply();
            if (live != null) live.set(status == PackageInstaller.STATUS_FAILURE_ABORTED ? State.AVAILABLE : State.FAILED);
        }
    }

    private static Updater live;

    Updater(Context context, Runnable changed) {
        this.context = context.getApplicationContext();
        this.changed = changed;
    }

    State state() {
        return state;
    }

    /** Checking, downloading or installing: nothing to start over. */
    boolean busy() {
        return state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLING || state == State.CONFIRMING;
    }

    String latest() {
        return latest;
    }

    int progress() {
        return progress;
    }

    static String installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Whether version `a` comes after `b`, reading "v1.10.2" as 1, 10, 2. */
    static boolean newer(String a, String b) {
        String[] x = a.replaceFirst("^v", "").split("\\."), y = b.replaceFirst("^v", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = Integer.compare(part(x, i), part(y, i));
            if (d != 0) return d > 0;
        }
        return false;
    }

    private static int part(String[] v, int i) {
        if (i >= v.length) return 0;
        String digits = v[i].replaceFirst("\\D.*", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    void check() {
        String current = installed(context);
        set(State.CHECKING);
        worker.execute(() -> {
            try {
                JSONObject release = new JSONObject(read(releases));
                String tag = release.getString("tag_name").replaceFirst("^v", "");
                String url = null;
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (asset.optString("name").endsWith(".apk")) url = asset.getString("browser_download_url");
                }
                String found = url;
                main.post(() -> {
                    latest = tag;
                    apk = found;
                    set(found != null && newer(tag, current) ? State.AVAILABLE : State.CURRENT);
                });
            } catch (IOException | JSONException e) {
                main.post(() -> set(State.FAILED));
            }
        });
    }

    /**
     * Streams the release's APK into an install session. Android replaces Lumen without asking when
     * Lumen installed itself last time; otherwise it asks first.
     */
    @SuppressLint("ApplySharedPref") // the relaunch mark must be on disk before Android kills Lumen to replace it
    void update() {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            try {
                context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                set(State.BLOCKED);
            }
            return;
        }
        live = this;
        String url = apk;
        progress = 0;
        set(State.DOWNLOADING);
        worker.execute(() -> {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            int id = -1;
            try {
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    HttpURLConnection c = open(url);
                    long length = c.getContentLengthLong();
                    try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("lumen.apk", 0, length)) {
                        byte[] buf = new byte[1 << 16];
                        long done = 0;
                        int n, shown = 0;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                            int pct = length > 0 ? (int) (done * 100 / length) : 0;
                            if (pct != shown) {
                                shown = pct;
                                main.post(() -> {
                                    progress = pct;
                                    changed.run();
                                });
                            }
                        }
                        session.fsync(out);
                    }
                    context.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().putLong(Prefs.RELAUNCH, Wall.millis()).commit();
                    main.post(() -> set(State.INSTALLING));
                    Intent report = new Intent(context, Result.class);
                    int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
                    session.commit(PendingIntent.getBroadcast(context, id, report, PendingIntent.FLAG_UPDATE_CURRENT | mutable).getIntentSender());
                }
            } catch (IOException | RuntimeException e) {
                if (id >= 0) installer.abandonSession(id);
                main.post(() -> set(State.FAILED));
            }
        });
    }

    private void set(State s) {
        state = s;
        changed.run();
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "Lumen");
        if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    private static String read(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
