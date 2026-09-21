package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import com.nebu965039.monsterraising.core.minigame.MiniGameProgressCodec
import java.io.File

/** ミニゲーム共通の進行状況(1 日のプレイ回数・持ち物)を端末内に保存する。 */
class MiniGameStore(context: Context, name: String = "minigame_progress.json") {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    fun load(): MiniGameProgress = synchronized(LOCK) { read() }

    /** 最新の状態を読み、[block] が返す新しい状態を保存して、もう一方の値を返す。 */
    fun <T> update(block: (MiniGameProgress) -> Pair<MiniGameProgress, T>): T = synchronized(LOCK) {
        val (next, value) = block(read())
        tmp.writeText(MiniGameProgressCodec.encode(next))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
        value
    }

    private fun read(): MiniGameProgress =
        (if (file.exists()) MiniGameProgressCodec.decode(file.readText()) else null) ?: MiniGameProgress()

    private companion object {
        val LOCK = Any()
    }
}
