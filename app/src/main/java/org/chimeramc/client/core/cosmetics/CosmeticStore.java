package org.chimeramc.client.core.cosmetics;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistence for the equipped cosmetic selection.
 *
 * SharedPreferences is right here rather than the settings backup: the selection is small,
 * changes on every tap, and has nothing to do with instance launch. The keys are plain
 * strings, not package paths, so a rebrand does not strand a player's chosen cape.
 */
public final class CosmeticStore {

    private static final String PREFS_NAME = "cosmetics_state";
    private static final String KEY_CAPE = "equipped_cape";
    private static final String KEY_ACCESSORY = "equipped_accessory";
    private static final String KEY_PET = "equipped_pet";

    private final SharedPreferences prefs;

    public CosmeticStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The equipped cape id, or {@link CosmeticCatalog#NONE}. */
    public String getEquippedCapeId() {
        return prefs.getString(KEY_CAPE, CosmeticCatalog.NONE);
    }

    public CosmeticCatalog.Cape getEquippedCape() {
        return CosmeticCatalog.equippedCape(getEquippedCapeId());
    }

    public void setEquippedCape(String capeId) {
        prefs.edit().putString(KEY_CAPE, capeId == null ? CosmeticCatalog.NONE : capeId).apply();
    }

    public String getEquippedAccessoryId() {
        return prefs.getString(KEY_ACCESSORY, CosmeticCatalog.NONE);
    }

    public CosmeticCatalog.Accessory getEquippedAccessory() {
        return CosmeticCatalog.equippedAccessory(getEquippedAccessoryId());
    }

    public void setEquippedAccessory(String accessoryId) {
        prefs.edit().putString(KEY_ACCESSORY,
                accessoryId == null ? CosmeticCatalog.NONE : accessoryId).apply();
    }

    public String getEquippedPetId() {
        return prefs.getString(KEY_PET, CosmeticCatalog.NONE);
    }

    public CosmeticCatalog.Pet getEquippedPet() {
        return CosmeticCatalog.equippedPet(getEquippedPetId());
    }

    public void setEquippedPet(String petId) {
        prefs.edit().putString(KEY_PET, petId == null ? CosmeticCatalog.NONE : petId).apply();
    }
}
