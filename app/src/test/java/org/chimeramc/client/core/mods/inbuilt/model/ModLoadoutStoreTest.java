package org.chimeramc.client.core.mods.inbuilt.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pins the loadout capture/apply and JSON rules. No mocks: the store is pure, so the round-trip
 * a running Mod Menu depends on is the one tested here.
 */
public class ModLoadoutStoreTest {

    private static List<String> ids(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    @Test
    public void captureStoresEveryIdWithItsResolvedState() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        state.put("aim_settings", true);
        state.put("fps_display", false);

        ModLoadoutStore.Loadout loadout =
                ModLoadoutStore.capture("PvP", ids("aim_settings", "fps_display", "hitbox"),
                        id -> {
                            Boolean value = state.get(id);
                            return value != null && value;
                        });

        assertEquals("PvP", loadout.name);
        assertEquals(3, loadout.size());
        assertTrue(loadout.states.get("aim_settings"));
        assertFalse(loadout.states.get("fps_display"));
        assertFalse(loadout.states.get("hitbox"));
    }

    @Test
    public void captureSkipsNullAndEmptyIds() {
        ModLoadoutStore.Loadout loadout =
                ModLoadoutStore.capture("x", ids("a", null, "", "b"), id -> true);
        assertEquals(2, loadout.size());
    }

    @Test
    public void applyOnlyTouchesTheIdsTheLoadoutNames() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        states.put("aim_settings", true);
        states.put("fps_display", false);
        ModLoadoutStore.Loadout loadout = new ModLoadoutStore.Loadout("saved", states);

        Map<String, Boolean> applied = new LinkedHashMap<>();
        ModLoadoutStore.apply(loadout, applied::put);

