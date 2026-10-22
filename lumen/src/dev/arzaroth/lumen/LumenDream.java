package dev.arzaroth.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.View;

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

    private static final class ClockFace extends View {
        private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final Runnable tick = this::tick;
        private float fx = 0.5f, fy = 0.5f;

        ClockFace(Context c) {
            super(c);
            time.setTypeface(Fonts.outfit(c, 150));
            time.setColor(0xFFE8E8EE);
            time.setTextAlign(Paint.Align.CENTER);
            date.setTypeface(Fonts.outfit(c, 400));
            date.setColor(0x80FFFFFF);
            date.setTextAlign(Paint.Align.CENTER);
            date.setLetterSpacing(0.08f);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            tick();
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        private void tick() {
            animate().alpha(0f).setDuration(1200).withEndAction(() -> {
                fx = 0.3f + random.nextFloat() * 0.4f;
                fy = 0.35f + random.nextFloat() * 0.3f;
                invalidate();
                animate().alpha(1f).setDuration(1600).start();
            }).start();
            long now = System.currentTimeMillis();
            postDelayed(tick, 60_000 - now % 60_000 + 50);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF000000);
            float w = getWidth(), h = getHeight();
            time.setTextSize(h * 0.22f);
            date.setTextSize(h * 0.032f);
            Calendar now = Calendar.getInstance();
            String t = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", now).toString();
            String d = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), now).toString().toUpperCase(Locale.getDefault());
            canvas.drawText(t, w * fx, h * fy, time);
            canvas.drawText(d, w * fx, h * fy + h * 0.08f, date);
        }
    }
}
