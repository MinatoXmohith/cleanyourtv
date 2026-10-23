package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard home (mkdziner design, Frame 1): greeting, time + avatar pill, glass rail, music card,
 * date + weather card, an Apps panel with seven favourites and All apps, and Featured / Movies / Shows,
 * which open {@link BrowseActivity}. The app grid ({@link MainActivity}) is the All apps screen.
 */
public class DashboardActivity extends GlassScreen implements NowPlaying.Listener {

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000L;

    private FrameLayout root;
    private NowPlaying nowPlaying;
    private TextView musicTitle, musicArtist, weatherNow, weatherCity;
    private ImageView musicArt, musicPlay;
    private GridLayout appsPanel;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long weatherAt;
    private View firstApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        root = findViewById(R.id.dashRoot);
        setUpScreen(root);

        TextView hello = findViewById(R.id.hello);
        hello.setText(cfg.greetingName.isEmpty() ? "HELLO!" : "HELLO! " + cfg.greetingName);

        View status = findViewById(R.id.btnStatus);
        pill(status);
        status.setOnClickListener(v -> showControlPanel(status));
        ((ImageView) findViewById(R.id.avatar)).setImageDrawable(Avatar.make(getResources(),
                getExternalFilesDir(null), cfg.greetingName, hello.getTypeface(), density));

        glass(findViewById(R.id.rail), 33, 0x14000000);
        findViewById(R.id.railHome).setOnClickListener(v -> focusFirstApp());
        findViewById(R.id.railSearch).setOnClickListener(v -> search(null));
        View railSettings = findViewById(R.id.railSettings);
        railSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        railSettings.setOnLongClickListener(v -> {
            startActivity(new Intent(this, LauncherSettingsActivity.class));
            return true;
        });

        glass(findViewById(R.id.musicCard), 22, 0x24000000);
        musicTitle = findViewById(R.id.musicTitle);
        musicArtist = findViewById(R.id.musicArtist);
        musicArt = findViewById(R.id.musicArt);
        musicPlay = findViewById(R.id.musicPlay);
        round(musicArt, 14);
        findViewById(R.id.musicPrev).setOnClickListener(v -> nowPlaying.previous());
        findViewById(R.id.musicNext).setOnClickListener(v -> nowPlaying.next());
        musicPlay.setOnClickListener(v -> {
            if (nowPlaying.hasSession()) nowPlaying.playPause();
            else openPackage(defaultMusicApp());
        });

        glass(findViewById(R.id.dateCard), 22, 0x24000000);
        weatherNow = findViewById(R.id.weatherNow);
        weatherCity = findViewById(R.id.weatherCity);

        appsPanel = findViewById(R.id.appsPanel);
        glass(appsPanel, 24, 0x14000000);
        buildApps();

        for (int[] p : new int[][]{{R.id.pillFeatured, 0}, {R.id.pillMovies, 1}, {R.id.pillShows, 2}}) {
            View v = findViewById(p[0]);
            pill(v);
            String kind = p[1] == 0 ? "featured" : p[1] == 1 ? "movies" : "shows";
            v.setOnClickListener(x -> startActivity(new Intent(this, BrowseActivity.class).putExtra("kind", kind)));
        }

        nowPlaying = new NowPlaying(this, this);
        root.post(this::focusFirstApp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        nowPlaying.start();
        if (System.currentTimeMillis() - weatherAt > WEATHER_EVERY_MS) loadWeather();
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while the dashboard is already in front: back to the first app.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) focusFirstApp();
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    private void focusFirstApp() {
        if (firstApp != null) firstApp.requestFocus();
    }

