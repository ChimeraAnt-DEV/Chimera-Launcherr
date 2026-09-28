package org.chimeramc.client.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Looper;
import android.view.ViewOutlineProvider;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;

import org.chimeramc.client.R;
import org.chimeramc.client.core.minecraft.MinecraftImportIntents;
import org.chimeramc.client.core.minecraft.LaunchTrace;
import org.chimeramc.client.core.minecraft.MinecraftLauncher;
import org.chimeramc.client.core.minecraft.PlaytimeManager;
import org.chimeramc.client.core.mods.FileHandler;
import org.chimeramc.client.core.mods.Mod;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.news.ChangelogManager;
import org.chimeramc.client.core.versions.GameVersion;
import org.chimeramc.client.core.versions.VersionManager;
import org.chimeramc.client.databinding.ActivityMainBinding;
import org.chimeramc.client.settings.FeatureSettings;

import org.chimeramc.client.ui.animation.DynamicAnim;
import org.chimeramc.client.ui.dialogs.CustomAlertDialog;
import org.chimeramc.client.ui.dialogs.LibsRepairDialog;
import org.chimeramc.client.ui.dialogs.PlayStoreValidationDialog;
import org.chimeramc.client.ui.views.MainViewModel;
import org.chimeramc.client.ui.views.MainViewModelFactory;
import org.chimeramc.client.util.ApkImportManager;
import org.chimeramc.client.util.GithubReleaseUpdater;
import org.chimeramc.client.util.LanguageManager;
import org.chimeramc.client.util.InstanceShortcutManager;
import org.chimeramc.client.util.LauncherStorage;
import org.chimeramc.client.util.PermissionsHandler;
import org.chimeramc.client.util.PersonalizationManager;
import org.chimeramc.client.util.PlayStoreValidator;
import org.chimeramc.client.util.ResourcepackHandler;
import org.chimeramc.client.util.StorageMigrationManager;
import org.chimeramc.client.util.StorageMigrationService;
import org.chimeramc.client.util.UIHelper;
import org.chimeramc.client.core.content.ContentManager;
import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


 import android.widget.Button;
 import android.widget.ProgressBar;
 import android.graphics.Bitmap;
 import android.view.Gravity;
 import android.widget.PopupWindow;
 import android.view.LayoutInflater;
 import android.graphics.drawable.ColorDrawable;
 import android.util.TypedValue;
 import android.view.ViewGroup;
 import android.view.ViewTreeObserver;
 import androidx.core.content.ContextCompat;

