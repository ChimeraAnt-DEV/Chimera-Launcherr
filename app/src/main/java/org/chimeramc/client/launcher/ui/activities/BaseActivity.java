package org.chimeramc.client.ui.activities;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.chimeramc.client.R;
import org.chimeramc.client.core.auth.MsftAccountStore;
import org.chimeramc.client.core.news.NewsFeed;
import org.chimeramc.client.core.news.NewsRepository;
import org.chimeramc.client.core.news.NewsState;
import org.chimeramc.client.launcher.controller.ControllerConnectionMonitor;
import org.chimeramc.client.launcher.controller.ControllerToastView;
import org.chimeramc.client.ui.views.FireworkTouchLayer;
import org.chimeramc.client.launcher.controller.ControllerType;
import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.ui.navigation.LauncherTab;
import org.chimeramc.client.util.AccountTextUtils;
import org.chimeramc.client.util.PersonalizationManager;
import org.chimeramc.client.util.ThemeManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BaseActivity extends AppCompatActivity {
    private int appliedThemeGeneration = -1;
    private int appliedPersonalizationGeneration = -1;

    /** Shows the connection pill for whichever screen is in front. */
    private final ControllerConnectionMonitor.Listener controllerListener =
            new ControllerConnectionMonitor.Listener() {
                @Override
                public void onControllerConnected(String name, ControllerType type, String profileName) {
                    if (isFinishing()) return;
                    ControllerToastView.showConnected(BaseActivity.this, name, profileName, type);
                }

                @Override
                public void onControllerDisconnected(String name) {
                    if (isFinishing()) return;
                    ControllerToastView.showDisconnected(BaseActivity.this, name);
                }
            };
    private boolean navBarInjected = false;
    private final OkHttpClient navAvatarClient = BaseActivity.buildLatencyTunedClient();

    private static OkHttpClient buildLatencyTunedClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        org.chimeramc.client.settings.LowLatencyNetworkManager.configure(builder);
        return builder.build();
    }
    private final ExecutorService navAccountExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> navAccountLoginLauncher;
    private boolean newsReceiverRegistered;
    private final BroadcastReceiver newsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshNewsBadge();
            onNewsChanged();
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String languageCode = prefs.getString("language", Locale.getDefault().toLanguageTag());
        Locale locale = Locale.forLanguageTag(languageCode);
        Locale.setDefault(locale);
        float fontScale = newBase.getSharedPreferences(PersonalizationManager.PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(PersonalizationManager.KEY_FONT_SCALE, PersonalizationManager.FONT_SCALE_DEFAULT);
        Resources res = newBase.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        config.fontScale = fontScale;
        Context localizedContext = newBase.createConfigurationContext(config);
        super.attachBaseContext(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager themeManager = new ThemeManager(this);
        themeManager.applyTheme();
        // Must run before super.onCreate so the dynamic-color overlay theme is installed
        // before any view is inflated in this activity.
        ThemeManager.applyDynamicColors(this);
        appliedThemeGeneration = ThemeManager.getThemeChangeGeneration();
        appliedPersonalizationGeneration = PersonalizationManager.getChangeGeneration();
        super.onCreate(savedInstanceState);
        navAccountLoginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleNavAccountLoginResult(result.getResultCode(), result.getData()));
        hideSystemUI();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                visibility -> getWindow().getDecorView().post(this::hideSystemUI));
    }

    @Override
    public void setContentView(int layoutResID) {
        View contentView = LayoutInflater.from(this).inflate(layoutResID, null);
        wrapWithNavBar(contentView);
    }

    @Override
    public void setContentView(View view) {
        wrapWithNavBar(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        wrapWithNavBar(view);
    }

    /** The firework touch layer for this screen, or null when the nav bar is skipped. */
    private FireworkTouchLayer fireworkLayer;

    /** True once the nav indicator has been placed once, so later moves spring instead of jump. */
    private boolean navIndicatorPlaced;

    /**
     * Installs the shared firework touch layer over the screen.
     *
     * It is a sibling drawn on top, non-clickable and never consuming a touch, so it can decorate
     * the whole screen without intercepting a gesture meant for the content underneath. It is
     * skipped where the nav bar is skipped (the game activity, dialogs hosted elsewhere), so the
     * effect never appears over Minecraft.
     */
    private View attachFireworkLayer(View root) {
        // The holder observes every touch that reaches it (it is on top), then passes the event
        // down itself. The firework view cannot return true for a DOWN without swallowing the
        // gesture, and a view that returns false never receives the MOVE stream, so a drag trail
        // was impossible from inside the child. Observing here is what makes the trail work while
        // the screen below still gets a completely normal gesture.
        FrameLayout holder = new FrameLayout(this) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (fireworkLayer != null) fireworkLayer.observe(ev);
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (fireworkLayer != null) fireworkLayer.observe(ev);
                return false;
            }
        };
        holder.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fireworkLayer = new FireworkTouchLayer(this);
        holder.addView(fireworkLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fireworkLayer.setClickable(false);
        fireworkLayer.setFocusable(false);
        return holder;
    }

    private void wrapWithNavBar(View contentView) {
        if (shouldSkipNavBar()) {
            super.setContentView(contentView);
            applyPersonalization();
            return;
        }

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View navBar = LayoutInflater.from(this).inflate(R.layout.nav_bar, wrapper, false);
        wrapper.addView(navBar);

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentView.setLayoutParams(contentParams);
        wrapper.addView(contentView);

        contentView.setAlpha(0f);
        contentView.setTranslationY(8f * getResources().getDisplayMetrics().density);

        super.setContentView(attachFireworkLayer(wrapper));
        navBarInjected = true;
        setupBaseNavBar();

        applyPersonalization();

        contentView.post(() -> {
            DynamicAnim.springAlphaTo(contentView, 1f).start();
            DynamicAnim.springTranslationYTo(contentView, 0f).start();
        });
    }

    private void applyPersonalization() {
        PersonalizationManager pm = new PersonalizationManager(this);
        pm.applyToActivity(this);
    }

    protected boolean shouldSkipNavBar() {
        return false;
    }

    /**
     * Whether controller bumper keys should switch tabs.
     *
     * Overridden by screens that need the raw button presses themselves: the in-game activity
     * passes keys straight to the game, and the controller screen uses them to highlight
     * buttons. Without an override the tab handler would swallow those presses.
     */
    protected boolean shouldHandleNavKeys() {
        return true;
    }

    /**
     * Console-style tab switching. Shoulder buttons and D-pad left/right cycle the top-level
     * destinations, which is how a controller user expects to move between sections.
     *
     * Screens that need those raw presses opt out via {@link #shouldHandleNavKeys()}, and
     * {@link LauncherTab#shouldHandleKey} suppresses the behaviour while a game session is
     * running so a stray bumper press can never drop out of a game.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && navBarInjected
                && shouldHandleNavKeys()
                && LauncherTab.shouldHandleKey(
                        event.getKeyCode(), true,
                        org.chimeramc.client.settings.LowLatencyNetworkManager.isGameSessionActive())) {
            LauncherTab current = LauncherTab.forActivity(getClass());
            LauncherTab next = (current != null)
                    ? current.offset(LauncherTab.directionForKey(event.getKeyCode()))
                    : LauncherTab.LAUNCH;
            if (next != current) {
                startActivity(new Intent(this, next.activity()));
                overridePendingTransition(R.anim.nav_tab_in, R.anim.nav_tab_out);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Top-bar tabs, in declaration order. Order matches LauncherTab and nav_bar.xml. */
    private static final int[] NAV_TAB_IDS = {
            R.id.nav_tab_launch, R.id.nav_tab_instances, R.id.nav_tab_installations,
            R.id.nav_tab_mods, R.id.nav_tab_customize, R.id.nav_tab_settings
    };

    /** Labels for {@link #NAV_TAB_IDS}, same order, so compact mode can restore the active one. */
    private static final int[] NAV_TAB_LABELS = {
            R.string.nav_launch, R.string.nav_instances, R.string.nav_installations,
            R.string.nav_mods, R.string.nav_customize, R.string.nav_settings
    };

    /** True on a narrow window, where only the selected tab shows its label. */
    private boolean compactNavTabs;

    /**
     * Applies the narrow-window navigation configuration.
     *
     * On a phone the wordmark is hidden (the bar has no room for it beside six tabs and the
     * account controls without the two colliding) and the tabs are icon-only, with the selected
     * tab's label restored by {@link #setActiveNavTab}. On a tablet both are reversed by the
     * w600dp overrides, so this method reads the same booleans in both cases rather than
     * measuring the window at runtime.
     */
    private void applyNavCompactMode() {
        View appName = findViewById(R.id.nav_app_name);
        if (appName != null) {
            appName.setVisibility(getResources().getBoolean(R.bool.nav_show_app_name)
                    ? View.VISIBLE : View.GONE);
        }
        compactNavTabs = getResources().getBoolean(R.bool.nav_compact_tabs);
        if (compactNavTabs) {
            // Default the visible label to this screen's own tab so a screen that never calls
            // setActiveNavTab still shows one label rather than six bare icons. Activities that
            // do call it (most of them, to tint the entry) override this with the same answer.
            LauncherTab own = LauncherTab.forActivity(getClass());
            int ownTabId = own != null && own.index() < NAV_TAB_IDS.length
                    ? NAV_TAB_IDS[own.index()] : 0;
            for (int id : NAV_TAB_IDS) {
                TextView tab = findViewById(id);
                if (tab != null) tab.setText(id == ownTabId ? getString(labelForTab(id)) : "");
            }
        }
    }

    private void setupBaseNavBar() {
        PersonalizationManager pm = new PersonalizationManager(this);

        // The wordmark is dropped entirely on a narrow window rather than left to shrink, and
        // the tab strips are icon-only there with just the selected label shown. Both come from
        // values/bools.xml (overridden in values-w600dp), so the two configurations are resolved
        // by the resource system and cannot be half-applied.
        applyNavCompactMode();

        // Each tab is a single TextView carrying its own icon via drawableStart, so tinting
        // the compound drawable is what colours the icon.
        for (int id : NAV_TAB_IDS) {
            TextView tab = findViewById(id);
            if (tab == null) continue;
            int color = getResources().getColor(R.color.text_secondary, getTheme());
            tab.setTextColor(color);
            tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.NORMAL);
            TextViewCompat.setCompoundDrawableTintList(tab, ColorStateList.valueOf(color));
            DynamicAnim.applyPressScale(tab);
        }

        if (pm.hasBackgroundImage()) {
            View navRoot = findViewById(R.id.nav_bar_root);
            if (navRoot != null) {
                boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                navRoot.setBackgroundColor(isDark
                        ? android.graphics.Color.argb(90, 25, 25, 25)
                        : android.graphics.Color.argb(110, 255, 255, 255));
            }
            View navDivider = findViewById(R.id.nav_divider);
            if (navDivider != null) {
                boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                navDivider.setBackgroundColor(isDark
                        ? android.graphics.Color.argb(40, 255, 255, 255)
                        : android.graphics.Color.argb(40, 0, 0, 0));
            }
        }

        View backButton = findViewById(R.id.nav_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            DynamicAnim.applyPressScale(backButton);
        }

        View signIn = findViewById(R.id.nav_sign_in_button);
        if (signIn != null) {
            signIn.setOnClickListener(v -> navAccountLoginLauncher.launch(new Intent(this, MsftLoginActivity.class)));
            DynamicAnim.applyPressScale(signIn);
        }

        View avatarContainer = findViewById(R.id.nav_account_avatar_container);
        if (avatarContainer != null) {
            avatarContainer.setOnClickListener(v -> startActivity(new Intent(this, AccountsActivity.class)));
            DynamicAnim.applyPressScale(avatarContainer);
        }

        View news = findViewById(R.id.nav_news_container);
        if (news != null) {
            news.setOnClickListener(v -> {
                if (!(this instanceof NewsActivity)) {
                    startActivity(new Intent(this, NewsActivity.class));
                }
            });
            DynamicAnim.applyPressScale(news);
        }

        // Click the whole row, not the 22dp icon, so the label is part of the hit target.
        findViewById(R.id.nav_tab_launch).setOnClickListener(v -> {
            if (!(this instanceof MainActivity)) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                switchNavTab(intent);
            }
        });
        findViewById(R.id.nav_tab_instances).setOnClickListener(v -> {
            if (!(this instanceof InstancesActivity)) {
                switchNavTab(new Intent(this, InstancesActivity.class));
            }
        });
        findViewById(R.id.nav_tab_installations).setOnClickListener(v -> {
            if (!(this instanceof InstallationsActivity)) {
                switchNavTab(new Intent(this, InstallationsActivity.class));
            }
        });
        findViewById(R.id.nav_tab_mods).setOnClickListener(v -> {
            if (!(this instanceof ModsFullscreenActivity)) {
                switchNavTab(new Intent(this, ModsFullscreenActivity.class));
            }
        });
        findViewById(R.id.nav_tab_customize).setOnClickListener(v -> {
            if (!(this instanceof CustomizeActivity)) {
                switchNavTab(new Intent(this, CustomizeActivity.class));
            }
        });
        findViewById(R.id.nav_tab_settings).setOnClickListener(v -> {
            if (!(this instanceof SettingsActivity)) {
                switchNavTab(new Intent(this, SettingsActivity.class));
            }
        });

        refreshNavAccountUI();
        refreshNewsBadge();
    }

    /**
     * Plays the nav bar's arrival animation: each tab pops in with a slight overshoot, staggered
     * left to right.
     *
     * <p>Called only on a cold start that came through the splash, so it reads as the launcher
     * arriving rather than replaying every time a tab is revisited. The overshoot language is
     * shared with the home cards
     * ({@link org.chimeramc.client.ui.animation.DynamicAnim#overshootScaleIn}), so the whole
     * arrival is one motion rather than two unrelated effects.
     */
    protected void animateNavTabsArrival() {
        if (!navBarInjected) return;
        View[] tabs = new View[NAV_TAB_IDS.length];
        for (int i = 0; i < NAV_TAB_IDS.length; i++) {
            tabs[i] = findViewById(NAV_TAB_IDS[i]);
        }
        org.chimeramc.client.ui.animation.DynamicAnim.staggerArrival(tabs, 60L);
    }

    private void refreshNewsBadge() {
        if (!navBarInjected) return;
        NewsRepository.loadCached(this, (feed, error) -> applyNewsBadge(feed));
        NewsRepository.refreshIfStale(this, (feed, error) -> applyNewsBadge(feed));
    }

    private void applyNewsBadge(NewsFeed feed) {
        if (isFinishing() || isDestroyed()) return;
        int unread = NewsState.getUnreadCount(this, feed);
        View badge = findViewById(R.id.nav_news_badge);
        View container = findViewById(R.id.nav_news_container);
        if (badge != null) badge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        if (container != null) {
            container.setContentDescription(unread > 0
                    ? getString(R.string.news_unread_description, unread)
                    : getString(R.string.news_title));
        }
    }

    protected void onNewsChanged() {
    }

    protected void refreshNavAccountUI() {
        if (!navBarInjected) return;
        java.util.List<MsftAccountStore.MsftAccount> list = MsftAccountStore.list(this);
        MsftAccountStore.MsftAccount active = null;
        for (MsftAccountStore.MsftAccount a : list) if (a.active) { active = a; break; }
        View signIn = findViewById(R.id.nav_sign_in_button);
        View avatarContainer = findViewById(R.id.nav_account_avatar_container);
        if (active == null) {
            if (signIn != null) signIn.setVisibility(View.VISIBLE);
            if (avatarContainer != null) avatarContainer.setVisibility(View.GONE);
            clearNavAvatar();
        } else {
            if (signIn != null) signIn.setVisibility(View.GONE);
            if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);
            loadNavXboxAvatar(active);
        }
    }

    private void clearNavAvatar() {
        com.microsoft.xbox.idp.toolkit.CircleImageView avatar = findViewById(R.id.nav_account_avatar);
        ProgressBar progress = findViewById(R.id.nav_avatar_progress);
        if (avatar != null) avatar.setImageResource(R.drawable.ic_minecraft_cube);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    private void loadNavXboxAvatar(MsftAccountStore.MsftAccount active) {
        com.microsoft.xbox.idp.toolkit.CircleImageView avatar = findViewById(R.id.nav_account_avatar);
        ProgressBar progress = findViewById(R.id.nav_avatar_progress);
        if (avatar == null) return;

        String url = AccountTextUtils.sanitizeUrl(active != null ? active.xboxAvatarUrl : null);
        if (url == null) {
            avatar.setImageResource(R.drawable.ic_minecraft_cube);
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        Object currentUrl = avatar.getTag(R.id.nav_account_avatar);
        if (url.equals(currentUrl) && avatar.getDrawable() != null) {
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        Bitmap cached = AccountTextUtils.getCachedAvatar(url);
        if (cached != null) {
            avatar.setTag(R.id.nav_account_avatar, url);
            avatar.setImageBitmap(cached);
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        avatar.setTag(R.id.nav_account_avatar, url);
        avatar.setImageResource(R.drawable.ic_minecraft_cube);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        navAccountExecutor.execute(() -> {
            Bitmap bmp = null;
            try (Response imgResp = navAvatarClient.newCall(new Request.Builder().url(url).build()).execute()) {
                if (imgResp.isSuccessful() && imgResp.body() != null) {
                    bmp = android.graphics.BitmapFactory.decodeStream(imgResp.body().byteStream());
                }
            } catch (Exception ignored) {
            }

            final Bitmap loaded = bmp;
            runOnUiThread(() -> {
                if (!url.equals(avatar.getTag(R.id.nav_account_avatar))) return;
                if (loaded != null) {
                    AccountTextUtils.cacheAvatar(url, loaded);
                    avatar.setImageBitmap(loaded);
                }
                if (progress != null) progress.setVisibility(View.GONE);
            });
        });
    }

    private void handleNavAccountLoginResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null
                && data.getBooleanExtra(MsftLoginActivity.EXTRA_LOGIN_COMPLETED, false)) {
            String name = data.getStringExtra(MsftLoginActivity.EXTRA_LOGIN_NAME);
            String statusName = name != null ? name : getString(R.string.not_signed_in);
            Toast.makeText(this, getString(R.string.ms_login_success, statusName), Toast.LENGTH_SHORT).show();
            refreshNavAccountUI();
            onNavAccountChanged();
            return;
        }
        refreshNavAccountUI();
    }

    protected void onNavAccountChanged() {
    }

    protected void setActiveNavTab(int activeTabId) {
        if (!navBarInjected) return;

        PersonalizationManager pm = new PersonalizationManager(this);
        int accent = pm.getAccentColor();
        int accentColor = accent != 0
                ? accent
                : getResources().getColor(R.color.on_surface, getTheme());
        int inactive = getResources().getColor(R.color.text_secondary, getTheme());

        for (int id : NAV_TAB_IDS) {
            TextView tab = findViewById(id);
            if (tab == null) continue;
            int color = id == activeTabId ? accentColor : inactive;
            tab.setTextColor(color);
            tab.setTypeface(tab.getTypeface(), id == activeTabId
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            TextViewCompat.setCompoundDrawableTintList(tab, ColorStateList.valueOf(color));
            if (compactNavTabs) {
                tab.setText(id == activeTabId ? getString(labelForTab(id)) : "");
            }
            if (id == activeTabId) moveNavIndicator(tab, accentColor);
        }
    }

    /**
     * Slides the accent indicator under the active tab.
     *
     * <p>The indicator lives in the same {@code FrameLayout} as the tab row, so it scrolls with the
     * strip instead of floating over it. It is positioned from the tab's own bounds once layout has
     * run (a fresh screen's tabs have no width yet at {@code onResume}), and its width tracks the
     * tab so a wider label keeps the bar centred. The move is a spring so the marker glides rather
     * than snapping; reduced motion makes it instant through {@link DynamicAnim}.
     */
    private void moveNavIndicator(TextView tab, int accentColor) {
        View indicator = findViewById(R.id.nav_active_indicator);
        if (indicator == null) return;
        indicator.setVisibility(View.VISIBLE);
        tintIndicator(indicator, accentColor);
        Runnable place = () -> {
            View parent = (View) indicator.getParent();
            if (parent == null) return;
            int tabWidth = tab.getWidth();
            if (tabWidth <= 0) return;
            int indicatorWidth = Math.max(dp(24), (int) (tabWidth * 0.55f));
            ViewGroup.LayoutParams lp = indicator.getLayoutParams();
            if (lp.width != indicatorWidth) {
                lp.width = indicatorWidth;
                indicator.setLayoutParams(lp);
            }
            float targetX = tab.getLeft() + (tabWidth - indicatorWidth) / 2f;
            if (!DynamicAnim.areAnimationsEnabled()) {
                indicator.setTranslationX(targetX);
                indicator.setAlpha(1f);
                navIndicatorPlaced = true;
                return;
            }
            if (!navIndicatorPlaced) {
                // First placement: no slide, just fade in under the tab.
                indicator.setTranslationX(targetX);
                indicator.setAlpha(0f);
                indicator.animate().alpha(1f).setDuration(180L).start();
                navIndicatorPlaced = true;
            } else {
                DynamicAnim.springTranslationXTo(indicator, targetX).start();
                indicator.setAlpha(1f);
            }
        };
        if (tab.getWidth() > 0) place.run();
        else tab.post(place);
    }

    /** Tints the sliding indicator to the active accent. */
    private void tintIndicator(View indicator, int color) {
        android.graphics.drawable.Drawable bg = indicator.getBackground();
        if (bg == null) return;
        android.graphics.drawable.Drawable tinted = androidx.core.graphics.drawable.DrawableCompat.wrap(bg.mutate());
        androidx.core.graphics.drawable.DrawableCompat.setTint(tinted, color);
        indicator.setBackground(tinted);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** The resource string for one of {@link #NAV_TAB_IDS}, or 0 for an unknown id. */
    private int labelForTab(int tabId) {
        for (int i = 0; i < NAV_TAB_IDS.length; i++) {
            if (NAV_TAB_IDS[i] == tabId) return NAV_TAB_LABELS[i];
        }
        return 0;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!newsReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    newsReceiver,
                    new IntentFilter(NewsState.ACTION_NEWS_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            newsReceiverRegistered = true;
        }
        ControllerConnectionMonitor monitor = controllerMonitor();
        monitor.setListener(controllerListener);
        monitor.start();
    }

    /**
     * Returns the process-wide connection monitor.
     *
     * One monitor serves every screen; each activity attaches its own listener while visible so
     * exactly the front activity shows the pill.
     */
    private ControllerConnectionMonitor controllerMonitor() {
        return ControllerConnectionMonitor.get(this);
    }

    /** The process-wide monitor, so MinecraftActivity can share it. */
    public static ControllerConnectionMonitor sharedControllerMonitor(Context context) {
        return ControllerConnectionMonitor.get(context);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fireworkLayer != null) {
            // Re-read the gates on every resume: Battery Saver and "remove animations" can be
            // toggled while the app is backgrounded, and a settings change must take effect.
            fireworkLayer.refresh(this);
        }
        int currentGen = ThemeManager.getThemeChangeGeneration();
        int currentPGen = PersonalizationManager.getChangeGeneration();
        if (appliedThemeGeneration != currentGen || appliedPersonalizationGeneration != currentPGen) {
            appliedThemeGeneration = currentGen;
            appliedPersonalizationGeneration = currentPGen;
            recreate();
            return;
        }
        getDelegate().applyDayNight();
        hideSystemUI();
        refreshNavAccountUI();
        refreshNewsBadge();
    }

    @Override
    protected void onStop() {
        if (newsReceiverRegistered) {
            unregisterReceiver(newsReceiver);
            newsReceiverRegistered = false;
        }
        // Detach only this screen's listener. The monitor's device registration is process-wide
        // and deliberately left running: stopping it here unregistered the listener while the
        // incoming screen (or the game) was already in front, so a pad plugged in during a tab
        // change was never seen.
        controllerMonitor().clearListener(controllerListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        navAccountExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().applyDayNight();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    protected void hideSystemUI() {
        View decorView = getWindow().getDecorView();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        }

        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private boolean shouldSuppressTransition(Intent intent) {
        return intent != null && (intent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0;
    }

    private void switchNavTab(Intent intent){
        startActivity(intent);
        if (!shouldSuppressTransition(intent)) {
            overridePendingTransition(R.anim.nav_tab_in, R.anim.nav_tab_out);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        if (!shouldSuppressTransition(intent)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        super.startActivity(intent, options);
        if (!shouldSuppressTransition(intent)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void finishAfterTransition() {
        super.finishAfterTransition();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
