package dev.arzaroth.lumen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;

final class InputsPanel extends View {
    private static final String[] LIVE_TV_PACKAGES = {"com.android.tv", "com.realtek.tv", "com.google.android.tv", "com.google.android.apps.tv.launcherx"};

    private static final class Item {
        final String label;
        final Intent intent;

        Item(String label, Intent intent) {
            this.label = label;
            this.intent = intent;
        }
    }

    private final ArrayList<Item> items = new ArrayList<>();
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint header = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float s, rowH, pad, headerH, radius;
    private int selected;
    private float open;
    private ValueAnimator anim;

    InputsPanel(Context c, float s) {
        super(c);
        this.s = s;
        rowH = 64 * s;
        pad = 14 * s;
        headerH = 54 * s;
        radius = 28 * s;
        panel.setColor(0xEB16161C);
        panel.setShadowLayer(60 * s, 0, 20 * s, 0xB0000000);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, 1.5f * s));
        edge.setColor(0x2EFFFFFF);
        selection.setColor(0xF5FFFFFF);
        text.setTypeface(Fonts.outfit(c, 500));
        text.setTextSize(26 * s);
        header.setTypeface(Fonts.outfit(c, 600));
        header.setTextSize(18 * s);
        header.setLetterSpacing(0.12f);
        header.setColor(0x80FFFFFF);
        setVisibility(GONE);
        setPivotY(0);
    }

    int desiredWidth() {
        return Math.round(360 * s);
    }

    int desiredHeight() {
        return Math.round(headerH + pad + Math.max(1, items.size()) * rowH + pad);
    }

    private boolean shown;

    boolean isOpen() {
        return shown;
    }

    void reload() {
        items.clear();
        Context c = getContext();
        PackageManager pm = c.getPackageManager();
        for (String pkg : LIVE_TV_PACKAGES) {
            Intent live = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (live != null) {
                items.add(new Item("Live TV", live.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                break;
            }
        }
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            int first = items.size();
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isHidden(c) || !info.isPassthroughInput() || info.getParentId() != null) continue;
                CharSequence custom = info.loadCustomLabel(c);
                String label = String.valueOf(custom != null ? custom : info.loadLabel(c));
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                items.add(new Item(label, i));
            }
            items.subList(first, items.size()).sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        }
        selected = 0;
        requestLayout();
    }

    void toggle(boolean show) {
        shown = show;
        if (anim != null) anim.cancel();
        if (show) {
            setVisibility(VISIBLE);
            selected = 0;
        }
        anim = ValueAnimator.ofFloat(open, show ? 1f : 0f);
        anim.setDuration(show ? 260 : 160);
        anim.setInterpolator(new DecelerateInterpolator(2f));
        anim.addUpdateListener(a -> {
            open = (float) a.getAnimatedValue();
            setAlpha(open);
            setScaleY(0.92f + 0.08f * open);
            setTranslationY((1 - open) * -12 * s);
        });
        if (!show) {
            anim.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator a) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator a) {
                    if (!cancelled) setVisibility(GONE);
                }
            });
        }
        anim.start();
    }

    void move(int delta) {
        if (items.isEmpty()) return;
        int next = Math.max(0, Math.min(items.size() - 1, selected + delta));
        if (next != selected) {
            selected = next;
            invalidate();
        }
    }

    boolean atTop() {
        return selected == 0;
    }

    boolean launchSelected() {
        if (items.isEmpty()) return false;
        try {
            getContext().startActivity(items.get(selected).intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(desiredWidth(), desiredHeight());
        setPivotX(desiredWidth());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, panel);
        rect.inset(edge.getStrokeWidth() / 2, edge.getStrokeWidth() / 2);
        canvas.drawRoundRect(rect, radius, radius, edge);
        canvas.drawText("INPUTS", pad + 18 * s, headerH * 0.78f, header);
        float y = headerH;
        if (items.isEmpty()) {
            text.setColor(0x80FFFFFF);
            canvas.drawText("No inputs found", pad + 18 * s, y + pad + rowH / 2 - (text.descent() + text.ascent()) / 2, text);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            float top = y + pad + i * rowH;
            boolean sel = i == selected;
            if (sel) {
                rect.set(pad, top + 4 * s, w - pad, top + rowH - 4 * s);
                canvas.drawRoundRect(rect, (rowH - 8 * s) / 2, (rowH - 8 * s) / 2, selection);
            }
            text.setColor(sel ? 0xFF101014 : 0xE6FFFFFF);
            canvas.drawText(items.get(i).label, pad + 18 * s, top + rowH / 2 - (text.descent() + text.ascent()) / 2, text);
        }
    }
}
