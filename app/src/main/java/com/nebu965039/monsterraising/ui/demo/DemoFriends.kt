package com.nebu965039.monsterraising.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 動作確認用の「フレンドがいるか」の切り替え。フレンド機能(Phase 7)ができるまでの仮の情報源で、プロセスが終了すると元に戻る。
 * フレンドがいると、探索で同時に 3 か所まで探索でき、2 か所目以降は「フレンドが協力しに来てくれました」と表示される(8.6節)。
 */
object DemoFriends {
    var hasFriends by mutableStateOf(false)
}
