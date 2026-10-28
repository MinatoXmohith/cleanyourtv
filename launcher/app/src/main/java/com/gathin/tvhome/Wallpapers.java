package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Mood wallpapers picked on the TV (Launcher settings → Wallpapers): five static images bundled in the APK and five
 * live scenes ({@link LiveWallpaperView}). "theme" means the theme's own wallpaper.jpg, as before.
 */
final class Wallpapers {

    private static final String PREFS = "wallpaper_choice", KEY = "wallpaper";
    static final String THEME = "theme";

    static final String[] STATIC_IDS = {"calm", "cozy", "focus", "dreamy", "fresh"};
    static final String[] STATIC_NAMES = {"Calm", "Cozy", "Focus", "Dreamy", "Fresh"};
    static final String[] STATIC_HINTS = {"Blue-hour sea", "Amber dusk", "Graphite light", "Pastel clouds", "Misty pines"};
    static final int[] STATIC_RES = {R.drawable.wall_calm, R.drawable.wall_cozy, R.drawable.wall_focus,
            R.drawable.wall_dreamy, R.drawable.wall_fresh};

    static final String[] LIVE_IDS = {"aurora", "stars", "ocean", "fireflies", "neon"};
    static final String[] LIVE_NAMES = {"Calm", "Sleepy", "Chill", "Cozy", "Party"};
    static final String[] LIVE_HINTS = {"Aurora", "Starry night", "Ocean waves", "Fireflies", "Neon flow"};

    private Wallpapers() {}

    static String choice(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, THEME);
    }

    static void set(Context c, String value) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply();
    }

    static int index(String[] ids, String id) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        return -1;
    }

    /**
     * Puts the chosen mood wallpaper behind everything in root (index 0). Returns false for "theme", so the caller
     * shows the theme's wallpaper as before. Live scenes leave the glass without a frozen backdrop, so the motion
     * shows through the glass itself.
     */
    static boolean applyMood(Activity a, ViewGroup root) {
        View v = create(a, choice(a), true);
        if (v == null) return false;
        root.addView(v, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    /** A view showing the given choice, or null for the theme wallpaper. setGlass feeds the static image to the glass. */
    static View create(Activity a, String choice, boolean setGlass) {
        if (choice.startsWith("live:")) {
            int k = index(LIVE_IDS, choice.substring(5));
            if (k < 0) return null;
            if (setGlass) LiquidGlass.clear();
            return new LiveWallpaperView(a, k);
        }
        if (choice.startsWith("static:")) {
            int k = index(STATIC_IDS, choice.substring(7));
            if (k < 0) return null;
            Bitmap bmp = decode(a, STATIC_RES[k], 1);
            if (bmp == null) return null;
            if (setGlass) {
                android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
                LiquidGlass.setWallpaper(bmp, dm.widthPixels, dm.heightPixels);
            }
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            return iv;
        }
        return null;
    }

    static Bitmap decode(Context c, int res, int sample) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(c.getResources(), res, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
