package com.nebu965039.monsterraising.core.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** アプリの設定(基本設計書10.1節の設定画面)。端末内に保存する。 */
@Serializable
data class AppSettings(
    /** 探索が終わったときに通知を出すか(8.6節)。端末の通知の許可とは別に、アプリ内で切り替えられる */
    val notifyExploration: Boolean = true,
    /** 自分のログイン状況・ステータスをフレンドに表示するか(9節: 表示 ON/OFF)。サーバー(Phase 7)で使う */
    val showStatusToFriends: Boolean = true,
)

object AppSettingsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings)

    fun decode(text: String): AppSettings? = try {
        json.decodeFromString(AppSettings.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
