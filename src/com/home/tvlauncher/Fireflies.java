package com.home.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Fireflies for clear nights: depth layers, smooth wandering flight, each with its own
 * blink rhythm (dim glow, then a bright flash), and now and then a wave that flashes together.
 * Glows are pre-rendered sprites, so it stays light on TV hardware.
 */
final class Fireflies {
    private static final int COUNT = 34, STARS = 28;

    private final Random rnd = new Random();
    private final float density;
    private final Bitmap glow;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    // fireflies
    private final float[] x = new float[COUNT], y = new float[COUNT], vx = new float[COUNT], vy = new float[COUNT];
    private final float[] depth = new float[COUNT], wander = new float[COUNT], wanderSpeed = new float[COUNT];
    private final float[] period = new float[COUNT], clock = new float[COUNT], flashBoost = new float[COUNT];
    // stars
    private final float[] sx = new float[STARS], sy = new float[STARS], sr = new float[STARS], sp = new float[STARS];

    private float waveIn = 12f;      // seconds until the next synchronized wave
    private float waveX = -1;        // the wave sweeps across the screen from left to right
    private boolean seeded;

    Fireflies(float density) {
        this.density = density;
        int s = 64;
        glow = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(glow);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // soft yellow-green halo with a hot, almost white core
        p.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f,
                new int[]{0xFFFFFDE0, 0xFFE9FF8A, 0x88B8F050, 0x2296D23C, 0x0096D23C},
                new float[]{0f, 0.12f, 0.32f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        starPaint.setColor(0xFFFFFFFF);
    }

    private void seed(int w, int h, int i) {
        depth[i] = rnd.nextFloat();                         // 0 far .. 1 near
        x[i] = rnd.nextFloat() * w;
        y[i] = h * (0.38f + rnd.nextFloat() * 0.58f);       // among the trees
        wander[i] = rnd.nextFloat() * 6.28f;
        wanderSpeed[i] = 0.25f + rnd.nextFloat() * 0.5f;
        float sp = (10 + depth[i] * 22) * density;
        vx[i] = (float) Math.cos(wander[i]) * sp;
        vy[i] = (float) Math.sin(wander[i]) * sp;
        period[i] = 2.4f + rnd.nextFloat() * 3.6f;          // own blink rhythm
        clock[i] = rnd.nextFloat() * period[i];
    }

    void draw(Canvas c, int w, int h, float dt) {
        if (!seeded) {
            for (int i = 0; i < COUNT; i++) seed(w, h, i);
            for (int i = 0; i < STARS; i++) {
                sx[i] = rnd.nextFloat() * w;
                sy[i] = rnd.nextFloat() * rnd.nextFloat() * h * 0.28f;
                sr[i] = (0.3f + rnd.nextFloat() * 0.5f) * density;
                sp[i] = rnd.nextFloat() * 6.28f;
            }
            seeded = true;
        }

        // faint, slowly twinkling stars high in the sky
        for (int i = 0; i < STARS; i++) {
            sp[i] += dt * 0.7f;
            starPaint.setAlpha((int) (60 + 50 * Math.sin(sp[i])));
            c.drawCircle(sx[i], sy[i], sr[i], starPaint);
        }

        // synchronized wave: every 20-40 s a flash ripples across the forest
        waveIn -= dt;
        if (waveIn <= 0 && waveX < 0) { waveX = -w * 0.1f; waveIn = 20 + rnd.nextFloat() * 20; }
        if (waveX >= 0 || waveX > -w * 0.11f) {
            waveX += dt * w * 0.35f;
            if (waveX > w * 1.1f) waveX = -1;
        }

        float top = h * 0.32f, bottom = h * 1.02f;
        for (int i = 0; i < COUNT; i++) {
            // smooth wandering: the heading turns slowly, never in straight lines
            wander[i] += (float) Math.sin(clock[i] * wanderSpeed[i] * 2.1f + i) * dt * 1.4f;
            float sp = (10 + depth[i] * 22) * density;
            float tx = (float) Math.cos(wander[i]) * sp, ty = (float) Math.sin(wander[i]) * sp * 0.6f;
            vx[i] += (tx - vx[i]) * Math.min(1f, dt * 1.5f);
            vy[i] += (ty - vy[i]) * Math.min(1f, dt * 1.5f);
            // gentle pull back into the forest band
            if (y[i] < top) vy[i] += dt * 30 * density;
            if (y[i] > bottom) vy[i] -= dt * 30 * density;
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt + (float) Math.sin(clock[i] * 3 + i) * dt * 4 * density; // tiny bob
            if (x[i] < -40 * density) x[i] = w + 30 * density;
            if (x[i] > w + 40 * density) x[i] = -30 * density;

            // blink: a quiet glow most of the time, then a quick bright flash
            clock[i] += dt;
            float t = (clock[i] % period[i]) / period[i];
            float flash = t > 0.82f ? (float) Math.sin((t - 0.82f) / 0.18f * Math.PI) : 0f;
            if (waveX > 0 && Math.abs(x[i] - waveX) < w * 0.06f) flashBoost[i] = 1f;
            flashBoost[i] = Math.max(0, flashBoost[i] - dt * 1.6f);
            float bright = Math.max(flash, flashBoost[i]);
            float a = 0.18f + 0.82f * bright;                     // never fully dark
            float size = (5 + depth[i] * 13) * density * (0.75f + 0.5f * bright);
            paint.setAlpha((int) (255 * a * (0.55f + 0.45f * depth[i])));
            dst.set(x[i] - size, y[i] - size, x[i] + size, y[i] + size);
            c.drawBitmap(glow, null, dst, paint);
        }
    }
}
