package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStateCodec
import java.io.File

/**
 * 育成状態を端末内(アプリ専用領域)の JSON ファイルに保存する。書き込みは一時ファイル経由で置き換える。
 * 本体アプリ・ウィジェット・定期更新(WorkManager)が同じファイルを読み書きするため、
 * 読み書きは [update] でまとめて排他制御する(同一プロセス内。ウィジェットも同じプロセスで動く)。
 */
class PetStore(context: Context, name: String = "pet_state.json") {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    /** 保存済みの状態。ファイルがない・壊れている場合は null */
    fun load(): PetState? = synchronized(LOCK) { read() }

    /**
     * 最新の状態を読み、[transform] を適用して保存し、その結果を返す。
     * 保存された状態がない(または壊れている)場合は、新しい卵から始める。
     */
    fun update(nowMs: Long, config: PetConfig = PetConfig(), transform: (PetState) -> PetState): PetState =
        synchronized(LOCK) {
            val next = transform(read() ?: PetState.newEgg(nowMs, config))
            write(next)
            next
        }

    fun clear() = synchronized(LOCK) {
        file.delete()
        tmp.delete()
    }

    private fun read(): PetState? = if (file.exists()) PetStateCodec.decode(file.readText()) else null

    private fun write(state: PetState) {
        tmp.writeText(PetStateCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
