package com.arzaroth.lumen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

final class Highlight extends View {
    private final RectF current = new RectF();
    private final RectF from = new RectF();
    private final RectF to = new RectF();
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix sheenMatrix = new Matrix();
    private final LinearGradient sheenShader;
    private final float radius;
    private final float glowRadius;
    private int accent = Apps.NEUTRAL;
    private ValueAnimator anim;
    private float visibility;
    private ValueAnimator fade;

    Highlight(Context c, float s) {
        super(c);
        radius = 26 * s;
        glowRadius = 46 * s;
        glass.setColor(0x2EFFFFFF);
        sheenShader = new LinearGradient(0, 0, 0, 1, new int[] {0x55FFFFFF, 0x10FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        sheen.setShader(sheenShader);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, 2f * s));
        edge.setColor(0x70FFFFFF);
    }

    void moveTo(RectF target, int accentColor, boolean animate) {
        accent = accentColor;
        if (anim != null) anim.cancel();
        show(true, animate);
        if (!animate || current.isEmpty()) {
            current.set(target);
            invalidate();
            return;
        }
        from.set(current);
        to.set(target);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(420);
        anim.setInterpolator(Highlight::spring);
        anim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            current.set(
                    from.left + (to.left - from.left) * t,
                    from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t,
                    from.bottom + (to.bottom - from.bottom) * t);
            invalidate();
        });
        anim.start();
    }

    private static float spring(float t) {
        double zeta = 0.62, omega = 13.0;
        double wd = omega * Math.sqrt(1 - zeta * zeta);
        double env = Math.exp(-zeta * omega * t);
        return (float) (1 - env * (Math.cos(wd * t) + (zeta * omega / wd) * Math.sin(wd * t)));
    }

    void show(boolean on, boolean animate) {
        float target = on ? 1f : 0f;
        if (fade != null) fade.cancel();
        if (!animate) {
            visibility = target;
            invalidate();
            return;
        }
        if (visibility == target) return;
        fade = ValueAnimator.ofFloat(visibility, target);
        fade.setDuration(on ? 200 : 160);
        fade.addUpdateListener(a -> {
            visibility = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (current.isEmpty() || visibility <= 0.01f) return;
        int a = Math.round(255 * visibility);
        glass.setAlpha(Math.round(0x2E * visibility));
        glass.setShadowLayer(glowRadius, 0, 0, Apps.withAlpha(accent, Math.round(0xB0 * visibility)));
        canvas.drawRoundRect(current, radius, radius, glass);
        sheenMatrix.setScale(1, current.height());
        sheenMatrix.postTranslate(0, current.top);
        sheenShader.setLocalMatrix(sheenMatrix);
        sheen.setAlpha(a);
        canvas.drawRoundRect(current, radius, radius, sheen);
        edge.setAlpha(Math.round(0x70 * visibility));
        canvas.drawRoundRect(current, radius, radius, edge);
    }
}
