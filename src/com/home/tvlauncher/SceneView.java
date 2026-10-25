package com.home.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/** Animated weather drawn over the wallpaper: snow, rain, storm, fog, drifting clouds or stars. */
final class SceneView extends View {
    static final int NONE = 0, SNOW = 1, RAIN = 2, STORM = 3, FOG = 4, CLOUDS = 5, STARS = 6;

    private final int mode;
    private final float density;
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long last;
    private boolean seeded;

    private float[] x, y, r, speed, drift, phase;
    private int[] alpha;
    private int count;
    private float flash;          // storm lightning brightness, 0..1
    private float nextFlashIn = 4f;

    SceneView(Context c, int mode) {
        super(c);
        this.mode = mode;
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        count = mode == SNOW ? 150 : mode == RAIN ? 220 : mode == STORM ? 300 : mode == FOG ? 9
                : mode == CLOUDS ? 6 : mode == STARS ? 180 : 0;
        x = new float[count]; y = new float[count]; r = new float[count];
        speed = new float[count]; drift = new float[count]; phase = new float[count];
        alpha = new int[count];
    }

    private void seed(int w, int h, int i, boolean anywhere) {
        float depth = random.nextFloat();
        x[i] = random.nextFloat() * w;
        phase[i] = random.nextFloat() * 6.28f;
        switch (mode) {
            case SNOW:
                y[i] = anywhere ? random.nextFloat() * h : -10 * density;
                r[i] = (0.6f + depth * 2.2f) * density;
                speed[i] = (18 + depth * 55) * density;
                drift[i] = (6 + random.nextFloat() * 14) * density;
                alpha[i] = 90 + (int) (depth * 150);
                break;
            case RAIN:
            case STORM:
                y[i] = anywhere ? random.nextFloat() * h : -30 * density;
                r[i] = (10 + depth * 18) * density;            // streak length
                speed[i] = (520 + depth * 520) * density;
                alpha[i] = 50 + (int) (depth * 110);
                break;
            case FOG:
            case CLOUDS:
                y[i] = mode == FOG ? h * (0.35f + random.nextFloat() * 0.6f) : h * (0.02f + random.nextFloat() * 0.35f);
                r[i] = (mode == FOG ? 0.25f + random.nextFloat() * 0.25f : 0.18f + random.nextFloat() * 0.2f) * w;
                speed[i] = (6 + random.nextFloat() * 10) * density * (random.nextBoolean() ? 1 : -1);
                alpha[i] = mode == FOG ? 34 + random.nextInt(26) : 26 + random.nextInt(24);
                break;
            case STARS:
                y[i] = random.nextFloat() * h * 0.5f;
                r[i] = (0.6f + depth * 1.4f) * density;
                speed[i] = 0.6f + random.nextFloat() * 1.6f;   // twinkle rate
                alpha[i] = 120 + (int) (depth * 135);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || count == 0) return;
        if (!seeded) {
            for (int i = 0; i < count; i++) seed(w, h, i, true);
            seeded = true;
            last = System.nanoTime();
        }
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;

        switch (mode) {
            case SNOW:
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(null);
                for (int i = 0; i < count; i++) {
                    phase[i] += dt * 0.8f;
                    y[i] += speed[i] * dt;
                    if (y[i] > h + 10 * density) seed(w, h, i, false);
                    paint.setColor((alpha[i] << 24) | 0xFFFFFF);
                    canvas.drawCircle(x[i] + (float) Math.sin(phase[i]) * drift[i], y[i], r[i], paint);
                }
                break;
            case RAIN:
            case STORM:
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, density * 1.1f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                float slant = 0.18f;
                for (int i = 0; i < count; i++) {
                    y[i] += speed[i] * dt;
                    x[i] += speed[i] * dt * slant;
                    if (y[i] > h + 20 * density || x[i] > w + 40 * density) { seed(w, h, i, false); x[i] -= h * slant * 0.5f; }
                    paint.setColor((alpha[i] << 24) | 0xDDE6F2);
                    canvas.drawLine(x[i], y[i], x[i] - r[i] * slant, y[i] - r[i], paint);
                }
                if (mode == STORM) {
                    nextFlashIn -= dt;
                    if (nextFlashIn <= 0) { flash = 1f; nextFlashIn = 5 + random.nextFloat() * 9; }
                    if (flash > 0) {
                        int a = (int) (flash * flash * 150);
                        canvas.drawColor((a << 24) | 0xE8EEFF);
                        flash = Math.max(0, flash - dt * (flash > 0.6f ? 2.5f : 1.4f));
                    }
                }
                break;
            case FOG:
            case CLOUDS:
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < count; i++) {
                    x[i] += speed[i] * dt;
                    if (x[i] - r[i] > w) x[i] = -r[i];
                    if (x[i] + r[i] < 0) x[i] = w + r[i];
                    int col = mode == FOG ? 0xE6E8EE : 0xF0F2F6;
                    paint.setShader(new RadialGradient(x[i], y[i], r[i],
                            (alpha[i] << 24) | col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
                    canvas.save();
                    canvas.scale(1f, 0.38f, x[i], y[i]);
                    canvas.drawCircle(x[i], y[i], r[i], paint);
                    canvas.restore();
                }
                paint.setShader(null);
                break;
            case STARS:
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < count; i++) {
                    phase[i] += dt * speed[i];
                    int a = (int) (alpha[i] * (0.55f + 0.45f * Math.sin(phase[i])));
                    paint.setColor((Math.max(0, a) << 24) | 0xFFFFFF);
                    canvas.drawCircle(x[i], y[i], r[i], paint);
                }
                break;
        }
        postInvalidateOnAnimation();
    }
}
