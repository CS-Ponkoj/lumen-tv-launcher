package com.home.tvlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Fireflies modelled on the common "big dipper" firefly (Photinus pyralis):
 * - males drift slowly and low, then dip and swoop sharply upward in a J, lighting up
 *   on the upswing for about 0.6-0.8 s, ending at the top of the climb; every 5-7 s;
 * - females sit still on the vegetation near the ground and answer a nearby male's flash
 *   1-2 s later with a short flash of their own.
 */
final class Fireflies {
    private static final int MALES = 46, FEMALES = 16, STARS = 22;

    private final Random rnd = new Random();
    private final float d;                       // density
    private final Bitmap halo;
    private final Paint haloPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    // males
    private final float[] mx = new float[MALES], my = new float[MALES], dir = new float[MALES], depth = new float[MALES];
    private final float[] cycle = new float[MALES], period = new float[MALES], swoopX = new float[MALES], swoopY = new float[MALES];
    private final boolean[] answered = new boolean[MALES];
    // females
    private final float[] fx = new float[FEMALES], fy = new float[FEMALES], fReplyIn = new float[FEMALES], fGlow = new float[FEMALES];
    // stars
    private final float[] sx = new float[STARS], sy = new float[STARS], sr = new float[STARS], sp = new float[STARS];
    private boolean seeded;

    private static final float FLASH = 0.7f;     // seconds lit
    private static final float SWOOP = 1.2f;     // dip + upswing, flash on the last part

    Fireflies(float density) {
        d = density;
        int s = 48;
        halo = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f,
                new int[]{0xAAE6F56A, 0x55C8EA48, 0x00B0DC3C}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(halo).drawCircle(s / 2f, s / 2f, s / 2f, p);
        core.setColor(0xFFF6FFC2);
        starPaint.setColor(0xFFFFFFFF);
    }

    private void seed(int w, int h) {
        for (int i = 0; i < MALES; i++) {
            depth[i] = rnd.nextFloat();
            mx[i] = rnd.nextFloat() * w;
            my[i] = h * (0.55f + rnd.nextFloat() * 0.4f);       // a few feet above the ground
            dir[i] = rnd.nextBoolean() ? 1 : -1;
            period[i] = 5f + rnd.nextFloat() * 2f;               // 5-7 s rhythm
            cycle[i] = rnd.nextFloat() * period[i];
        }
        for (int i = 0; i < FEMALES; i++) {
            fx[i] = rnd.nextFloat() * w;
            fy[i] = h * (0.82f + rnd.nextFloat() * 0.16f);      // resting in the grass
            fReplyIn[i] = -1;
        }
        for (int i = 0; i < STARS; i++) {
            sx[i] = rnd.nextFloat() * w;
            sy[i] = rnd.nextFloat() * rnd.nextFloat() * h * 0.25f;
            sr[i] = (0.3f + rnd.nextFloat() * 0.4f) * d;
            sp[i] = rnd.nextFloat() * 6.28f;
        }
        seeded = true;
    }

    private void light(Canvas c, float x, float y, float coreR, float b) {
        if (b <= 0.01f) return;
        float hr = coreR * 6f;
        haloPaint.setAlpha((int) (255 * b));
        dst.set(x - hr, y - hr, x + hr, y + hr);
        c.drawBitmap(halo, null, dst, haloPaint);
        core.setAlpha((int) (255 * b));
        c.drawCircle(x, y, coreR, core);
    }

    /** Fast rise, short hold, slightly slower fade - like a real flash. */
    private static float flashCurve(float t) {
        if (t < 0 || t > 1) return 0;
        if (t < 0.15f) return t / 0.15f;
        if (t < 0.6f) return 1f;
        float f = 1 - (t - 0.6f) / 0.4f;
        return f * f;
    }

    void draw(Canvas c, int w, int h, float dt) {
        if (!seeded) seed(w, h);

        for (int i = 0; i < STARS; i++) {
            sp[i] += dt * 0.5f;
            starPaint.setAlpha((int) (45 + 30 * Math.sin(sp[i])));
            c.drawCircle(sx[i], sy[i], sr[i], starPaint);
        }

        for (int i = 0; i < MALES; i++) {
            float k = 0.5f + depth[i];                           // near ones move a bit faster on screen
            cycle[i] += dt;
            float swoopStart = period[i] - SWOOP;
            if (cycle[i] < swoopStart) {
                // slow forward hover, slight vertical bob
                mx[i] += dir[i] * 6 * d * k * dt;
                my[i] += (float) Math.sin(cycle[i] * 1.7f + i) * 3 * d * dt;
                swoopX[i] = mx[i];
                swoopY[i] = my[i];
                answered[i] = false;
            } else {
                // the J: a short dip, then a sharp upward arc; light on the upswing
                float t = (cycle[i] - swoopStart) / SWOOP;       // 0..1
                float dip = 10 * d * k, rise = 46 * d * k, ahead = 26 * d * k;
                float py = t < 0.3f ? (float) Math.sin(t / 0.3f * Math.PI / 2) * dip
                        : dip - (float) Math.pow((t - 0.3f) / 0.7f, 1.3) * (dip + rise);
                mx[i] = swoopX[i] + dir[i] * ahead * t;
                my[i] = swoopY[i] + py;
                float ft = (cycle[i] - (period[i] - FLASH)) / FLASH;
                float b = flashCurve(ft) * (0.55f + 0.45f * depth[i]);
                light(c, mx[i], my[i], (0.8f + depth[i] * 1.2f) * d, b);

                // a nearby female notices and will answer
                if (!answered[i] && ft > 0.3f) {
                    answered[i] = true;
                    for (int f = 0; f < FEMALES; f++) {
                        if (fReplyIn[f] < 0 && Math.abs(fx[f] - mx[i]) < w * 0.12f && rnd.nextFloat() < 0.6f) {
                            fReplyIn[f] = 1f + rnd.nextFloat();  // 1-2 s delay
                            break;
                        }
                    }
                }
                if (cycle[i] >= period[i]) {
                    cycle[i] = 0;
                    my[i] = swoopY[i] - 6 * d;                   // settles a little higher, drifts back down
                    period[i] = 5f + rnd.nextFloat() * 2f;
                    if (rnd.nextFloat() < 0.25f) dir[i] = -dir[i];
                }
            }
            if (my[i] < h * 0.45f) my[i] += 20 * d * dt;
            if (my[i] > h * 0.97f) my[i] -= 20 * d * dt;
            if (mx[i] < -30 * d) { mx[i] = w + 20 * d; swoopX[i] = mx[i]; }
            if (mx[i] > w + 30 * d) { mx[i] = -20 * d; swoopX[i] = mx[i]; }
        }

        for (int f = 0; f < FEMALES; f++) {
            if (fReplyIn[f] >= 0) {
                fReplyIn[f] -= dt;
                if (fReplyIn[f] < 0) fGlow[f] = 0.001f;
            }
            if (fGlow[f] > 0) {
                fGlow[f] += dt;
                float t = fGlow[f] / 0.55f;
                light(c, fx[f], fy[f], 0.9f * d, flashCurve(t) * 0.75f);
                if (t >= 1) { fGlow[f] = 0; fReplyIn[f] = -1; }
            }
        }
    }
}
