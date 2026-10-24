package com.arzaroth.lumen;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Brings Home back after Lumen replaced itself, as Android returns to the last app instead. Tried as
 * soon as the new Lumen is in place, and again when the system binds the keys service, which may
 * start activities from the background where a plain receiver may not.
 */
public final class Relaunch extends BroadcastReceiver {
    static final long WINDOW_MS = 10 * 60 * 1000;

    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) home(c, false);
    }

    /** Starts Home if Lumen handed itself to Android to be replaced moments ago; `last` forgets it did. */
    static void home(Context c, boolean last) {
        SharedPreferences prefs = c.getSharedPreferences("lumen", Context.MODE_PRIVATE);
        long at = prefs.getLong(Prefs.RELAUNCH, 0);
        if (at == 0) return;
        boolean fresh = Wall.millis() - at < WINDOW_MS;
        if (last || !fresh) prefs.edit().remove(Prefs.RELAUNCH).apply();
        if (!fresh) return;
        try {
            c.startActivity(new Intent(c, LumenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }
}
