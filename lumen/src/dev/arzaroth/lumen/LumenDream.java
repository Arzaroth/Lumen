package dev.arzaroth.lumen;

import android.content.Context;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public final class LumenDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(new ClockFace(this));
    }

    /**
     * Lumen's clock, large. Each minute it plays its change where it stands, then fades out and
     * comes back somewhere else, against burn-in.
     */
    private static final class ClockFace extends FrameLayout {
        private final Random random = new Random();
        private final Runnable tick = this::tick;
        private final LinearLayout holder;
        private final ClockView clock;
        private final TextView date;

        ClockFace(Context c) {
            super(c);
            setBackgroundColor(0xFF000000);
            setClipChildren(false);
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            float h = Math.min(dm.widthPixels, dm.heightPixels);
            clock = new ClockView(c, h * 0.22f, false);
            clock.setAccent(0xFFB8C6E8);
            date = new TextView(c);
            date.setTypeface(Fonts.outfit(c, 400));
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.032f);
            date.setTextColor(0x80FFFFFF);
            date.setLetterSpacing(0.08f);
            date.setIncludeFontPadding(false);
            holder = new LinearLayout(c);
            holder.setOrientation(LinearLayout.VERTICAL);
            holder.setGravity(Gravity.CENTER_HORIZONTAL);
            holder.setClipChildren(false);
            holder.addView(clock);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = -Math.round(clock.inset() * 0.8f);
            holder.addView(date, dlp);
            holder.setAlpha(0f);
            addView(holder, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            clock.setFormat(DateFormat.is24HourFormat(getContext()));
            clock.setRunning(true);
            post(this::place);
            schedule();
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            clock.setRunning(false);
            super.onDetachedFromWindow();
        }

        private void schedule() {
            long now = System.currentTimeMillis();
            postDelayed(tick, 60_000 - now % 60_000 + 2500);
        }

        private void tick() {
            holder.animate().alpha(0f).setDuration(1200).withEndAction(this::place).start();
            schedule();
        }

        private void place() {
            date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"),
                    Calendar.getInstance()).toString().toUpperCase(Locale.getDefault()));
            float fx = 0.3f + random.nextFloat() * 0.4f, fy = 0.35f + random.nextFloat() * 0.3f;
            holder.setTranslationX(getWidth() * fx - holder.getWidth() / 2f);
            holder.setTranslationY(getHeight() * fy - holder.getHeight() / 2f);
            holder.animate().alpha(1f).setDuration(1600).start();
        }
    }
}
