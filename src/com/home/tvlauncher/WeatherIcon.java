package com.home.tvlauncher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Simple white line-art weather icons drawn in code (sun, cloud, rain, snow, fog, storm). */
final class WeatherIcon extends Drawable {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6, MOON = 7;

    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherIcon(int kind) {
        this.kind = kind;
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    /** WMO weather code (Open-Meteo) to icon kind. */
    static int fromCode(int code) {
        if (code == 0) return CLEAR;
        if (code <= 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        if (code >= 51) return RAIN;
        return CLOUDY;
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left, y = b.top;
        p.setStrokeWidth(s * 0.065f);
        switch (kind) {
            case MOON: {
                Path m = new Path();
                m.addCircle(x + s * 0.5f, y + s * 0.5f, s * 0.32f, Path.Direction.CW);
                Path cut = new Path();
                cut.addCircle(x + s * 0.68f, y + s * 0.36f, s * 0.28f, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(m, p);
                break;
            }
            case CLEAR:
                sun(c, x + s * 0.5f, y + s * 0.5f, s * 0.2f, s);
                break;
            case PARTLY:
                sun(c, x + s * 0.36f, y + s * 0.36f, s * 0.14f, s * 0.7f);
                cloud(c, x + s * 0.06f, y + s * 0.3f, s * 0.9f);
                break;
            case FOG:
                for (int i = 0; i < 4; i++) {
                    float yy = y + s * (0.3f + i * 0.14f);
                    c.drawLine(x + s * (0.15f + (i % 2) * 0.08f), yy, x + s * (0.85f - (i % 2) * 0.08f), yy, p);
                }
                break;
            case RAIN:
                cloud(c, x, y + s * 0.02f, s);
                for (int i = 0; i < 3; i++) {
                    float xx = x + s * (0.32f + i * 0.17f);
                    c.drawLine(xx, y + s * 0.72f, xx - s * 0.06f, y + s * 0.9f, p);
                }
                break;
            case SNOW:
                cloud(c, x, y + s * 0.02f, s);
                for (int i = 0; i < 3; i++) {
                    float xx = x + s * (0.32f + i * 0.17f);
                    c.drawPoint(xx, y + s * 0.78f + (i % 2) * s * 0.08f, p);
                }
                break;
            case STORM:
                cloud(c, x, y + s * 0.02f, s);
                Path bolt = new Path();
                bolt.moveTo(x + s * 0.52f, y + s * 0.64f);
                bolt.lineTo(x + s * 0.42f, y + s * 0.8f);
                bolt.lineTo(x + s * 0.54f, y + s * 0.8f);
                bolt.lineTo(x + s * 0.46f, y + s * 0.96f);
                c.drawPath(bolt, p);
                break;
            default:
                cloud(c, x, y + s * 0.12f, s);
        }
    }

    private void sun(Canvas c, float cx, float cy, float r, float s) {
        c.drawCircle(cx, cy, r, p);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float r1 = r * 1.45f, r2 = r * 1.85f;
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
        }
    }

    private void cloud(Canvas c, float x, float y, float s) {
        Path path = new Path();
        path.moveTo(x + s * 0.25f, y + s * 0.62f);
        path.cubicTo(x + s * 0.08f, y + s * 0.62f, x + s * 0.08f, y + s * 0.38f, x + s * 0.27f, y + s * 0.38f);
        path.cubicTo(x + s * 0.3f, y + s * 0.18f, x + s * 0.6f, y + s * 0.16f, x + s * 0.64f, y + s * 0.36f);
        path.cubicTo(x + s * 0.88f, y + s * 0.34f, x + s * 0.9f, y + s * 0.62f, x + s * 0.72f, y + s * 0.62f);
        path.close();
        c.drawPath(path, p);
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
