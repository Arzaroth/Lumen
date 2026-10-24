package com.arzaroth.lumen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StageTest {
    static final int W = 480, H = 270;
    private static final String[] KINDS = {"ALL_ENTERS", "CALM_ENTERS", "COSMOS_HOLDS", "FIREWORK_HOLDS", "CALM_HOLDS",
            "COSMOS_EXITS", "FIREWORK_EXITS", "QUICK_EXITS", "CALM_EXITS"};
    private final HashMap<String, int[]> saved = new HashMap<>();
    private final Canvas canvas = Robo.canvas(W, H);

    @Before
    public void save() {
        for (String k : KINDS) saved.put(k, kinds(k).clone());
    }

    @After
    public void restore() {
        for (String k : KINDS) System.arraycopy(saved.get(k), 0, kinds(k), 0, saved.get(k).length);
        Wall.clock = System::currentTimeMillis;
    }

    static int[] kinds(String name) {
        try {
            Field f = Stage.class.getDeclaredField(name);
            f.setAccessible(true);
            return (int[]) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Starts the phase `method` with its kind pinned to `kind`, then lets the stage choose freely again. */
    private void force(Stage s, String array, String field, int kind, String method, Object... args) {
        int[] options = kinds(array), original = options.clone();
        java.util.Arrays.fill(options, kind);
        Robo.set(s, field, -1);
        try {
            Robo.call(s, method, args);
        } finally {
            System.arraycopy(original, 0, options, 0, original.length);
        }
    }

    /** A banner: a dark field with a coloured mark and a thin wordmark, like most TV banners. */
    static AppEntry logo(String pkg, int color) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFF2F2F2);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(90, 90, 45, p);
        p.setColor(0xFF303030);
        p.setTextSize(52);
        c.drawText("KODI", 150, 108, p);
        return new AppEntry(new ComponentName(pkg, pkg + ".Main"), pkg, b, color);
    }

    static AppEntry blank(String pkg) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF202020);
        return new AppEntry(new ComponentName(pkg, pkg + ".Main"), pkg, b, Apps.NEUTRAL);
    }

    static AppEntry busy(String pkg) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Random r = new Random(3);
        for (int y = 0; y < 180; y++) for (int x = 0; x < 320; x++) b.setPixel(x, y, 0xFF000000 | r.nextInt(0xFFFFFF));
        for (int y = 0; y < 180; y++) {
            b.setPixel(0, y, 0xFF000000);
            b.setPixel(319, y, 0xFF000000);
        }
        return new AppEntry(new ComponentName(pkg, pkg + ".Main"), pkg, b, 0xFFE91E63);
    }

    /** A stage in a window: it hands built mosaics back with View.post, which waits for one. */
    static Stage stage() {
        android.app.Activity host = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        Stage s = new Stage(host, 0.25f);
        s.layoutStage(new RectF(170, 50, 460, 200), 230, W, H);
        host.setContentView(s, new android.view.ViewGroup.LayoutParams(W, H));
        Robo.layout(s, W, H);
        return s;
    }

    static void settle(Stage s) {
        for (int i = 0; i < 400; i++) {
            ShadowLooper.idleMainLooper();
            if ((int) Robo.get(s, "count") > 0 && !(boolean) Robo.get(s, "hasPending")) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run(Stage s, float seconds) {
        int n = Math.round(seconds * 30);
        for (int i = 0; i < n; i++) {
            Robo.idle(33);
            s.draw(canvas);
        }
    }

    private Stage ready(AppEntry app) {
        Stage s = stage();
        s.setRunning(true);
        s.setScene(app, true);
        settle(s);
        run(s, 0.2f);
        return s;
    }

    @Test
    public void everyEntranceHoldAndExitPlays() {
        Stage s = ready(logo("org.example.kodi", 0xFF1E88E5));
        for (int k = 0; k < 7; k++) {
            force(s, "ALL_ENTERS", "enterKind", k, "startEnter");
            run(s, 2.1f);
        }
        for (int h = 0; h < 7; h++) {
            if (h == 3) continue;
            force(s, "COSMOS_HOLDS", "holdKind", h, "startHold");
            run(s, 6f);
        }
        for (int x = 0; x < 9; x++) {
            if (x == 5) continue;
            Robo.call(s, "startHold");
            force(s, "COSMOS_EXITS", "exitKind", x, "startExit", false);
            run(s, 3f);
        }
    }

    @Test
    public void fireworksShowsShellsAndLaunches() {
        Stage s = ready(logo("org.example.jellyfin", 0xFF8E24AA));
        s.setStyle(Stage.FIREWORKS);
        force(s, "FIREWORK_HOLDS", "holdKind", 3, "startHold");
        run(s, 5f);
        force(s, "FIREWORK_HOLDS", "holdKind", 0, "startHold");
        Robo.set(s, "nextRocket", 0f);
        run(s, 4f);
        for (int t = 0; t < 8; t++) Robo.call(s, "shell", t, 240f, 80f, 2);
        run(s, 3f);
        force(s, "FIREWORK_EXITS", "exitKind", 5, "startExit", false);
        run(s, 3.5f);
        s.setStyle(Stage.COSMOS);
    }

    @Test
    public void quickExitsWhenTheAppChanges() {
        Stage s = ready(logo("org.example.a", 0xFF43A047));
        AppEntry b = logo("org.example.b", 0xFFFB8C00), c = logo("org.example.c", 0xFF00ACC1);
        for (int q = 0; q < 6; q++) {
            int[] quick = kinds("QUICK_EXITS");
            int[] original = quick.clone();
            java.util.Arrays.fill(quick, original[q]);
            Robo.set(s, "exitKind", -1);
            Robo.call(s, "startHold");
            s.setScene(q % 2 == 0 ? b : c, true);
            System.arraycopy(original, 0, quick, 0, original.length);
            settle(s);
            run(s, 0.6f);
            s.setScene(q % 2 == 0 ? c : b, true);
            settle(s);
            run(s, 2.5f);
        }
        s.setScene(b, true);
        s.setScene(b, true);
        settle(s);
        run(s, 3f);
    }

    /** A banner whose mark sits high, ending well above the middle. */
    static AppEntry high(String pkg) {
        Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF101010);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFB300);
        c.drawRect(60, 10, 260, 60, p);
        return new AppEntry(new ComponentName(pkg, pkg + ".Main"), pkg, b, 0xFFFFB300);
    }

    private static float lowest(Stage s) {
        float[] gy = Robo.get(s, "gy");
        float low = 0;
        for (int i = 0, n = Robo.get(s, "count"); i < n; i++) low = Math.max(low, gy[i]);
        return low;
    }

    private static int ghosts(Stage s) {
        float[] life = Robo.get(s, "hLife");
        int n = 0;
        for (float l : life) if (l > 0) n++;
        return n;
    }

    @Test
    public void aBigLogoLeavesAThinnedTrailOfGhosts() {
        Stage s = ready(busy("org.example.ghosts"));
        int count = Robo.get(s, "count");
        float[] pa = Robo.get(s, "pa");
        java.util.Arrays.fill(pa, 0, count, 1f);
        Robo.call(s, "ghost", 0);
        int left = ghosts(s);
        assertTrue(count > 1300);
        assertTrue(left >= 1150 && left <= 1200);
        java.util.Arrays.fill((float[]) Robo.get(s, "hLife"), 0f);
        java.util.Arrays.fill(pa, 0, 600, 1f);
        java.util.Arrays.fill(pa, 600, count, 0f);
        Robo.call(s, "ghost", 0);
        assertEquals(600, ghosts(s));
    }

    @Test
    public void aNewAppsLogoMaterialisesOutOfWhereItsTileIsWhenItStarts() {
        Stage s = ready(logo("org.example.first", 0xFF1E88E5));
        run(s, 3f);
        RectF tile = new RectF(20, 200, 110, 251);
        s.setScene(logo("org.example.second", 0xFFE53935), true, () -> tile);
        tile.offset(0, -150);
        for (int i = 0; i < 200 && (int) Robo.get(s, "enterKind") != 7; i++) {
            settle(s);
            run(s, 0.1f);
        }
        assertEquals(7, (int) Robo.<Integer>get(s, "enterKind"));
        assertEquals(50f, Robo.<RectF>get(s, "source").top, 0f);
        assertNull(Robo.get(s, "bannerOf"));
        run(s, 2f);
        s.setScene(logo("org.example.third", 0xFF43A047), true, () -> null);
        settle(s);
        run(s, 4f);
        assertNotEquals(7, (int) Robo.<Integer>get(s, "enterKind"));
        s.setScene(logo("org.example.fourth", 0xFF8E24AA), false, () -> tile);
        assertNull(Robo.get(s, "bannerOf"));
    }

    /** A theme with the least a backdrop can be: a plain colour, one look per app. */
    static final class Plain implements Theme {
        int looks, laidOut, drawn;
        Backdrop.Look shown;

        @Override
        public String id() {
            return "plain";
        }

        @Override
        public String name() {
            return "Plain";
        }

        @Override
        public Backdrop backdrop(float scale) {
            return new Backdrop() {
                @Override
                public Look look(int[] sparks, int accent, Random random) {
                    looks++;
                    return new Look() {
                    };
                }

                @Override
                public void setLook(Look look, boolean animate) {
                    shown = look;
                }

                @Override
                public void layout(RectF area, int w, int h) {
                    laidOut++;
                }

                @Override
                public void update(float dt) {
                }

                @Override
                public void drawBack(Canvas c) {
                    drawn++;
                    c.drawColor(0xFF101010);
                }

                @Override
                public boolean moving() {
                    return false;
                }
            };
        }
    }

    @Test
    public void aNewThemeBringsItsOwnBackdropAndLooks() {
        Stage s = ready(logo("org.example.themed", 0xFF1E88E5));
        Plain plain = new Plain();
        s.setTheme(plain);
        assertEquals(1, plain.laidOut);
        for (int i = 0; i < 100 && plain.shown == null; i++) {
            settle(s);
            run(s, 0.1f);
        }
        assertTrue(plain.looks >= 1);
        s.setTheme(plain);
        assertEquals(1, plain.laidOut);
        s.intro(0.2f);
        s.setParallax(1f, 1f);
        s.setWeather(1f, 1f, 0f);
        s.setSkyStill(true);
        s.setSkyStill(false);
        s.setMeteors(2f);
        s.setSkyTurning(true);
        s.setNebula(false);
        s.setRealSky(null);
        run(s, 2f);
        assertTrue(plain.drawn > 0);
        s.setTheme(new SpaceTheme());
        run(s, 0.5f);
    }

    @Test
    public void everyLogoStandsOnTheSameFloor() {
        Stage s = ready(logo("org.example.floor", 0xFF1E88E5));
        float floor = Robo.get(s, "floorY");
        assertEquals(1f, lowest(s), 1e-4f);
        s.setScene(high("org.example.high"), false);
        settle(s);
        run(s, 0.3f);
        assertEquals(1f, lowest(s), 1e-4f);
        assertEquals(floor, (float) Robo.get(s, "floorY"), 0f);
    }

    @Test
    public void bannersOfEveryKindBecomeMosaics() {
        Stage s = ready(blank("org.example.blank"));
        assertTrue((int) Robo.get(s, "count") > 0);
        s.setScene(busy("org.example.busy"), false);
        settle(s);
        run(s, 0.5f);
        s.setScene(blank("org.example.blank"), false);
        settle(s);
        run(s, 0.3f);
    }

    @Test
    public void pacesFlatLogoDimAndIntro() {
        Stage s = ready(logo("org.example.d", 0xFFD81B60));
        s.setPace(Stage.CALM);
        s.setPace(Stage.CALM);
        Robo.call(s, "startHold");
        run(s, 4f);
        Robo.call(s, "startExit", false);
        run(s, 3f);
        s.setPace(Stage.STILL);
        run(s, 1f);
        s.setPace(Stage.LIVELY);
        run(s, 1f);
        s.setFlat(true);
        run(s, 1f);
        s.setFlat(false);
        s.setSkyStill(true);
        s.setMeteors(2.5f);
        s.setSkyTurning(true);
        run(s, 0.5f);
        s.setSkyStill(false);
        s.setSkyTurning(false);
        s.setDim(0.28f);
        s.setDim(0.28f);
        run(s, 0.6f);
        s.setDim(0.01f);
        run(s, 0.6f);
        s.setDim(1f);
        run(s, 0.6f);
        s.intro(1f);
        run(s, 1.5f);
        s.setRunning(false);
        s.draw(canvas);
        s.setRunning(false);
        s.setRunning(true);
    }

    @Test
    public void slowFramesLowerTheQuality() {
        Stage s = ready(logo("org.example.e", 0xFF3949AB));
        for (int i = 0; i < 400; i++) {
            Robo.idle(45);
            s.draw(canvas);
        }
        assertEquals(0, (int) Robo.get(s, "quality"));
    }

    @Test
    public void withoutAnimationsTheLogoJustStands() throws Exception {
        java.lang.reflect.Method scale = ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
        scale.setAccessible(true);
        scale.invoke(null, 0f);
        try {
            Stage s = stage();
            s.setRunning(true);
            s.setScene(logo("org.example.f", 0xFF6D4C41), true);
            settle(s);
            run(s, 0.5f);
            s.setScene(logo("org.example.g", 0xFF00897B), true);
            settle(s);
            run(s, 0.5f);
        } finally {
            scale.invoke(null, 1f);
        }
    }

    @Test
    public void drawsBeforeItIsLaidOut() {
        android.app.Activity host = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        Stage s = new Stage(host, 0.25f);
        host.setContentView(s);
        Robo.layout(s, W, H);
        s.draw(canvas);
        s.setScene(logo("org.example.h", 0xFF5E35B1), true);
        s.setDim(0.5f);
    }
}
