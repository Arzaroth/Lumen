package com.arzaroth.lumen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A thin downward chevron under the dock: there are more apps below. */
final class Chevron extends View {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    Chevron(Context c, float s) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, 3f * s));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0x80FFFFFF);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float inset = stroke.getStrokeWidth();
        path.reset();
        path.moveTo(inset, inset);
        path.lineTo(w / 2f, h - inset);
        path.lineTo(w - inset, inset);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawPath(path, stroke);
    }
}
