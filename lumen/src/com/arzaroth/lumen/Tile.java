package com.arzaroth.lumen;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

final class Tile extends View {
    static final float FOCUS_SCALE = 1.1f;

    final AppEntry app;
    final int imgW;
    final int imgH;
    private final float radius;
    private final float lift;
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final String text;
    private final float labelY;
    private float labelAlpha;
    private boolean focused;
    private ValueAnimator labelAnim;
    private ObjectAnimator wiggle;

    Tile(Context c, AppEntry app, int w, int h, int labelSpace, float s) {
        super(c);
        this.app = app;
        this.imgW = w;
        this.imgH = h;
        this.radius = 18 * s;
        this.lift = 28 * s;
        image.setShader(new BitmapShader(app.banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 1.5f * s));
        rim.setColor(0x1FFFFFFF);
        label.setTypeface(Fonts.outfit(c, 500));
        label.setTextSize(24 * s);
        label.setColor(0xFFFFFFFF);
        label.setTextAlign(Paint.Align.CENTER);
        label.setShadowLayer(10 * s, 0, 2 * s, 0xCC000000);
        text = TextUtils.ellipsize(app.label, label, w * 0.95f, TextUtils.TruncateAt.END).toString();
        labelY = h + labelSpace * 0.62f;
        setPivotX(w / 2f);
        setPivotY(h / 2f);
        final float r = radius;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, imgW, imgH, r);
                outline.setAlpha(0.85f);
            }
        });
        setLayerType(LAYER_TYPE_NONE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, imgW, imgH);
        canvas.drawRoundRect(rect, radius, radius, image);
        rect.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
        canvas.drawRoundRect(rect, radius, radius, rim);
        if (labelAlpha > 0.01f) {
            label.setAlpha(Math.round(255 * labelAlpha));
            canvas.drawText(text, imgW / 2f, labelY + (1 - labelAlpha) * -8 * (lift / 28f), label);
        }
    }

    void setFocusedState(boolean f, boolean animate) {
        if (f == focused && animate) return;
        focused = f;
        float scale = f ? FOCUS_SCALE : 1f;
        float z = f ? lift : 0f;
        if (animate) {
            animate().scaleX(scale).scaleY(scale).translationZ(z)
                    .setInterpolator(f ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator())
                    .setDuration(f ? 340 : 220).start();
        } else {
            animate().cancel();
            setScaleX(scale);
            setScaleY(scale);
            setTranslationZ(z);
        }
        rim.setColor(f ? 0x40FFFFFF : 0x1FFFFFFF);
        if (labelAnim != null) labelAnim.cancel();
        if (animate) {
            labelAnim = ValueAnimator.ofFloat(labelAlpha, f ? 1f : 0f);
            labelAnim.setDuration(f ? 260 : 140);
            labelAnim.setStartDelay(f ? 60 : 0);
            labelAnim.addUpdateListener(a -> {
                labelAlpha = (float) a.getAnimatedValue();
                invalidate();
            });
            labelAnim.start();
        } else {
            labelAlpha = f ? 1f : 0f;
            invalidate();
        }
    }

    void setWiggling(boolean on) {
        if (wiggle != null) {
            wiggle.cancel();
            wiggle = null;
        }
        if (on) {
            wiggle = ObjectAnimator.ofFloat(this, ROTATION, -1.4f, 1.4f);
            wiggle.setDuration(130);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        } else {
            animate().rotation(0).setDuration(120).start();
        }
    }
}
