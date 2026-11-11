package indi.tatenlee.inputmethod.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PreferencesRegressionTest {
    @Test public void upgradeKeepsOrderDropsDuplicatesAndAppendsMissingActions() {
        assertEquals(List.of(ToolbarAction.VOICE, ToolbarAction.EMOJI,
                        ToolbarAction.CLIPBOARD, ToolbarAction.SETTINGS),
                ToolbarAction.restore("voice,removed,emoji,voice"));
    }
    @Test public void toolbarOrderSurvivesRoundTrip() {
        var order = List.of(ToolbarAction.SETTINGS, ToolbarAction.VOICE,
                ToolbarAction.CLIPBOARD, ToolbarAction.EMOJI);
        assertEquals(order, ToolbarAction.restore(ToolbarAction.save(order)));
    }
    @Test public void rotationUsesNewWindowWithoutGrowingPastIt() {
        int portrait = KeyboardGeometry.bodyHeight(264, 840, false, 24, 12);
        int landscape = KeyboardGeometry.bodyHeight(200, 360, true, 24, 12);
        assertEquals(264, portrait);
        assertTrue(landscape <= 198);
        assertTrue(landscape + 24 + 12 + 92 <= 360);
        assertEquals(portrait, KeyboardGeometry.bodyHeight(264, 840, false, 24, 12));
    }
    @Test public void systemBarIsCountedOnce() {
        assertEquals(24, KeyboardGeometry.bottomInset(24, 24, 0));
        assertEquals(48, KeyboardGeometry.bottomInset(48, 24, 48));
        assertEquals(0, KeyboardGeometry.bottomInset(0, 0, 0));
    }
    @Test public void oldSpeechCannotEnterNewEditorEvenAfterReturning() {
        SessionGuard guard = new SessionGuard();
        long recording = guard.advance();
        guard.advance();
        guard.advance();
        assertFalse(guard.accepts(recording));
        assertTrue(guard.accepts(guard.current()));
    }
}
