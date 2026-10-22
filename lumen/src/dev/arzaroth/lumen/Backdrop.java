package dev.arzaroth.lumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class Backdrop extends View {
    private static final int W = 480, H = 270;
    private static final float WASH = 0.62f;

    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint wash = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint glow = new Paint(Paint.DITHER_FLAG);
    private final Paint shade = new Paint(Paint.DITHER_FLAG);
    private final Rect dst = new Rect();
    private final Rect washDst = new Rect(-W / 20, -H / 20, W + W / 20, Math.round(H * 0.95f));
    private final Shader[] shades = {
            new LinearGradient(0, 0, 0, H, new int[] {0xB0000000, 0x00000000, 0x00000000, 0xE6000000, 0xFF000000}, new float[] {0f, 0.32f, 0.42f, 0.72f, 0.9f}, Shader.TileMode.CLAMP),
            new LinearGradient(0, 0, W, 0, new int[] {0xA0000000, 0x30000000, 0x00000000}, new float[] {0f, 0.45f, 0.75f}, Shader.TileMode.CLAMP),
    };
    private static HandlerThread worker;
    private final Handler composer;
    private Bitmap current;
    private Bitmap previous;
    private Bitmap shownWash;
    private int generation;
    private int shownAccent;
    private float mix = 1f;
    private float dim = 1f;
    private ValueAnimator sceneAnim;
    private ValueAnimator dimAnim;

    Backdrop(Context c) {
        super(c);
        wash.setAlpha(Math.round(255 * WASH));
        synchronized (Backdrop.class) {
            if (worker == null) {
                worker = new HandlerThread("lumen-backdrop", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                worker.start();
            }
        }
        composer = new Handler(worker.getLooper());
    }

    private Bitmap compose(Bitmap src, int accent) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        if (src != null) c.drawBitmap(src, null, washDst, wash);
        glow.setShader(new RadialGradient(W * 0.32f, H * 0.66f, W * 0.62f,
                new int[] {Apps.withAlpha(accent, 0x66), Apps.withAlpha(accent, 0x1C), 0x00000000},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, glow);
        for (Shader sh : shades) {
            shade.setShader(sh);
            c.drawRect(0, 0, W, H, shade);
        }
        return b;
    }

    void setScene(final Bitmap src, final int accent, final boolean animate) {
        if (src == shownWash && accent == shownAccent && current != null) return;
        shownWash = src;
        shownAccent = accent;
        final int gen = ++generation;
        composer.removeCallbacksAndMessages(null);
        composer.post(() -> {
            final Bitmap scene = compose(src, accent);
            post(() -> {
                if (gen == generation) show(scene, animate);
            });
        });
    }

    private void show(Bitmap scene, boolean animate) {
        if (sceneAnim != null) sceneAnim.cancel();
        if (!animate || current == null) {
            previous = null;
            current = scene;
            mix = 1f;
            invalidate();
            return;
        }
        if (mix < 1f && previous != null) {
            previous = mix < 0.5f ? previous : current;
        } else {
            previous = current;
        }
        current = scene;
        mix = 0f;
        sceneAnim = ValueAnimator.ofFloat(0f, 1f);
        sceneAnim.setDuration(650);
        sceneAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        sceneAnim.addUpdateListener(a -> {
            mix = (float) a.getAnimatedValue();
            invalidate();
        });
        sceneAnim.start();
    }

    void setDim(float target) {
        if (dimAnim != null) dimAnim.cancel();
        if (dim == target) return;
        dimAnim = ValueAnimator.ofFloat(dim, target);
        dimAnim.setDuration(380);
        dimAnim.addUpdateListener(a -> {
            dim = (float) a.getAnimatedValue();
            invalidate();
        });
        dimAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (current == null) {
            canvas.drawColor(0xFF000000);
            return;
        }
        dst.set(0, 0, getWidth(), getHeight());
        if (previous != null && mix < 1f) {
            blit.setAlpha(255);
            canvas.drawBitmap(previous, null, dst, blit);
            blit.setAlpha(Math.round(255 * mix));
        } else {
            blit.setAlpha(255);
        }
        canvas.drawBitmap(current, null, dst, blit);
        if (dim < 0.999f) canvas.drawColor(Apps.withAlpha(0xFF000000, Math.round(255 * (1 - dim))));
    }
}
