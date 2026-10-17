package com.histoury.app.map

import com.histoury.app.data.repository.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A saved travel mode is never swapped for another one. These run on the
 * JVM (no device): ./gradlew :app:testDebugUnitTest
 */
class TravelModesTest {

    @Test
    fun savedModesAreReadBackExactly() {
        assertEquals(TravelMode.WALKING, TravelModes.fromWire("WALK"))
        assertEquals(TravelMode.DRIVING, TravelModes.fromWire("DRIVE"))
        assertEquals(TravelMode.MOTORCYCLE, TravelModes.fromWire("TWO_WHEELER"))
        assertEquals(TravelMode.COMMUTE, TravelModes.fromWire("TRANSIT"))
    }

    @Test
    fun onlyMissingOrUnknownValuesDefaultToWalking() {
        assertEquals(TravelMode.WALKING, TravelModes.fromWire(null))
        assertEquals(TravelMode.WALKING, TravelModes.fromWire(""))
        assertEquals(TravelMode.WALKING, TravelModes.fromWire("HOVERBOARD"))
    }

    @Test
    fun walkAndDriveAreAlwaysAvailable() {
        assertTrue(TravelModes.isAvailable(TravelMode.WALKING))
        assertTrue(TravelModes.isAvailable(TravelMode.DRIVING))
        assertNull(TravelModes.unavailableMessage(TravelMode.WALKING))
        assertNull(TravelModes.unavailableMessage(TravelMode.DRIVING))
    }

    @Test
    fun mapLibreBuildOffersEveryModeWithoutSubstituting() {
        assumeTrue("only for HISTOURY_MAP_RENDERER=maplibre", MapConfig.useMapLibre)

        // Walk/drive (ORS), motorcycle (Valhalla), commute (trains from
        // OpenStreetMap): each is routed as itself, never as another mode.
        assertEquals(TravelMode.entries.toList(), TravelModes.available)
        TravelMode.entries.forEach {
            assertTrue(TravelModes.isAvailable(it))
            assertNull(TravelModes.unavailableMessage(it))
            assertEquals(TravelMode.entries.toList(), TravelModes.shownWith(it))
        }
    }

    @Test
    fun googleBuildKeepsEveryMode() {
        assumeTrue("only for HISTOURY_MAP_RENDERER=google", !MapConfig.useMapLibre)

        assertEquals(TravelMode.entries.toList(), TravelModes.available)
        TravelMode.entries.forEach { assertNull(TravelModes.unavailableMessage(it)) }
    }
}
