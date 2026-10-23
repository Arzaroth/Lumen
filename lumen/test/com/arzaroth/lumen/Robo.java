package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

/** Test helpers: a context, real canvases, the fake clock, and reach into private state. */
final class Robo {
    private Robo() {
    }

    static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    static Canvas canvas(int w, int h) {
        return new Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
    }

    /** Moves the clock on by `ms`, running whatever the main looper has due (animators included). */
    static void idle(long ms) {
        ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    static void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
    }

    static void wrap(View v) {
        v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    /** Draws `n` frames `ms` apart. */
    static void frames(View v, Canvas c, int n, long ms) {
        for (int i = 0; i < n; i++) {
            idle(ms);
            v.draw(c);
        }
    }

    static void untilBackgroundIdle() {
        for (int i = 0; i < 200; i++) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static boolean mainLooper() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    static Duration ms(long ms) {
        return Duration.ofMillis(ms);
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Object o, String name) {
        try {
            Field f = field(o.getClass(), name);
            f.setAccessible(true);
            return (T) f.get(o instanceof Class ? null : o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static void set(Object o, String name, Object value) {
        try {
            Field f = field(o.getClass(), name);
            f.setAccessible(true);
            f.set(o, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T call(Object o, String name, Object... args) {
        try {
            for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
                for (Method m : k.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                    m.setAccessible(true);
                    return (T) m.invoke(o, args);
                }
            }
            throw new AssertionError("no method " + name);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Field field(Class<?> k, String name) throws NoSuchFieldException {
        for (Class<?> c = k; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
