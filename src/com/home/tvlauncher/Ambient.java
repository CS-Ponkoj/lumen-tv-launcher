package com.home.tvlauncher;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/** Daytime and seasonal life: sunbeams, floating motes, birds, falling leaves and blossom petals. */
final class Ambient {
    static final int SUNBEAMS = 0, MOTES = 1, BIRDS = 2, LEAVES = 3, PETALS = 4;

    private final int kind;
    private final float d;
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private boolean seeded;
    private Shader beamShader;

    // generic particles
    private int n;
    private float[] x, y, vx, vy, size, phase, spin, angle;
    private int[] color;
    // birds
    private float flockIn = 6f, flockX, flockY, flockSpeed;
    private int flockSize;

    Ambient(int kind, float density) {
        this.kind = kind;
        d = density;
        n = kind == SUNBEAMS ? 5 : kind == MOTES ? 70 : kind == LEAVES ? 24 : kind == PETALS ? 34 : 6;
        x = new float[n]; y = new float[n]; vx = new float[n]; vy = new float[n];
        size = new float[n]; phase = new float[n]; spin = new float[n]; angle = new float[n];
        color = new int[n];
    }

    private void seed(int w, int h, int i, boolean anywhere) {
        phase[i] = rnd.nextFloat() * 6.28f;
        switch (kind) {
            case SUNBEAMS:
                x[i] = w * (0.35f + i * 0.16f + rnd.nextFloat() * 0.05f);   // beam top position
                size[i] = w * (0.05f + rnd.nextFloat() * 0.06f);           // beam width
                spin[i] = 0.08f + rnd.nextFloat() * 0.12f;                 // breathing speed
                break;
            case MOTES:
                x[i] = rnd.nextFloat() * w;
                y[i] = h * (0.15f + rnd.nextFloat() * 0.8f);
                vx[i] = (rnd.nextFloat() - 0.5f) * 6 * d;
                vy[i] = (rnd.nextFloat() - 0.6f) * 4 * d;
                size[i] = (0.5f + rnd.nextFloat() * 1.1f) * d;
                break;
            case LEAVES:
            case PETALS: {
                boolean leaf = kind == LEAVES;
                x[i] = rnd.nextFloat() * w * 1.2f - w * 0.1f;
                y[i] = anywhere ? rnd.nextFloat() * h : -20 * d;
                float depth = rnd.nextFloat();
                size[i] = (leaf ? 5 + depth * 7 : 3 + depth * 4) * d;
                vy[i] = (leaf ? 22 + depth * 30 : 14 + depth * 18) * d;
                vx[i] = (leaf ? 8 : 12) * d * (0.5f + rnd.nextFloat());      // drift with the breeze
                spin[i] = (rnd.nextFloat() - 0.5f) * (leaf ? 3f : 2f);
                angle[i] = rnd.nextFloat() * 6.28f;
                int[] leafCols = {0xFFD9772B, 0xFFC4462A, 0xFFE8B23A, 0xFF9E3B22, 0xFFDA9440};
                int[] petalCols = {0xFFF7C6D6, 0xFFF2B0C6, 0xFFFBE0E8, 0xFFF5D1DD};
                int base = leaf ? leafCols[rnd.nextInt(leafCols.length)] : petalCols[rnd.nextInt(petalCols.length)];
                color[i] = ((int) (150 + depth * 100) << 24) | (base & 0xFFFFFF);
                break;
            }
        }
    }

    void draw(Canvas c, int w, int h, float dt) {
        if (!seeded) {
            for (int i = 0; i < n; i++) seed(w, h, i, true);
            seeded = true;
        }
        switch (kind) {
            case SUNBEAMS: drawBeams(c, w, h, dt); break;
            case MOTES: drawMotes(c, w, h, dt); break;
            case BIRDS: drawBirds(c, w, h, dt); break;
            default: drawFalling(c, w, h, dt);
        }
    }

    private android.graphics.Bitmap beams;
    private final Paint beamPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF full = new RectF();

    private void drawBeams(Canvas c, int w, int h, float dt) {
        if (beams == null) {       // pre-render the rays once, small; scaled up they stay soft
            int bw = Math.max(1, w / 4), bh = Math.max(1, h / 4);
            beams = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(beams);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, bh * 0.95f,
                    new int[]{0x5CFFF2D2, 0x2CFFEBC0, 0x00FFE8B8}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            p.setMaskFilter(new android.graphics.BlurMaskFilter(bw * 0.012f, android.graphics.BlurMaskFilter.Blur.NORMAL)); // hazy edges
            for (int i = 0; i < n; i++) {
                float topX = x[i] / 4, bw2 = size[i] / 4, slant = bw * 0.42f, botY = bh * 0.95f;
                Path pth = new Path();
                pth.moveTo(topX, 0);
                pth.lineTo(topX + bw2, 0);
                pth.lineTo(topX + bw2 * 2.4f - slant, botY);
                pth.lineTo(topX - slant, botY);
                pth.close();
                bc.drawPath(pth, p);
            }
        }
        phase[0] += dt * 0.15f;
        beamPaint.setAlpha((int) (170 + 85 * Math.sin(phase[0])));   // slow breathing
        full.set(0, 0, w, h);
        c.drawBitmap(beams, null, full, beamPaint);
    }

