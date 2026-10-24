package com.arzaroth.lumen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class LumenKeys extends AccessibilityService {
    private static LumenKeys instance;
    private static boolean homeInFront;

    static final String SMARTTUBE = "org.smarttube.stable";

    static void setHomeInFront(boolean front) {
        homeInFront = front;
        if (instance != null) instance.filterKeys(front && instance.enabled());
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        filterKeys(homeInFront && enabled());
        Relaunch.home(this, true);
    }

    private boolean enabled() {
        return getSharedPreferences("lumen", MODE_PRIVATE).getInt(Prefs.KEYS, 0) == 0;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    // Any key filter stops held keys from repeating (volume moves one step per press), so filter only on Home.
    private void filterKeys(boolean on) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean now = (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        if (now == on) return;
        if (on) info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        else info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        String app;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_TV_INPUT:
                app = null;
                break;
            case KeyEvent.KEYCODE_BUTTON_3:
                boolean smart = getSharedPreferences("lumen", MODE_PRIVATE).getInt(Prefs.YOUTUBE, 0) == 1;
                app = smart ? SMARTTUBE : "com.google.android.youtube.tv";
                break;
            case KeyEvent.KEYCODE_BUTTON_4:
                app = "com.netflix.ninja";
                break;
            case KeyEvent.KEYCODE_BUTTON_6:
                app = "com.disney.disneyplus";
                break;
            default:
                return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (app == null) {
                start(new Intent(this, LumenActivity.class).setAction(LumenActivity.ACTION_INPUTS));
            } else {
                Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(app);
                start(launch != null ? launch : new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app)));
            }
        }
        return true;
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
