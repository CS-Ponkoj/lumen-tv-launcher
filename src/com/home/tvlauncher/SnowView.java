package com.home.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Random;

/** Gently falling snow drawn over the wallpaper. Light enough for TV hardware. */
final class SnowView extends View {
    private static final int FLAKES = 150;

    private final float[] x = new float[FLAKES], y = new float[FLAKES], r = new float[FLAKES],
            speed = new float[FLAKES], drift = new float[FLAKES], phase = new float[FLAKES];
    private final int[] alpha = new int[FLAKES];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;
    private long last;
    private boolean seeded;

    SnowView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        paint.setColor(0xFFFFFFFF);
        setFocusable(false);
        setClickable(false);
    }

    private void seed(int w, int h, int i, boolean anywhere) {
        x[i] = random.nextFloat() * w;
        y[i] = anywhere ? random.nextFloat() * h : -10 * density;
        float depth = random.nextFloat();              // 0 = far, 1 = near
        r[i] = (0.6f + depth * 2.2f) * density;
        speed[i] = (18 + depth * 55) * density;        // px per second
        drift[i] = (6 + random.nextFloat() * 14) * density;
        phase[i] = random.nextFloat() * 6.28f;
        alpha[i] = 90 + (int) (depth * 150);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!seeded) {
            for (int i = 0; i < FLAKES; i++) seed(w, h, i, true);
            seeded = true;
            last = System.nanoTime();
        }
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        for (int i = 0; i < FLAKES; i++) {
            phase[i] += dt * 0.8f;
            y[i] += speed[i] * dt;
            float px = x[i] + (float) Math.sin(phase[i]) * drift[i];
            if (y[i] > h + 10 * density) seed(w, h, i, false);
            paint.setAlpha(alpha[i]);
            canvas.drawCircle(px, y[i], r[i], paint);
        }
        postInvalidateOnAnimation();
    }
}
