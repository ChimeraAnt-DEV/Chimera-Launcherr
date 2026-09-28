package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.ui.views.ControllerLayout;
import org.junit.Test;

import java.util.List;

/**
 * Covers {@link Controller3DProjection}: the geometry the 3D view draws from.
 *
 * <p>These assertions exist because a projection defect looks plausible on screen — the 2D
 * layout shipped three that only its own test caught. The important properties are that the
 * pitch recedes the top edge, that shoulder controls are lifted and drawn in the shoulder layer,
 * and that depth ordering runs far-to-near.
 */
public class Controller3DProjectionTest {

    private static final float SCALE = 100f;
    private static final float CX = 300f;
    private static final float CY = 300f;

    @Test
    public void shoulderControlsAreClassified() {
        assertTrue(Controller3DProjection.isShoulder(ControllerLayout.Shape.BUMPER));
        assertTrue(Controller3DProjection.isShoulder(ControllerLayout.Shape.TRIGGER));
        assertFalse(Controller3DProjection.isShoulder(ControllerLayout.Shape.STICK));
        assertFalse(Controller3DProjection.isShoulder(ControllerLayout.Shape.FACE_XBOX));
    }

    @Test
    public void pitchRecedesTheTopEdge() {
        // A point above centre gains positive depth (further away) under the tilt.
        float[] above = Controller3DProjection.tilt(0f, 1f, 0f);
        float[] below = Controller3DProjection.tilt(0f, -1f, 0f);
        assertTrue("top should recede", above[2] > 0f);
        assertTrue("bottom should approach", below[2] < 0f);
    }

    @Test
    public void perspectiveShrinksFarControlsAndGrowsNearOnes() {
        float[] far = Controller3DProjection.project(0f, 1f, 0f, SCALE, CX, CY);
        float[] near = Controller3DProjection.project(0f, -1f, 0f, SCALE, CX, CY);
        assertTrue("far control is projected smaller", far[2] < near[2]);
        // Screen y grows downward; a point above centre must land above the centre line.
        assertTrue(far[1] < CY);
        assertTrue(near[1] > CY);
    }

    @Test
    public void shoulderControlsAreLiftedTowardTheCamera() {
        List<Controller3DProjection.Projected> projected =
                Controller3DProjection.project(ControllerType.XBOX, SCALE, CX, CY);
        Controller3DProjection.Projected trigger = null;
        for (Controller3DProjection.Projected p : projected) {
            if (p.spec.id.equals("lt")) trigger = p;
        }
        assertTrue("lt must be projected", trigger != null);
        assertTrue("trigger is in the shoulder layer", trigger.shoulder);
        // The lift is toward the camera, so its perspective factor is larger than a face control's.
        float[] face = Controller3DProjection.project(0f, 0f, 0f, SCALE, CX, CY);
        assertTrue(trigger.z < 0f);
        assertTrue(trigger.radius > 0f);
        assertTrue(face[2] > 0f);
    }

    @Test
    public void projectedRegionsAreOrderedFarToNear() {
        List<Controller3DProjection.Projected> projected =
                Controller3DProjection.project(ControllerType.DS4, SCALE, CX, CY);
        for (int i = 1; i < projected.size(); i++) {
            assertTrue("draw order must not regress in depth",
                    projected.get(i - 1).z <= projected.get(i).z + 0.0001f);
        }
    }

    @Test
    public void everyRegionIsProjectedForEachType() {
        for (ControllerType type : ControllerType.values()) {
            int expected = ControllerLayout.regions(type).size();
            List<Controller3DProjection.Projected> projected =
                    Controller3DProjection.project(type, SCALE, CX, CY);
            assertEquals(type.getDisplayName(), expected, projected.size());
        }
    }

    @Test
    public void shellProjectsToAFiniteClosedPolygon() {
        float[] poly = Controller3DProjection.projectShell(ControllerType.XBOX, 12, SCALE, CX, CY);
        assertTrue(poly.length >= 6);
        for (float v : poly) {
            assertFalse("no NaN in the shell", Float.isNaN(v));
            assertTrue("shell stays on screen", Math.abs(v) < 100000f);
        }
    }

    @Test
    public void aZeroScaleDoesNotProduceNaN() {
        // The view guards this, but the projection should not invent NaN if it is ever called
        // with a degenerate size during layout.
        List<Controller3DProjection.Projected> projected =
                Controller3DProjection.project(ControllerType.XBOX, 0f, CX, CY);
        for (Controller3DProjection.Projected p : projected) {
            assertFalse(Float.isNaN(p.x));
            assertFalse(Float.isNaN(p.y));
        }
    }
}
