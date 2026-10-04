package org.chimeramc.client.core.minecraft;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.chimeramc.client.R;
import org.chimeramc.client.settings.FeatureSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders and wires the Bedrock Optifine Mode section of the Settings screen.
 *
 * <p>One row per item, built from {@link OptifineItemIds#ALL} so the list and the preloader's
 * item table cannot drift. Each row carries its own toggle (so a device-specific misbehaviour can
 * be isolated) and a live status line read back from the preloader.
 *
 * <p>The section is honest by construction: a status of "skipped" always comes with the
 * preloader's own reason, and there is deliberately no frame-rate claim anywhere here. The
 * controller never reports an item as active when the preloader did not.
 */
public final class OptifineSettingsController {

    private final Context context;
    private final ViewGroup container;
    private final TextView summary;
    private final Map<String, TextView> statusViews = new HashMap<>();

    public OptifineSettingsController(Context context, ViewGroup container, TextView summary) {
        this.context = context;
        this.container = container;
        this.summary = summary;
    }

    /** Builds every item row. Call once after the section is inflated. */
    public void bind() {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        statusViews.clear();

        FeatureSettings settings = FeatureSettings.getInstance();
        LayoutInflater inflater = LayoutInflater.from(context);
        String lastTier = null;
        for (String id : OptifineItemIds.ALL) {
            boolean tier2 = OptifineItemIds.isTier2(id);
            String tier = tier2 ? "2" : "1";
            if (!tier.equals(lastTier)) {
                container.addView(buildTierHeader(inflater, tier2));
                lastTier = tier;
            }

            View row = inflater.inflate(R.layout.item_optifine_toggle, container, false);
            TextView title = row.findViewById(R.id.optifine_item_title);
            TextView desc = row.findViewById(R.id.optifine_item_desc);
            TextView status = row.findViewById(R.id.optifine_item_status);
            SwitchMaterial toggle = row.findViewById(R.id.optifine_item_switch);

            title.setText(OptifineItemIds.titleRes(id));
            desc.setText(OptifineItemIds.descriptionRes(id));
            statusViews.put(id, status);

            boolean enabled = settings.isOptifineItemEnabled(id);
            toggle.setChecked(enabled);
            toggle.setOnCheckedChangeListener((button, checked) -> {
                FeatureSettings.getInstance().setOptifineItemEnabled(id, checked);
                // Re-push so the change applies to a session already running where possible.
                OptifineModeManager.apply(context);
                refreshStatus();
            });

            container.addView(row);
        }
        refreshStatus();
    }

    private View buildTierHeader(LayoutInflater inflater, boolean tier2) {
        TextView header = new TextView(context);
        header.setText(tier2 ? R.string.optifine_tier2_header : R.string.optifine_tier1_header);
        header.setTextSize(12f);
        header.setPadding(0, tier2 ? 16 : 4, 0, 2);
        header.setTextColor(context.getResources().getColor(R.color.text_secondary));
        return header;
    }

    /**
     * Re-reads the preloader's per-item report and updates the status lines and the summary.
     *
     * <p>Safe to call when the native library is absent: the report is empty and every row shows
     * "Off", which is accurate — nothing was applied.
     */
    public void refreshStatus() {
        FeatureSettings settings = FeatureSettings.getInstance();
        List<OptifineModeManager.ItemState> states = OptifineModeManager.readState();
        Map<String, OptifineModeManager.ItemState> byId = new HashMap<>();
        for (OptifineModeManager.ItemState state : states) {
            byId.put(state.id, state);
        }

        int active = 0;
        for (String id : OptifineItemIds.ALL) {
            TextView view = statusViews.get(id);
            if (view == null) {
                continue;
            }
            OptifineModeManager.ItemState state = byId.get(id);
            int status = state == null ? OptifineStatus.NOT_ATTEMPTED : state.status;
            if (OptifineStatus.isActive(status)) {
                active++;
            }
            String label = context.getString(OptifineStatus.labelRes(status));
            String detail = state == null ? "" : state.detail;
            view.setText(detail == null || detail.isEmpty() ? label : label + " — " + detail);
            view.setTextColor(context.getResources().getColor(OptifineStatus.colorRes(status)));
        }

        if (summary == null) {
            return;
        }
        if (!settings.isOptifineModeEnabled()) {
            summary.setText(R.string.optifine_summary_master_off);
        } else if (states.isEmpty()) {
            summary.setText(R.string.optifine_summary_unavailable);
        } else {
            String counts = context.getString(R.string.optifine_summary_counts, active,
                    OptifineItemIds.ALL.size());
            summary.setText(counts + " " + context.getString(R.string.optifine_summary_applies_next_launch));
        }
    }
}
