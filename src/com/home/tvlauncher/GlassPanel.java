package com.home.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.FrameLayout;

/**
 * Frosted-glass panel. Android 11 has no live background blur, so this draws a pre-blurred
 * copy of the wallpaper, lined up with where the panel sits on screen, then a light tint and rim.
 */
final class GlassPanel extends FrameLayout {
    private final Bitmap blur;
    private final float radius;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final int[] loc = new int[2];

    GlassPanel(Context c, Bitmap blur, float radius) {
        super(c);
        this.blur = blur;
        this.radius = radius;
        setWillNotDraw(false);
        tint.setColor(blur != null ? 0x26FFFFFF : 0x40101622);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(c.getResources().getDisplayMetrics().density);
        rim.setColor(0x4DFFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (blur != null) {
            getLocationOnScreen(loc);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            float s = blur.getWidth() / (float) screenW;
            Rect src = new Rect(Math.round(loc[0] * s), Math.round(loc[1] * s),
                    Math.round((loc[0] + w) * s), Math.round((loc[1] + h) * s));
            canvas.drawBitmap(blur, src, new Rect(0, 0, w, h), bitmapPaint);
        }
        canvas.drawRect(box, tint);
        canvas.restore();
        box.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
        canvas.drawRoundRect(box, radius, radius, rim);
        super.onDraw(canvas);
    }

    /** Small, fast box blur (three passes ≈ gaussian) for a downscaled wallpaper bitmap. */
    static Bitmap blur(Bitmap src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxPass(a, b, w, h, radius, true);
            boxPass(b, a, w, h, radius, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static void boxPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int px = horizontal ? in[l * w + j] : in[j * w + l];
                    rs += (px >> 16) & 0xFF; gs += (px >> 8) & 0xFF; bs += px & 0xFF; n++;
                }
                int idx = horizontal ? l * w + i : i * w + l;
                out[idx] = 0xFF000000 | ((rs / n) << 16) | ((gs / n) << 8) | (bs / n);
            }
        }
    }
}
