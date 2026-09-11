package com.winlator.cmod;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GraphicsWrapperSelectionTest {
    @Test
    public void resolvesBundledWrapperArchives() {
        assertEquals("wrapper", XServerDisplayActivity.resolveGraphicsWrapperArchiveName("wrapper"));
        assertEquals("wrapper-original", XServerDisplayActivity.resolveGraphicsWrapperArchiveName("wrapper-winnative"));
        assertEquals("wrapper-leegao", XServerDisplayActivity.resolveGraphicsWrapperArchiveName("wrapper-leegao"));
        assertEquals("wrapper-legacy", XServerDisplayActivity.resolveGraphicsWrapperArchiveName("wrapper-legacy"));
    }
}
