package com.qasim.nexa.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandIdentityTest {
    @Test
    fun creatorIdentityIsPresent() {
        assertEquals("Mirza Qasim", BrandIdentity.CREATOR)
        assertEquals("Faisalabad, Pakistan", BrandIdentity.ORIGIN)
    }

    @Test
    fun productNamesAreStable() {
        assertEquals("NEXA TV Launcher", BrandIdentity.APP_NAME)
        assertEquals("BixSigma AI", BrandIdentity.AI_NAME)
    }
}
