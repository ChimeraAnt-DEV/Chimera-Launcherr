package org.chimeramc.client.core.mods.inbuilt.vip;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.client.R;
import org.chimeramc.client.core.mods.inbuilt.UnifiedMod;
import org.chimeramc.client.core.mods.inbuilt.model.ModAvailability;
import org.chimeramc.client.core.mods.inbuilt.overlay.ModMenuNavigation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The VIP module grid.
 *
 * <p>Separate from {@code ModMenuAdapter} on purpose: the touch menu's cards are dense and
 * utilitarian, while this is the premium screen, so the card carries an icon tile, a group-
 * coloured top strip and a status pill. The navigation maths is not duplicated — this reuses
 * {@link ModMenuNavigation}, so a controller steps the VIP grid and the touch grid identically and
 * there is one tested layout rule rather than two.
 */
public class VipModuleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_GROUP = 0;
    private static final int TYPE_MOD = 1;

    private final List<Item> items = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, Boolean> favorite = new HashMap<>();
    private final VipTheme theme;
    private Listener listener;
    private int focus = ModMenuNavigation.NONE;
    private RecyclerView recycler;

    public interface Listener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod);
    }

    public VipModuleAdapter(VipTheme theme) {
        this.theme = theme;
        setHasStableIds(true);
    }

    public void setListener(Listener listener) {
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

    public void submit(List<UnifiedMod> mods, Set<String> favorites) {
        List<Item> next = new ArrayList<>();
        Map<String, Boolean> nextEnabled = new HashMap<>();
        Map<String, Boolean> nextFavorite = new HashMap<>();
        String lastGroup = "\u0000";
        for (UnifiedMod mod : mods) {
            if (!Objects.equals(mod.getGroupId(), lastGroup)) {
                next.add(Item.group(mod.getGroupId(), mod.getGroupName()));
                lastGroup = mod.getGroupId();
            }
            next.add(Item.mod(mod));
            nextEnabled.put(mod.getStableKey(), mod.isEnabled());
            nextFavorite.put(mod.getStableKey(),
                    favorites != null && favorites.contains(mod.getStableKey()));
        }

        List<Item> old = new ArrayList<>(items);
        Map<String, Boolean> oldEnabled = new HashMap<>(enabled);
        Map<String, Boolean> oldFavorite = new HashMap<>(favorite);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return old.get(oldPos).key().equals(next.get(newPos).key());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Item a = old.get(oldPos);
                Item b = next.get(newPos);
                if (a.isGroup() || b.isGroup()) return a.isGroup() == b.isGroup();
                String k = b.mod.getStableKey();
                return Objects.equals(oldEnabled.get(k), nextEnabled.get(k))
                        && Objects.equals(oldFavorite.get(k), nextFavorite.get(k))
                        && a.mod.isEnabled() == b.mod.isEnabled();
            }
        });

        items.clear();
        items.addAll(next);
        enabled.clear();
        enabled.putAll(nextEnabled);
        favorite.clear();
        favorite.putAll(nextFavorite);
        diff.dispatchUpdatesTo(this);
    }

    public void clear() {
        items.clear();
        enabled.clear();
        favorite.clear();
        focus = ModMenuNavigation.NONE;
        notifyDataSetChanged();
    }

    public int itemCount() {
        return items.size();
    }

    /** True when the item at a position is a full-row group header. */
    public boolean isGroupAt(int position) {
        return position >= 0 && position < items.size() && items.get(position).isGroup();
    }

    /** Repaints every card after a toggle mutates a module's enabled state. */
    public void refreshStates() {
        notifyDataSetChanged();
    }

    public int getFocus() {
        return focus;
    }

    public void clearFocus() {
        int previous = focus;
        focus = ModMenuNavigation.NONE;
        if (previous >= 0 && previous < items.size()) notifyItemChanged(previous);
    }

    private int columns() {
        if (recycler != null
                && recycler.getLayoutManager() instanceof GridLayoutManager) {
            return ((GridLayoutManager) recycler.getLayoutManager()).getSpanCount();
        }
        return 3;
    }

    private boolean[] selectableFlags() {
        boolean[] flags = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            flags[i] = !item.isGroup() && item.mod != null
                    && ModAvailability.isInteractive(item.mod.getId());
        }
        return flags;
    }

    private boolean[] fullRowFlags() {
        boolean[] flags = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) flags[i] = items.get(i).isGroup();
        return flags;
    }

    /** Moves the selection; true when the press was consumed. */
    public boolean move(ModMenuNavigation.Direction direction) {
        if (items.isEmpty()) return false;
        int target = ModMenuNavigation.move(focus, columns(), fullRowFlags(),
                selectableFlags(), direction);
        if (target == ModMenuNavigation.NONE || target == focus) {
            return focus != ModMenuNavigation.NONE;
        }
        int previous = focus;
        focus = target;
        if (previous >= 0) notifyItemChanged(previous);
        notifyItemChanged(target);
        if (recycler != null) recycler.smoothScrollToPosition(target);
        return true;
    }

    /** Activates the focused card: module toggles, gear opens settings. */
    public boolean select() {
        if (focus < 0 || focus >= items.size()) return false;
        Item item = items.get(focus);
        if (item.isGroup() || item.mod == null) return false;
        if (!ModAvailability.isInteractive(item.mod.getId())) return true;
        if (listener != null) {
            listener.onToggle(item.mod, !enabled.getOrDefault(item.mod.getStableKey(), false));
        }
        return true;
    }

    /** Opens the focused card's settings; true when it has a settings surface. */
    public boolean openConfig() {
        if (focus < 0 || focus >= items.size()) return false;
        Item item = items.get(focus);
        if (item.isGroup() || item.mod == null || !item.mod.hasConfig()) return false;
        if (listener != null) listener.onConfig(item.mod);
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isGroup() ? TYPE_GROUP : TYPE_MOD;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        String key = items.get(position).key();
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            View view = inflater.inflate(R.layout.item_vip_group_header, parent, false);
            return new GroupHolder(view);
        }
        View view = inflater.inflate(R.layout.item_vip_module_card, parent, false);
        return new ModHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = items.get(position);
        if (holder instanceof GroupHolder) {
            GroupHolder group = (GroupHolder) holder;
            group.title.setText(item.groupName == null ? "" : item.groupName);
            // A fresh GradientDrawable rather than a cast of the XML background: the layout sets a
            // raw colour, which inflates as a ColorDrawable, so the old cast threw on the first bind.
            GradientDrawable bar = new GradientDrawable();
            bar.setShape(GradientDrawable.RECTANGLE);
            bar.setCornerRadius(1.5f * group.bar.getResources().getDisplayMetrics().density);
            bar.setColor(theme.groupColor(item.groupId));
            group.bar.setBackground(bar);
            return;
        }
        ModHolder modHolder = (ModHolder) holder;
        UnifiedMod mod = item.mod;
        try {
            bindMod(modHolder, item, mod, position);
        } catch (Throwable t) {
            // One card that cannot bind must not take the whole grid -- and therefore the running
            // game session -- down with it. The grid is opened over a live game, so a RecyclerView
            // bind that throws propagates to the main looper and kills the process. Degrade to a
            // safe placeholder for that single card and keep the menu usable.
            showBrokenCard(modHolder, mod);
        }
    }

    /** Fills one module card; any failure here is caught by {@link #onBindViewHolder}. */
    private void bindMod(ModHolder modHolder, Item item, UnifiedMod mod, int position) {
        boolean isEnabled = enabled.getOrDefault(mod.getStableKey(), false);
        boolean isAvailable = ModAvailability.isInteractive(mod.getId());

        modHolder.name.setText(mod.getName());
        if (item.showGroup && mod.getGroupName() != null) {
            modHolder.group.setVisibility(View.VISIBLE);
            modHolder.group.setText(mod.getGroupName());
        } else {
            modHolder.group.setVisibility(View.GONE);
        }
        modHolder.unavailable.setVisibility(isAvailable ? View.GONE : View.VISIBLE);
        modHolder.icon.setImageResource(iconFor(mod));
        bindStatus(modHolder, isEnabled, isAvailable);
        bindAccent(modHolder, mod.getGroupId(), isEnabled, isAvailable);

        boolean focused = position == focus;
        applyFocus(modHolder, focused);

        modHolder.itemView.setAlpha(isAvailable ? 1f : 0.6f);
        modHolder.itemView.setEnabled(isAvailable);
        modHolder.itemView.setClickable(isAvailable);

        modHolder.itemView.setOnClickListener(v -> {
            if (!ModAvailability.isInteractive(mod.getId())) return;
            if (listener != null) {
                listener.onToggle(mod, !enabled.getOrDefault(mod.getStableKey(), false));
            }
        });
        modHolder.config.setVisibility(mod.hasConfig() ? View.VISIBLE : View.GONE);
        modHolder.config.setOnClickListener(v -> {
            if (listener != null) listener.onConfig(mod);
        });

        boolean canConfig = mod.hasConfig();
        modHolder.itemView.setOnLongClickListener(v -> {
            if (!canConfig) return false;
            if (listener != null) listener.onConfig(mod);
            return true;
        });
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof ModHolder) {
            ((ModHolder) holder).itemView.setForeground(null);
        }
    }

    /**
     * A minimal, non-interactive card used when a module's normal bind threw.
     *
     * <p>It is deliberately the simplest possible paint -- no drawables, no theme lookups, no
     * listeners -- so it cannot itself fail. The card stays visible (the module is discoverable)
     * but cannot be toggled, so a card whose metadata is broken cannot take the grid down again.
     */
    private void showBrokenCard(ModHolder holder, UnifiedMod mod) {
        try {
            holder.name.setText(mod != null && mod.getName() != null ? mod.getName() : "");
            holder.group.setVisibility(View.GONE);
            holder.unavailable.setVisibility(View.VISIBLE);
            holder.status.setVisibility(View.GONE);
            holder.config.setVisibility(View.GONE);
            holder.itemView.setForeground(null);
            holder.itemView.setTranslationZ(0f);
            holder.itemView.setAlpha(0.6f);
            holder.itemView.setEnabled(false);
            holder.itemView.setClickable(false);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(null);
        } catch (Throwable ignored) {
            // Even the placeholder must not throw; there is nothing further to degrade to.
        }
    }

    private void bindStatus(ModHolder holder, boolean isEnabled, boolean available) {
        if (!available) {
            holder.status.setText(R.string.vip_status_unavailable);
            holder.status.setTextColor(theme.statusUnavailable());
            holder.status.setBackgroundResource(R.drawable.bg_vip_status_unavailable);
        } else if (isEnabled) {
            holder.status.setText(R.string.vip_status_active);
            holder.status.setTextColor(theme.statusActive());
            holder.status.setBackgroundResource(R.drawable.bg_vip_status_on);
        } else {
            holder.status.setText(R.string.vip_status_off);
            holder.status.setTextColor(theme.statusOff());
            holder.status.setBackgroundResource(R.drawable.bg_vip_status_off);
        }
    }

    /**
     * Paints the card surface and its top strip.
     *
     * <p>An enabled card is lifted and tinted toward the accent; the strip carries the section
     * colour so a column reads as grouped at a glance. Elevation is zero when the user turned glow
     * effects off, matching every other card in the app.
     */
    private void bindAccent(ModHolder holder, String groupId, boolean isEnabled, boolean isAvailable) {
        CardView card = (CardView) holder.itemView;
        // The strip's fill is replaced with a GradientDrawable we own rather than casting
        // whatever background the XML happened to set: a raw colour in the layout is a
        // ColorDrawable, and the old cast threw ClassCastException on the first bind.
        // Availability is passed in rather than read from itemView.isEnabled(), which is only set
        // after this call -- a recycled card would otherwise paint with the previous item's state.
        int stripColor;
        if (!isAvailable) {
            card.setCardBackgroundColor(0xFF1A1729);
            card.setCardElevation(0f);
            stripColor = 0x33FFFFFF;
        } else {
            if (isEnabled) {
                card.setCardBackgroundColor(theme.enabledCardColor());
                card.setCardElevation(theme.enabledElevation());
            } else {
                card.setCardBackgroundColor(theme.disabledCardColor());
                card.setCardElevation(theme.disabledElevation());
            }
            int color = theme.groupColor(groupId);
            stripColor = isEnabled ? color : VipTheme.withAlpha(color, 0x55);
        }
        GradientDrawable strip = new GradientDrawable();
        strip.setShape(GradientDrawable.RECTANGLE);
        strip.setColor(stripColor);
        holder.accent.setBackground(strip);
    }

    /** Accent ring + lift on the controller-selected card; the platform highlight stays disabled. */
    private void applyFocus(ModHolder holder, boolean focused) {
        float density = holder.itemView.getResources().getDisplayMetrics().density;
        if (focused) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.RECTANGLE);
            ring.setCornerRadius(16f * density);
            ring.setColor(0x00000000);
            ring.setStroke(Math.max(2, (int) (2f * density)), theme.accent());
            holder.itemView.setForeground(ring);
            holder.itemView.setTranslationZ(8f * density);
        } else {
            holder.itemView.setForeground(null);
            holder.itemView.setTranslationZ(0f);
        }
    }

    /** A per-group glyph, so the grid is not fifteen identical squares. */
    private int iconFor(UnifiedMod mod) {
        String id = mod.getId() == null ? "" : mod.getId().toLowerCase(java.util.Locale.US);
        if (id.contains("voice")) return R.drawable.ic_nav_voice;
        if (id.contains("fps")) return R.drawable.ic_vip_fps;
        if (id.contains("cps")) return R.drawable.ic_vip_cps;
        if (id.contains("zoom")) return R.drawable.ic_vip_zoom;
        if (id.contains("gyro") || id.contains("aim") || id.contains("snaplook")) {
            return R.drawable.ic_crosshair;
        }
        if (id.contains("hitbox") || id.contains("hit")) return R.drawable.ic_vip_target;
        if (id.contains("armor")) return R.drawable.ic_vip_armor;
        if (id.contains("crystal")) return R.drawable.ic_vip_crystal;
        if (id.contains("sprint")) return R.drawable.ic_vip_sprint;
        if (id.contains("hotbar")) return R.drawable.ic_vip_hotbar;
        if (mod.getSource() == UnifiedMod.Source.EXTERNAL) return R.drawable.ic_plugin;
        return R.drawable.ic_modules;
    }

    private static final class Item {
        final String groupId;
        final String groupName;
        final UnifiedMod mod;
        final boolean showGroup;

        private Item(String groupId, String groupName, UnifiedMod mod, boolean showGroup) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.mod = mod;
            this.showGroup = showGroup;
        }

        static Item group(String groupId, String groupName) {
            return new Item(groupId, groupName, null, false);
        }

        static Item mod(UnifiedMod mod) {
            return new Item(null, null, mod, true);
        }

        boolean isGroup() {
            return mod == null;
        }

        String key() {
            return isGroup() ? "group:" + groupId : "mod:" + mod.getStableKey();
        }
    }

    static class GroupHolder extends RecyclerView.ViewHolder {
        final View bar;
        final TextView title;

        GroupHolder(View itemView) {
            super(itemView);
            bar = itemView.findViewById(R.id.vip_group_bar);
            title = itemView.findViewById(R.id.vip_group_title);
        }
    }

    static class ModHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView group;
        final TextView status;
        final TextView unavailable;
        final ImageButton config;
        final View accent;

        ModHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.vip_card_icon);
            name = itemView.findViewById(R.id.vip_card_name);
            group = itemView.findViewById(R.id.vip_card_group);
            status = itemView.findViewById(R.id.vip_card_status);
            unavailable = itemView.findViewById(R.id.vip_card_unavailable);
            config = itemView.findViewById(R.id.vip_card_config);
            accent = itemView.findViewById(R.id.vip_card_accent);
        }
    }
}
