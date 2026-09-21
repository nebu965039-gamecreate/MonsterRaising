package com.nebu965039.monsterraising.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaults_notifyAndShowStatus() {
        val s = AppSettings()
        assertTrue(s.notifyExploration)
        assertTrue(s.showStatusToFriends)
    }

    @Test
    fun codec_roundTrips() {
        val s = AppSettings(notifyExploration = false, showStatusToFriends = false)
        assertEquals(s, AppSettingsCodec.decode(AppSettingsCodec.encode(s)))
    }

    @Test
    fun missingFields_fallBackToTheDefaults() {
        val s = AppSettingsCodec.decode("""{"notifyExploration":false}""")!!
        assertFalse(s.notifyExploration)
        assertTrue(s.showStatusToFriends) // 古い保存データに項目がなくても読める
    }

    @Test
    fun garbage_isRejected() {
        assertNull(AppSettingsCodec.decode("{ broken"))
        assertNull(AppSettingsCodec.decode("[1]"))
    }
}