    // ------------------------------------------------------------------ music
    @Override
    public void onNowPlaying(NowPlaying np) {
        if (!np.allowed()) {
            musicTitle.setText("Music access is off");
            musicArtist.setText("Allow it once over ADB (see HOW-TO-RESTORE.md)");
        } else if (!np.hasSession() || np.title.isEmpty()) {
            musicTitle.setText("Nothing playing");
            musicArtist.setText("Press play to open " + appLabel(defaultMusicApp()));
        } else {
            musicTitle.setText(np.title);
            String sub = np.artist;
            if (!np.appLabel.isEmpty()) sub = sub.isEmpty() ? np.appLabel : sub + "  ·  " + np.appLabel;
            musicArtist.setText(sub);
        }
        if (np.art != null) {
            musicArt.setImageBitmap(np.art);
            musicArt.setPadding(0, 0, 0, 0);
            musicArt.setBackground(null);
        } else {
            musicArt.setImageResource(R.drawable.ic_music);
            int p = (int) (34 * density);
            musicArt.setPadding(p, p, p, p);
            musicArt.setBackground(new ColorDrawable(0x33FFFFFF));
        }
        musicPlay.setImageResource(np.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private String defaultMusicApp() {
        String pkg = themeJson.optString("music_app", "");
        if (!pkg.isEmpty()) return pkg;
        for (String p : new String[]{"com.spotify.tv.android", "com.google.android.youtube.tvmusic"}) {
            if (getPackageManager().getLeanbackLaunchIntentForPackage(p) != null) return p;
        }
        return "com.google.android.youtube.tvmusic";
    }

    private String appLabel(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "music";
        }
    }

    // ------------------------------------------------------------------ weather
    private void loadWeather() {
        String city = themeJson.optString("weather_city", "");
        if (city.isEmpty()) {
            weatherNow.setText("Weather");
            weatherCity.setText("Set weather_city in config.json");
            return;
        }
        weatherCity.setText(city);
        Net.IO.execute(() -> {
            Weather.Now w = Weather.fetch(this, city);
            main.post(() -> {
                if (w == null) {
                    weatherNow.setText("Weather unavailable");
                    return;
                }
                weatherAt = System.currentTimeMillis();
                weatherNow.setText(w.symbol + "  " + w.tempC + "°C  ·  " + w.text);
                weatherCity.setText(w.city);
            });
        });
    }

    // ------------------------------------------------------------------ apps
    private void buildApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || cfg.hiddenApps.contains(p)) continue;
            apps.add(ri);
        }
        Collections.sort(apps, (a, b) -> {
            int ia = cfg.dockApps.indexOf(a.activityInfo.packageName), ib = cfg.dockApps.indexOf(b.activityInfo.packageName);
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            if (ia != ib) return ia - ib;
            return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
        });
        appsPanel.removeAllViews();
        int tileW = (int) (139 * density), tileH = (int) (70 * density), gap = (int) (8 * density);
        for (int i = 0; i < 8; i++) {
            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setClickable(true);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            if (i < 7 && i < apps.size()) {
                ResolveInfo ri = apps.get(i);
                Drawable banner = ri.activityInfo.loadBanner(pm);
                if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                if (banner != null) {
                    img.setImageDrawable(banner);
                } else {
                    img.setImageDrawable(ri.loadIcon(pm));
                    img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    int p = (int) (14 * density);
                    img.setPadding(p, p, p, p);
                    img.setBackground(new ColorDrawable(0x40FFFFFF));
                }
                String pkg = ri.activityInfo.packageName;
                tile.setContentDescription(ri.loadLabel(pm));
                tile.setOnClickListener(v -> openPackage(pkg));
                if (firstApp == null) firstApp = tile;
            } else {
                // All apps: the classic grid screen.
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.HORIZONTAL);
                box.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(this);
                icon.setImageResource(R.drawable.ic_apps);
                icon.setImageTintList(getResources().getColorStateList(R.drawable.pill_text, null));
                icon.setDuplicateParentStateEnabled(true);
                TextView t = new TextView(this);
                t.setText("All apps");
                t.setTextSize(15);
                t.setTypeface(((TextView) findViewById(R.id.hello)).getTypeface());
                t.setTextColor(getResources().getColorStateList(R.drawable.pill_text, null));
                t.setDuplicateParentStateEnabled(true);
                t.setPadding((int) (8 * density), 0, 0, 0);
                box.addView(icon, new LinearLayout.LayoutParams((int) (22 * density), (int) (22 * density)));
                box.addView(t);
                box.setDuplicateParentStateEnabled(true);
                tile.removeView(img);
                tile.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                tile.setBackground(getDrawable(R.drawable.pill_bg));
                pill(tile);
                tile.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
            }
            round(tile, 12);
            if (i < 7) tile.setForeground(getDrawable(R.drawable.focus_ring));
            tile.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.07f : 1f).scaleY(f ? 1.07f : 1f)
                    .translationZ(f ? 12 * density : 0).setDuration(150).start());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 2), GridLayout.spec(i % 2));
            lp.width = tileW;
            lp.height = tileH;
            lp.setMargins(i % 2 == 0 ? 0 : gap, i < 2 ? 0 : gap, 0, 0);
            appsPanel.addView(tile, lp);
        }
        appsPanel.setClipChildren(false);
        appsPanel.setClipToPadding(false);
    }

    // ------------------------------------------------------------------ control panel
    private void showControlPanel(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = popup(panel, (int) (260 * density));
        row(rows, popup, R.drawable.ic_input, getString(R.string.inputs), () -> showInputs(anchor));
        row(rows, popup, R.drawable.ic_wifi, getString(R.string.wifi), () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        row(rows, popup, R.drawable.ic_settings, getString(R.string.settings), () -> open(new Intent(Settings.ACTION_SETTINGS)));
        row(rows, popup, R.drawable.ic_home_settings, getString(R.string.launcher_settings),
                () -> startActivity(new Intent(this, LauncherSettingsActivity.class)));
        popup.showAsDropDown(anchor, 0, -anchor.getHeight(), Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private void showInputs(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = popup(panel, (int) (260 * density));
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Collections.sort(inputs, (a, b) -> String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this))));
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                CharSequence custom = info.loadCustomLabel(this);
                String label = custom != null && custom.length() > 0 ? custom.toString() : String.valueOf(info.loadLabel(this));
                String id = info.getId();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                row(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, () -> {
                    Uri uri = TvContract.buildChannelUriForPassthroughInput(id);
                    open(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel"));
                });
            }
        }
        if (!cfg.liveTvPackage.isEmpty()) {
            row(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), () -> openPackage(cfg.liveTvPackage));
        }
        if (rows.getChildCount() == 0) return;
        popup.showAsDropDown(anchor, 0, -anchor.getHeight(), Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow popup(View panel, int width) {
        PopupWindow p = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        p.setBackgroundDrawable(new ColorDrawable(0));
        p.setOutsideTouchable(true);
        p.setElevation(0f);
        panel.setBackground(new LiquidGlass.PillDrawable(panel, null, density, 24 * density));
        return p;
    }

    private void row(LinearLayout rows, PopupWindow popup, int icon, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Media keys on the remote control whatever is playing.
        if (event.getAction() == KeyEvent.ACTION_DOWN && nowPlaying != null && nowPlaying.hasSession()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: nowPlaying.playPause(); return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT: nowPlaying.next(); return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS: nowPlaying.previous(); return true;
                default: break;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
