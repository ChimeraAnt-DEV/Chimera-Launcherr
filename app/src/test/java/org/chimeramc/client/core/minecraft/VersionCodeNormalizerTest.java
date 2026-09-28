package org.chimeramc.client.core.minecraft;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the version normalisation that gates the shader compatibility fixer.
 *
 * <p>The fixer compares numeric parts, so a channel suffix on a version string must not become
 * part of a part: handing it {@code "1.21.132.1-preview"} as a version would make its range-gated
 * fixes compare against a value no real build has. The rule is pure, so it is tested directly.
 */
public class VersionCodeNormalizerTest {

    @Test
    public void stripsAPreviewSuffix() {
        assertEquals("1.21.132.1", VersionCodeNormalizer.normalize("1.21.132.1-preview"));
    }

    @Test
    public void stripsABetaSuffix() {
        assertEquals("1.26.50.4", VersionCodeNormalizer.normalize("1.26.50.4-beta"));
    }

    @Test
    public void stripsATrailingReleaseChannel() {
        assertEquals("1.26.60.28", VersionCodeNormalizer.normalize("1.26.60.28_RC3"));
    }

    @Test
    public void keepsAPlainVersion() {
        assertEquals("1.21.132.1", VersionCodeNormalizer.normalize("1.21.132.1"));
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals("1.26.51.1", VersionCodeNormalizer.normalize("  1.26.51.1  "));
    }

    @Test
    public void doesNotLeaveATrailingDot() {
        assertEquals("1.26.50", VersionCodeNormalizer.normalize("1.26.50."));
    }

    @Test
    public void aBlankOrNullVersionReadsAsEmpty() {
        assertEquals("", VersionCodeNormalizer.normalize(null));
        assertEquals("", VersionCodeNormalizer.normalize(""));
        assertEquals("", VersionCodeNormalizer.normalize("preview"));
    }
}
