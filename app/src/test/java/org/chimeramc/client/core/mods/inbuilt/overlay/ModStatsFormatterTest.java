package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The Mod Menu stats strip formatter. Pure — the point is that "no reading" never renders as a
 * plausible number, so it is pinned here rather than only on a device.
 */
public class ModStatsFormatterTest {

    @Test
    public void anUnknownFpsIsAnEmDashNotZero() {
        assertEquals(ModStatsFormatter.NO_READING, ModStatsFormatter.fps(0));
        assertEquals(ModStatsFormatter.NO_READING, ModStatsFormatter.fps(-1));
        assertTrue(ModStatsFormatter.fps(60).startsWith("60"));
    }

    @Test
    public void anUnknownPingIsAnEmDashNotZeroMillis() {
        assertEquals(ModStatsFormatter.NO_READING, ModStatsFormatter.ping(0));
        assertTrue(ModStatsFormatter.ping(42).contains("42"));
    }

    @Test
    public void anUnknownBatteryIsAnEmDash() {
        assertEquals(ModStatsFormatter.NO_READING, ModStatsFormatter.battery(-1, false));
    }

    @Test
    public void batteryShowsPercentAndAMarkerWhileCharging() {
        assertEquals("50%", ModStatsFormatter.battery(50, false));
        assertEquals("50%+", ModStatsFormatter.battery(50, true));
    }

    @Test
    public void batteryPercentIsClampedToAHundred() {
        assertTrue(ModStatsFormatter.battery(140, false).startsWith("100"));
    }
}
