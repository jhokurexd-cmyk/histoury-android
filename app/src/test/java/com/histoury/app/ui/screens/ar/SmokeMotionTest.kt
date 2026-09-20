package com.histoury.app.ui.screens.ar

import com.histoury.app.data.model.ArSmokeEmitter
import com.histoury.app.data.model.ArSmokeSettings
import org.junit.Assert.*
import org.junit.Test

class SmokeMotionTest {
    @Test fun legacySitesDoNotEnableSmoke() {
        assertFalse(ArSmokeSettings().sanitized().enabled)
    }

    @Test fun malformedRemoteSettingsCannotCreateUnboundedEffects() {
        val s = ArSmokeSettings(
            density = Double.NaN, heightMeters = Double.POSITIVE_INFINITY,
            widthMeters = -100.0, windRightMetersPerSecond = 500.0,
            emitters = List(200) { ArSmokeEmitter(Double.NaN, 1000.0, -1000.0) }
        ).sanitized()
        assertEquals(3, s.emitters.size)
        assertEquals(0.65, s.density, 0.0)
        assertEquals(4.0, s.heightMeters, 0.0)
        assertEquals(0.25, s.widthMeters, 0.0)
        assertEquals(2.0, s.windRightMetersPerSecond, 0.0)
        assertEquals(ArSmokeEmitter(0.0, 100.0, -100.0), s.emitters.first())
    }

    @Test fun movingBillowsFadeToInvisibleAtLoopBoundary() {
        val s = ArSmokeSettings(enabled = true)
        assertEquals(0f, SmokeMotion.sample(s, 0, 0.0).opacity, 0f)
        assertTrue(SmokeMotion.sample(s, 0, 6.99999).opacity < 0.00001f)
        assertEquals(0f, SmokeMotion.sample(s, 0, 7.0).opacity, 0f)
        assertTrue(SmokeMotion.sample(s, 0, 3.0).opacity > 0f)
        assertTrue(SmokeMotion.sample(s, 0, 3.0).y > SmokeMotion.sample(s, 0, 1.0).y)
    }

    @Test fun sourceOffsetsAndWindFollowModelAxesInMetres() {
        val base = ArSmokeSettings(enabled = true, windRightMetersPerSecond = 0.0)
        val moved = base.copy(emitters = listOf(ArSmokeEmitter(5.0, 2.0, -3.0)))
        val a = SmokeMotion.sample(base, 2, 1.0)
        val b = SmokeMotion.sample(moved, 2, 1.0)
        assertEquals(5f, b.x - a.x, 0.0001f)
        assertEquals(2f, b.y - a.y, 0.0001f)
        assertEquals(-3f, b.z - a.z, 0.0001f)
        assertTrue(SmokeMotion.sample(base.copy(windRightMetersPerSecond = 1.0), 2, 1.0).x > a.x)
    }
}
