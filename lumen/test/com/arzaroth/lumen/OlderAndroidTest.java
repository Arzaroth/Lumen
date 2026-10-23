package com.arzaroth.lumen;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;

/** Android 9: before transport info, the location-aware network callback, receiver export flags and mutable pending intents. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w960dp-h540dp-land-mdpi")
public class OlderAndroidTest {
    @Test
    public void homeRunsOnAndroid9() {
        Robo.context().getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().putInt(Prefs.INTRO, 1).commit();
        AppsTest.install("org.example.old1", "org.example.old2");
        LumenActivity a = ActivityTest.launch(null).get();
        ActivityTest.key(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        NetworkCapabilities wifi = ShadowNetworkCapabilities.newInstance();
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        Robo.call(a, "showNetwork", wifi);
        ConnectivityManager cm = Robo.context().getSystemService(ConnectivityManager.class);
        for (ConnectivityManager.NetworkCallback cb : shadowOf(cm).getNetworkCallbacks()) {
            cb.onCapabilitiesChanged(null, wifi);
            cb.onLost(null);
        }
    }

    @Test
    public void updatesOnAndroid9() throws Exception {
        try (Releases github = new Releases("9.0.0")) {
            Updater updater = new Updater(Robo.context(), () -> { });
            shadowOf(Robo.context().getPackageManager()).setCanRequestPackageInstalls(true);
            updater.check();
            UpdaterTest.until(() -> updater.state() == Updater.State.AVAILABLE);
            updater.update();
            UpdaterTest.until(() -> updater.state() == Updater.State.INSTALLING);
        }
    }
}
