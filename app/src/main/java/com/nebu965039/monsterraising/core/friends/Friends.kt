package com.nebu965039.monsterraising.core.friends

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** フレンド 1 人(基本設計書9節)。サーバー(Phase 6・7)ができるまでは、動作確認用の仮のフレンドだけ。 */
data class Friend(
    val id: String,
    val name: String,
    /** 最後にログインした時刻(エポックms)。ステータス・ログイン状況を「表示しない」設定のフレンドは null */
    val lastLoginMs: Long?,
)

/**
 * フレンドシップポイントと、いいね(基本設計書9節)。UI・Android 非依存。
 * ポイントは、進化の特別ルート(フレンドルート。4.3節)の条件になる。
 */
@Serializable
data class FriendshipState(
    val points: Long = 0L,
    /** 「いいね」を送って、送信側のポイントを最後に得た日(日付の通し番号) */
    val sendRewardDay: Long = -1L,
    /** 今日「いいね」を送ったフレンド(同じフレンドには 1 日 1 回まで) */
    val likedToday: Set<String> = emptySet(),
    val likedDay: Long = -1L,
) {
    /** 日付が変わっていれば、今日送ったフレンドの記録を空にする */
    private fun rolledTo(day: Long): FriendshipState = if (likedDay == day) this else copy(likedToday = emptySet(), likedDay = day)

    fun hasLiked(friendId: String, day: Long): Boolean = friendId in rolledTo(day).likedToday
}

/** 「いいね」の結果。 */
data class LikeResult(val state: FriendshipState, val pointsGained: Long)

object Friendship {
    /** いいね 1 回のポイント(9節: 送信側も受信側も 100P) */
    const val LIKE_POINTS = 100L

    /**
     * フレンドに「いいね」を送る。送信側が得られるのは 1 日 100P が上限(何人に送っても、最初の 1 回だけ)。
     * 同じフレンドには 1 日 1 回まで。すでに送っていれば null。
     */
    fun sendLike(state: FriendshipState, friendId: String, day: Long): LikeResult? {
        val rolled = state.copy(
            likedToday = if (state.likedDay == day) state.likedToday else emptySet(),
            likedDay = day,
        )
        if (friendId in rolled.likedToday) return null
        val rewarded = rolled.sendRewardDay != day
        val next = rolled.copy(
            likedToday = rolled.likedToday + friendId,
            points = rolled.points + if (rewarded) LIKE_POINTS else 0L,
            sendRewardDay = if (rewarded) day else rolled.sendRewardDay,
        )
        return LikeResult(next, if (rewarded) LIKE_POINTS else 0L)
    }

    /** フレンドから「いいね」をもらった(受信側は 100P で、上限なし。フレンドごとに 1 日 1 回まで送られてくる)。 */
    fun receiveLike(state: FriendshipState): FriendshipState = state.copy(points = state.points + LIKE_POINTS)

    /** 「最終ログイン」の表示(例:「2時間前」)。分からなければ「不明」 */
    fun lastLoginText(nowMs: Long, lastLoginMs: Long?): String {
        if (lastLoginMs == null) return "不明"
        val minutes = ((nowMs - lastLoginMs).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1 -> "たった今"
            minutes < 60 -> "${minutes}分前"
            minutes < 24 * 60 -> "${minutes / 60}時間前"
            else -> "${minutes / (24 * 60)}日前"
        }
    }
}

object FriendshipStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: FriendshipState): String = json.encodeToString(FriendshipState.serializer(), state)

    fun decode(text: String): FriendshipState? = try {
        json.decodeFromString(FriendshipState.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
