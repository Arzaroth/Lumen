package com.arzaroth.lumen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The glass dropdown under the top bar: a stack of pages. A page is either a list of rows (actions,
 * choices that cycle with Left/Right, submenus) or a question with two buttons side by side.
 */
final class Menu extends View {
    static final class Row {
        final String label;
        final Runnable action;
        final Supplier<Page> sub;
        final String[] values;
        final IntConsumer onChoice;
        final boolean enabled;
        int value;

        private Row(String label, Runnable action, Supplier<Page> sub, String[] values, int value, IntConsumer onChoice,
                    boolean enabled) {
            this.label = label;
            this.action = action;
            this.sub = sub;
            this.values = values;
            this.value = value;
            this.onChoice = onChoice;
            this.enabled = enabled;
        }

        static Row action(String label, Runnable action) {
            return new Row(label, action, null, null, 0, null, true);
        }

        static Row sub(String label, Supplier<Page> sub) {
            return new Row(label, null, sub, null, 0, null, true);
        }

        static Row choice(String label, String[] values, int value, IntConsumer onChoice) {
            return new Row(label, null, null, values, Math.max(0, Math.min(values.length - 1, value)), onChoice, true);
        }

        static Row note(String label) {
            return new Row(label, null, null, null, 0, null, false);
        }
    }

    static final class Page {
        final String title;
        final float width;
        final List<Row> rows = new ArrayList<>();
        final String message;
        final String[] buttons;
        final Runnable[] answers;
        int selected;

        Page(String title, float width) {
            this(title, width, null, null, null, 0);
        }

        private Page(String title, float width, String message, String[] buttons, Runnable[] answers, int selected) {
            this.title = title;
            this.width = width;
            this.message = message;
            this.buttons = buttons;
            this.answers = answers;
            this.selected = selected;
        }

        /** A question with two buttons; `selected` is the one under the cursor when it opens. */
        static Page ask(String title, String message, String left, Runnable onLeft, String right, Runnable onRight, int selected) {
            return new Page(title, 560, message, new String[] {left, right}, new Runnable[] {onLeft, onRight}, selected);
        }

        Page add(Row row) {
            rows.add(row);
            return this;
        }

        boolean isQuestion() {
            return buttons != null;
        }
    }

    private final ArrayList<Page> stack = new ArrayList<>();
    private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint value = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint header = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint body = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float s, rowH, pad, headerH, radius, inset;
    private StaticLayout messageLayout;
    private float open, slide;
    private boolean shown;
    private ValueAnimator anim, pageAnim;

