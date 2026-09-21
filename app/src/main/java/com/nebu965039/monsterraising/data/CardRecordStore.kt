package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.minigame.CardRecordUpdate
import com.nebu965039.monsterraising.core.minigame.CardRecords
import com.nebu965039.monsterraising.core.minigame.CardRecordsCodec
import com.nebu965039.monsterraising.minigame.card.MatchResult
import java.io.File

/** カード×NPC戦の連勝数・解放状況を端末内に保存する。 */
class CardRecordStore(context: Context, name: String = "card_records.json") {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    fun load(): CardRecords = synchronized(LOCK) { read() }

    fun record(result: MatchResult): CardRecordUpdate = synchronized(LOCK) {
        val update = read().record(result)
        tmp.writeText(CardRecordsCodec.encode(update.records))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
        update
    }

    private fun read(): CardRecords =
        (if (file.exists()) CardRecordsCodec.decode(file.readText()) else null) ?: CardRecords()

    private companion object {
        val LOCK = Any()
    }
}