        assertEquals(2, applied.size());
        assertTrue(applied.get("aim_settings"));
        assertFalse(applied.get("fps_display"));
        // A module the loadout never captured (added after it was saved) is left alone.
        assertFalse(applied.containsKey("brand_new_module"));
    }

    @Test
    public void jsonRoundTripPreservesNamesAndStates() {
        List<ModLoadoutStore.Loadout> loadouts = new ArrayList<>();
        loadouts.add(ModLoadoutStore.capture("PvP", ids("aim_settings", "hit_timing"),
                id -> id.equals("aim_settings")));
        loadouts.add(ModLoadoutStore.capture("Building", ids("fps_display"),
                id -> true));

        List<ModLoadoutStore.Loadout> restored =
                ModLoadoutStore.fromJson(ModLoadoutStore.toJson(loadouts));

        assertEquals(2, restored.size());
        assertEquals("PvP", restored.get(0).name);
        assertTrue(restored.get(0).states.get("aim_settings"));
        assertFalse(restored.get(0).states.get("hit_timing"));
        assertEquals("Building", restored.get(1).name);
        assertTrue(restored.get(1).states.get("fps_display"));
    }

    @Test
    public void aNameWithQuotesAndBackslashesSurvivesTheRoundTrip() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        states.put("a", true);
        ModLoadoutStore.Loadout loadout =
                new ModLoadoutStore.Loadout("PvP \"hard\" \\ mode", states);

        List<ModLoadoutStore.Loadout> restored =
                ModLoadoutStore.fromJson(ModLoadoutStore.toJson(Arrays.asList(loadout)));

        assertEquals(1, restored.size());
        assertEquals("PvP \"hard\" \\ mode", restored.get(0).name);
        assertTrue(restored.get(0).states.get("a"));
    }

    @Test
    public void malformedJsonYieldsNothingRatherThanThrowing() {
        assertTrue(ModLoadoutStore.fromJson(null).isEmpty());
        assertTrue(ModLoadoutStore.fromJson("").isEmpty());
        assertTrue(ModLoadoutStore.fromJson("[]").isEmpty());
        assertTrue(ModLoadoutStore.fromJson("not json at all").isEmpty());
        assertTrue(ModLoadoutStore.fromJson("{\"unterminated\":").isEmpty());
    }

    @Test
    public void aLoadoutWithoutANameIsDropped() {
        assertTrue(ModLoadoutStore.fromJson("[{\"mods\":{\"a\":true}}]").isEmpty());
        assertTrue(ModLoadoutStore.fromJson("[{\"name\":\"  \",\"mods\":{}}]").isEmpty());
    }

    @Test
    public void aLoadoutWithoutAModsBlockStillParsesWithNoStates() {
        List<ModLoadoutStore.Loadout> parsed =
                ModLoadoutStore.fromJson("[{\"name\":\"Empty\"}]");
        assertEquals(1, parsed.size());
        assertEquals("Empty", parsed.get(0).name);
        assertEquals(0, parsed.get(0).size());
    }

    @Test
    public void upsertReplacesByNameAndAppendsNewOnes() {
        Map<String, Boolean> on = new LinkedHashMap<>();
        on.put("a", true);
        Map<String, Boolean> off = new LinkedHashMap<>();
        off.put("a", false);
        Map<String, Boolean> b = new LinkedHashMap<>();
        b.put("b", true);

        List<ModLoadoutStore.Loadout> loadouts = new ArrayList<>();
        loadouts = ModLoadoutStore.upsert(loadouts, new ModLoadoutStore.Loadout("PvP", on));
        loadouts = ModLoadoutStore.upsert(loadouts, new ModLoadoutStore.Loadout("PvP", off));
        assertEquals(1, loadouts.size());
        assertFalse(loadouts.get(0).states.get("a"));

        loadouts = ModLoadoutStore.upsert(loadouts, new ModLoadoutStore.Loadout("Building", b));
        assertEquals(2, loadouts.size());
        assertEquals("Building", loadouts.get(1).name);
    }

    @Test
    public void removeDropsOnlyTheNamedLoadout() {
        List<ModLoadoutStore.Loadout> loadouts = Arrays.asList(
                new ModLoadoutStore.Loadout("A", new LinkedHashMap<>()),
                new ModLoadoutStore.Loadout("B", new LinkedHashMap<>()));
        List<ModLoadoutStore.Loadout> remaining = ModLoadoutStore.remove(loadouts, "A");
        assertEquals(1, remaining.size());
        assertEquals("B", remaining.get(0).name);
    }

    @Test
    public void findReturnsTheNamedLoadoutOrNull() {
        List<ModLoadoutStore.Loadout> loadouts = Arrays.asList(
                new ModLoadoutStore.Loadout("PvP", new LinkedHashMap<>()));
        assertNotNull(ModLoadoutStore.find(loadouts, "PvP"));
        assertNull(ModLoadoutStore.find(loadouts, "Missing"));
        assertNull(ModLoadoutStore.find(null, "PvP"));
    }

    @Test
    public void matchesReportsWhetherApplyingWouldChangeAnything() {
        Map<String, Boolean> current = new LinkedHashMap<>();
        current.put("a", true);
        current.put("b", false);

        Map<String, Boolean> sameStates = new LinkedHashMap<>();
        sameStates.put("a", true);
        sameStates.put("b", false);
        ModLoadoutStore.Loadout same = new ModLoadoutStore.Loadout("same", sameStates);
        assertTrue(same.matches(current));

        Map<String, Boolean> diffStates = new LinkedHashMap<>();
        diffStates.put("a", false);
        ModLoadoutStore.Loadout different = new ModLoadoutStore.Loadout("diff", diffStates);
        assertFalse(different.matches(current));
    }

    @Test
    public void namesListsOnlyNonEmptyNames() {
        List<ModLoadoutStore.Loadout> loadouts = Arrays.asList(
                new ModLoadoutStore.Loadout("A", new LinkedHashMap<>()),
                new ModLoadoutStore.Loadout("", new LinkedHashMap<>()),
                null);
        assertEquals(Arrays.asList("A"), ModLoadoutStore.names(loadouts));
    }

    @Test
    public void aRunawayNameIsClampedRatherThanStored() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 100; i++) name.append('x');
        List<ModLoadoutStore.Loadout> parsed =
                ModLoadoutStore.fromJson("[{\"name\":\"" + name + "\",\"mods\":{}}]");
        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0).name.length() <= 40);
    }
}
