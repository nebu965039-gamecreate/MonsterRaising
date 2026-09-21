package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStateCodec
import java.io.File

/** 育成状態を端末内(アプリ専用領域)の JSON ファイルに保存する。書き込みは一時ファイル経由で置き換える。 */
class PetStore(context: Context, name: String = "pet_state.json") {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    /** 保存済みの状態。ファイルがない・壊れている場合は null */
    fun load(): PetState? =
        if (file.exists()) PetStateCodec.decode(file.readText()) else null

    fun save(state: PetState) {
        tmp.writeText(PetStateCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
    }

    fun clear() {
        file.delete()
        tmp.delete()
    }
}
