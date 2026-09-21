package com.nebu965039.monsterraising.data

import android.content.Context
import java.io.File

/**
 * 端末内(アプリ専用領域)の JSON ファイル 1 つに、値を保存する汎用のストア。書き込みは一時ファイル経由で置き換える。
 * 読み込めない(ファイルがない・壊れている)ときは [default] を返す。
 * 本体アプリ・ウィジェット・定期処理から同じファイルを扱うため、読み書きは全ストア共通の鍵で排他制御する。
 */
class JsonFileStore<T : Any>(
    context: Context,
    name: String,
    private val decode: (String) -> T?,
    private val encode: (T) -> String,
    private val default: () -> T,
) {
    private val file = File(context.filesDir, name)
    private val tmp = File(context.filesDir, "$name.tmp")

    fun load(): T = synchronized(LOCK) { read() }

    /** 最新の値を読み、[transform] を適用して保存し、その結果を返す */
    fun update(transform: (T) -> T): T = synchronized(LOCK) {
        val next = transform(read())
        tmp.writeText(encode(next))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "failed to write ${file.name}" }
        }
        next
    }

    private fun read(): T = (if (file.exists()) decode(file.readText()) else null) ?: default()

    private companion object {
        val LOCK = Any()
    }
}
