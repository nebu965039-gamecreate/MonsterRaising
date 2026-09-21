package com.nebu965039.monsterraising.ui.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * 動作確認用の仮想時計(端末の時計は変更しない)。育成デモ画面で時間を進め、パズル画面など他の画面でも同じ時刻を使う。
 * ウィジェットは実時間で動くため、使用中は表示が食い違う。プロセスが終了すると元に戻る。
 */
object DemoClock {
    var offsetMs by mutableLongStateOf(0L)

    fun now(): Long = System.currentTimeMillis() + offsetMs
}
