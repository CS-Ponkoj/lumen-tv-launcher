package com.home.tvlauncher;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Random;

/**
 * OLED-safe screensaver: a dim clock on pure black that moves every 30 seconds
 * so no pixels stay lit in one place.
 */
public class ClockDream extends DreamService {
    private static final long MOVE_EVERY_MS = 30_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private FrameLayout root;
    private LinearLayout clock;

    private final Runnable mover = new Runnable() {
        @Override public void run() {
            moveClock();
            handler.postDelayed(this, MOVE_EVERY_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextColor(0xB3FFFFFF);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 96);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0x80FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        clock.addView(date);

        root.addView(clock, new FrameLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        root.post(mover);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(mover);
        super.onDreamingStopped();
    }

    private void moveClock() {
        int maxX = Math.max(0, root.getWidth() - clock.getWidth());
        int maxY = Math.max(0, root.getHeight() - clock.getHeight());
        float x = maxX == 0 ? 0 : random.nextInt(maxX);
        float y = maxY == 0 ? 0 : random.nextInt(maxY);
        clock.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            clock.setTranslationX(x);
            clock.setTranslationY(y);
            clock.animate().alpha(1f).setDuration(600).start();
        }).start();
    }
}
