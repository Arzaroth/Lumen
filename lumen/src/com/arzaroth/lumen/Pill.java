package com.arzaroth.lumen;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.OvershootInterpolator;

final class Pill extends View {
    static final int INPUTS = 0, WIFI = 1, SETTINGS = 2;

    private final int kind;
    private final float s;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private final float height, padH, iconSize, gapIcon;
    private String label;
    private boolean wired;
    private float focus;
    private ValueAnimator anim;

    Pill(Context c, int kind, String label, float s) {
        super(c);
        this.kind = kind;
        this.label = label;
        this.s = s;
        height = 60 * s;
        padH = label.isEmpty() ? 18 * s : 26 * s;
        iconSize = 26 * s;
        gapIcon = 12 * s;
        text.setTypeface(Fonts.outfit(c, 500));
        text.setTextSize(24 * s);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, 1.5f * s));
        glyph.setStyle(Paint.Style.STROKE);
        glyph.setStrokeWidth(2.4f * s);
        glyph.setStrokeCap(Paint.Cap.ROUND);
        glyph.setStrokeJoin(Paint.Join.ROUND);
        setPivotY(height / 2f);
    }

    void setLabel(String l) {
        if (l.equals(label)) return;
        label = l;
        requestLayout();
        invalidate();
    }

    void setWired(boolean w) {
        if (w == wired) return;
        wired = w;
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float w = padH * 2 + iconSize + (label.isEmpty() ? 0 : gapIcon + text.measureText(label));
        setMeasuredDimension(Math.round(w), Math.round(height));
        setPivotX(w / 2f);
    }

    void setFocusedState(boolean f, boolean animate) {
        float target = f ? 1f : 0f;
        if (anim != null) anim.cancel();
        animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                .setInterpolator(new OvershootInterpolator(2f)).setDuration(animate ? 260 : 0).start();
        if (!animate) {
            focus = target;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(focus, target);
        anim.setDuration(180);
        anim.addUpdateListener(a -> {
            focus = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        fill.setColor((int) argb.evaluate(focus, 0x24FFFFFF, 0xF5FFFFFF));
        fill.setShadowLayer(focus * 24 * s, 0, focus * 6 * s, Apps.withAlpha(0xFF000000, Math.round(0x80 * focus)));
        canvas.drawRoundRect(rect, h / 2, h / 2, fill);
        edge.setColor(Apps.withAlpha(0xFFFFFFFF, Math.round(0x30 * (1 - focus))));
        rect.inset(edge.getStrokeWidth() / 2, edge.getStrokeWidth() / 2);
        canvas.drawRoundRect(rect, h / 2, h / 2, edge);

        int fg = (int) argb.evaluate(focus, 0xE6FFFFFF, 0xFF101014);
        glyph.setColor(fg);
        text.setColor(fg);
        float cx = padH + iconSize / 2, cy = h / 2;
        drawGlyph(canvas, cx, cy, iconSize / 2);
        if (!label.isEmpty()) {
            float baseline = cy - (text.descent() + text.ascent()) / 2;
            canvas.drawText(label, padH + iconSize + gapIcon, baseline, text);
        }
    }

    private void drawGlyph(Canvas c, float cx, float cy, float r) {
        path.reset();
        switch (kind) {
            case INPUTS:
                rect.set(cx - r, cy - r * 0.72f, cx + r, cy + r * 0.52f);
                c.drawRoundRect(rect, r * 0.18f, r * 0.18f, glyph);
                path.moveTo(cx - r * 0.45f, cy + r * 0.9f);
                path.lineTo(cx + r * 0.45f, cy + r * 0.9f);
                path.moveTo(cx - r * 0.25f, cy - r * 0.1f);
                path.lineTo(cx + r * 0.3f, cy - r * 0.1f);
                path.moveTo(cx + r * 0.05f, cy - r * 0.35f);
                path.lineTo(cx + r * 0.3f, cy - r * 0.1f);
                path.lineTo(cx + r * 0.05f, cy + r * 0.15f);
                c.drawPath(path, glyph);
                break;
            case WIFI:
                if (wired) {
                    rect.set(cx - r * 0.62f, cy - r * 0.55f, cx + r * 0.62f, cy + r * 0.35f);
                    c.drawRoundRect(rect, r * 0.12f, r * 0.12f, glyph);
                    rect.set(cx - r * 0.3f, cy + r * 0.35f, cx + r * 0.3f, cy + r * 0.62f);
                    c.drawRect(rect, glyph);
                    for (int i = -1; i <= 1; i++) {
                        c.drawLine(cx + i * r * 0.3f, cy - r * 0.55f, cx + i * r * 0.3f, cy - r * 0.2f, glyph);
                    }
                    c.drawLine(cx, cy + r * 0.62f, cx, cy + r, glyph);
                    break;
                }
                float by = cy + r * 0.7f;
                for (int i = 1; i <= 3; i++) {
                    float rr = r * 0.42f * i;
                    rect.set(cx - rr, by - rr, cx + rr, by + rr);
                    c.drawArc(rect, 225, 90, false, glyph);
                }
                glyph.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, by, r * 0.13f, glyph);
                glyph.setStyle(Paint.Style.STROKE);
                break;
            default:
                int teeth = 8;
                for (int i = 0; i < teeth; i++) {
                    double a = Math.PI * 2 * i / teeth;
                    float x0 = cx + (float) Math.cos(a) * r * 0.62f, y0 = cy + (float) Math.sin(a) * r * 0.62f;
                    float x1 = cx + (float) Math.cos(a) * r * 0.95f, y1 = cy + (float) Math.sin(a) * r * 0.95f;
                    c.drawLine(x0, y0, x1, y1, glyph);
                }
                c.drawCircle(cx, cy, r * 0.62f, glyph);
                c.drawCircle(cx, cy, r * 0.24f, glyph);
                break;
        }
    }
}
