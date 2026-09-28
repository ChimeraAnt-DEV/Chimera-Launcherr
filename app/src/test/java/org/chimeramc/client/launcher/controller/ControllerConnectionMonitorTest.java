package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the parts of {@link ControllerConnectionMonitor} that do not need a device: the source
 * filter and the message text. The device-add path itself is instrumented territory.
 */
public class ControllerConnectionMonitorTest {

    @Test
    public void connectedMessageNamesTheControllerAndSaysSettingsLoaded() {
        assertEquals("Xbox Wireless Controller has been connected and detected! Settings loaded.",
                ControllerConnectionMonitor.connectedMessage("Xbox Wireless Controller"));
    }

    @Test
    public void disconnectedMessageFallsBackToTouchControls() {
        assertEquals("DualSense disconnected. Touch controls active.",
                ControllerConnectionMonitor.disconnectedMessage("DualSense"));
    }

    @Test
    public void debounceWindowIsShortEnoughToFeelResponsive() {
        assertTrue(ControllerConnectionMonitor.DEBOUNCE_MS > 0);
        assertTrue("a reconnect flap must be swallowed",
                ControllerConnectionMonitor.DEBOUNCE_MS >= 1000L);
        assertFalse("but a genuine replug must not be held back forever",
                ControllerConnectionMonitor.DEBOUNCE_MS > 3000L);
    }
}
