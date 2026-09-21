package com.nebu965039.monsterraising.core.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendshipTest {
    private val day = 100L

    // --- いいね(送信側)(9節) ---

    @Test
    fun theFirstLikeOfTheDay_gives100Points() {
        val r = Friendship.sendLike(FriendshipState(), "a", day)!!
        assertEquals(100L, r.pointsGained)
        assertEquals(100L, r.state.points)
    }

    @Test
    fun likingManyFriends_stillGivesOnly100PointsPerDay() {
        var s = FriendshipState()
        for (id in listOf("a", "b", "c")) s = Friendship.sendLike(s, id, day)!!.state
        assertEquals(100L, s.points)
    }

    @Test
    fun theSecondFriendGivesNoPoints_butTheLikeIsStillSent() {
        val first = Friendship.sendLike(FriendshipState(), "a", day)!!
        val second = Friendship.sendLike(first.state, "b", day)!!
        assertEquals(0L, second.pointsGained)
        assertTrue(second.state.hasLiked("b", day))
    }

    @Test
    fun theSameFriendCanBeLikedOnlyOncePerDay() {
        val s = Friendship.sendLike(FriendshipState(), "a", day)!!.state
        assertNull(Friendship.sendLike(s, "a", day))
        assertTrue(s.hasLiked("a", day))
    }

    @Test
    fun aNewDay_allowsLikingAgain_andGivesPointsAgain() {
        val s = Friendship.sendLike(FriendshipState(), "a", day)!!.state
        assertFalse(s.hasLiked("a", day + 1))
        val next = Friendship.sendLike(s, "a", day + 1)
        assertNotNull(next)
        assertEquals(200L, next!!.state.points)
    }

    // --- いいね(受信側) ---

    @Test
    fun receivingLikes_gives100EachWithNoDailyLimit() {
        var s = FriendshipState()
        repeat(5) { s = Friendship.receiveLike(s) }
        assertEquals(500L, s.points)
    }

    @Test
    fun sendingAndReceiving_addUp() {
        var s = Friendship.sendLike(FriendshipState(), "a", day)!!.state
        s = Friendship.receiveLike(s)
        assertEquals(200L, s.points)
    }

    // --- 最終ログインの表示 ---

    @Test
    fun lastLoginText_roundsDownToTheLargestUnit() {
        val now = 10L * 24 * 3_600_000L
        assertEquals("たった今", Friendship.lastLoginText(now, now - 30_000))
        assertEquals("5分前", Friendship.lastLoginText(now, now - 5 * 60_000))
        assertEquals("59分前", Friendship.lastLoginText(now, now - 59 * 60_000))
        assertEquals("2時間前", Friendship.lastLoginText(now, now - 2 * 3_600_000L - 10 * 60_000))
        assertEquals("1日前", Friendship.lastLoginText(now, now - 25 * 3_600_000L))
        assertEquals("3日前", Friendship.lastLoginText(now, now - 3 * 24 * 3_600_000L))
    }

    @Test
    fun lastLoginText_isUnknownWhenHidden() {
        assertEquals("不明", Friendship.lastLoginText(1_000L, null))
    }

    @Test
    fun lastLoginText_neverGoesNegative() {
        assertEquals("たった今", Friendship.lastLoginText(1_000L, 5_000L))
    }

    // --- 保存 ---

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val s = Friendship.sendLike(FriendshipState(), "a", day)!!.state
        assertEquals(s, FriendshipStateCodec.decode(FriendshipStateCodec.encode(s)))
        assertNull(FriendshipStateCodec.decode("{ broken"))
    }
}
