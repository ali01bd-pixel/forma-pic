package com.ali.formapic

import com.ali.formapic.domain.model.CameraMode
import com.ali.formapic.domain.model.ProManualParameters
import com.ali.formapic.domain.model.RenderingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormaPicUnitTest {

    @Test
    fun verifyShutterSpeedFormatting() {
        val fastParams = ProManualParameters(isShutterAuto = false, shutterSpeedNs = 1_000_000L)
        assertEquals("1/1000s", fastParams.formattedShutterSpeed())

        val slowParams = ProManualParameters(isShutterAuto = false, shutterSpeedNs = 500_000_000L)
        assertEquals("0.5s", slowParams.formattedShutterSpeed())
    }

    @Test
    fun verifyAllCameraModesAndRenderingPreferencesPresent() {
        assertTrue(CameraMode.entries.size >= 16)
        assertEquals(3, RenderingPreference.entries.size)
    }
}