import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import okhttp3.OkHttpClient;
 import okhttp3.Request;
 import okhttp3.Response;

 import org.chimeramc.client.core.auth.MsftAccountStore;
 import org.chimeramc.client.core.auth.MsftAuthManager;
 import org.chimeramc.client.ui.dialogs.LoadingDialog;
 import org.chimeramc.client.util.AccountTextUtils;
 import org.chimeramc.client.util.DialogUtils;

 import static org.chimeramc.client.core.minecraft.MinecraftProcessRestarterKt.ACTION_MAIN_ACTIVITY_FIRST_DRAWN;
 import static org.chimeramc.client.core.minecraft.MinecraftProcessRestarterKt.EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW;

 public class MainActivity extends BaseActivity {
    private ActivityMainBinding binding;
    private MinecraftLauncher minecraftLauncher;
    private LanguageManager languageManager;
    private PermissionsHandler permissionsHandler;
    private FileHandler fileHandler;
    private ApkImportManager apkImportManager;
    private MainViewModel viewModel;
    private VersionManager versionManager;
    private StorageMigrationManager storageMigrationManager;
    private ActivityResultLauncher<Intent> permissionResultLauncher;
    private ActivityResultLauncher<Intent> apkImportResultLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    private ContentManager contentManager;
    private TextView worldsCountText;
    private TextView resourcePacksCountText;
    private TextView behaviorPacksCountText;

    private com.microsoft.xbox.idp.toolkit.CircleImageView accountAvatar;
    private View accountAvatarContainer;
    private ProgressBar avatarProgress;
    private Button signInButton;
    private boolean headerAccountViewsLoaded;
    private com.microsoft.xbox.idp.toolkit.CircleImageView headerAccountAvatar;
    private TextView headerAccountName;
    private Button headerSignInButton;
    private String lastAvatarXuid;
    private final OkHttpClient avatarClient = MainActivity.buildLatencyTunedClient();

    private static OkHttpClient buildLatencyTunedClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        org.chimeramc.client.settings.LowLatencyNetworkManager.configure(builder);
        return builder.build();
    }
    private ExecutorService accountExecutor = Executors.newSingleThreadExecutor();
    private LoadingDialog accountLoadingDialog;
    private ActivityResultLauncher<Intent> accountLoginLauncher;
    private OnBackPressedCallback onBackPressedCallback;
    private boolean migrationPromptShown;
    private boolean migrationPromptCheckInFlight;
    private boolean postMigrationInitialized;
    private boolean postEulaFlowRunning;
    private StorageMigrationService storageMigrationService;
    private boolean storageMigrationBound;
    private LibsRepairDialog storageMigrationDialog;
    private StorageMigrationService.MigrationState lastMigrationState;
    private final ExecutorService storageMigrationExecutor = Executors.newSingleThreadExecutor();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pulseRunnable;
    private boolean pulseRunning;
    /** True while a launch is in flight, so the hero card and Play button cannot double-fire. */
    private boolean launchInProgress;
    private ValueAnimator activeProgressAnimator;

    private final StorageMigrationService.MigrationListener storageMigrationListener =
            state -> runOnUiThread(() -> handleStorageMigrationState(state));

    private final ServiceConnection storageMigrationConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            storageMigrationService = ((StorageMigrationService.LocalBinder) service).getService();
            storageMigrationBound = true;
            storageMigrationService.addListener(storageMigrationListener);
            handleStorageMigrationState(storageMigrationService.getCurrentState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (storageMigrationService != null) {
                storageMigrationService.removeListener(storageMigrationListener);
            }
            storageMigrationService = null;
            storageMigrationBound = false;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        closeLauncherRestartAfterFirstDraw();
        setupNavBar();
        setupManagersAndHandlers();
        if (!isInstanceShortcutIntent()) {
            new GithubReleaseUpdater(this, "ChimeraAnt-DEV", "ChimeraLauncher", permissionResultLauncher).checkUpdateOnLaunch();
        }
        showEulaIfNeeded();
        setupOnBackPressedCallback();

        accountLoginLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null
                    && result.getData().getBooleanExtra(MsftLoginActivity.EXTRA_LOGIN_COMPLETED, false)) {
                String name = result.getData().getStringExtra(MsftLoginActivity.EXTRA_LOGIN_NAME);
                String statusName = name != null ? name : getString(R.string.not_signed_in);
                Toast.makeText(this, getString(R.string.ms_login_success, statusName), Toast.LENGTH_SHORT).show();
            }
            refreshAccountHeaderUI();
            refreshNavAccountUI();
        });

        initAccountHeader();
        binding.getRoot().post(this::showPostEulaFlow);
        binding.getRoot().post(this::playArrivalAnimations);
    }

    /**
     * The arrival motion for a cold start launched through the splash.
     *
     * <p>Gated strictly behind the splash extra so it plays once per cold start and never replays
     * when the user returns to the Launch tab: a tab switch recreates this activity, and an
     * animation that re-ran every time would read as the app reloading rather than the user moving.
     */
    private void playArrivalAnimations() {
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra(SplashActivity.EXTRA_FROM_SPLASH, false)) {
            return;
        }
        // Consume the extra so a configuration change or a return to this tab cannot replay it.
        intent.removeExtra(SplashActivity.EXTRA_FROM_SPLASH);

        animateNavTabsArrival();
        animateHomeCardsArrival();
    }

    /**
     * Home screen entrance stagger: hero card, then the quick-action row, then the content/stats
     * block, each a beat apart.
     *
     * <p>Same overshoot language as the nav tabs, so the whole screen arrives as one motion system.
     * Reduced motion is handled inside {@link DynamicAnim#overshootScaleIn}, which snaps the views
     * straight to full.
     */
    private void animateHomeCardsArrival() {
        int[] order = {
                R.id.last_played_card,
                R.id.quick_versions_card,
                R.id.quick_mods_card,
                R.id.quick_content_card,
        };
        for (int i = 0; i < order.length; i++) {
            View view = findViewById(order[i]);
            if (view == null) continue;
            DynamicAnim.overshootScaleIn(view, i * DynamicAnim.ARRIVAL_STEP_MS);
        }
        View statsBlock = findViewById(R.id.contentViewAll);
        if (statsBlock != null) {
            DynamicAnim.overshootScaleIn(statsBlock, order.length * DynamicAnim.ARRIVAL_STEP_MS);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVersionDependentIntent();
    }

    private void closeLauncherRestartAfterFirstDraw() {
        Intent intent = getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW, false)) {
            return;
        }
        intent.removeExtra(EXTRA_CLOSE_RESTART_ACTIVITY_ON_FIRST_DRAW);
        setIntent(intent);

        final View root = binding.getRoot();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) {
                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                root.post(() -> {
                    hideSystemUI();
                    sendBroadcast(new Intent(ACTION_MAIN_ACTIVITY_FIRST_DRAWN).setPackage(getPackageName()));
                });
                return true;
            }
        });
    }


    private void initAccountHeader() {
        signInButton = findViewById(R.id.nav_sign_in_button);
        accountAvatar = findViewById(R.id.nav_account_avatar);
        accountAvatarContainer = findViewById(R.id.nav_account_avatar_container);
        avatarProgress = findViewById(R.id.nav_avatar_progress);

        if (signInButton != null) {
            signInButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, MsftLoginActivity.class);
                accountLoginLauncher.launch(intent);
            });
            DynamicAnim.applyPressScale(signInButton);
        }
        if (accountAvatarContainer != null) {
            accountAvatarContainer.setOnClickListener(this::showAccountSwitchPopup);
            DynamicAnim.applyPressScale(accountAvatarContainer);
        }

        refreshAccountHeaderUI();
    }

    private MsftAccountStore.MsftAccount getActiveAccount() {
        java.util.List<MsftAccountStore.MsftAccount> list = MsftAccountStore.list(this);
        for (MsftAccountStore.MsftAccount a : list) if (a.active) return a;
        return null;
    }

     private void setupOnBackPressedCallback() {
         onBackPressedCallback = new OnBackPressedCallback(true) {
             @Override
             public void handleOnBackPressed() {
                 org.chimeramc.client.ui.dialogs.CustomAlertDialog exitDialog = new org.chimeramc.client.ui.dialogs.CustomAlertDialog(MainActivity.this);
                 exitDialog.setTitleText(getString(org.chimeramc.client.R.string.dialog_title_exit_app))
                         .setMessage(getString(org.chimeramc.client.R.string.dialog_message_exit_app))
                         .setPositiveButton(getString(org.chimeramc.client.R.string.dialog_positive_exit), v -> {
                             exitDialog.dismissImmediately();
                             finishAffinity();
                         })
                         .setNegativeButton(getString(org.chimeramc.client.R.string.dialog_negative_cancel), null)
                         .show();
             }
         };

         getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
     }

    private void refreshAccountHeaderUI() {
        ensureHeaderAccountViews();
        MsftAccountStore.MsftAccount active = getActiveAccount();
        if (active == null) {
            if (signInButton != null) signInButton.setVisibility(View.VISIBLE);
            if (accountAvatarContainer != null) accountAvatarContainer.setVisibility(View.GONE);
            if (accountAvatar != null) accountAvatar.setImageDrawable(null);
            lastAvatarXuid = null;
            if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
            if (headerSignInButton != null) headerSignInButton.setVisibility(View.VISIBLE);
            if (headerAccountAvatar != null) headerAccountAvatar.setVisibility(View.GONE);
            if (headerAccountName != null) headerAccountName.setText(AccountTextUtils.displayNameOrNotSigned(this, null));
        } else {
            if (signInButton != null) signInButton.setVisibility(View.GONE);
            if (accountAvatarContainer != null) accountAvatarContainer.setVisibility(View.VISIBLE);
            if (headerSignInButton != null) headerSignInButton.setVisibility(View.GONE);
            if (headerAccountAvatar != null) headerAccountAvatar.setVisibility(View.VISIBLE);
            if (headerAccountName != null) headerAccountName.setText(AccountTextUtils.displayNameOrNotSigned(this, active));
            loadXboxAvatar(active);
            if (headerAccountAvatar != null && accountAvatar != null && accountAvatar.getDrawable() != null) {
                headerAccountAvatar.setImageDrawable(accountAvatar.getDrawable());
            }
        }
    }

    private void ensureHeaderAccountViews() {
        if (headerAccountViewsLoaded) return;
        headerAccountAvatar = findViewById(R.id.header_account_avatar);
        headerAccountName = findViewById(R.id.header_account_name);
        headerSignInButton = findViewById(R.id.header_sign_in_button);
        headerAccountViewsLoaded = true;
    }

    private void loadXboxAvatar(MsftAccountStore.MsftAccount active) {
        if (accountAvatar == null) return;
        String url = AccountTextUtils.sanitizeUrl(active != null ? active.xboxAvatarUrl : null);
        if (url == null) {
            if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
            accountAvatar.setImageDrawable(null);
            lastAvatarXuid = null;
            return;
        }

        Object currentUrl = accountAvatar.getTag(R.id.nav_account_avatar);
        if (url.equals(currentUrl) && accountAvatar.getDrawable() != null) {
            if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
            return;
        }

        Bitmap cached = AccountTextUtils.getCachedAvatar(url);
        if (cached != null) {
            accountAvatar.setTag(R.id.nav_account_avatar, url);
            accountAvatar.setImageBitmap(cached);
            if (headerAccountAvatar != null) headerAccountAvatar.setImageBitmap(cached);
            if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
            return;
        }

        accountAvatar.setTag(R.id.nav_account_avatar, url);
        accountAvatar.setImageDrawable(null);
        if (avatarProgress != null) avatarProgress.setVisibility(View.VISIBLE);
        accountExecutor.execute(() -> {
            try {
                try (Response imgResp = avatarClient.newCall(new Request.Builder().url(url).build()).execute()) {
                    Bitmap bmp = (imgResp.isSuccessful() && imgResp.body() != null) ? android.graphics.BitmapFactory.decodeStream(imgResp.body().byteStream()) : null;
                    runOnUiThread(() -> {
                        if (!url.equals(accountAvatar.getTag(R.id.nav_account_avatar))) return;
                        if (bmp != null) {
                            AccountTextUtils.cacheAvatar(url, bmp);
                            accountAvatar.setImageBitmap(bmp);
                            if (headerAccountAvatar != null) headerAccountAvatar.setImageBitmap(bmp);
                        }
                        if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (avatarProgress != null) avatarProgress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void showAccountSwitchPopup(View anchor) {
        java.util.List<MsftAccountStore.MsftAccount> list = MsftAccountStore.list(this);

        View content = LayoutInflater.from(this).inflate(R.layout.popup_account_switch, null);
        androidx.recyclerview.widget.RecyclerView recyclerAccounts = content.findViewById(R.id.recycler_accounts);
        TextView manageAction = content.findViewById(R.id.manage_action);
        com.microsoft.xbox.idp.toolkit.CircleImageView headerAvatar = content.findViewById(R.id.header_avatar);
        View headerContainer = content.findViewById(R.id.header_container);
        TextView headerName = content.findViewById(R.id.header_name);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        int selectableRes = outValue.resourceId;

        int paddingH = (int) (16 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (12 * getResources().getDisplayMetrics().density);
        int paddingR = (int) (12 * getResources().getDisplayMetrics().density);

        MsftAccountStore.MsftAccount active = getActiveAccount();
        headerName.setText(AccountTextUtils.displayNameOrNotSigned(this, active));
        if (accountAvatar != null && accountAvatar.getDrawable() != null) {
            headerAvatar.setImageDrawable(accountAvatar.getDrawable());
        } else if (active != null) {
            final String url = AccountTextUtils.sanitizeUrl(active.xboxAvatarUrl);
            if (url != null) {
                accountExecutor.execute(() -> {
                    try {
                        okhttp3.OkHttpClient.Builder clientBuilder = new okhttp3.OkHttpClient.Builder();
                        org.chimeramc.client.settings.LowLatencyNetworkManager.configure(clientBuilder);
                        okhttp3.OkHttpClient client = clientBuilder.build();
                        okhttp3.Response imgResp = client.newCall(new okhttp3.Request.Builder().url(url).build()).execute();
                        final android.graphics.Bitmap bmp = (imgResp.isSuccessful() && imgResp.body() != null) ? android.graphics.BitmapFactory.decodeStream(imgResp.body().byteStream()) : null;
                        runOnUiThread(() -> { if (bmp != null) headerAvatar.setImageBitmap(bmp); });
                    } catch (Exception ignored) {}
                });
            }
        }

        final PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        content.setAlpha(0f);
        content.setTranslationY(24f);
        float dens = getResources().getDisplayMetrics().density;
        if (headerContainer != null) {
            headerContainer.setAlpha(0f);
            headerContainer.setTranslationY(8f * dens);
        }
        if (headerAvatar != null) {
            headerAvatar.setAlpha(0f);
            headerAvatar.setScaleX(0.94f);
            headerAvatar.setScaleY(0.94f);
        }
        if (headerName != null) {
            headerName.setAlpha(0f);
            headerName.setTranslationY(6f * dens);
        }
        if (manageAction != null) {
            manageAction.setAlpha(0f);
            manageAction.setTranslationX(6f * dens);
        }
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        if (android.os.Build.VERSION.SDK_INT >= 21) popup.setElevation(8f);

        final ViewGroup root = findViewById(android.R.id.content);
        final View scrim = new View(this);
        scrim.setBackgroundColor(ContextCompat.getColor(this, R.color.scrim));
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> popup.dismiss());
        root.addView(scrim, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(120).start();

         final java.util.List<MsftAccountStore.MsftAccount> displayList = new java.util.ArrayList<>();
        for (MsftAccountStore.MsftAccount a : list) {
            if (active == null || !android.text.TextUtils.equals(a.id, active.id)) displayList.add(a);
        }

         class AccountRowViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
             TextView tv;
             AccountRowViewHolder(TextView t) { super(t); this.tv = t; }
         }

         recyclerAccounts.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
         recyclerAccounts.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<AccountRowViewHolder>() {
             @Override
             public AccountRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                 TextView row = new TextView(parent.getContext());
                 row.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                 row.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.on_surface));
                 row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                 row.setPadding(paddingH, paddingV, paddingR, paddingV);
                 row.setBackgroundResource(selectableRes);
                 return new AccountRowViewHolder(row);
             }

             @Override
             public void onBindViewHolder(AccountRowViewHolder holder, int position) {
                 MsftAccountStore.MsftAccount account = displayList.get(position);
                 holder.tv.setText(AccountTextUtils.titleOrUnknown(account));
                 holder.tv.setOnClickListener(v -> {
                     popup.dismiss();

                     MsftAccountStore.setActive(MainActivity.this, account.id);
                     boolean withinSevenDays = AccountTextUtils.isRecentlyUpdated(account, 7);

                     if (withinSevenDays) {
                         runOnUiThread(() -> {
                             DialogUtils.dismissQuietly(accountLoadingDialog);
                             String statusName = AccountTextUtils.displayNameOrNotSigned(MainActivity.this, account);
                             Toast.makeText(MainActivity.this, getString(R.string.ms_login_success, statusName), Toast.LENGTH_SHORT).show();
                             refreshAccountHeaderUI();
                         });
                         return;
                     }

                     accountLoadingDialog = DialogUtils.ensure(MainActivity.this, accountLoadingDialog);
                     DialogUtils.showWithMessage(accountLoadingDialog, getString(R.string.ms_login_auth_xbox_device));

                     accountExecutor.execute(() -> {
                         try {
                             BedrockAuthManager authManager = MsftAuthManager.refreshAndAuth(account);
                             MsftAuthManager.saveAccountOrThrow(MainActivity.this, authManager);
                             MsftAccountStore.setActive(MainActivity.this, account.id);
                             String minecraftUsername = authManager.getMinecraftCertificateChain().getUpToDate().getIdentityDisplayName();

                             runOnUiThread(() -> {
                                 DialogUtils.dismissQuietly(accountLoadingDialog);
                                 String statusName = minecraftUsername != null ? minecraftUsername : getString(R.string.not_signed_in);
                                 Toast.makeText(MainActivity.this, getString(R.string.ms_login_success, statusName), Toast.LENGTH_SHORT).show();
                                 refreshAccountHeaderUI();
                             });
                         } catch (Exception e) {
                             runOnUiThread(() -> {
                                 DialogUtils.dismissQuietly(accountLoadingDialog);
                                 Toast.makeText(MainActivity.this, getString(R.string.ms_login_failed_detail, MsftAuthManager.describeError(e)), Toast.LENGTH_LONG).show();
                                 refreshAccountHeaderUI();
                             });
                         }
                     });
                 });
             }

             @Override
             public int getItemCount() { return displayList.size(); }
         });

         float density = getResources().getDisplayMetrics().density;
         if (displayList.size() > 2) {
             int limitHeight = (int) ((48 * 2 + 16) * density);
             recyclerAccounts.getLayoutParams().height = limitHeight;
         } else {
             recyclerAccounts.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
         }

        manageAction.setOnClickListener(v -> {
            popup.dismiss();
            startActivity(new Intent(this, AccountsActivity.class));
        });
        DynamicAnim.applyPressScale(manageAction);

        popup.setOnDismissListener(() -> {
            if (root != null && scrim != null) {
                scrim.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    try { root.removeView(scrim); } catch (Exception ignored) {}
                }).start();
            }
        });

        int edgeMargin = (int) (4 * getResources().getDisplayMetrics().density);
        popup.showAsDropDown(anchor, -edgeMargin, edgeMargin / 4, Gravity.END);

        DynamicAnim.springAlphaTo(content, 1f).start();
        DynamicAnim.springTranslationYTo(content, 0f).start();
        recyclerAccounts.post(() -> DynamicAnim.staggerRecyclerChildren(recyclerAccounts));
        if (headerContainer != null) {
            DynamicAnim.springAlphaTo(headerContainer, 1f).start();
            DynamicAnim.springTranslationYTo(headerContainer, 0f).start();
        }
        if (headerAvatar != null) {
            DynamicAnim.springAlphaTo(headerAvatar, 1f).start();
            DynamicAnim.springScaleXTo(headerAvatar, 1f).start();
            DynamicAnim.springScaleYTo(headerAvatar, 1f).start();
        }
        if (headerName != null) {
            DynamicAnim.springAlphaTo(headerName, 1f).start();
            DynamicAnim.springTranslationYTo(headerName, 0f).start();
        }
        if (manageAction != null) {
            DynamicAnim.springAlphaTo(manageAction, 1f).start();
            DynamicAnim.springTranslationXTo(manageAction, 0f).start();
        }
    }

    private void setupManagersAndHandlers() {
        languageManager = new LanguageManager(this);
        languageManager.applySavedLanguage();
        storageMigrationManager = new StorageMigrationManager(this);
        permissionResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (permissionsHandler != null)
                        permissionsHandler.onActivityResult(result.getResultCode(), result.getData());
                }
        );
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                }
        );
        apkImportResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (apkImportManager != null)
                        apkImportManager.handleActivityResult(result.getResultCode(), result.getData());
                }
        );
        permissionsHandler = PermissionsHandler.getInstance();
        permissionsHandler.setActivity(this, permissionResultLauncher);
        initListeners();
    }

    private void initializeAfterMigrationGate() {
        if (postMigrationInitialized || isFinishing() || isDestroyed()) return;
        postMigrationInitialized = true;

        minecraftLauncher = new MinecraftLauncher(this);
        viewModel = new ViewModelProvider(this, new MainViewModelFactory(getApplication())).get(MainViewModel.class);
        apkImportManager = new ApkImportManager(this, viewModel);

        initializeVersionManager();
        initModsSection();
        initContentManagementSection();
        initMiscellaneousSection();
    }

    private void initializeVersionManager() {
        binding.launchButton.setEnabled(false);
        versionManager = VersionManager.getIfInitialized();
        if (versionManager != null) {
            onVersionManagerReady();
            return;
        }
        VersionManager.initializeAsync(this, manager -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            versionManager = manager;
            onVersionManagerReady();
        }));
    }

    private void onVersionManagerReady() {
        if (versionManager == null || binding == null) return;
        fileHandler = new FileHandler(this, viewModel, versionManager);
        resetLaunchButton();
        if (!handleInstanceShortcutLaunch()) {
            setTextMinecraftVersion();
            updateViewModelVersion();
            repairNeededVersions();
            handleVersionDependentIntent();
        }
        refreshContentCounts();
    }

    private void handleVersionDependentIntent() {
        if (versionManager == null || fileHandler == null) return;
        if (handleInstanceShortcutLaunch()) return;
        if (!forwardIncomingMinecraftResourceToRunningGame()) {
            checkResourcepack();
            handleIncomingFiles();
        }
        handleMinecraftUriLaunch();
    }

    private boolean handleInstanceShortcutLaunch() {
        Intent intent = getIntent();
        if (intent == null || !InstanceShortcutManager.ACTION_LAUNCH_INSTANCE.equals(intent.getAction())) {
            return false;
        }

        String type = intent.getStringExtra(InstanceShortcutManager.EXTRA_INSTANCE_TYPE);
        String key = intent.getStringExtra(InstanceShortcutManager.EXTRA_INSTANCE_KEY);
        intent.setAction(null);
        intent.removeExtra(InstanceShortcutManager.EXTRA_INSTANCE_TYPE);
        intent.removeExtra(InstanceShortcutManager.EXTRA_INSTANCE_KEY);
        setIntent(intent);

        GameVersion target = findShortcutVersion(type, key);
        if (target == null) {
            Toast.makeText(this, R.string.instance_shortcut_missing, Toast.LENGTH_LONG).show();
            return true;
        }

        versionManager.selectVersion(target);
        setTextMinecraftVersion();
        updateViewModelVersion();
        if (viewModel != null) viewModel.refreshMods();
        binding.getRoot().post(this::performActualLaunch);
        return true;
    }

    private GameVersion findShortcutVersion(String type, String key) {
        if (key == null || key.isEmpty()) return null;
        if (InstanceShortcutManager.TYPE_INSTALLED.equals(type)) {
            for (GameVersion candidate : versionManager.getInstalledVersions()) {
                if (key.equals(candidate.packageName)) return candidate;
            }
            return null;
        }
        if (InstanceShortcutManager.TYPE_CUSTOM.equals(type)) {
            for (GameVersion candidate : versionManager.getCustomVersions()) {
                if (key.equals(candidate.directoryName)) return candidate;
            }
        }
        return null;
    }

    private void initModsSection() {
        if (versionManager == null || viewModel == null) return;

        // The quick_mods_card is the single way into the mods screen. The old "Active Mods"
        // heading with a Manage button and a list of enabled mod names was a second, weaker
        // entry point to the same destination, so it is gone; only the per-instance count on
        // the hero card remains.
        viewModel.getModsLiveData().observe(this, this::updateModsUI);
    }

    private void updateViewModelVersion() {
        if (viewModel == null) return;
        GameVersion selectedVersion = versionManager.getSelectedVersion();
        if (selectedVersion != null) {
            viewModel.setCurrentVersion(selectedVersion);
        }
    }

    private void checkResourcepack() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        new ResourcepackHandler(
                this, minecraftLauncher, executorService
        ).checkIntentForResourcepack();
    }

    private void repairNeededVersions() {
        GameVersion selectedVersion = versionManager != null ? versionManager.getSelectedVersion() : null;
        if (selectedVersion != null && selectedVersion.needsRepair) {
            VersionManager.attemptRepairLibs(this, selectedVersion);
        }
    }

    private void requestBasicPermissions() {
        requestStoragePermissionForMigration(() -> {
            if (storageMigrationManager != null) {
                startStorageMigrationService();
            }
        });
    }

    private void requestStoragePermissionForMigration(Runnable onGranted) {
        permissionsHandler.requestPermission(PermissionsHandler.PermissionType.STORAGE, new PermissionsHandler.PermissionResultCallback() {
            @Override
            public void onPermissionGranted(PermissionsHandler.PermissionType type) {
                if (type == PermissionsHandler.PermissionType.STORAGE) {
                    if (onGranted != null) onGranted.run();
                }
            }

            @Override
            public void onPermissionDenied(PermissionsHandler.PermissionType type, boolean permanentlyDenied) {
                if (type == PermissionsHandler.PermissionType.STORAGE) {
                    Toast.makeText(MainActivity.this, R.string.storage_migration_permission_denied, Toast.LENGTH_LONG).show();
                    showBlockingMigrationRetryDialog(
                            getString(R.string.storage_migration_failed_title),
                            getString(R.string.storage_migration_permission_denied)
                    );
                }
            }
        });
    }

    private void showStorageMigrationPromptIfNeeded() {
        if (postMigrationInitialized || migrationPromptShown || migrationPromptCheckInFlight || storageMigrationManager == null || isFinishing() || isDestroyed()) return;
        if (StorageMigrationService.isMigrationRunning(this)) {
            resumeStorageMigrationService();
            return;
        }
        migrationPromptCheckInFlight = true;
        storageMigrationExecutor.execute(() -> {
            boolean shouldOfferMigration = false;
            try {
                shouldOfferMigration = storageMigrationManager.shouldOfferMigration();
            } catch (Exception ignored) {
            }
            boolean finalShouldOfferMigration = shouldOfferMigration;
            runOnUiThread(() -> {
                migrationPromptCheckInFlight = false;
                if (isFinishing() || isDestroyed()) return;
                if (!finalShouldOfferMigration) {
                    initializeAfterMigrationGate();
                    return;
                }
                if (migrationPromptShown || storageMigrationManager == null) return;
                showStorageMigrationPromptDialog();
            });
        });
    }

    private void showStorageMigrationPromptDialog() {
        migrationPromptShown = true;

        CustomAlertDialog dialog = new CustomAlertDialog(this)
                .setTitleText(getString(R.string.storage_migration_title))
                .setMessage(getString(
                        R.string.storage_migration_message,
                        LauncherStorage.getTargetAppRootDisplayPath(this)
                ))
                .setPositiveButton(getString(R.string.storage_migration_start), v -> {
                    if (storageMigrationManager.canReadLegacyRoot()) {
                        startStorageMigrationService();
                    } else {
                        requestBasicPermissions();
                    }
                })
                .setNegativeButton(getString(R.string.exit), v -> finishAffinity());
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showPostEulaFlow() {
        SharedPreferences prefs = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("eula_accepted", false) || postEulaFlowRunning) return;
        if (isInstanceShortcutIntent()) {
            showStorageMigrationPromptIfNeeded();
            return;
        }
        postEulaFlowRunning = true;
        ChangelogManager.showIfNeeded(this, () -> {
            postEulaFlowRunning = false;
            showStorageMigrationPromptIfNeeded();
        });
    }

    private boolean isInstanceShortcutIntent() {
        Intent intent = getIntent();
        return intent != null && InstanceShortcutManager.ACTION_LAUNCH_INSTANCE.equals(intent.getAction());
    }

    private void startStorageMigrationService() {
        if (isFinishing()) return;
        requestNotificationPermissionForMigration();
        showStorageMigrationDialog();
        StorageMigrationService.startMigration(this);
        bindStorageMigrationService();
    }

    private void resumeStorageMigrationService() {
        if (isFinishing()) return;
        requestNotificationPermissionForMigration();
        showStorageMigrationDialog();
        StorageMigrationService.startMigration(this);
        bindStorageMigrationService();
    }

    private void requestNotificationPermissionForMigration() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationPermissionLauncher == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void bindStorageMigrationService() {
        if (storageMigrationBound) return;
        if (!StorageMigrationService.isMigrationRunning(this)) return;
        Intent intent = new Intent(this, StorageMigrationService.class);
        bindService(intent, storageMigrationConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindStorageMigrationService() {
        if (!storageMigrationBound) return;
        if (storageMigrationService != null) {
            storageMigrationService.removeListener(storageMigrationListener);
        }
        unbindService(storageMigrationConnection);
        storageMigrationBound = false;
        storageMigrationService = null;
    }

    private void showStorageMigrationDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (storageMigrationDialog != null && storageMigrationDialog.isShowing()) return;
        LibsRepairDialog dialog = new LibsRepairDialog(this);
        storageMigrationDialog = dialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(shownDialog -> {
            if (storageMigrationDialog != dialog || isFinishing() || isDestroyed()) return;
            dialog.setTitleText(getString(R.string.storage_migration_progress_title));
            dialog.setSubtitleText(getString(R.string.storage_migration_progress_subtitle));
            dialog.setStatusText(getString(R.string.storage_migration_scanning));
            dialog.setEtaText(getString(R.string.storage_migration_eta_pending));
            dialog.setBackgroundHintText(getString(R.string.storage_migration_background_hint));
            dialog.setPauseButton("", null);
            dialog.setIndeterminate(true);
            dialog.updateProgress(0);
            if (lastMigrationState != null) {
                updateStorageMigrationDialog(lastMigrationState);
            }
        });
        dialog.show();
    }

    private void handleStorageMigrationState(StorageMigrationService.MigrationState state) {
        if (state == null || isFinishing()) return;
        lastMigrationState = state;
        if (state.isActive()) {
            showStorageMigrationDialog();
            updateStorageMigrationDialog(state);
            return;
        }
        if (state.isFinished()) {
            dismissStorageMigrationDialog(() -> showStorageMigrationResult(state));
            return;
        }
    }

    private void updateStorageMigrationDialog(StorageMigrationService.MigrationState state) {
        if (storageMigrationDialog == null || !storageMigrationDialog.isShowing()) return;
        if (state.status == StorageMigrationService.Status.SCANNING) {
            storageMigrationDialog.setIndeterminate(true);
            storageMigrationDialog.setStatusText(getString(R.string.storage_migration_scanning));
            storageMigrationDialog.setEtaText(getMigrationEtaText(state));
            storageMigrationDialog.updateProgress(0);
            return;
        }
        if (state.status != StorageMigrationService.Status.RUNNING) return;
        storageMigrationDialog.setIndeterminate(false);
        String progressDetail = getString(
                R.string.storage_migration_progress_detail,
                state.processedFiles,
                state.totalFiles,
                shortenMigrationPath(state.currentFile)
        );
        storageMigrationDialog.setStatusText(progressDetail);
        storageMigrationDialog.setEtaText(getMigrationEtaText(state));
        storageMigrationDialog.updateProgress(state.percent);
    }

    private void dismissStorageMigrationDialog(Runnable afterDismiss) {
        LibsRepairDialog dialog = storageMigrationDialog;
        storageMigrationDialog = null;
        if (dialog == null) {
            if (afterDismiss != null) afterDismiss.run();
            return;
        }
        dialog.setOnDismissAnimationEndListener(afterDismiss);
        if (dialog.isShowing()) {
            dialog.dismiss();
        } else if (afterDismiss != null) {
            afterDismiss.run();
        }
    }

    private void showStorageMigrationResult(StorageMigrationService.MigrationState state) {
        if (isFinishing()) return;
        if (state.status == StorageMigrationService.Status.COMPLETED) {
            boolean wasInitialized = postMigrationInitialized;
            initializeAfterMigrationGate();
            if (wasInitialized && versionManager != null) {
                versionManager.reload();
                setTextMinecraftVersion();
                updateViewModelVersion();
            }
            if (viewModel != null) viewModel.refreshMods();
            refreshContentCounts();
            new CustomAlertDialog(MainActivity.this)
                    .setTitleText(getString(R.string.storage_migration_completed_title))
                    .setMessage(getString(
                            R.string.storage_migration_completed_message,
                            state.totalFiles,
                            formatBytes(state.totalBytes),
                            state.skippedFiles
                    ))
                    .setPositiveButton(getString(R.string.confirm), null)
                    .show();
        } else if (state.status == StorageMigrationService.Status.PARTIAL) {
            showBlockingMigrationRetryDialog(
                    getString(R.string.storage_migration_partial_title),
                    getString(
                            R.string.storage_migration_partial_message,
                            state.failedFiles,
                            state.totalFiles
                    )
            );
        } else if (state.status == StorageMigrationService.Status.FAILED) {
            showBlockingMigrationRetryDialog(
                    getString(R.string.storage_migration_failed_title),
                    getString(R.string.storage_migration_failed_message, state.errorMessage)
            );
        }
    }

    private void showBlockingMigrationRetryDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        migrationPromptShown = false;
        CustomAlertDialog dialog = new CustomAlertDialog(MainActivity.this)
                .setTitleText(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.retry), v -> showStorageMigrationPromptIfNeeded())
                .setNegativeButton(getString(R.string.exit), v -> finishAffinity());
        dialog.setCancelable(false);
        dialog.show();
    }

    private String shortenMigrationPath(String path) {
        if (path == null || path.isEmpty()) return "";
        final int max = 48;
        return path.length() <= max ? path : "..." + path.substring(path.length() - max);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb);
        return String.format(java.util.Locale.getDefault(), "%.1f GB", mb / 1024.0);
    }

    private String getMigrationEtaText(StorageMigrationService.MigrationState state) {
        if (state.estimatedRemainingMillis < 0L || state.estimatedCompletionAtMillis <= 0L) {
            return getString(R.string.storage_migration_eta_pending);
        }
        String remaining = formatMigrationDuration(state.estimatedRemainingMillis);
        String completionTime = DateFormat.getTimeInstance(DateFormat.SHORT, java.util.Locale.getDefault())
                .format(new Date(state.estimatedCompletionAtMillis));
        return getString(R.string.storage_migration_eta_detail, remaining, completionTime);
    }

    private String formatMigrationDuration(long millis) {
        long seconds = Math.max(1L, Math.round(millis / 1000.0d));
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0L) {
            return getString(R.string.storage_migration_duration_hours_minutes, hours, minutes);
        }
        if (minutes > 0L) {
            return getString(R.string.storage_migration_duration_minutes_seconds, minutes, remainingSeconds);
        }
        return getString(R.string.storage_migration_duration_seconds, remainingSeconds);
    }

    private void showEulaIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("LauncherPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("eula_accepted", false)) {
            showEulaDialog();
        }
    }

    private void showEulaDialog() {
        CustomAlertDialog dia = new CustomAlertDialog(this)
                .setTitleText(getString(R.string.eula_title))
                .setMessage(getString(R.string.eula_message))
                .setUseBorderedBackground(true)
                .setBlurBackground(true)
                .setPositiveButton(getString(R.string.eula_agree), v -> {
                    getSharedPreferences("LauncherPrefs", MODE_PRIVATE)
                            .edit().putBoolean("eula_accepted", true).apply();
                    binding.getRoot().postDelayed(this::showPostEulaFlow, 220L);
                })
                .setNegativeButton(getString(R.string.eula_exit), v -> finishAffinity());
        dia.setCancelable(false);
        dia.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPersonalization();
        boolean anims = new PersonalizationManager(this).isShowAnimations();
        if (!anims) {
            stopHeroCardPulse();
        } else if (!pulseRunning) {
            startHeroCardPulse();
        }
        refreshAccountHeaderUI();
        if (StorageMigrationService.isMigrationRunning(this)) {
            resumeStorageMigrationService();
            return;
        }
        if (!postMigrationInitialized) {
            showPostEulaFlow();
            return;
        }
        if (versionManager != null) {
            setTextMinecraftVersion();
            viewModel.refreshMods();
            refreshContentCounts();
        }
        if (binding != null && versionManager != null) {
            resetLaunchButton();
        }
    }

    private void applyPersonalization() {
        PersonalizationManager pm = new PersonalizationManager(this);
        
        // Apply Animation Speed
        float animSpeed = pm.getAnimationSpeed();
        DynamicAnim.setGlobalSpeedMultiplier(animSpeed);
        
        // Apply UI Transparency
        int transparency = pm.getUiTransparency();
        if (binding != null && binding.getRoot() != null) {
            float alpha = transparency / 100f;
            binding.getRoot().setAlpha(alpha);
        }
        
        // Apply Card Rounding
        int rounding = pm.getCardRounding();
        applyCardRounding(rounding);
        
        // Apply Icon Size
        int iconSize = pm.getIconSize();
        applyIconSize(iconSize);
        
        // Apply Blur Intensity
        int blur = pm.getBlurIntensity();
        applyBlurIntensity(blur);
        
        // Apply Glow Effects
        boolean glow = pm.isEnableGlowEffects();
        applyGlowEffects(glow);
        
        // Apply Compact Mode
        boolean compact = pm.isCompactMode();
        applyCompactMode(compact);
        
        // Apply Show Animations toggle
        boolean showAnims = pm.isShowAnimations();
        if (!showAnims) {
            DynamicAnim.disableAnimations();
        } else {
            DynamicAnim.enableAnimations();
        }
    }
    
    private void applyCardRounding(int roundingDp) {
        if (binding == null) return;
        float density = getResources().getDisplayMetrics().density;
        float radiusPx = roundingDp * density;
        
        // Apply to launch button
        if (binding.launchButton.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) binding.launchButton.getBackground()).setCornerRadius(radiusPx);
        }
        
        // Apply to select version button
        if (binding.selectVersionButton.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) binding.selectVersionButton.getBackground()).setCornerRadius(radiusPx);
        }
    }
    
    private void applyIconSize(int sizePercent) {
        // Icon size implementation would go here
        // For now, this is a placeholder for future icon scaling
    }
    
    private void applyBlurIntensity(int intensity) {
        // Blur intensity is not a blur: this method dims the dashboard. Painting a black
        // scrim over the whole root also darkens the wallpaper image that the user picked,
        // so the scrim is only applied while a background image is actually in use. Without
        // one there is nothing behind the cards to blur, and the scrim would just make the
        // dashboard needlessly dark.
        if (binding == null || binding.getRoot() == null) return;
        PersonalizationManager pm = new PersonalizationManager(this);
        if (!pm.hasBackgroundImage()) {
            // Leave the layout's own background alone rather than painting a scrim
            // over a dashboard that has nothing behind it.
            return;
        }
        float dimAmount = intensity / 200f; // 0.0 to 0.5
        binding.getRoot().setBackgroundColor(Color.argb((int) (dimAmount * 255), 0, 0, 0));
    }

    private void applyGlowEffects(boolean enabled) {
        float glowElevation = enabled ? dp(8f) : dp(0f);
        float cardElevation = enabled ? dp(3f) : dp(0f);

        if (binding != null) {
            if (binding.launchButton != null) binding.launchButton.setElevation(glowElevation);
            if (binding.premiumLaunchCard != null) binding.premiumLaunchCard.setElevation(cardElevation);
        }

        int[] cardIds = {
                R.id.last_played_card,
                R.id.quick_versions_card,
                R.id.quick_mods_card,
                R.id.quick_content_card
        };
        for (int id : cardIds) {
            View card = findViewById(id);
            if (card != null) {
                card.setElevation(enabled ? dp(4f) : dp(0f));
                if (enabled && card.getBackground() instanceof GradientDrawable && card.getId() == R.id.last_played_card) {
                    GradientDrawable bg = (GradientDrawable) card.getBackground().mutate();
                    int accent = new PersonalizationManager(this).getAccentColor();
                    bg.setStroke((int) dp(1.5f), accent != 0 ? accent : getColor(R.color.grad_pulse_glow));
                }
            }
        }
    }
    
    private void applyCompactMode(boolean enabled) {
        if (binding == null) return;
        
        if (enabled) {
            // Reduce padding/margins for compact mode
            int smallPadding = (int)(8 * getResources().getDisplayMetrics().density);
            if (binding.getRoot() instanceof ViewGroup) {
                ((ViewGroup) binding.getRoot()).setPadding(smallPadding, smallPadding, smallPadding, smallPadding);
            }
        } else {
            // Restore normal padding
            int normalPadding = (int)(16 * getResources().getDisplayMetrics().density);
            if (binding.getRoot() instanceof ViewGroup) {
                ((ViewGroup) binding.getRoot()).setPadding(normalPadding, normalPadding, normalPadding, normalPadding);
            }
        }
    }

    private void startHeroCardPulse() {
        View heroCard = findViewById(R.id.last_played_card);
        if (heroCard == null) return;
        PersonalizationManager pm = new PersonalizationManager(this);
        if (!pm.isShowAnimations()) return;
        if (pulseRunning) return;
        // Cancel any stale per-card touches (spring shenanigans) so the pulse owns the card.
        heroCard.animate().cancel();
        pulseRunning = true;
        int glowColor = pm.isDarkMode(this)
                ? getColor(R.color.grad_pulse_glow)
                : pm.getAccentColor() != 0 ? pm.getAccentColor() : getColor(R.color.grad_pulse_glow);
        pulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (!pulseRunning) return;
                // Subtle glow + lift pulse on the hero card, then settle back.
                heroCard.animate().cancel();
                heroCard.animate()
                        .translationZ(dp(6f))
                        .alpha(0.94f)
                        .setDuration(700L)
                        .setInterpolator(DynamicAnim.getDefaultInterpolator())
                        .start();
                heroCard.postDelayed(() -> {
                    if (!pulseRunning) return;
                    heroCard.animate()
                            .translationZ(dp(0f))
                            .alpha(1f)
                            .setDuration(900L)
                            .setInterpolator(DynamicAnim.getDefaultInterpolator())
                            .withLayer()
                            .start();
                }, 700L);
                uiHandler.postDelayed(this, 2400L);
            }
        };
        // Straightforward glow ring via a highlight drawable when glow effects are on.
        if (pm.isEnableGlowEffects() && heroCard.getBackground() instanceof GradientDrawable) {
            GradientDrawable base = (GradientDrawable) heroCard.getBackground().mutate();
            base.setStroke((int) dp(1.5f), glowColor);
        }
        uiHandler.post(pulseRunnable);
    }

    private void stopHeroCardPulse() {
        pulseRunning = false;
        if (pulseRunnable != null) {
            uiHandler.removeCallbacks(pulseRunnable);
            pulseRunnable = null;
        }
        View heroCard = findViewById(R.id.last_played_card);
        if (heroCard != null) {
            heroCard.animate().cancel();
            heroCard.animate().translationZ(0f).alpha(1f).setDuration(200L).start();
        }
    }

    /**
     * Smoothly advances the launch progress bar (with a subtle glow on the thumb),
     * instead of snapping between values. The bar is revealed on the first update
     * and hidden again once any file import finishes.
     */
    private void animateProgressTo(int target) {
        if (binding == null || binding.progressLoader == null) return;
        if (activeProgressAnimator != null) {
            activeProgressAnimator.cancel();
        }
        if (binding.progressLoader.getVisibility() != View.VISIBLE) {
            binding.progressLoader.setIndeterminate(false);
            binding.progressLoader.setProgress(0);
            binding.progressLoader.setVisibility(View.VISIBLE);
        }
        PersonalizationManager pm = new PersonalizationManager(this);
        int from = binding.progressLoader.getProgress();
        int duration = pm.isShowAnimations() ? 350 : 0;
        if (duration == 0) {
            binding.progressLoader.setProgress(target);
            return;
        }
        activeProgressAnimator = ValueAnimator.ofInt(from, target).setDuration(duration);
        activeProgressAnimator.setInterpolator(DynamicAnim.getDefaultInterpolator());
        activeProgressAnimator.addUpdateListener(animator ->
                binding.progressLoader.setProgress((Integer) animator.getAnimatedValue()));
        activeProgressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                activeProgressAnimator = null;
            }
        });
        activeProgressAnimator.start();
    }

    private void hideProgressLoader() {
        if (binding == null || binding.progressLoader == null) return;
        if (activeProgressAnimator != null) {
            activeProgressAnimator.cancel();
            activeProgressAnimator = null;
        }
        binding.progressLoader.setProgress(0);
        binding.progressLoader.setVisibility(View.GONE);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onStop() {
        stopHeroCardPulse();
        if (activeProgressAnimator != null) {
            activeProgressAnimator.cancel();
            activeProgressAnimator = null;
        }
        unbindStorageMigrationService();
        super.onStop();
    }






    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionsHandler != null) {
            permissionsHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "UnsafeIntentLaunch"})
    private void initListeners() {
        binding.launchButton.setEnabled(false);
        binding.launchButton.setOnClickListener(v -> launchGame());
        DynamicAnim.applyPressScale(binding.launchButton);
        binding.selectVersionButton.setOnClickListener(v -> showVersionSelectDialog());
        DynamicAnim.applyPressScale(binding.selectVersionButton);

        // Setup Quick Actions Grid
        setupQuickActions();

        // Setup Account Header
        setupAccountHeader();

        // Setup News Section
        setupNewsSection();

        FeatureSettings.init(getApplicationContext());
        showRandomTip();
    }

    private void setupQuickActions() {
        // Last Played Hero Card
        View lastPlayedCard = findViewById(R.id.last_played_card);
        if (lastPlayedCard != null) {
            lastPlayedCard.setOnClickListener(v -> launchGame());
            DynamicAnim.applyPressScale(lastPlayedCard);
        }

        // Refresh hero card stats
        refreshLastPlayedCard();
        startHeroCardPulse();

        // Quick Launch Card was removed: the hero card and PLAY MINECRAFT already launch.
        // Quick Versions Card
        View quickVersionsCard = findViewById(R.id.quick_versions_card);
        if (quickVersionsCard != null) {
            quickVersionsCard.setOnClickListener(v -> showVersionSelectDialog());
            DynamicAnim.applyPressScale(quickVersionsCard);
        }

        // Quick Mods Card
        View quickModsCard = findViewById(R.id.quick_mods_card);
        if (quickModsCard != null) {
            quickModsCard.setOnClickListener(v -> openModsFullscreen());
            DynamicAnim.applyPressScale(quickModsCard);
        }

        // Quick Content Card
        View quickContentCard = findViewById(R.id.quick_content_card);
        if (quickContentCard != null) {
            quickContentCard.setOnClickListener(v -> openContentManagement());
            DynamicAnim.applyPressScale(quickContentCard);
        }
    }

    private void setupAccountHeader() {
        Button headerSignInButton = findViewById(R.id.header_sign_in_button);
        if (headerSignInButton != null) {
            headerSignInButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, MsftLoginActivity.class);
                accountLoginLauncher.launch(intent);
            });
            DynamicAnim.applyPressScale(headerSignInButton);
        }

        TextView headerAccountName = findViewById(R.id.header_account_name);
        com.microsoft.xbox.idp.toolkit.CircleImageView headerAvatar = findViewById(R.id.header_account_avatar);
        
        // Update header account info when account changes
        refreshAccountHeaderUI();
    }

    private void setupNewsSection() {
        TextView newsViewAll = findViewById(R.id.news_view_all);
        if (newsViewAll != null) {
            newsViewAll.setOnClickListener(v -> openNewsFull());
            DynamicAnim.applyPressScale(newsViewAll);
        }

        androidx.recyclerview.widget.RecyclerView newsRecycler = findViewById(R.id.news_recycler);
        if (newsRecycler != null) {
            newsRecycler.setLayoutManager(new LinearLayoutManager(this));
            newsRecycler.setHasFixedSize(true);
            // TODO: Set up news adapter
        }
    }

    private void openNewsFull() {
        Intent intent = new Intent(this, NewsActivity.class);
        startActivity(intent);
    }

    private void showRandomTip() {
        String[] tips = getResources().getStringArray(R.array.launcher_tips);
        if (tips.length == 0 || binding.tipText == null) return;
        binding.tipText.setText(tips[new java.util.Random().nextInt(tips.length)]);
        android.os.Handler handler = new android.os.Handler(getMainLooper());
        Runnable rotateTip = new Runnable() {
            @Override
            public void run() {
                if (binding == null || binding.tipText == null) return;
                binding.tipText.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    if (binding == null || binding.tipText == null) return;
                    binding.tipText.setText(tips[new java.util.Random().nextInt(tips.length)]);
                    binding.tipText.animate().alpha(1f).setDuration(300).start();
                }).start();
                handler.postDelayed(this, 8000);
            }
        };
        handler.postDelayed(rotateTip, 8000);
    }

    private void initContentManagementSection() {
        worldsCountText = binding.contentWorldsCount;
        resourcePacksCountText = binding.contentResourcePacksCount;
        behaviorPacksCountText = binding.contentBehaviorPacksCount;

        contentManager = ContentManager.getInstance(this);
        contentManager.getWorldsLiveData().observe(this, worlds -> {
            if (worldsCountText != null)
                worldsCountText.setText(String.valueOf(worlds != null ? worlds.size() : 0));
        });
        contentManager.getResourcePacksLiveData().observe(this, packs -> {
            if (resourcePacksCountText != null)
                resourcePacksCountText.setText(String.valueOf(packs != null ? packs.size() : 0));
        });
        contentManager.getBehaviorPacksLiveData().observe(this, packs -> {
            if (behaviorPacksCountText != null)
                behaviorPacksCountText.setText(String.valueOf(packs != null ? packs.size() : 0));
        });

        binding.contentViewAll.setOnClickListener(v -> openContentManagement());
        DynamicAnim.applyPressScale(binding.contentViewAll);

        binding.contentWorldsRow.setOnClickListener(v -> openContentList(ContentListActivity.TYPE_WORLDS));
        binding.contentResourcePacksRow.setOnClickListener(v -> openContentList(ContentListActivity.TYPE_RESOURCE_PACKS));
        binding.contentBehaviorPacksRow.setOnClickListener(v -> openContentList(ContentListActivity.TYPE_BEHAVIOR_PACKS));
        DynamicAnim.applyPressScale(binding.contentWorldsRow);
        DynamicAnim.applyPressScale(binding.contentResourcePacksRow);
        DynamicAnim.applyPressScale(binding.contentBehaviorPacksRow);

        refreshContentCounts();
    }

    private void refreshContentCounts() {
        if (versionManager == null || contentManager == null) return;
        GameVersion currentVersion = versionManager.getSelectedVersion();
        if (currentVersion == null) return;

        android.content.SharedPreferences cmPrefs = getSharedPreferences("content_management", MODE_PRIVATE);
        String savedType = cmPrefs.getString("storage_type", "INTERNAL");
        org.chimeramc.client.settings.FeatureSettings.StorageType storageType;
        try {
            storageType = org.chimeramc.client.settings.FeatureSettings.StorageType.valueOf(savedType);
        } catch (IllegalArgumentException e) {
            storageType = org.chimeramc.client.settings.FeatureSettings.StorageType.INTERNAL;
        }
        storageType = LauncherStorage.normalizeContentStorageType(
                storageType,
                currentVersion.versionIsolation
        );

        java.io.File baseDir = LauncherStorage.getContentGameDataDir(
                this,
                currentVersion.getStorageProfileId(),
                storageType
        );

        contentManager.setStorageDirectories(
                new java.io.File(baseDir, "minecraftWorlds"),
                new java.io.File(baseDir, "resource_packs"),
                new java.io.File(baseDir, "behavior_packs"),
                new java.io.File(baseDir, "skin_packs"),
                new java.io.File(baseDir, "Screenshots"),
                new java.io.File(baseDir, "minecraftpe"));
    }

    private void openContentList(int contentType) {
        GameVersion currentVersion = versionManager != null ? versionManager.getSelectedVersion() : null;
        if (currentVersion == null) {
            Toast.makeText(this, getString(R.string.not_found_version), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ContentListActivity.class);
        intent.putExtra(ContentListActivity.EXTRA_CONTENT_TYPE, contentType);
        startActivity(intent);
    }

    private void initMiscellaneousSection() {
        binding.miscCurseforgeRow.setOnClickListener(v -> startActivity(new Intent(this, CurseForgeActivity.class)));
        binding.miscModHubRow.setOnClickListener(v -> startActivity(new Intent(this, ModHubActivity.class)));
        binding.miscAccountsRow.setOnClickListener(v -> startActivity(new Intent(this, AccountsActivity.class)));
        binding.miscQuickLaunchRow.setOnClickListener(v -> startActivity(new Intent(this, QuickLaunchActivity.class)));
        DynamicAnim.applyPressScale(binding.miscCurseforgeRow);
        DynamicAnim.applyPressScale(binding.miscModHubRow);
        DynamicAnim.applyPressScale(binding.miscAccountsRow);
        DynamicAnim.applyPressScale(binding.miscQuickLaunchRow);
    }

    private void openModsFullscreen() {
        Intent intent = new Intent(this, ModsFullscreenActivity.class);
        startActivity(intent);
    }

    private void launchGame() {
        if (!isVersionManagerReady()) return;
        performActualLaunch();
    }

    /** Clears the in-flight flag and re-enables the Play button after a launch is abandoned. */
    private void resetLaunchButton() {
        launchInProgress = false;
        if (binding != null) binding.launchButton.setEnabled(true);
    }

    private void performActualLaunch() {
        // Both the hero card and the Play button call this. Only the button was disabled, so
        // tapping the hero card while a launch was already in flight started a second session —
        // the "duplicate Play" report. One flag guards every entry point.
        if (launchInProgress) return;
        launchInProgress = true;
        binding.launchButton.setEnabled(false);
        LaunchTrace trace = LaunchTrace.create(null);
        trace.milestone("Launch requested");

        GameVersion version = versionManager != null ? versionManager.getSelectedVersion() : null;

        if (version == null) {
            trace.warning("Launch cancelled", "No version selected");
            resetLaunchButton();
            new CustomAlertDialog(this)
                    .setTitleText(getString(R.string.dialog_title_no_version))
                    .setMessage(getString(R.string.dialog_message_no_version))
                    .setPositiveButton(getString(R.string.dialog_positive_ok), null)
                    .show();
            return;
        }

        if (FeatureSettings.getInstance().isLauncherManagedMcLoginEnabled()) {
            trace.mark("Checking launcher-managed login");
            MsftAccountStore.MsftAccount active = getActiveAccount();
            boolean loggedIn = active != null && active.minecraftUsername != null && !active.minecraftUsername.isEmpty();
            if (!loggedIn) {
                trace.warning("Launch cancelled", "Minecraft account is missing");
                resetLaunchButton();
                new CustomAlertDialog(this)
                        .setTitleText(getString(R.string.dialog_title_login_required))
                        .setMessage(getString(R.string.dialog_message_login_required))
                        .setPositiveButton(getString(R.string.go_to_accounts), v -> {
                            startActivity(new Intent(this, AccountsActivity.class));
                        })
                        .setNegativeButton(getString(R.string.disable_launcher_login_and_continue), null)
                        .show();
                return;
            }
        }

        if (!PlayStoreValidator.isMinecraftInstalled(this)) {
            trace.warning("Launch cancelled", "Minecraft package is not installed");
            resetLaunchButton();
            PlayStoreValidationDialog.showNotInstalledDialog(this);
            return;
        }

        trace.mark("Launch validation completed", version.directoryName + " " + version.versionCode);
        try {
            Intent launchIntent = createMinecraftLaunchIntent();
            launchIntent.putExtra(LaunchTrace.EXTRA_SESSION_ID, trace.getSessionId());
            launchIntent.putExtra(LaunchTrace.EXTRA_STARTED_ELAPSED_MS,
                    android.os.SystemClock.elapsedRealtime() - trace.elapsedMs());
            minecraftLauncher.launch(launchIntent, version, new MinecraftLauncher.LaunchCallback() {
                @Override
                public void onLaunchStarted() {
                    trace.milestone("Loading screen requested");
                }

                @Override
                public void onLaunchFailed(Exception e) {
                    trace.error("Launch failed before loading screen", e.getMessage());
                    runOnUiThread(() -> {
                        resetLaunchButton();
                    });
                }
            });
        } catch (Exception e) {
            trace.error("Launch failed before activity start", e.getMessage());
            resetLaunchButton();
            new CustomAlertDialog(this)
                    .setTitleText(getString(R.string.dialog_title_launch_failed))
                    .setMessage(getString(R.string.dialog_message_launch_failed, e.getMessage()))
                    .setPositiveButton(getString(R.string.dialog_positive_ok), null)
                    .show();
        }
    }

    private Intent createMinecraftLaunchIntent() {
        Intent launchIntent = new Intent();
        Intent sourceIntent = getIntent();
        if (sourceIntent == null) return launchIntent;

        if (sourceIntent.hasExtra("MINECRAFT_URI")) {
            launchIntent.putExtra("MINECRAFT_URI", sourceIntent.getStringExtra("MINECRAFT_URI"));
        }
        if (sourceIntent.hasExtra("MINECRAFT_URI_ACTION")) {
            launchIntent.putExtra("MINECRAFT_URI_ACTION", sourceIntent.getStringExtra("MINECRAFT_URI_ACTION"));
        }
        return launchIntent;
    }

     private void showVersionSelectDialog() {
        if (!isVersionManagerReady()) return;

        List<GameVersion> allVersions = new ArrayList<>();
        List<GameVersion> installed = versionManager.getInstalledVersions();
        List<GameVersion> custom = versionManager.getCustomVersions();
        if (installed != null) allVersions.addAll(installed);
        if (custom != null) allVersions.addAll(custom);

        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_instance_selector, null);
        new PersonalizationManager(this).applyAccentToView(popupView, this);
        PopupWindow popup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);

        RecyclerView recycler = popupView.findViewById(R.id.recycler_instances);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        GameVersion selectedVersion = versionManager.getSelectedVersion();
        InstancePopupAdapter adapter = new InstancePopupAdapter(allVersions, selectedVersion);
        recycler.setAdapter(adapter);

        android.widget.EditText searchInput = popupView.findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        adapter.setOnItemClickListener(version -> {
            versionManager.selectVersion(version);
            viewModel.setCurrentVersion(version);
            setTextMinecraftVersion();
            popup.dismiss();
        });

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = popupView.getMeasuredWidth();
        int anchorWidth = binding.selectVersionButton.getWidth();
        int xOffset = anchorWidth - popupWidth;
        popup.showAsDropDown(binding.selectVersionButton, xOffset, 4);
    }

    private static class InstancePopupAdapter extends RecyclerView.Adapter<InstancePopupAdapter.VH> {
        private final List<GameVersion> allVersions;
        private List<GameVersion> filteredVersions;
        private final GameVersion selectedVersion;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onClick(GameVersion version);
        }

        void setOnItemClickListener(OnItemClickListener l) { this.listener = l; }

        InstancePopupAdapter(List<GameVersion> versions, GameVersion selected) {
            this.allVersions = versions;
            this.filteredVersions = new ArrayList<>(versions);
            this.selectedVersion = selected;
        }

        void filter(String query) {
            if (query == null || query.isEmpty()) {
                filteredVersions = new ArrayList<>(allVersions);
            } else {
                String q = query.toLowerCase();
                filteredVersions = new ArrayList<>();
                for (GameVersion v : allVersions) {
                    String name = getInstanceDisplayName(v).toLowerCase();
                    String code = v.versionCode != null ? v.versionCode.toLowerCase() : "";
                    String dir = v.directoryName != null ? v.directoryName.toLowerCase() : "";
                    if (name.contains(q) || code.contains(q) || dir.contains(q)) {
                        filteredVersions.add(v);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_instance_popup, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            GameVersion v = filteredVersions.get(position);
            boolean isSelected = selectedVersion != null
                    && selectedVersion.directoryName != null
                    && selectedVersion.directoryName.equals(v.directoryName);

            holder.name.setText(getInstanceDisplayName(v));
            String versionText = getInstanceVersionText(v);
            holder.version.setText(versionText);
            holder.version.setVisibility(TextUtils.isEmpty(versionText) ? View.GONE : View.VISIBLE);
            String abi = v.abiList;
            if (abi != null && !abi.isEmpty()) {
                boolean is32Bit = "armeabi-v7a".equals(abi) || "x86".equals(abi);
                holder.abiBadge.setText(holder.itemView.getContext().getString(
                        is32Bit ? R.string.abi_32_bit : R.string.abi_64_bit));
                holder.abiBadge.setTextColor(is32Bit ? 0xFFF6A821 : 0xFF6236E8);
                holder.abiBadge.setVisibility(View.VISIBLE);
            } else {
                holder.abiBadge.setVisibility(View.GONE);
            }
            holder.itemView.setActivated(isSelected);
            holder.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.tag.setVisibility(View.GONE);
            applyInstanceSelectionStyle(holder, isSelected);

            holder.itemView.setOnClickListener(_v -> {
                if (listener != null) listener.onClick(v);
            });
        }

        private static void applyInstanceSelectionStyle(@NonNull VH holder, boolean isSelected) {
            android.content.Context context = holder.itemView.getContext();
            PersonalizationManager pm = new PersonalizationManager(context);
            int accent = pm.getAccentColor();
            if (accent == 0) {
                accent = ContextCompat.getColor(context, R.color.primary);
            }

            float density = context.getResources().getDisplayMetrics().density;
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(10 * density);
            if (isSelected) {
                background.setColor(Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)));
                background.setStroke(Math.max(1, (int) (1 * density)), accent);
            } else {
                background.setColor(Color.TRANSPARENT);
                background.setStroke(0, Color.TRANSPARENT);
            }
            holder.itemView.setBackground(background);
            holder.check.setImageTintList(ColorStateList.valueOf(accent));
        }

        @Override public int getItemCount() { return filteredVersions.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView name, version, tag;
            TextView abiBadge;
            ImageView check;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.instance_name);
                version = v.findViewById(R.id.instance_version);
                tag = v.findViewById(R.id.instance_tag);
                abiBadge = v.findViewById(R.id.instance_abi_badge);
                check = v.findViewById(R.id.instance_check);
            }
        }
    }

    private void startFilePicker(String type, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        launcher.launch(intent);
    }

    private void startApkFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/vnd.android.package-archive",
                "application/octet-stream",
                "application/zip",
                "application/xapk",
                "application/x-xapk"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        apkImportResultLauncher.launch(intent);
    }

    private void openContentManagement() {
        GameVersion currentVersion = versionManager != null ? versionManager.getSelectedVersion() : null;
        if (currentVersion == null) {
            Toast.makeText(this, getString(R.string.not_found_version), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ContentManagementActivity.class);
        startActivity(intent);
    }


    public void setTextMinecraftVersion() {
        if (binding == null) return;
        if (versionManager == null) {
            binding.textMinecraftVersion.setText(getString(R.string.not_found_version));
            updateLastPlayedName(null, null);
            return;
        }
        GameVersion selectedVersion = versionManager.getSelectedVersion();
        String instanceName = selectedVersion != null ? getInstanceDisplayName(selectedVersion) : null;
        binding.textMinecraftVersion.setText(TextUtils.isEmpty(instanceName) ? getString(R.string.not_found_version) : instanceName);
        updateLastPlayedName(instanceName, selectedVersion);
        refreshLastPlayedCard();
    }

    private void refreshLastPlayedCard() {
        GameVersion selectedVersion = versionManager != null ? versionManager.getSelectedVersion() : null;
        int installedCount =0;
        if (versionManager != null) {
            List<org.chimeramc.client.core.versions.GameVersion> installed = versionManager.getInstalledVersions();
            List<org.chimeramc.client.core.versions.GameVersion> custom = versionManager.getCustomVersions();
            installedCount = (installed != null ? installed.size() : 0) + (custom != null ? custom.size() : 0);
        }
        TextView instancesStat = findViewById(R.id.last_played_instances_stat);
        if (instancesStat != null) {
            // Count up from zero rather than appearing at the final value: the hero card is the
            // first thing drawn after the splash, so the numbers should settle, not blink in.
            final int count = installedCount;
            DynamicAnim.countUp(count, 400L,
                    value -> setAnimatedStatText(instancesStat,
                            getString(R.string.stat_instances_count, (int) value)));
        }
        // The mods count comes from the live mod list, not from a count captured here: the list
        // loads asynchronously per instance, so reading it at bind time would report the previous
        // instance (usually zero). updateModsUI recomputes it whenever the list changes.
        if (viewModel != null) {
            refreshActiveModsStat(viewModel.getModsLiveData().getValue());
        }
        TextView playtimeStat = findViewById(R.id.last_played_time_stat);
        if (playtimeStat != null) {
            long totalPlaytime = selectedVersion != null
                    ? PlaytimeManager.getTotalMs(MinecraftLauncher.getStorageProfileId(selectedVersion))
                    : 0L;
            playtimeStat.setText(PlaytimeManager.formatPlaytime(totalPlaytime));
        }
        updateLastPlayedName(
                selectedVersion != null ? getInstanceDisplayName(selectedVersion) : null,
                selectedVersion
        );
    }

    private void setAnimatedStatText(TextView view, String text) {
        if (view == null) return;
        PersonalizationManager pm = new PersonalizationManager(this);
        if (!pm.isShowAnimations()) {
            view.setText(text);
            return;
        }
        String current = view.getText().toString();
        if (current.equals(text)) {
            return;
        }
        view.setText(text);
        view.setScaleX(0.6f);
        view.setScaleY(0.6f);
        view.setAlpha(0.3f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(320L)
                .setInterpolator(DynamicAnim.getDefaultInterpolator())
                .withLayer()
                .start();
    }

    private void updateLastPlayedName(String instanceName, GameVersion selectedVersion) {
        TextView heroName = findViewById(R.id.last_played_name);
        String name = TextUtils.isEmpty(instanceName) ? getString(R.string.not_found_version) : instanceName;
        if (heroName != null) {
            heroName.setText(name);
        }
        TextView heroVersion = findViewById(R.id.last_played_version);
        if (heroVersion != null) {
            String versionText = selectedVersion != null ? getInstanceVersionText(selectedVersion) : "";
            // Instances are commonly named after their version, so the same "26.51" would appear
            // twice. The name is the identity; only show the version line when it adds anything.
            if (versionText != null && versionText.equals(name)) {
                versionText = "";
            }
            heroVersion.setText(versionText);
            heroVersion.setVisibility(TextUtils.isEmpty(versionText) ? View.GONE : View.VISIBLE);
        }
        TextView abiBadge = findViewById(R.id.last_played_abi_badge);
        if (abiBadge != null) {
            String abi = selectedVersion != null ? selectedVersion.abiList : null;
            if (TextUtils.isEmpty(abi)) {
                abiBadge.setVisibility(View.GONE);
            } else {
                boolean is32Bit = "armeabi-v7a".equals(abi) || "x86".equals(abi);
                abiBadge.setText(is32Bit ? getString(R.string.abi_32_bit) : getString(R.string.abi_64_bit));
                abiBadge.setTextColor(is32Bit ? 0xFFF6A821 : 0xFF6236E8);
                abiBadge.setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean isVersionManagerReady() {
        if (versionManager != null) return true;
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        return false;
    }

    private static String getInstanceDisplayName(GameVersion version) {
        if (version == null) return "";

        String displayName = stripVersionSuffix(version.displayName, version.versionCode);
        if (!TextUtils.isEmpty(displayName)) return displayName;
        if (!TextUtils.isEmpty(version.directoryName)) return version.directoryName;
        return !TextUtils.isEmpty(version.versionCode) ? version.versionCode : "";
    }

    private static String getInstanceVersionText(GameVersion version) {
        if (version == null) return "";
        String base = !TextUtils.isEmpty(version.versionCode) ? version.versionCode
                : !TextUtils.isEmpty(version.directoryName) ? version.directoryName : "";
        String abi = version.abiList;
        if (!TextUtils.isEmpty(abi) && ("armeabi-v7a".equals(abi) || "x86".equals(abi))) {
            return base + " (" + abi + ")";
        }
        return base;
    }

    private static String stripVersionSuffix(String displayName, String versionCode) {
        if (TextUtils.isEmpty(displayName)) return "";

        String trimmedName = displayName.trim();
        if (TextUtils.isEmpty(versionCode)) return trimmedName;

        String suffix = " (" + versionCode + ")";
        if (trimmedName.endsWith(suffix)) {
            return trimmedName.substring(0, trimmedName.length() - suffix.length()).trim();
        }
        return trimmedName;
    }

    private void handleIncomingFiles() {
        if (fileHandler == null) return;
        Intent intent = getIntent();
        if (MinecraftImportIntents.isMinecraftResourceIntent(this, intent)) {
            return;
        }
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            if ("minecraft".equals(data.getScheme())) {
                return;
            }
        }
        fileHandler.processIncomingFilesWithConfirmation(intent, new FileHandler.FileOperationCallback() {
            @Override
            public void onSuccess(int processedFiles) {
                hideProgressLoader();
                if (processedFiles > 0)
                    UIHelper.showToast(MainActivity.this, getString(R.string.files_processed, processedFiles));
            }

            @Override
            public void onError(String errorMessage) {
                hideProgressLoader();
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    UIHelper.showToast(MainActivity.this, errorMessage);
                }
            }

            @Override
            public void onProgressUpdate(int progress) {
                if (binding != null) animateProgressTo(progress);
            }
        }, false);
    }

    private boolean forwardIncomingMinecraftResourceToRunningGame() {
        Intent intent = getIntent();
        if (!MinecraftImportIntents.isMinecraftResourceIntent(this, intent)) {
            return false;
        }
        if (!MinecraftImportIntents.forwardToRunningMinecraft(this, intent)) {
            return false;
        }

        clearIncomingIntent();
        return true;
    }

    private void clearIncomingIntent() {
        Intent cleanIntent = new Intent(this, MainActivity.class);
        setIntent(cleanIntent);
    }

    private void handleMinecraftUriLaunch() {
        Intent intent = getIntent();
        if (intent == null) return;
        if (intent.getBooleanExtra("LAUNCH_WITH_URI", false)) {
            intent.removeExtra("LAUNCH_WITH_URI");
            setIntent(intent);
            binding.getRoot().post(this::launchGame);
        }
    }

    private void updateModsUI(List<Mod> mods) {
        if (binding == null) return;

        // The per-instance "N active" stat is derived from this same list. Mod discovery runs
        // off the UI thread and the version is bound after the live data is observed, so the
        // count read at bind time is usually the pre-load value (zero). Recompute it here so
        // the hero card reflects the instance that is actually selected.
        refreshActiveModsStat(mods);

    }

    private void refreshActiveModsStat(List<Mod> mods) {
        TextView modsStat = findViewById(R.id.last_played_mods_stat);
        if (modsStat == null) return;
        int count = Mod.countEnabled(mods);
        DynamicAnim.countUp(count, 400L,
                value -> setAnimatedStatText(modsStat, getString(R.string.stat_mods_count, (int) value)));
    }

    private void setupNavBar() {
        setActiveNavTab(R.id.nav_tab_launch);
        findViewById(R.id.nav_tab_launch).setOnClickListener(v -> {});
    }

    @Override
    protected void onDestroy() {
        unbindStorageMigrationService();
        dismissStorageMigrationDialog(null);
        storageMigrationExecutor.shutdownNow();
        super.onDestroy();
    }

 }
