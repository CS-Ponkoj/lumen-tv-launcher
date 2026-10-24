package com.home.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * Scenic wallpapers drawn in code, so the app stays tiny and needs no image licences.
 * Each scene is painted once into a bitmap (software canvas, so blur effects work) and reused.
 */
final class ArtWallpaper extends Drawable {
    static final String[] NAMES = {"Aurora", "Sunset hills", "Misty mountains", "Moonlit sea", "Misty forest"};
    /** Transparent layer with only stars, laid over the plain Midnight gradient. */
    static final int STARS_ONLY = 100;

    private final int kind;
    private Bitmap cache;

    ArtWallpaper(int kind) {
        this.kind = kind;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        if (cache == null || cache.getWidth() != b.width() || cache.getHeight() != b.height()) {
            cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
            paint(new Canvas(cache), b.width(), b.height());
        }
        canvas.drawBitmap(cache, b.left, b.top, null);
    }

    private void paint(Canvas c, int w, int h) {
        switch (kind) {
            case 1: sunset(c, w, h); break;
            case 2: mountains(c, w, h); break;
            case 3: sea(c, w, h); break;
            case 4: forest(c, w, h); break;
            case 5: horizon(c, w, h); break;
            case STARS_ONLY: stars(c, w, h, 0.85f, 260, 42); break;
            default: aurora(c, w, h); break;
        }
    }

    // ---------------------------------------------------------------- scenes

    private void aurora(Canvas c, int w, int h) {
        sky(c, w, h, new int[]{0xFF020617, 0xFF06142E, 0xFF0B2A3C}, new float[]{0f, 0.55f, 1f});
        stars(c, w, h, 0.7f, 220, 7);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        int[] colors = {0x8800E5A0, 0x6600C8FF, 0x557C4DFF};
        float[] ys = {0.30f, 0.38f, 0.24f};
        for (int i = 0; i < 3; i++) {
            p.setColor(colors[i]);
            p.setStrokeWidth(h * 0.10f);
            p.setMaskFilter(new BlurMaskFilter(h * 0.07f, BlurMaskFilter.Blur.NORMAL));
            Path path = new Path();
            for (int x = -50; x <= w + 50; x += 20) {
                float y = h * ys[i] + (float) (Math.sin(x / (w * 0.18) + i * 1.7) * h * 0.06
                        + Math.sin(x / (w * 0.05) + i) * h * 0.012);
                if (x == -50) path.moveTo(x, y); else path.lineTo(x, y);
            }
            c.drawPath(path, p);
        }
        ridge(c, w, h, 0.80f, 0.10f, 0xFF071322, 11, 9);
        ridge(c, w, h, 0.88f, 0.07f, 0xFF02070F, 23, 12);
    }

    private void sunset(Canvas c, int w, int h) {
        sky(c, w, h, new int[]{0xFF1A0B2E, 0xFF5B1A4F, 0xFFD9573B, 0xFFF9A83F},
                new float[]{0f, 0.35f, 0.62f, 0.72f});
        glowCircle(c, w * 0.5f, h * 0.60f, h * 0.075f, 0xFFFFD58A, h * 0.35f, 0x66FFB060);
        int[] layers = {0xFF7A2552, 0xFF54173F, 0xFF360D2E, 0xFF1C0619};
        for (int i = 0; i < layers.length; i++) {
            hills(c, w, h, 0.64f + i * 0.08f, 0.05f - i * 0.006f, layers[i], i * 1.3f);
        }
    }

    private void mountains(Canvas c, int w, int h) {
        sky(c, w, h, new int[]{0xFF0B1430, 0xFF2B4C7E, 0xFF7AA6C2, 0xFFF2C9A0},
                new float[]{0f, 0.35f, 0.6f, 0.8f});
        int[] layers = {0xFF6F8FB0, 0xFF4E6D90, 0xFF33506F, 0xFF1C3049, 0xFF0C1828};
        for (int i = 0; i < layers.length; i++) {
            ridge(c, w, h, 0.50f + i * 0.09f, 0.16f - i * 0.02f, layers[i], 31 + i * 7, 7 + i * 2);
            fog(c, w, h, 0.52f + i * 0.09f);
        }
    }

