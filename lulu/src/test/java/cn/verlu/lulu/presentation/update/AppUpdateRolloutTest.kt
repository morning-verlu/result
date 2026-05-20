package cn.verlu.lulu.presentation.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRolloutTest {
    @Test
    fun zeroPercentDisablesRollout() {
        assertFalse(isInRollout("install-a", "cn.verlu.lulu", 0))
    }

    @Test
    fun fullPercentAlwaysIncludesInstall() {
        assertTrue(isInRollout("install-a", "cn.verlu.lulu", 100))
    }

    @Test
    fun bucketIsStableForSameInstallAndPackage() {
        val first = rolloutBucket("install-a", "cn.verlu.lulu")
        val second = rolloutBucket("install-a", "cn.verlu.lulu")

        assertEquals(first, second)
        assertTrue(first in 1..100)
    }
}
