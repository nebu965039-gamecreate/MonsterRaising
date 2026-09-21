package com.nebu965039.monsterraising.core.map

import com.nebu965039.monsterraising.core.exploration.ExplorationSite
import com.nebu965039.monsterraising.core.minigame.MiniGame

/**
 * アプリの画面(基本設計書10.1節)。メイン画面を起点に、ワールドマップから各拠点の画面へ遷移する。
 * 戻る操作([parent])は、拠点の画面 → ワールドマップ、ゲーム拠点の各ミニゲーム → ゲーム選択 → ワールドマップ → メイン画面の順。
 * UI・Android 非依存。画面の状態を保存できるよう、文字列の [key] に変換できる。
 */
sealed interface Destination {
    /** メイン画面(育成画面) */
    data object Home : Destination

    data object WorldMap : Destination

    /** ゲーム拠点のゲーム選択画面 */
    data object GameSelect : Destination

    data class Game(val game: MiniGame) : Destination

    /** 探索拠点の画面 */
    data class Site(val site: ExplorationSite) : Destination

    /** 図鑑画面(10.5節) */
    data object Dex : Destination

    /** フレンド画面(9節) */
    data object Friends : Destination

    /** 設定画面(10.1節) */
    data object Settings : Destination

    /** 開発用(動作確認用の操作・アニメーション確認) */
    data object Dev : Destination

    /** 戻る操作で移る画面。メイン画面は戻る先がない(null) */
    val parent: Destination?
        get() = when (this) {
            Home -> null
            WorldMap -> Home
            GameSelect -> WorldMap
            is Game -> GameSelect
            is Site -> WorldMap
            Dex, Friends, Settings, Dev -> Home
        }

    /** 保存用の文字列 */
    val key: String
        get() = when (this) {
            Home -> "home"
            WorldMap -> "map"
            GameSelect -> "games"
            is Game -> "game:${game.name}"
            is Site -> "site:${site.name}"
            Dex -> "dex"
            Friends -> "friends"
            Settings -> "settings"
            Dev -> "dev"
        }

    companion object {
        /** 保存した文字列から画面を復元する。読めなければ null(メイン画面へ戻す) */
        fun fromKey(key: String): Destination? = when {
            key == "home" -> Home
            key == "map" -> WorldMap
            key == "games" -> GameSelect
            key == "dev" -> Dev
            key == "dex" -> Dex
            key == "friends" -> Friends
            key == "settings" -> Settings
            key.startsWith("game:") -> runCatching { Game(MiniGame.valueOf(key.removePrefix("game:"))) }.getOrNull()
            key.startsWith("site:") -> runCatching { Site(ExplorationSite.valueOf(key.removePrefix("site:"))) }.getOrNull()
            else -> null
        }

        /** ワールドマップで拠点を選んだときに移る画面(10.4節: 拠点に対応する画面へ遷移する) */
        fun forLocation(id: MapLocationId): Destination = when (id) {
            MapLocationId.HOME -> Home
            MapLocationId.GAME_BASE -> GameSelect
            MapLocationId.CAVE -> Site(ExplorationSite.CAVE)
            MapLocationId.COAST -> Site(ExplorationSite.COAST)
            MapLocationId.MOUNTAIN -> Site(ExplorationSite.MOUNTAIN)
        }
    }
}
