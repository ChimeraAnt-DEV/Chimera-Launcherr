package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.chimeramc.client.core.voice.VoiceChatModule;
import org.chimeramc.client.core.voice.VoiceRegistry;

import java.util.List;

/**
 * Proximity voice status pill: how many players are in range, which channel you are on, and
 * whether your microphone is live.
 *
 * <p>Deliberately a readout, not a control surface. Channel switching lives in the module's
 * config dialog, where it persists; this pill exists so the player can tell at a glance that the
 * feature is on and who can hear them. It never draws a peer's position — the launcher cannot
 * see other players' coordinates from the game, and inventing a direction would be a lie.
 *
 * <p>Draggable in HUD-editor mode like the other overlays.
 */
public final class VoiceChatOverlay {
    private static final int REFRESH_MS = 250;
    private static final float DRAG_THRESHOLD = 10f;

    private final Activity activity;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private WindowManager.LayoutParams wmParams;
    private boolean isShowing;
    private boolean isHudEditorMode;
    private boolean isLocked;

    private float initialX, initialY, initialTouchX, initialTouchY;
    private boolean isDragging;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
            if (overlayView != null) overlayView.invalidate();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public VoiceChatOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    public void show(int startX, int startY) {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        VoiceChatView view = new VoiceChatView(activity);
        try {
            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wmParams.gravity = Gravity.TOP | Gravity.START;
            OverlayBounds.Position position = OverlayBounds.clampPosition(activity, view, startX, startY);
            wmParams.x = position.x;
            wmParams.y = position.y;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            view.setOnTouchListener(this::handleTouch);
            windowManager.addView(view, wmParams);
            overlayView = view;
        } catch (Exception e) {
            wmParams = null;
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.START;
            OverlayBounds.Position position = OverlayBounds.clampPosition(activity, view, startX, startY);
            params.leftMargin = position.x;
            params.topMargin = position.y;
            view.setOnTouchListener(this::handleTouchFallback);
            root.addView(view, params);
            overlayView = view;
        }
        isShowing = true;
        isLocked = InbuiltModManager.getInstance(activity).isOverlayLocked(ModIds.VOICE_CHAT);
        handler.post(refreshRunnable);
    }

    public void hide() {
        if (!isShowing) return;
        isShowing = false;
        handler.removeCallbacks(refreshRunnable);
        try {
            if (wmParams != null && windowManager != null && overlayView != null) {
                windowManager.removeView(overlayView);
            } else if (overlayView != null) {
                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root != null) root.removeView(overlayView);
            }
        } catch (Exception ignored) {
        }
        overlayView = null;
        wmParams = null;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void applyConfigurationChanges() {
        isLocked = InbuiltModManager.getInstance(activity).isOverlayLocked(ModIds.VOICE_CHAT);
        if (overlayView != null) overlayView.invalidate();
    }

    public void setHudEditorMode(boolean active) {
        isHudEditorMode = active;
        if (overlayView != null) overlayView.invalidate();
    }

    public void setOverlayVisibility(int visibility) {
        if (overlayView != null && overlayView.getVisibility() != visibility) {
            overlayView.setVisibility(visibility);
        }
    }

    private boolean handleTouch(View view, MotionEvent event) {
        if (isLocked || !isHudEditorMode) return false;
        return drag(view, event, true);
    }

    private boolean handleTouchFallback(View view, MotionEvent event) {
        if (isLocked || !isHudEditorMode) return false;
        return drag(view, event, false);
    }

    private boolean drag(View view, MotionEvent event, boolean windowMode) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initialX = windowMode ? wmParams.x : view.getLeft();
                initialY = windowMode ? wmParams.y : view.getTop();
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                isDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - initialTouchX;
                float dy = event.getRawY() - initialTouchY;
                if (!isDragging && Math.hypot(dx, dy) < DRAG_THRESHOLD) return true;
                isDragging = true;
                int newX = (int) (initialX + dx);
                int newY = (int) (initialY + dy);
                if (windowMode && wmParams != null) {
                    wmParams.x = newX;
                    wmParams.y = newY;
                    try {
                        windowManager.updateViewLayout(view, wmParams);
                    } catch (Exception ignored) {
                    }
                } else {
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    if (params instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) params).leftMargin = newX;
                        ((FrameLayout.LayoutParams) params).topMargin = newY;
                        view.setLayoutParams(params);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    InbuiltModManager.getInstance(activity).setOverlayPosition(
                            ModIds.VOICE_CHAT, windowMode ? wmParams.x : view.getLeft(),
                            windowMode ? wmParams.y : view.getTop());
                }
                isDragging = false;
                return true;
            default:
                return false;
        }
    }

    private final class VoiceChatView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        VoiceChatView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            text.setTextAlign(Paint.Align.LEFT);
            text.setFakeBoldText(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            VoiceChatModule module = VoiceChatModule.peek();
            String status;
            int color;
            if (module == null || !module.isRunning()) {
                status = activity.getString(R.string.voice_chat_status_off);
                color = 0xFF8F979F;
            } else {
                List<VoiceRegistry.Audible> audible = module.audibleNow();
                String channel = InbuiltModManager.getInstance(activity).getVoiceChannel();
                String mic = module.isTransmitting()
                        ? activity.getString(R.string.voice_chat_mic_on)
                        : activity.getString(R.string.voice_chat_mic_off);
                if (module.isChannelMode()) {
                    status = activity.getString(R.string.voice_chat_status, audible.size(), channel, mic);
                } else {
                    status = activity.getString(R.string.voice_chat_status, audible.size(), channel, mic);
                }
                color = audible.isEmpty() ? 0xFFA8B0B8 : 0xFF6BD68A;
            }

            float pad = 8f * density;
            text.setTextSize(11f * density);
            float textWidth = text.measureText(status);
            float boxHeight = 20f * density;
            fill.setColor(0x99000000);
            canvas.drawRoundRect(pad, pad, pad + textWidth + pad * 2, pad + boxHeight,
                    4f * density, 4f * density, fill);
            text.setColor(color);
            canvas.drawText(status, pad * 2, pad + boxHeight * 0.68f, text);
        }
    }
}
