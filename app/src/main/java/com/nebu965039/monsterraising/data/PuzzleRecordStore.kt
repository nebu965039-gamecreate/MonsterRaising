package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.minigame.PuzzleRecordUpdate
import com.nebu965039.monsterraising.core.minigame.PuzzleRecords
import com.nebu965039.monsterraising.core.minigame.PuzzleRecordsCodec
import com.nebu965039.monsterraising.minigame.puzzle.PuzzleResult
import java.io.File

/** 落ち物パズルのハイスコア・クリア記録を端末内に保存する。 */
class PuzzleRecordStore(context: Context, name: String = "puzzle_records.json") {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    fun load(): PuzzleRecords = synchronized(LOCK) { read() }

    /** 1 プレイの結果を記録に反映して保存し、更新内容(自己ベスト・初回クリア)を返す。 */
    fun record(result: PuzzleResult): PuzzleRecordUpdate = synchronized(LOCK) {
        val update = read().record(result)
        tmp.writeText(PuzzleRecordsCodec.encode(update.records))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
        update
    }

    private fun read(): PuzzleRecords =
        (if (file.exists()) PuzzleRecordsCodec.decode(file.readText()) else null) ?: PuzzleRecords()

    private companion object {
        val LOCK = Any()
    }
}
