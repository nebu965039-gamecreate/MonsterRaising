package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.minigame.ScoreRecordUpdate
import com.nebu965039.monsterraising.core.minigame.ScoreRecords
import com.nebu965039.monsterraising.core.minigame.ScoreRecordsCodec
import java.io.File

/** 難易度ごとのハイスコア・クリア記録を端末内に保存する(ミニゲームごとに [name] を分ける)。 */
class ScoreRecordStore(context: Context, name: String) {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    fun load(): ScoreRecords = synchronized(LOCK) { read() }

    fun record(key: String, score: Int, cleared: Boolean): ScoreRecordUpdate = synchronized(LOCK) {
        val update = read().record(key, score, cleared)
        tmp.writeText(ScoreRecordsCodec.encode(update.records))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
        update
    }

    private fun read(): ScoreRecords =
        (if (file.exists()) ScoreRecordsCodec.decode(file.readText()) else null) ?: ScoreRecords()

    private companion object {
        val LOCK = Any()
    }
}
