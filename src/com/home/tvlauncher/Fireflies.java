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

    // Forest photo used to hide far fireflies behind trees (dark pixels = trunk/branches in front).
    private Bitmap forest;
    private int screenW, screenH;

    private float avgLum = 70;

    void setForest(Bitmap bg) {
        forest = bg;
        if (bg == null) return;
        long sum = 0; int n = 0;   // average brightness of the tree band, so it works at night too
        for (int y = bg.getHeight() * 2 / 5; y < bg.getHeight() * 4 / 5; y += 8)
            for (int x = 0; x < bg.getWidth(); x += 8) {
                int c = bg.getPixel(x, y);
                sum += (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; n++;
            }
        avgLum = n > 0 ? sum / (float) n : 70;
    }

    /** 0..1 how much a firefly at depth `dp` is hidden by the tree at (x, y). */
    private float occlusion(float x, float y, float dp) {
        if (forest == null || dp > 0.6f || screenW == 0) return 0;
        float scale = Math.max(screenW / (float) forest.getWidth(), screenH / (float) forest.getHeight());
        float offX = (screenW - forest.getWidth() * scale) / 2f, offY = (screenH - forest.getHeight() * scale) / 2f;
        int bx = (int) ((x - offX) / scale), by = (int) ((y - offY) / scale);
        if (bx < 0 || by < 0 || bx >= forest.getWidth() || by >= forest.getHeight()) return 0;
        int c = forest.getPixel(bx, by);
        int lum = (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
        // darker than the misty gaps = a tree in front; the farther the firefly, the more it hides
        float tree = Math.max(0, Math.min(1, (avgLum * 0.85f - lum) / (avgLum * 0.35f)));
        return tree * (1 - dp / 0.6f);
    }

    private static final float FLASH = 2.0f;     // seconds lit: a slow, soft glow
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
            my[i] = h * (0.36f + rnd.nextFloat() * 0.5f);       // among the trees
            dir[i] = rnd.nextFloat() * 6.28f;
            period[i] = 6f + rnd.nextFloat() * 4f;               // 6-10 s rhythm
            cycle[i] = rnd.nextFloat() * period[i];
        }
        for (int i = 0; i < FEMALES; i++) {
            fx[i] = rnd.nextFloat() * w;
            fy[i] = h * (0.6f + rnd.nextFloat() * 0.3f);       // resting on branches and grass
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

    private void light(Canvas c, float x, float y, float coreR, float b, float dp) {
        b *= 1 - occlusion(x, y, dp);
        b *= 0.45f + 0.55f * dp;                  // far ones are dimmer through the mist
        if (b <= 0.01f) return;
        // very faint light spilling onto the mist around it
        float wide = coreR * 16f;
        haloPaint.setAlpha((int) (40 * b));
        dst.set(x - wide, y - wide, x + wide, y + wide);
        c.drawBitmap(halo, null, dst, haloPaint);
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
        float s = (float) Math.sin(t * Math.PI);   // gentle swell on, gentle fade off
        return s * s * (3 - 2 * s);
    }

    void draw(Canvas c, int w, int h, float dt) {
        screenW = w; screenH = h;
        if (!seeded) seed(w, h);

        for (int i = 0; i < STARS; i++) {
            sp[i] += dt * 0.5f;
            starPaint.setAlpha((int) (45 + 30 * Math.sin(sp[i])));
            c.drawCircle(sx[i], sy[i], sr[i], starPaint);
        }

        for (int i = 0; i < MALES; i++) {
            // slow, lazy wandering in any direction (a few pixels a second)
            dir[i] += (rnd.nextFloat() - 0.5f) * dt * 1.6f;          // heading, radians
            float speed = (2.5f + depth[i] * 4f) * d;
            mx[i] += (float) Math.cos(dir[i]) * speed * dt;
            my[i] += (float) Math.sin(dir[i]) * speed * 0.6f * dt;

            cycle[i] += dt;
            float ft = (cycle[i] - (period[i] - FLASH)) / FLASH;
            if (ft >= 0 && ft <= 1) {
                my[i] -= 4 * d * dt;                                    // the faintest lift while lit
                float b = flashCurve(ft) * (0.55f + 0.45f * depth[i]);
                light(c, mx[i], my[i], (0.7f + depth[i] * 1.3f) * d, b, depth[i]);
                if (!answered[i] && ft > 0.3f) {
                    answered[i] = true;
                    for (int f = 0; f < FEMALES; f++) {
                        if (fReplyIn[f] < 0 && Math.abs(fx[f] - mx[i]) < w * 0.12f && rnd.nextFloat() < 0.6f) {
                            fReplyIn[f] = 1f + rnd.nextFloat();
                            break;
                        }
                    }
                }
            }
            if (cycle[i] >= period[i]) {
                cycle[i] = 0;
                answered[i] = false;
                period[i] = 6f + rnd.nextFloat() * 4f;
            }
            // stay in the lower forest band, wrap at the sides
            if (my[i] < h * 0.34f) dir[i] = (float) (Math.PI / 2);
            if (my[i] > h * 0.9f) dir[i] = (float) (-Math.PI / 2);
            if (mx[i] < -30 * d) mx[i] = w + 20 * d;
            if (mx[i] > w + 30 * d) mx[i] = -20 * d;
        }

        for (int f = 0; f < FEMALES; f++) {
            if (fReplyIn[f] >= 0) {
                fReplyIn[f] -= dt;
                if (fReplyIn[f] < 0) fGlow[f] = 0.001f;
            }
            if (fGlow[f] > 0) {
                fGlow[f] += dt;
                float t = fGlow[f] / 1.2f;
                light(c, fx[f], fy[f], 0.9f * d, flashCurve(t) * 0.75f, 0.7f);
                if (t >= 1) { fGlow[f] = 0; fReplyIn[f] = -1; }
            }
        }
    }
}
