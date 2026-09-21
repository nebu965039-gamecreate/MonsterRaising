package com.nebu965039.monsterraising.ui.demo

import androidx.compose.runtime.getValue
import com.nebu965039.monsterraising.core.friends.Friend
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 動作確認用の「フレンドがいるか」の切り替え。フレンド機能(Phase 7)ができるまでの仮の情報源で、プロセスが終了すると元に戻る。
 * フレンドがいると、探索で同時に 3 か所まで探索でき、2 か所目以降は「フレンドが協力しに来てくれました」と表示される(8.6節)。
 */
object DemoFriends {
    var hasFriends by mutableStateOf(false)

    /** 動作確認用の仮のフレンド(フレンドがいる設定のときだけ) */
    fun friends(nowMs: Long): List<Friend> =
        if (!hasFriends) {
            emptyList()
        } else {
            listOf(
                Friend("demo-a", "テストフレンドA", nowMs - 2 * 3_600_000L),
                Friend("demo-b", "テストフレンドB", nowMs - 26 * 3_600_000L),
            )
        }
}
