package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Liquid glass for the top pills and the dock band: the wallpaper behind, blurred, then a light tint, a top
 * sheen and a rim that is bright at the top and fades down. Android 9 has no live blur, so the blur is made
 * once from the wallpaper (a small copy, about 130 KB) and each glass shape samples the part of it that sits
 * behind it. Without a wallpaper the same tint, sheen and rim are drawn over the plain background.
 */
final class LiquidGlass {

    private static final int SCALE = 8;           // backdrop is 1/8 of the screen, then box blurred
    private static Bitmap backdrop;
    private static float backdropScale;

    private LiquidGlass() {}

    /** No wallpaper: the glass is drawn over the plain background colour. */
    static void clear() {
        backdrop = null;
    }

    /** Builds the blurred backdrop from the wallpaper as CENTER_CROP shows it on a screenW x screenH screen. */
    static void setWallpaper(Bitmap wall, int screenW, int screenH) {
        try {
            int bw = Math.max(1, screenW / SCALE), bh = Math.max(1, screenH / SCALE);
            Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            float s = Math.max((float) bw / wall.getWidth(), (float) bh / wall.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((bw - wall.getWidth() * s) / 2f, (bh - wall.getHeight() * s) / 2f);
            c.drawBitmap(wall, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] px = new int[bw * bh];
            small.getPixels(px, 0, bw, 0, 0, bw, bh);
            for (int pass = 0; pass < 3; pass++) boxBlur(px, bw, bh, 3);
            small.setPixels(px, 0, bw, 0, 0, bw, bh);
            backdrop = small;
            backdropScale = (float) screenW / bw;
        } catch (Exception | OutOfMemoryError e) {
            backdrop = null;   // glass without the blur is still glass
        }
    }

    /** Horizontal then vertical box blur of the given radius, in place. */
    private static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = px[y * w + Math.min(w - 1, Math.max(0, i))];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = px[y * w + Math.max(0, x - r)], in = px[y * w + Math.min(w - 1, x + r + 1)];
                ra += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ga += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = tmp[Math.min(h - 1, Math.max(0, i)) * w + x];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                px[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int out = tmp[Math.max(0, y - r) * w + x], in = tmp[Math.min(h - 1, y + r + 1) * w + x];
                ra += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                ga += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                ba += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    /** Paints for one glass shape; reused across frames. */
    static final class Painter {
        private final float density;
        private final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF inner = new RectF();
        private Bitmap shaderFor;
        private BitmapShader shader;
        private float lastTop = Float.NaN, lastBottom = Float.NaN;

        Painter(float density) {
            this.density = density;
            // Slightly darker and more saturated than the wallpaper: white text stays readable on bright art.
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.35f);
            ColorMatrix dim = new ColorMatrix();
            dim.setScale(0.72f, 0.72f, 0.72f, 1f);
            cm.postConcat(dim);
            blur.setColorFilter(new ColorMatrixColorFilter(cm));
            tint.setColor(0x1AFFFFFF);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.4f * density);
            shade.setStyle(Paint.Style.STROKE);
            shade.setStrokeWidth(1f * density);
            shade.setColor(0x33000000);
        }

        /**
         * Draws glass in rect (canvas coordinates). originX/originY is where the canvas's (0,0) sits on the
         * screen, so the blurred wallpaper lines up with the real one behind.
         */
        void draw(Canvas c, RectF rect, float radius, float originX, float originY) {
            Bitmap b = backdrop;
            if (b != null) {
                if (shaderFor != b) {
                    shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blur.setShader(shader);
                    shaderFor = b;
                }
                matrix.setScale(backdropScale, backdropScale);
                matrix.postTranslate(-originX, -originY);
                shader.setLocalMatrix(matrix);
                c.drawRoundRect(rect, radius, radius, blur);
            } else {
                tint.setColor(0x14FFFFFF);
            }
            c.drawRoundRect(rect, radius, radius, tint);
            if (rect.top != lastTop || rect.bottom != lastBottom) {
                float h = rect.height();
                sheen.setShader(new LinearGradient(0, rect.top, 0, rect.top + h * 0.6f,
                        0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                rim.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        new int[]{0xD9FFFFFF, 0x26FFFFFF, 0x59FFFFFF}, new float[]{0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP));
                lastTop = rect.top;
                lastBottom = rect.bottom;
            }
            c.drawRoundRect(rect, radius, radius, sheen);
            // A hairline of shadow just outside the rim lifts the glass off bright wallpaper.
            inner.set(rect);
            inner.inset(-0.5f * density, -0.5f * density);
            c.drawRoundRect(inner, radius + 0.5f * density, radius + 0.5f * density, shade);
            inner.set(rect);
            inner.inset(0.7f * density, 0.7f * density);
            c.drawRoundRect(inner, radius, radius, rim);
        }
    }

    /**
     * Background for a pill: liquid glass normally, the pill's own drawable (solid white) when focused or
     * activated, so the focused button reads as clearly as before.
     */
    static final class PillDrawable extends Drawable {
        private final View host;
        private final Drawable original;
        private final Painter painter;
        private final RectF rect = new RectF();
        private final int[] loc = new int[2];
        private boolean solid;
        /** Corner radius in px; 0 = fully rounded ends (half the height). */
        private final float radius;

        PillDrawable(View host, Drawable original, float density) {
            this(host, original, density, 0f);
        }

        /** Glass panel with fixed corners; original may be null when the panel never takes focus. */
        PillDrawable(View host, Drawable original, float density, float radius) {
            this.host = host;
            this.original = original != null ? original : new android.graphics.drawable.ColorDrawable(0);
            this.painter = new Painter(density);
            this.radius = radius;
        }

        @Override
        public void draw(Canvas c) {
            if (solid) {
                original.draw(c);
                return;
            }
            Rect r = getBounds();
            rect.set(r);
            host.getLocationOnScreen(loc);
            painter.draw(c, rect, radius > 0 ? radius : rect.height() / 2f, loc[0], loc[1]);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            original.setState(state);
            boolean s = false;
            for (int st : state) {
                if (st == android.R.attr.state_focused || st == android.R.attr.state_activated) s = true;
            }
            boolean changed = s != solid;
            solid = s;
            return changed;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            original.setBounds(bounds);
        }

        @Override
        public void setAlpha(int alpha) {
            original.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            original.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
