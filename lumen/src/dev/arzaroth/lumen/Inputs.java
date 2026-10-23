package dev.arzaroth.lumen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.function.Consumer;

/** The Inputs menu: Live TV, then every HDMI/AV passthrough input, by name. */
final class Inputs {
    private static final String[] LIVE_TV_PACKAGES = {"com.android.tv", "com.realtek.tv", "com.google.android.tv", "com.google.android.apps.tv.launcherx"};

    private Inputs() {
    }

    static Menu.Page page(Context c, Consumer<Intent> launch) {
        Menu.Page page = new Menu.Page("Inputs", 360);
        PackageManager pm = c.getPackageManager();
        for (String pkg : LIVE_TV_PACKAGES) {
            Intent live = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (live != null) {
                Intent i = live.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                page.add(Menu.Row.action("Live TV", () -> launch.accept(i)));
                break;
            }
        }
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            ArrayList<TvInputInfo> passthrough = new ArrayList<>();
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isHidden(c) || !info.isPassthroughInput() || info.getParentId() != null) continue;
                passthrough.add(info);
            }
            passthrough.sort((a, b) -> label(c, a).compareToIgnoreCase(label(c, b)));
            for (TvInputInfo info : passthrough) {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                page.add(Menu.Row.action(label(c, info), () -> launch.accept(i)));
            }
        }
        if (page.rows.isEmpty()) page.add(Menu.Row.note("No inputs found"));
        return page;
    }

    private static String label(Context c, TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(c);
        return String.valueOf(custom != null ? custom : info.loadLabel(c));
    }
}