    private void drawMotes(Canvas c, int w, int h, float dt) {
        paint.setShader(null);
        for (int i = 0; i < n; i++) {
            phase[i] += dt * 0.9f;
            x[i] += (vx[i] + (float) Math.sin(phase[i]) * 3 * d) * dt;
            y[i] += (vy[i] + (float) Math.cos(phase[i] * 0.7f) * 2 * d) * dt;
            if (x[i] < -10) x[i] = w + 5; if (x[i] > w + 10) x[i] = -5;
            if (y[i] < h * 0.1f) y[i] = h * 0.95f; if (y[i] > h) y[i] = h * 0.12f;
            int a = (int) (90 + 90 * Math.sin(phase[i] * 1.3f + i));    // glints as it turns in the light
            paint.setColor((Math.max(20, a) << 24) | 0xFFF4D6);
            c.drawCircle(x[i], y[i], size[i], paint);
        }
    }

    private void drawBirds(Canvas c, int w, int h, float dt) {
        flockIn -= dt;
        if (flockIn <= 0 && flockSize == 0) {                       // a new flock
            flockSize = 3 + rnd.nextInt(4);
            flockX = -60 * d;
            flockY = h * (0.40f + rnd.nextFloat() * 0.08f);   // above the trees, below the clock and weather
            flockSpeed = (38 + rnd.nextFloat() * 20) * d;
            for (int i = 0; i < flockSize; i++) {
                x[i] = -i * (18 + rnd.nextFloat() * 10) * d;
                y[i] = (i % 2 == 0 ? 1 : -1) * i * (6 + rnd.nextFloat() * 6) * d;
                phase[i] = rnd.nextFloat() * 6.28f;
            }
        }
        if (flockSize == 0) return;
        flockX += flockSpeed * dt;
        flockY += (float) Math.sin(flockX / (120 * d)) * 6 * d * dt;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xAA2A2830);
        for (int i = 0; i < flockSize; i++) {
            phase[i] += dt * 7f;
            float bx = flockX + x[i], by = flockY + y[i];
            float span = 7 * d, flap = (float) Math.sin(phase[i]) * 4 * d;
            path.reset();
            path.moveTo(bx - span, by - flap);
            path.quadTo(bx - span * 0.4f, by - flap * 0.3f - 2 * d, bx, by);
            path.quadTo(bx + span * 0.4f, by - flap * 0.3f - 2 * d, bx + span, by - flap);
            c.drawPath(path, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        if (flockX > w + 200 * d) { flockSize = 0; flockIn = 30 + rnd.nextFloat() * 30; }
    }

    private void drawFalling(Canvas c, int w, int h, float dt) {
        paint.setShader(null);
        boolean leaf = kind == LEAVES;
        for (int i = 0; i < n; i++) {
            phase[i] += dt * (leaf ? 1.4f : 1.1f);
            angle[i] += spin[i] * dt;
            x[i] += (vx[i] + (float) Math.sin(phase[i]) * (leaf ? 24 : 16) * d) * dt;   // sway
            y[i] += vy[i] * dt * (0.75f + 0.25f * (float) Math.cos(phase[i]));
            if (y[i] > h + 20 * d || x[i] > w + 30 * d) seed(w, h, i, false);
            paint.setColor(color[i]);
            float s = size[i];
            float squash = 0.35f + 0.65f * Math.abs((float) Math.cos(angle[i] * 1.7f));   // tumbling
            c.save();
            c.rotate((float) Math.toDegrees(angle[i]), x[i], y[i]);
            if (leaf) {
                path.reset();
                path.moveTo(x[i], y[i] - s);
                path.quadTo(x[i] + s * squash, y[i], x[i], y[i] + s);
                path.quadTo(x[i] - s * squash, y[i], x[i], y[i] - s);
                c.drawPath(path, paint);
            } else {
                oval.set(x[i] - s * squash, y[i] - s * 0.6f, x[i] + s * squash, y[i] + s * 0.6f);
                c.drawOval(oval, paint);
            }
            c.restore();
        }
    }
}
