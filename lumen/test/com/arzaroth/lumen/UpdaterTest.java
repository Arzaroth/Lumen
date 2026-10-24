package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.function.BooleanSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpdaterTest {
    private Releases github;
    private Updater updater;
    private int changes;

    @Before
    public void setUp() throws Exception {
        github = new Releases("v9.0.0");
        updater = new Updater(Robo.context(), () -> changes++);
        shadowOf(Robo.context().getPackageManager()).setCanRequestPackageInstalls(false);
    }

    @After
    public void tearDown() throws Exception {
        github.close();
    }

    static void until(BooleanSupplier done) {
        for (int i = 0; i < 1000 && !done.getAsBoolean(); i++) {
            Robo.idle(1);
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(done.getAsBoolean());
    }

    private void settle(Updater.State expected) {
        until(() -> updater.state() == expected);
    }

    private Application app() {
        return (Application) Robo.context();
    }

    private void allowInstalls() {
        shadowOf(Robo.context().getPackageManager()).setCanRequestPackageInstalls(true);
    }

    static void report(int status, Intent extra) {
        Intent i = new Intent().putExtra(PackageInstaller.EXTRA_STATUS, status);
        if (extra != null) i.putExtra(Intent.EXTRA_INTENT, extra);
        new Updater.Result().onReceive(Robo.context(), i);
    }

    @Test
    public void comparesVersionsPartByPart() {
        assertTrue(Updater.newer("1.1.0", "1.0.0"));
        assertTrue(Updater.newer("v1.10.0", "1.9.9"));
        assertTrue(Updater.newer("2", "1.9"));
        assertTrue(Updater.newer("1.0.1", "1.0"));
        assertFalse(Updater.newer("1.0.0", "1.0.0"));
        assertFalse(Updater.newer("1.0", "1.0.0"));
        assertFalse(Updater.newer("0.9.9", "1.0.0"));
        assertFalse(Updater.newer("1.0.0-rc1", "1.0.0"));
        assertFalse(Updater.newer("x.y", "0.0"));
    }

    @Test
    public void installedIsBlankWhenThePackageCannotBeRead() {
        Context missing = new ContextWrapper(Robo.context()) {
            @Override
            public String getPackageName() {
                return "org.missing.lumen";
            }
        };
        assertEquals("", Updater.installed(missing));
        assertTrue(Updater.installed(Robo.context()).matches("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    public void findsANewerRelease() {
        assertEquals(Updater.State.IDLE, updater.state());
        updater.check();
        assertEquals(Updater.State.CHECKING, updater.state());
        settle(Updater.State.AVAILABLE);
        assertEquals("9.0.0", updater.latest());
        assertTrue(changes >= 2);
    }

    @Test
    public void staysPutOnTheSameReleaseOrOneWithoutAnApk() {
        github.release("1.0.0", "lumen.apk");
        updater.check();
        settle(Updater.State.CURRENT);
        github.release("9.0.0", "lumen.zip");
        updater.check();
        settle(Updater.State.CURRENT);
        github.latest = "{\"tag_name\":\"9.0.0\"}";
        updater.check();
        settle(Updater.State.CURRENT);
    }

    @Test
    public void failsWhenGitHubCannotBeReadOrMakesNoSense() {
        github.latestStatus = 500;
        updater.check();
        settle(Updater.State.FAILED);
        github.latestStatus = 200;
        github.latest = "not json";
        updater.check();
        settle(Updater.State.FAILED);
    }

    @Test
    public void asksForTheInstallPermissionFirst() {
        updater.check();
        settle(Updater.State.AVAILABLE);
        updater.update();
        Intent asked = shadowOf(app()).getNextStartedActivity();
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, asked.getAction());
        assertEquals("package:" + Robo.context().getPackageName(), asked.getDataString());
        assertEquals(Updater.State.AVAILABLE, updater.state());
        shadowOf(app()).checkActivities(true);
        updater.update();
        assertEquals(Updater.State.BLOCKED, updater.state());
    }

    @Test
    public void downloadsInstallsAndHandsTheConfirmationToAndroid() {
        allowInstalls();
        updater.check();
        settle(Updater.State.AVAILABLE);
        updater.update();
        assertEquals(Updater.State.DOWNLOADING, updater.state());
        settle(Updater.State.INSTALLING);
        assertEquals(100, updater.progress());
        android.content.SharedPreferences prefs = Robo.context().getSharedPreferences("lumen", Context.MODE_PRIVATE);
        assertTrue(prefs.contains(Prefs.RELAUNCH));
        PackageInstaller installer = Robo.context().getPackageManager().getPackageInstaller();
        assertEquals(1, installer.getAllSessions().size());
        assertEquals(Robo.context().getPackageName(), installer.getAllSessions().get(0).getAppPackageName());

        report(PackageInstaller.STATUS_PENDING_USER_ACTION, new Intent("android.content.pm.action.CONFIRM_INSTALL"));
        Intent confirm = shadowOf(app()).getNextStartedActivity();
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", confirm.getAction());
        assertTrue((confirm.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals(Updater.State.CONFIRMING, updater.state());
        assertTrue(updater.busy());
        report(PackageInstaller.STATUS_PENDING_USER_ACTION, null);
        assertNull(shadowOf(app()).getNextStartedActivity());
        report(PackageInstaller.STATUS_SUCCESS, null);
        assertEquals(Updater.State.CONFIRMING, updater.state());
        report(PackageInstaller.STATUS_FAILURE_ABORTED, null);
        assertEquals(Updater.State.AVAILABLE, updater.state());
        report(PackageInstaller.STATUS_FAILURE_CONFLICT, null);
        assertEquals(Updater.State.FAILED, updater.state());
        assertFalse(prefs.contains(Prefs.RELAUNCH));
    }

    @Test
    public void aBrokenDownloadAbandonsTheSession() {
        allowInstalls();
        updater.check();
        settle(Updater.State.AVAILABLE);
        github.apkStatus = 404;
        updater.update();
        settle(Updater.State.FAILED);
        assertTrue(Robo.context().getPackageManager().getPackageInstaller().getAllSessions().isEmpty());
        github.apkStatus = 200;
        github.apk = new byte[0];
        updater.check();
        settle(Updater.State.AVAILABLE);
        updater.update();
        settle(Updater.State.INSTALLING);
        assertEquals(0, updater.progress());
    }
}