    private void sea(Canvas c, int w, int h) {
        sky(c, w, h, new int[]{0xFF020814, 0xFF0A1F3D, 0xFF173A63}, new float[]{0f, 0.45f, 0.62f});
        stars(c, w, h, 0.55f, 160, 3);
        float mx = w * 0.72f, my = h * 0.24f;
        glowCircle(c, mx, my, h * 0.055f, 0xFFF4F1DE, h * 0.25f, 0x55BFD7FF);
        Paint water = new Paint();
        water.setShader(new LinearGradient(0, h * 0.62f, 0, h, 0xFF0D2747, 0xFF020814, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.62f, w, h, water);
        Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
        streak.setMaskFilter(new BlurMaskFilter(h * 0.02f, BlurMaskFilter.Blur.NORMAL));
        Random r = new Random(5);
        for (int i = 0; i < 26; i++) {
            float y = h * 0.64f + i * h * 0.013f;
            float half = w * (0.015f + 0.004f * i) * (0.6f + r.nextFloat() * 0.6f);
            streak.setColor((Math.max(0x10, 0x90 - i * 4) << 24) | 0xE8EEFF);
            c.drawRect(mx - half, y, mx + half, y + h * 0.004f, streak);
        }
        Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
        wave.setStyle(Paint.Style.STROKE);
        wave.setStrokeWidth(Math.max(1, h * 0.0015f));
        for (int i = 0; i < 9; i++) {
            wave.setColor(0x1FFFFFFF);
            Path p = new Path();
            float y0 = h * (0.66f + i * 0.037f);
            for (int x = 0; x <= w; x += 12) {
                float y = y0 + (float) Math.sin(x / (w * 0.03) + i) * h * 0.003f;
                if (x == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            c.drawPath(p, wave);
        }
    }

    /** Foggy pine forest: rows of firs fading into mist, soft grey-green palette. */
    private void forest(Canvas c, int w, int h) {
        sky(c, w, h, new int[]{0xFFB4C2C9, 0xFFC7D1D5, 0xFFB9C5C6}, new float[]{0f, 0.45f, 1f});
        int[] colors = {0xFFA3B1B3, 0xFF8C9C9B, 0xFF738683, 0xFF5B6F6A, 0xFF435752, 0xFF2E3F3A};
        Random r = new Random(17);
        for (int layer = 0; layer < colors.length; layer++) {
            float baseY = h * (0.42f + layer * 0.12f);
            float treeH = h * (0.16f + layer * 0.06f);
            int count = 26 - layer * 3;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(colors[layer]);
            for (int i = 0; i < count; i++) {
                float x = (i + r.nextFloat() * 0.8f) * w / (count - 1f) - w * 0.03f;
                float th = treeH * (0.7f + r.nextFloat() * 0.6f);
                pine(c, x, baseY + r.nextFloat() * h * 0.04f, th, th * 0.32f, p);
            }
            Paint ground = new Paint();
            ground.setColor(colors[layer]);
            c.drawRect(0, baseY + h * 0.03f, w, h, ground);
            // mist drifting in front of each row
            Paint fog = new Paint();
            fog.setShader(new LinearGradient(0, baseY - treeH * 0.6f, 0, baseY + h * 0.06f,
                    new int[]{0x00D0D9DC, 0x88D0D9DC, 0x00D0D9DC}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, baseY - treeH * 0.6f, w, baseY + h * 0.06f, fog);
        }
    }

    /** Above the clouds at dusk: dark sky, glowing blue-orange horizon, a lone peak, a sea of cloud. */
    private void horizon(Canvas c, int w, int h) {
        float hy = h * 0.53f;
        sky(c, w, h, new int[]{0xFF15191F, 0xFF1D2733, 0xFF2B4A6B, 0xFF3D8FD0, 0xFF9AD0F0},
                new float[]{0f, 0.25f, 0.40f, 0.49f, 0.53f});
        // blue glow above the horizon
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new LinearGradient(0, hy - h * 0.16f, 0, hy, 0x00329BE6, 0xAA4FB3F0, Shader.TileMode.CLAMP));
        c.drawRect(0, hy - h * 0.16f, w, hy, glow);
        // orange line of the setting sun, brightest in the middle
        Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        band.setShader(new LinearGradient(0, hy - h * 0.012f, 0, hy + h * 0.02f,
                new int[]{0x00FF9A3C, 0xFFFFA040, 0xFFE0601E, 0x00802010}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, hy - h * 0.012f, w, hy + h * 0.02f, band);
        // fade the glow towards the left and right edges (soft, no hard top edge)
        Paint side = new Paint();
        side.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xB015191F, 0x0015191F, 0x0015191F, 0xB015191F},
                new float[]{0f, 0.22f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        Paint fadeMask = new Paint();
        fadeMask.setShader(new LinearGradient(0, hy - h * 0.25f, 0, hy, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        int save = c.saveLayer(0, hy - h * 0.25f, w, hy + h * 0.03f, null);
        c.drawRect(0, hy - h * 0.25f, w, hy + h * 0.03f, side);
        fadeMask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        c.drawRect(0, hy - h * 0.25f, w, hy, fadeMask);
        c.restoreToCount(save);
        // the peak
        Paint peak = new Paint(Paint.ANTI_ALIAS_FLAG);
        peak.setColor(0xFF2A2C30);
        Path m = new Path();
        m.moveTo(w * 0.43f, hy + h * 0.004f);
        m.cubicTo(w * 0.465f, hy - h * 0.004f, w * 0.48f, hy - h * 0.026f, w * 0.50f, hy - h * 0.028f);
        m.cubicTo(w * 0.52f, hy - h * 0.027f, w * 0.528f, hy - h * 0.013f, w * 0.54f, hy - h * 0.015f);
        m.cubicTo(w * 0.553f, hy - h * 0.016f, w * 0.56f, hy - h * 0.003f, w * 0.585f, hy + h * 0.004f);
        m.close();
        c.drawPath(m, peak);
        // sea of clouds below
        Paint clouds = new Paint();
        clouds.setShader(new LinearGradient(0, hy, 0, h, new int[]{0xFF3A3D42, 0xFF2C2F34, 0xFF24272B},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, hy + h * 0.006f, w, h, clouds);
        Paint wisp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wisp.setMaskFilter(new BlurMaskFilter(h * 0.02f, BlurMaskFilter.Blur.NORMAL));
        Random r = new Random(9);
        for (int i = 0; i < 40; i++) {
            wisp.setColor(((10 + r.nextInt(18)) << 24) | 0xC8CCD2);
            float cy = hy + h * (0.03f + r.nextFloat() * 0.4f);
            float cx = r.nextFloat() * w, rw = w * (0.08f + r.nextFloat() * 0.2f);
            c.drawOval(cx - rw, cy - h * 0.012f, cx + rw, cy + h * 0.012f, wisp);
        }
    }

    private static void pine(Canvas c, float x, float baseY, float height, float width, Paint p) {
        Path path = new Path();
        int tiers = 5;
        for (int t = 0; t < tiers; t++) {
            float top = baseY - height + t * height / (tiers + 1.5f);
            float bottom = top + height / 2.6f;
            float half = width * (0.35f + 0.65f * (t + 1) / tiers) / 2;
            path.moveTo(x, top);
            path.lineTo(x - half, bottom);
            path.lineTo(x + half, bottom);
            path.close();
        }
        c.drawPath(path, p);
        c.drawRect(x - width * 0.04f, baseY - height * 0.12f, x + width * 0.04f, baseY + height * 0.05f, p);
    }

    // ---------------------------------------------------------------- helpers

    private static void sky(Canvas c, int w, int h, int[] colors, float[] stops) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, colors, stops, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void stars(Canvas c, int w, int h, float maxY, int count, long seed) {
        Random r = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = h / 1080f;
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * maxY;
            int a = 60 + r.nextInt(170);
            p.setColor((a << 24) | 0xFFFFFF);
            c.drawCircle(x, y, (0.6f + r.nextFloat() * 1.4f) * scale, p);
        }
    }

    private static void glowCircle(Canvas c, float x, float y, float r, int color, float glowR, int glowColor) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(x, y, glowR, glowColor, glowColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, glowR, g);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(x, y, r, p);
    }

    /** Jagged mountain ridge from a seeded random walk. */
    private static void ridge(Canvas c, int w, int h, float baseY, float amp, int color, long seed, int peaks) {
        Random r = new Random(seed);
        Path p = new Path();
        p.moveTo(0, h);
        float step = w / (float) (peaks * 4);
        float y = h * baseY;
        p.lineTo(0, y);
        for (float x = 0; x <= w + step; x += step) {
            y = h * baseY - (float) (Math.abs(Math.sin(x / w * Math.PI * peaks / 2 + seed)) * amp * h)
                    + (r.nextFloat() - 0.5f) * amp * h * 0.35f;
            p.lineTo(x, y);
        }
        p.lineTo(w, h);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        c.drawPath(p, paint);
    }

    private static void hills(Canvas c, int w, int h, float baseY, float amp, int color, float phase) {
        Path p = new Path();
        p.moveTo(0, h);
        for (int x = 0; x <= w; x += 10) {
            float y = h * baseY - (float) (Math.sin(x / (w * 0.22) + phase) * amp * h
                    + Math.sin(x / (w * 0.09) + phase * 2) * amp * h * 0.35);
            p.lineTo(x, y);
        }
        p.lineTo(w, h);
        p.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        c.drawPath(p, paint);
    }

    private static void fog(Canvas c, int w, int h, float y) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, h * (y - 0.04f), 0, h * (y + 0.04f),
                new int[]{0x00FFFFFF, 0x22FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, h * (y - 0.04f), w, h * (y + 0.04f), p);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return kind == STARS_ONLY ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE; }
}
