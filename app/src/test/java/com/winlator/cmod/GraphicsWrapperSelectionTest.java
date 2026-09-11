package com.winlator.cmod;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GraphicsWrapperSelectionTest {
    @Test
    public void resolvesBundledWrapperArchives() {
        assertEquals("wrapper", XServerDisplayActivity.resolveGraphicsDriverArchiveName("zink"));
        assertEquals("wrapper", XServerDisplayActivity.resolveGraphicsDriverArchiveName("wrapper"));
        assertEquals("wrapper-original", XServerDisplayActivity.resolveGraphicsDriverArchiveName("wrapper-winnative"));
        assertEquals("wrapper-leegao", XServerDisplayActivity.resolveGraphicsDriverArchiveName("wrapper-leegao"));
        assertEquals("wrapper-legacy", XServerDisplayActivity.resolveGraphicsDriverArchiveName("wrapper-legacy"));
    }
}
