package com.home.tvlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Clean, filled status icons (Wi-Fi with signal level, settings gear, inputs, search, picture). */
final class IconDrawable extends Drawable {
    static final int WIFI = 0, GEAR = 1, INPUT = 2, SEARCH = 3, PICTURE = 4, HOME = 5;

    private final int kind;
    private final int level; // Wi-Fi: 0..3, -1 = offline
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFFFFFFFF;

    IconDrawable(int kind) { this(kind, 3); }

    IconDrawable(int kind, int level) {
        this.kind = kind;
        this.level = level;
        p.setColor(color);
        dim.setColor(0x59FFFFFF);
    }

    void setColor(int c) {
        color = c;
        p.setColor(c);
        dim.setColor((c & 0x00FFFFFF) | 0x59000000);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        switch (kind) {
            case WIFI: wifi(c, x, y, s); break;
            case GEAR: gear(c, x + s / 2, y + s / 2, s); break;
            case INPUT: input(c, x, y, s); break;
            case SEARCH: search(c, x, y, s); break;
            case HOME: house(c, x, y, s); break;
            default: picture(c, x, y, s);
        }
    }

    /** Wi-Fi fan: filled wedges, lit up to the current signal level. */
    private void wifi(Canvas c, float x, float y, float s) {
        float cx = x + s / 2, cy = y + s * 0.92f;
        float gap = s * 0.065f;
        float[] outer = {s * 0.2f, s * 0.42f, s * 0.64f, s * 0.86f};
        for (int i = 0; i < 4; i++) {
            Paint paint = level >= 0 && i <= level ? p : dim;
            Path band = new Path();
            RectF ro = new RectF(cx - outer[i], cy - outer[i], cx + outer[i], cy + outer[i]);
            if (i == 0) { // innermost: a filled wedge
                band.moveTo(cx, cy);
                band.arcTo(ro, 225, 90);
                band.close();
            } else {      // a ring segment between the previous band (plus a gap) and this radius
                float ri = outer[i - 1] + gap;
                RectF rin = new RectF(cx - ri, cy - ri, cx + ri, cy + ri);
                band.arcTo(ro, 225, 90, true);
                band.arcTo(rin, 315, -90);
                band.close();
            }
            c.drawPath(band, paint);
        }
        if (level < 0) { // offline: small cross
            Paint xp = new Paint(p);
            xp.setStyle(Paint.Style.STROKE);
            xp.setStrokeWidth(s * 0.09f);
            xp.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x + s * 0.68f, y + s * 0.58f, x + s * 0.92f, y + s * 0.82f, xp);
            c.drawLine(x + s * 0.92f, y + s * 0.58f, x + s * 0.68f, y + s * 0.82f, xp);
        }
    }

    /** Filled cog with 8 teeth and a round hole. */
    private void gear(Canvas c, float cx, float cy, float s) {
        Path g = new Path();
        int teeth = 8;
        float outer = s * 0.47f, inner = s * 0.36f;
        for (int i = 0; i < teeth * 4; i++) {
            double a = Math.PI * 2 * i / (teeth * 4) - Math.PI / 2;
            float r = (i % 4 == 0 || i % 4 == 1) ? outer : inner;
            float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
            if (i == 0) g.moveTo(px, py); else g.lineTo(px, py);
        }
        g.close();
        g.addCircle(cx, cy, s * 0.15f, Path.Direction.CCW);
        g.setFillType(Path.FillType.EVEN_ODD);
        c.drawPath(g, p);
    }

    /** Input: a screen with an arrow coming in. */
    private void input(Canvas c, float x, float y, float s) {
        Paint st = new Paint(p);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(s * 0.09f);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setStrokeCap(Paint.Cap.ROUND);
        c.drawRoundRect(new RectF(x + s * 0.22f, y + s * 0.16f, x + s * 0.92f, y + s * 0.74f), s * 0.08f, s * 0.08f, st);
        c.drawLine(x + s * 0.45f, y + s * 0.88f, x + s * 0.69f, y + s * 0.88f, st);
        Path arrow = new Path();
        arrow.moveTo(x + s * 0.06f, y + s * 0.45f);
        arrow.lineTo(x + s * 0.55f, y + s * 0.45f);
        c.drawPath(arrow, st);
        Path head = new Path();
        head.moveTo(x + s * 0.44f, y + s * 0.33f);
        head.lineTo(x + s * 0.57f, y + s * 0.45f);
        head.lineTo(x + s * 0.44f, y + s * 0.57f);
        c.drawPath(head, st);
    }

    /** Google Home style house outline. */
    private void house(Canvas c, float x, float y, float s) {
        Paint st = new Paint(p);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(s * 0.1f);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setStrokeCap(Paint.Cap.ROUND);
        Path h = new Path();
        h.moveTo(x + s * 0.14f, y + s * 0.88f);
        h.lineTo(x + s * 0.14f, y + s * 0.44f);
        h.lineTo(x + s * 0.5f, y + s * 0.12f);
        h.lineTo(x + s * 0.86f, y + s * 0.44f);
        h.lineTo(x + s * 0.86f, y + s * 0.88f);
        h.close();
        c.drawPath(h, st);
        c.drawLine(x + s * 0.4f, y + s * 0.88f, x + s * 0.4f, y + s * 0.64f, st);
        c.drawLine(x + s * 0.4f, y + s * 0.64f, x + s * 0.6f, y + s * 0.64f, st);
        c.drawLine(x + s * 0.6f, y + s * 0.64f, x + s * 0.6f, y + s * 0.88f, st);
    }

    private void search(Canvas c, float x, float y, float s) {
        Paint st = new Paint(p);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(s * 0.11f);
        st.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(x + s * 0.42f, y + s * 0.42f, s * 0.27f, st);
        c.drawLine(x + s * 0.62f, y + s * 0.62f, x + s * 0.88f, y + s * 0.88f, st);
    }

    private void picture(Canvas c, float x, float y, float s) {
        Paint st = new Paint(p);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(s * 0.09f);
        st.setStrokeJoin(Paint.Join.ROUND);
        c.drawRoundRect(new RectF(x + s * 0.1f, y + s * 0.18f, x + s * 0.9f, y + s * 0.82f), s * 0.08f, s * 0.08f, st);
        Path m = new Path();
        m.moveTo(x + s * 0.18f, y + s * 0.74f);
        m.lineTo(x + s * 0.42f, y + s * 0.48f);
        m.lineTo(x + s * 0.58f, y + s * 0.62f);
        m.lineTo(x + s * 0.68f, y + s * 0.54f);
        m.lineTo(x + s * 0.82f, y + s * 0.74f);
        m.close();
        c.drawPath(m, p);
        c.drawCircle(x + s * 0.68f, y + s * 0.36f, s * 0.07f, p);
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); dim.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
