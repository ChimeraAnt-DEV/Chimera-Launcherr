package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.UnifiedMod;
import org.chimeramc.client.core.mods.inbuilt.model.ModAvailability;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ModMenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_GROUP = 0;
    private static final int VIEW_TYPE_MOD = 1;
    private static final int VIEW_TYPE_MOD_COMPACT = 2;

    private final List<MenuItem> items = new ArrayList<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private final Map<String, Boolean> favoriteStates = new HashMap<>();
    private OnModActionListener listener;
    private boolean compactMode;
    private ModMenuTheme theme;
    /** The index a controller has moved the selection to, or NONE. -1 because position 0 is valid. */
    private int controllerFocus = ModMenuNavigation.NONE;
    private RecyclerView recycler;

    public ModMenuAdapter(ModMenuTheme theme) {
        this.theme = theme != null ? theme : new ModMenuTheme(null);
        setHasStableIds(true);
    }

    public interface OnModActionListener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod);
        void onFavoriteChanged(UnifiedMod mod, boolean favorite);
    }

    public void setOnModActionListener(OnModActionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recycler = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recycler = null;
    }

    /** Columns per row, so vertical moves step by row exactly as the grid lays out. */
    private int columnCount() {
        if (recycler != null && recycler.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
            return ((androidx.recyclerview.widget.GridLayoutManager) recycler.getLayoutManager()).getSpanCount();
        }
        return compactMode ? 1 : 4;
    }

    /** One flag per item: group headers cannot be selected, module cards can. */
    private boolean[] selectableFlags() {
        boolean[] flags = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            flags[i] = !item.isGroup() && item.mod != null && ModAvailability.isInteractive(item.mod.getId());
        }
        return flags;
    }

    /** One flag per item: a group header spans its own row, so the layout has to say so. */
    private boolean[] fullRowFlags() {
        boolean[] flags = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            flags[i] = items.get(i).isGroup();
        }
        return flags;
    }

    /**
     * Moves the controller selection by one directional press.
     *
     * <p>Returns true when the press was consumed, so the caller can swallow it and the stick does
     * not also reach the game. The move is computed by {@link ModMenuNavigation}, which is pure and
     * tested; this method only applies the result to the list.
     */
    public boolean handleControllerNavigation(ModMenuNavigation.Direction direction) {
        if (items.isEmpty()) return false;
        int target = ModMenuNavigation.move(controllerFocus, columnCount(), fullRowFlags(),
                selectableFlags(), direction);
        if (target == ModMenuNavigation.NONE || target == controllerFocus) {
            // Nothing to move to: still consumed, so a press at the edge does not make the stick
            // act on the game behind the menu.
            return controllerFocus != ModMenuNavigation.NONE;
        }
        int previous = controllerFocus;
        controllerFocus = target;
        if (previous >= 0) notifyItemChanged(previous);
        notifyItemChanged(target);
        if (recycler != null) recycler.smoothScrollToPosition(target);
        return true;
    }

    /**
     * Activates the focused card: a module toggles (or, when unavailable, says why), a card with a
     * gear opens its settings.
     *
     * <p>The same rules the touch path uses, so a controller cannot switch on a module the touch
     * path refuses.
     */
    public boolean handleControllerSelect() {
        if (controllerFocus < 0 || controllerFocus >= items.size()) return false;
        MenuItem item = items.get(controllerFocus);
        if (item.isGroup() || item.mod == null) return false;
        if (!ModAvailability.isInteractive(item.mod.getId())) return true;
        if (listener != null) listener.onToggle(item.mod, !toggleStates.getOrDefault(item.mod.getStableKey(), false));
        return true;
    }

    /** Clears the controller selection, e.g. when the menu closes. */
    public void clearControllerFocus() {
        if (controllerFocus == ModMenuNavigation.NONE) return;
        int previous = controllerFocus;
        controllerFocus = ModMenuNavigation.NONE;
        if (previous >= 0 && previous < items.size()) notifyItemChanged(previous);
    }

    public void setCompactMode(boolean compactMode) {
        if (this.compactMode == compactMode) return;
        this.compactMode = compactMode;
        items.clear();
        toggleStates.clear();
        favoriteStates.clear();
        notifyDataSetChanged();
    }

    public void updateMods(List<UnifiedMod> mods, Set<String> favoriteKeys) {
        List<MenuItem> nextItems = new ArrayList<>();
        Map<String, Boolean> nextToggleStates = new HashMap<>();
        Map<String, Boolean> nextFavoriteStates = new HashMap<>();
        String lastGroupId = null;
        for (UnifiedMod mod : mods) {
            boolean firstInGroup = !Objects.equals(mod.getGroupId(), lastGroupId);
            if (!compactMode && firstInGroup) {
                nextItems.add(MenuItem.group(mod.getGroupId(), mod.getGroupName()));
            }
            boolean showGroupLabel = compactMode
                && firstInGroup
                && mod.getSource() == UnifiedMod.Source.EXTERNAL;
            nextItems.add(MenuItem.mod(mod, showGroupLabel));
            lastGroupId = mod.getGroupId();
            nextToggleStates.put(mod.getStableKey(), mod.isEnabled());
            nextFavoriteStates.put(mod.getStableKey(),
                favoriteKeys != null && favoriteKeys.contains(mod.getStableKey()));
        }

        List<MenuItem> oldItems = new ArrayList<>(items);
        Map<String, Boolean> oldToggleStates = new HashMap<>(toggleStates);
        Map<String, Boolean> oldFavoriteStates = new HashMap<>(favoriteStates);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return nextItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).stableKey()
                    .equals(nextItems.get(newItemPosition).stableKey());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                MenuItem oldItem = oldItems.get(oldItemPosition);
                MenuItem newItem = nextItems.get(newItemPosition);
                if (oldItem.isGroup() || newItem.isGroup()) {
                    return oldItem.isGroup() == newItem.isGroup()
                        && Objects.equals(oldItem.groupName, newItem.groupName);
                }
                String oldKey = oldItem.mod.getStableKey();
                String newKey = newItem.mod.getStableKey();
                return Objects.equals(oldItem.mod.getName(), newItem.mod.getName())
                    && Objects.equals(oldItem.mod.getDescription(), newItem.mod.getDescription())
                    && oldItem.mod.hasConfig() == newItem.mod.hasConfig()
                    && oldItem.showGroupLabel == newItem.showGroupLabel
                    && oldToggleStates.getOrDefault(oldKey, false)
                        .equals(nextToggleStates.getOrDefault(newKey, false))
                    && oldFavoriteStates.getOrDefault(oldKey, false)
                        .equals(nextFavoriteStates.getOrDefault(newKey, false));
            }
        }, false);

        items.clear();
        items.addAll(nextItems);
        toggleStates.clear();
        toggleStates.putAll(nextToggleStates);
        favoriteStates.clear();
        favoriteStates.putAll(nextFavoriteStates);
        diff.dispatchUpdatesTo(this);
        // A filter change or a refresh renumbers every position, so the controller selection would
        // point at a different card than the one that was highlighted. Drop it rather than let the
        // next A press toggle whatever moved under the index.
        controllerFocus = ModMenuNavigation.NONE;
    }

    public boolean isGroupHeader(int position) {
        return position >= 0 && position < items.size() && items.get(position).isGroup();
    }

    public void setTheme(ModMenuTheme theme) {
        this.theme = theme != null ? theme : new ModMenuTheme(null);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GROUP) {
            float density = parent.getResources().getDisplayMetrics().density;
            android.widget.LinearLayout row = new android.widget.LinearLayout(parent.getContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (38 * density));
            params.setMargins((int) (8 * density), (int) (8 * density),
                (int) (8 * density), 0);
            row.setLayoutParams(params);

            View bar = new View(parent.getContext());
            android.widget.LinearLayout.LayoutParams barParams =
                new android.widget.LinearLayout.LayoutParams((int) (3 * density), (int) (16 * density));
            barParams.setMarginEnd((int) (8 * density));
            bar.setLayoutParams(barParams);
            row.addView(bar);

            TextView title = new TextView(parent.getContext());
            title.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setTextSize(11);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title);

            return new GroupViewHolder(row, bar, title);
        }

        int layout = viewType == VIEW_TYPE_MOD_COMPACT
            ? R.layout.item_mod_menu_compact
            : R.layout.item_mod_menu_card;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ModViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MenuItem item = items.get(position);
        if (item.isGroup()) {
            GroupViewHolder group = (GroupViewHolder) holder;
            group.title.setText(item.groupName);
            int color = theme.groupColor(item.groupId);
            group.title.setTextColor(color);
            GradientDrawable barBg = new GradientDrawable();
            barBg.setShape(GradientDrawable.RECTANGLE);
            barBg.setCornerRadius(2f * group.bar.getResources().getDisplayMetrics().density);
            barBg.setColor(color);
            group.bar.setBackground(barBg);
            return;
        }

        ModViewHolder modHolder = (ModViewHolder) holder;
        UnifiedMod mod = item.mod;

        modHolder.name.setText(mod.getName());
        if (modHolder.groupText != null) {
            modHolder.groupText.setVisibility(item.showGroupLabel ? View.VISIBLE : View.GONE);
            if (item.showGroupLabel) {
                modHolder.groupText.setText(mod.getGroupName());
            }
        }

        if (mod.getSource() == UnifiedMod.Source.INBUILT) {
            modHolder.icon.setImageResource(ModIconHelper.getModIcon(mod.getId()));
            modHolder.icon.setImageTintList(null);
            modHolder.icon.setColorFilter(null);
        } else {
            modHolder.icon.setImageResource(R.drawable.ic_glowberry_mod);
            modHolder.icon.setImageTintList(null);
            modHolder.icon.setColorFilter(null);
        }

        boolean isEnabled = toggleStates.getOrDefault(mod.getStableKey(), false);
        boolean available = ModAvailability.isInteractive(mod.getId());
        updateStatusView(modHolder, isEnabled, available);
        updateAccentBar(modHolder, mod.getGroupId(), isEnabled);
        updateFavoriteView(modHolder, favoriteStates.getOrDefault(mod.getStableKey(), false));
        updateUnavailableView(modHolder, mod);

        View.OnClickListener toggleClick = v -> {
            // A module that cannot do its job must not look toggleable. The label says why, and
            // the click is dropped instead of flipping a switch that changes nothing on screen.
            if (!available) {
                if (modHolder.unavailableText != null) {
                    android.widget.Toast.makeText(modHolder.itemView.getContext(),
                            R.string.mod_unavailable_reason, android.widget.Toast.LENGTH_SHORT).show();
                }
                return;
            }
            boolean newState = !toggleStates.getOrDefault(mod.getStableKey(), false);
            toggleStates.put(mod.getStableKey(), newState);
            updateStatusView(modHolder, newState, true);
            updateAccentBar(modHolder, mod.getGroupId(), newState);
            // Per-icon behaviour on flip, not a generic bounce: Armor HUD "equips", the Voice mic
            // pulses. Only modules with distinct iconography animate; the rest fall through.
            org.chimeramc.client.ui.animation.ModToggleAnim.play(
                    modHolder.icon, mod.getId(), newState);
            if (listener != null) {
                listener.onToggle(mod, newState);
            }
        };

        modHolder.itemView.setOnClickListener(toggleClick);
        modHolder.statusText.setOnClickListener(toggleClick);
        modHolder.icon.setOnClickListener(toggleClick);
        DynamicAnim.applyPressScale(modHolder.itemView);

        modHolder.favoriteBtn.setOnClickListener(v -> {
            boolean favorite = !favoriteStates.getOrDefault(mod.getStableKey(), false);
            favoriteStates.put(mod.getStableKey(), favorite);
            updateFavoriteView(modHolder, favorite);
            if (listener != null) {
                listener.onFavoriteChanged(mod, favorite);
            }
        });

        if (mod.hasConfig() && available) {
            modHolder.configBtn.setVisibility(View.VISIBLE);
            modHolder.configBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConfig(mod);
                }
            });
        } else {
            modHolder.configBtn.setVisibility(View.GONE);
        }

        updateCardState(modHolder, isEnabled, available);
        applyControllerFocus(modHolder, position);
    }

    /**
     * Dresses the card a controller has selected with an accent ring.
     *
     * <p>This is why the overlay root is unfocusable: the platform's own focus highlight is the
     * white wash that made the menu look broken, so the selection is drawn here instead, in the
     * theme accent, on exactly one card.
     */
    private void applyControllerFocus(ModViewHolder holder, int position) {
        boolean focused = position == controllerFocus;
        float density = holder.itemView.getResources().getDisplayMetrics().density;
        if (focused) {
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            ring.setCornerRadius(14f * density);
            ring.setColor(0x00000000);
            ring.setStroke(Math.max(2, (int) (2f * density)), theme.accent());
            holder.itemView.setForeground(ring);
            holder.itemView.setAlpha(1f);
            holder.itemView.setTranslationZ(6f * density);
        } else {
            holder.itemView.setForeground(null);
            holder.itemView.setTranslationZ(0f);
        }
    }

    /**
     * Shows or hides the "doesn't work or is bugged" outline.
     *
     * <p>A module whose game feed has no provider is not merely off — it cannot be turned on. The
     * label is the whole point: without it the card looks like every other disabled module and the
     * player assumes their toggle is broken.
     */
    private void updateUnavailableView(ModViewHolder holder, UnifiedMod mod) {
        if (holder.unavailableText == null) return;
        boolean unavailable = ModAvailability.isUnavailable(mod.getId());
        holder.unavailableText.setVisibility(unavailable ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position).isGroup()) return VIEW_TYPE_GROUP;
        return compactMode ? VIEW_TYPE_MOD_COMPACT : VIEW_TYPE_MOD;
    }

    private void updateFavoriteView(ModViewHolder holder, boolean favorite) {
        int color = favorite ? 0xFFFFC107 : 0xFFA8B0B8;
        holder.favoriteBtn.setImageTintList(ColorStateList.valueOf(color));
        holder.favoriteBtn.setAlpha(favorite ? 1f : 0.9f);
        holder.favoriteBtn.setContentDescription(holder.favoriteBtn.getContext().getString(
            favorite ? R.string.mod_menu_unfavorite : R.string.mod_menu_favorite));
    }

    private void updateStatusView(ModViewHolder holder, boolean enabled,
                                  boolean available) {
        float density = holder.statusText.getResources().getDisplayMetrics().density;
        if (enabled) {
            holder.statusText.setText(R.string.mod_status_enabled);
            holder.statusText.setTextColor(theme.accent());
            GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(12f * density);
            pill.setColor(theme.accentFill(46));
            pill.setStroke((int) density, theme.accent());
            holder.statusText.setBackground(pill);
        } else {
            holder.statusText.setText(R.string.mod_status_disabled);
            holder.statusText.setTextColor(0xFFB4BBC3);
            holder.statusText.setBackgroundResource(R.drawable.bg_mod_status_disabled);
        }
        updateCardState(holder, enabled, available);
    }

    /** Paints the top edge strip so each card carries its section colour. */
    private void updateAccentBar(ModViewHolder holder, String groupId, boolean enabled) {
        if (holder.accentBar == null) return;
        int color = theme.groupColor(groupId);
        GradientDrawable bar = new GradientDrawable();
        bar.setShape(GradientDrawable.RECTANGLE);
        bar.setColor(enabled ? color : (color & 0x00FFFFFF) | 0x40000000);
        holder.accentBar.setBackground(bar);
    }

    private void updateCardState(ModViewHolder holder, boolean enabled, boolean available) {
        // Unavailable wins over the enabled/disabled tint: a greyed card is the whole signal.
        holder.itemView.setAlpha(available ? 1f : 0.55f);
        holder.itemView.setEnabled(available);
        holder.itemView.setClickable(available);
        holder.statusText.setEnabled(available);
        holder.icon.setAlpha(!available ? 0.5f : (enabled ? 1f : 0.8f));
        holder.name.setTextColor(!available ? 0xFF8892A0 : (enabled ? 0xFFF1F4F6 : 0xFFD6DCE2));
        
        if (holder.itemView instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView cv = (androidx.cardview.widget.CardView) holder.itemView;
            
            if (!available) {
                cv.setCardBackgroundColor(0xFF1B1E22);
                cv.setCardElevation(0f);
            } else if (enabled) {
                cv.setCardBackgroundColor(theme.enabledCardColor());
                cv.setCardElevation(theme.enabledElevation());
            } else {
                cv.setCardBackgroundColor(theme.disabledCardColor());
                cv.setCardElevation(theme.disabledElevation());
            }
        }
    }


    @Override
    public long getItemId(int position) {
        String key = items.get(position).stableKey();
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static class MenuItem {
        final String groupId;
        final String groupName;
        final UnifiedMod mod;
        final boolean showGroupLabel;

        private MenuItem(String groupId, String groupName, UnifiedMod mod, boolean showGroupLabel) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.mod = mod;
            this.showGroupLabel = showGroupLabel;
        }

        static MenuItem group(String groupId, String groupName) {
            return new MenuItem(groupId, groupName, null, false);
        }

        static MenuItem mod(UnifiedMod mod, boolean showGroupLabel) {
            return new MenuItem(null, null, mod, showGroupLabel);
        }

        boolean isGroup() {
            return mod == null;
        }

        String stableKey() {
            return isGroup() ? "group:" + groupId : "mod:" + mod.getStableKey();
        }
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        final View bar;
        final TextView title;

        GroupViewHolder(View itemView, View bar, TextView title) {
            super(itemView);
            this.bar = bar;
            this.title = title;
        }
    }

    static class ModViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        TextView statusText;
        TextView groupText;
        ImageButton favoriteBtn;
        ImageButton configBtn;
        TextView unavailableText;
        View accentBar;

        ModViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.mod_card_icon);
            name = itemView.findViewById(R.id.mod_card_name);
            statusText = itemView.findViewById(R.id.mod_card_status);
            groupText = itemView.findViewById(R.id.mod_card_group);
            favoriteBtn = itemView.findViewById(R.id.mod_card_favorite);
            configBtn = itemView.findViewById(R.id.mod_card_config);
            unavailableText = itemView.findViewById(R.id.mod_card_unavailable);
            accentBar = itemView.findViewById(R.id.mod_card_accent);
        }
    }
}
