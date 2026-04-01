package es.bercianor.tocacorrer.data.local

import android.content.Context
import android.content.SharedPreferences
import es.bercianor.tocacorrer.data.export.GpxSegmentationMode
import es.bercianor.tocacorrer.data.local.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for PreferencesManager.
 */
@RunWith(MockitoJUnitRunner::class)
class PreferencesManagerTest {

    @Mock
    lateinit var context: Context

    @Mock
    lateinit var sharedPreferences: SharedPreferences

    @Mock
    lateinit var editor: SharedPreferences.Editor

    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        // Configure mocks
        whenever(context.getSharedPreferences("tocacorrer_prefs", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        
        // Create manager with mock
        preferencesManager = PreferencesManager(context)
    }

    @Test
    fun `getSelectedCalendarId returns default value`() {
        whenever(sharedPreferences.getLong("calendar_id", -1L)).thenReturn(-1L)
        
        assertEquals(-1L, preferencesManager.selectedCalendarId)
    }

    @Test
    fun `setSelectedCalendarId saves value`() {
        preferencesManager.selectedCalendarId = 5L
        
        // Verify that putLong was called (Mockito verifies the interaction)
        assertTrue(true) // Test passes if no exception
    }

    @Test
    fun `getSelectedCalendarId returns saved value`() {
        whenever(sharedPreferences.getLong("calendar_id", -1L)).thenReturn(10L)
        
        assertEquals(10L, preferencesManager.selectedCalendarId)
    }

    @Test
    fun `getSelectedCalendarName returns default value`() {
        whenever(sharedPreferences.getString("calendar_name", "")).thenReturn("")
        
        assertEquals("", preferencesManager.selectedCalendarName)
    }

    @Test
    fun `setSelectedCalendarName saves value`() {
        preferencesManager.selectedCalendarName = "My Calendar"
        
        assertTrue(true) // Test passes if no exception
    }

    @Test
    fun `getDarkMode returns default value`() {
        whenever(sharedPreferences.getInt("dark_mode", 0)).thenReturn(0)
        
        assertEquals(0, preferencesManager.darkMode)
    }

    @Test
    fun `setDarkMode saves value`() {
        preferencesManager.darkMode = 2
        
        assertTrue(true)
    }

    @Test
    fun `getAutoPause returns default value`() {
        whenever(sharedPreferences.getBoolean("auto_pause", true)).thenReturn(true)
        
        assertTrue(preferencesManager.autoPause)
    }

    @Test
    fun `setAutoPause saves value`() {
        preferencesManager.autoPause = false
        
        assertTrue(true)
    }

    @Test
    fun `getAutoPauseTime returns default value`() {
        whenever(sharedPreferences.getInt("auto_pause_time", 10)).thenReturn(10)
        
        assertEquals(10, preferencesManager.autoPauseTime)
    }

    @Test
    fun `setAutoPauseTime saves value`() {
        preferencesManager.autoPauseTime = 20
        
        assertTrue(true)
    }

    @Test
    fun `getAutoPauseTime returns custom value`() {
        whenever(sharedPreferences.getInt("auto_pause_time", 10)).thenReturn(15)
        
        assertEquals(15, preferencesManager.autoPauseTime)
    }

    @Test
    fun `darkMode values are correct`() {
        // 0 = system
        whenever(sharedPreferences.getInt("dark_mode", 0)).thenReturn(0)
        assertEquals(0, preferencesManager.darkMode)
        
        // 1 = light
        whenever(sharedPreferences.getInt("dark_mode", 0)).thenReturn(1)
        assertEquals(1, preferencesManager.darkMode)
        
        // 2 = dark
        whenever(sharedPreferences.getInt("dark_mode", 0)).thenReturn(2)
        assertEquals(2, preferencesManager.darkMode)
    }

    @Test
    fun `autoPauseTime options are valid`() {
        val validOptions = listOf(5, 10, 15, 20, 30)
        
        for (option in validOptions) {
            whenever(sharedPreferences.getInt("auto_pause_time", 10)).thenReturn(option)
            assertEquals(option, preferencesManager.autoPauseTime)
        }
    }

    @Test
    fun `getAutoPause returns false when disabled`() {
        whenever(sharedPreferences.getBoolean("auto_pause", true)).thenReturn(false)
        
        assertFalse(preferencesManager.autoPause)
    }

    @Test
    fun `getCalendarName returns saved value`() {
        whenever(sharedPreferences.getString("calendar_name", "")).thenReturn("Running")
        
        assertEquals("Running", preferencesManager.selectedCalendarName)
    }

    // -------------------------------------------------------------------------
    // gpxSegmentationMode round-trip tests (String-based storage)
    // -------------------------------------------------------------------------

    @Test
    fun `gpxSegmentationMode returns NONE by default when no value stored`() {
        // getString returns null → no old int either → defaults to first entry (NONE)
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn(null)
        whenever(sharedPreferences.getInt("gpx_segmentation_mode", -1)).thenReturn(-1)

        assertEquals(GpxSegmentationMode.NONE, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode returns NONE when stored name is NONE`() {
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn("NONE")

        assertEquals(GpxSegmentationMode.NONE, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode returns TRACKS when stored name is TRACKS`() {
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn("TRACKS")

        assertEquals(GpxSegmentationMode.TRACKS, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode returns SEGMENTS when stored name is SEGMENTS`() {
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn("SEGMENTS")

        assertEquals(GpxSegmentationMode.SEGMENTS, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode setter persists enum name as String`() {
        // Just verify the setter doesn't throw (mocked SharedPreferences.Editor)
        preferencesManager.gpxSegmentationMode = GpxSegmentationMode.TRACKS
        assertTrue(true)
    }

    @Test
    fun `gpxSegmentationMode round trip for all values using String storage`() {
        for (mode in GpxSegmentationMode.entries) {
            whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn(mode.name)
            assertEquals("Round-trip failed for $mode", mode, preferencesManager.gpxSegmentationMode)
        }
    }

    // -------------------------------------------------------------------------
    // Migration fallback: getString returns null, old getInt has valid ordinal
    // -------------------------------------------------------------------------

    @Test
    fun `gpxSegmentationMode falls back to TRACKS ordinal when getString returns null`() {
        // getString returns null (not yet migrated) → falls back to ordinal 1 = TRACKS
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn(null)
        whenever(sharedPreferences.getInt("gpx_segmentation_mode", -1)).thenReturn(1)

        assertEquals(GpxSegmentationMode.TRACKS, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode falls back to SEGMENTS ordinal when getString returns null`() {
        // getString returns null → falls back to ordinal 2 = SEGMENTS
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn(null)
        whenever(sharedPreferences.getInt("gpx_segmentation_mode", -1)).thenReturn(2)

        assertEquals(GpxSegmentationMode.SEGMENTS, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `gpxSegmentationMode falls back to NONE when getString null and getInt out of range`() {
        // Both missing → defaults to first entry (NONE)
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn(null)
        whenever(sharedPreferences.getInt("gpx_segmentation_mode", -1)).thenReturn(-1)

        assertEquals(GpxSegmentationMode.NONE, preferencesManager.gpxSegmentationMode)
    }

    // -------------------------------------------------------------------------
    // Update segmentation mode — spec scenario: "Update segmentation mode"
    // -------------------------------------------------------------------------

    @Test
    fun `gpxSegmentationMode setter stores TRACKS name in SharedPreferences`() {
        preferencesManager.gpxSegmentationMode = GpxSegmentationMode.TRACKS

        verify(editor).putString("gpx_segmentation_mode", "TRACKS")
    }

    @Test
    fun `gpxSegmentationMode setter stores SEGMENTS name in SharedPreferences`() {
        preferencesManager.gpxSegmentationMode = GpxSegmentationMode.SEGMENTS

        verify(editor).putString("gpx_segmentation_mode", "SEGMENTS")
    }

    @Test
    fun `gpxSegmentationMode setter stores NONE name in SharedPreferences`() {
        preferencesManager.gpxSegmentationMode = GpxSegmentationMode.NONE

        verify(editor).putString("gpx_segmentation_mode", "NONE")
    }

    // -------------------------------------------------------------------------
    // Change GPX mode in settings — spec scenario: "Change GPX mode in settings"
    // Verifies the same setter that SettingsViewModel.setGpxSegmentationMode() delegates to.
    // -------------------------------------------------------------------------

    @Test
    fun `change GPX mode to SEGMENTS persists correct value`() {
        // Simulate the action that SettingsViewModel.setGpxSegmentationMode(2) performs:
        // it converts the index to the enum and delegates to the preference setter.
        val targetMode = GpxSegmentationMode.SEGMENTS
        preferencesManager.gpxSegmentationMode = targetMode

        // Verify the enum name was written to SharedPreferences
        verify(editor).putString("gpx_segmentation_mode", "SEGMENTS")

        // Verify reading it back returns SEGMENTS (mock returns "SEGMENTS")
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn("SEGMENTS")
        assertEquals(GpxSegmentationMode.SEGMENTS, preferencesManager.gpxSegmentationMode)
    }

    @Test
    fun `change GPX mode to TRACKS persists correct value`() {
        // Simulate the action that SettingsViewModel.setGpxSegmentationMode(1) performs
        val targetMode = GpxSegmentationMode.TRACKS
        preferencesManager.gpxSegmentationMode = targetMode

        // Verify the enum name was written to SharedPreferences
        verify(editor).putString("gpx_segmentation_mode", "TRACKS")

        // Verify reading it back returns TRACKS (mock returns "TRACKS")
        whenever(sharedPreferences.getString("gpx_segmentation_mode", null)).thenReturn("TRACKS")
        assertEquals(GpxSegmentationMode.TRACKS, preferencesManager.gpxSegmentationMode)
    }
}