    Menu(Context c, float s) {
        super(c);
        this.s = s;
        rowH = 64 * s;
        pad = 14 * s;
        headerH = 54 * s;
        radius = 28 * s;
        inset = 18 * s;
        panel.setColor(0xEB16161C);
        panel.setShadowLayer(60 * s, 0, 20 * s, 0xB0000000);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, 1.5f * s));
        edge.setColor(0x2EFFFFFF);
        selection.setColor(0xF5FFFFFF);
        button.setColor(0x24FFFFFF);
        text.setTypeface(Fonts.outfit(c, 500));
        text.setTextSize(26 * s);
        value.setTypeface(Fonts.outfit(c, 400));
        value.setTextSize(24 * s);
        header.setTypeface(Fonts.outfit(c, 600));
        header.setTextSize(18 * s);
        header.setLetterSpacing(0.12f);
        header.setColor(0x80FFFFFF);
        body.setTypeface(Fonts.outfit(c, 400));
        body.setTextSize(26 * s);
        body.setColor(0xE6FFFFFF);
        setVisibility(GONE);
        setPivotY(0);
    }

    boolean isOpen() {
        return shown;
    }

    private Page top() {
        return stack.get(stack.size() - 1);
    }

    /** Opens on `root`, dropping whatever was open. */
    void open(Page root) {
        stack.clear();
        stack.add(root);
        pageChanged(0);
        toggle(true);
    }

    void close() {
        toggle(false);
    }

    void push(Page page) {
        stack.add(page);
        pageChanged(1);
    }

    /** Swaps the current page for `page`, as a question leading to the next. */
    void replace(Page page) {
        stack.set(stack.size() - 1, page);
        pageChanged(1);
    }

    /** Back one page; false at the root. */
    boolean pop() {
        if (stack.size() <= 1) return false;
        stack.remove(stack.size() - 1);
        pageChanged(-1);
        return true;
    }

    private void pageChanged(int direction) {
        Page p = top();
        if (p.isQuestion()) {
            int w = Math.round(p.width * s - 2 * (pad + inset));
            messageLayout = StaticLayout.Builder.obtain(p.message, 0, p.message.length(), body, w)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4 * s, 1f).build();
        } else {
            messageLayout = null;
            if (p.selected >= p.rows.size() || !p.rows.isEmpty() && !p.rows.get(p.selected).enabled) p.selected = firstEnabled(p);
        }
        requestLayout();
        if (pageAnim != null) pageAnim.cancel();
        if (direction == 0) {
            slide = 0;
            invalidate();
            return;
        }
        pageAnim = ValueAnimator.ofFloat(direction, 0f);
        pageAnim.setDuration(220);
        pageAnim.setInterpolator(new DecelerateInterpolator(2f));
        pageAnim.addUpdateListener(a -> {
            slide = (float) a.getAnimatedValue();
            invalidate();
        });
        pageAnim.start();
    }

    private static int firstEnabled(Page p) {
        for (int i = 0; i < p.rows.size(); i++) if (p.rows.get(i).enabled) return i;
        return 0;
    }

    private void toggle(boolean show) {
        shown = show;
        if (anim != null) anim.cancel();
        if (show) setVisibility(VISIBLE);
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

    /** True when Up should leave the menu: the cursor is on the root page's first row. */
    boolean atTop() {
        Page p = top();
        return stack.size() == 1 && !p.isQuestion() && p.selected == firstEnabled(p);
    }

    void move(int delta) {
        Page p = top();
        if (p.isQuestion() || p.rows.isEmpty()) return;
        int next = p.selected;
        do {
            next += delta;
        } while (next >= 0 && next < p.rows.size() && !p.rows.get(next).enabled);
        if (next < 0 || next >= p.rows.size()) return;
        p.selected = next;
        invalidate();
    }

    /** Left/Right: cycles a choice, or moves between a question's buttons. */
    void side(int delta) {
        Page p = top();
        if (p.isQuestion()) {
            int next = Math.max(0, Math.min(1, p.selected + delta));
            if (next != p.selected) {
                p.selected = next;
                invalidate();
            }
            return;
        }
        if (p.rows.isEmpty()) return;
        Row r = p.rows.get(p.selected);
        if (r.values == null) return;
        r.value = (r.value + delta + r.values.length) % r.values.length;
        r.onChoice.accept(r.value);
        invalidate();
    }

    void activate() {
        Page p = top();
        if (p.isQuestion()) {
            p.answers[p.selected].run();
            return;
        }
        if (p.rows.isEmpty()) return;
        Row r = p.rows.get(p.selected);
        if (!r.enabled) return;
        if (r.sub != null) {
            push(r.sub.get());
        } else if (r.values != null) {
            side(1);
        } else if (r.action != null) {
            r.action.run();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        Page p = stack.isEmpty() ? null : top();
        int w = Math.round((p == null ? 360 : p.width) * s);
        float h;
        if (p == null) {
            h = headerH + pad + rowH + pad;
        } else if (p.isQuestion()) {
            h = headerH + pad + messageLayout.getHeight() + 28 * s + rowH + pad;
        } else {
            h = headerH + pad + Math.max(1, p.rows.size()) * rowH + pad;
        }
        setMeasuredDimension(w, Math.round(h));
        setPivotX(w);
    }

    private float baseline(TextPaint t, float top, float h) {
        return top + h / 2 - (t.descent() + t.ascent()) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (stack.isEmpty()) return;
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, panel);
        rect.inset(edge.getStrokeWidth() / 2, edge.getStrokeWidth() / 2);
        canvas.drawRoundRect(rect, radius, radius, edge);

        Page p = top();
        int save = canvas.save();
        canvas.clipRect(0, 0, w, h);
        canvas.translate(slide * 40 * s, 0);
        int alpha = Math.round(255 * (1 - Math.min(1f, Math.abs(slide))));
        header.setAlpha(Math.round(0x80 * alpha / 255f));
        canvas.drawText(p.title.toUpperCase(Locale.getDefault()), pad + inset, headerH * 0.78f, header);
        if (p.isQuestion()) {
            drawQuestion(canvas, p, w, alpha);
        } else {
            drawRows(canvas, p, w, alpha);
        }
        canvas.restoreToCount(save);
    }

    private void drawRows(Canvas canvas, Page p, float w, int alpha) {
        if (p.rows.isEmpty()) {
            text.setColor(withAlpha(0x80FFFFFF, alpha));
            canvas.drawText("Nothing here", pad + inset, baseline(text, headerH + pad, rowH), text);
            return;
        }
        for (int i = 0; i < p.rows.size(); i++) {
            Row r = p.rows.get(i);
            float top = headerH + pad + i * rowH;
            boolean sel = i == p.selected && r.enabled;
            if (sel) {
                rect.set(pad, top + 4 * s, w - pad, top + rowH - 4 * s);
                selection.setAlpha(Math.round(0xF5 * alpha / 255f));
                canvas.drawRoundRect(rect, (rowH - 8 * s) / 2, (rowH - 8 * s) / 2, selection);
            }
            int ink = !r.enabled ? 0x66FFFFFF : sel ? 0xFF101014 : 0xE6FFFFFF;
            text.setColor(withAlpha(ink, alpha));
            canvas.drawText(r.label, pad + inset, baseline(text, top, rowH), text);
            String right = r.values != null ? (sel ? "‹  " + r.values[r.value] + "  ›" : r.values[r.value])
                    : r.sub != null ? "›" : null;
            if (right != null) {
                value.setColor(withAlpha(sel ? 0xCC101014 : 0x99FFFFFF, alpha));
                canvas.drawText(right, w - pad - inset - value.measureText(right), baseline(value, top, rowH), value);
            }
        }
    }

    private void drawQuestion(Canvas canvas, Page p, float w, int alpha) {
        canvas.save();
        canvas.translate(pad + inset, headerH + pad);
        body.setAlpha(Math.round(0xE6 * alpha / 255f));
        messageLayout.draw(canvas);
        canvas.restore();
        float top = headerH + pad + messageLayout.getHeight() + 28 * s, gapW = 12 * s;
        float bw = (w - 2 * pad - gapW) / 2;
        for (int i = 0; i < 2; i++) {
            float left = pad + i * (bw + gapW);
            rect.set(left, top + 4 * s, left + bw, top + rowH - 4 * s);
            boolean sel = i == p.selected;
            Paint fill = sel ? selection : button;
            fill.setAlpha(Math.round((sel ? 0xF5 : 0x24) * alpha / 255f));
            canvas.drawRoundRect(rect, (rowH - 8 * s) / 2, (rowH - 8 * s) / 2, fill);
            text.setColor(withAlpha(sel ? 0xFF101014 : 0xE6FFFFFF, alpha));
            String label = p.buttons[i];
            canvas.drawText(label, left + (bw - text.measureText(label)) / 2, baseline(text, top, rowH), text);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return Apps.withAlpha(color, Math.round((color >>> 24) * alpha / 255f));
    }
}
