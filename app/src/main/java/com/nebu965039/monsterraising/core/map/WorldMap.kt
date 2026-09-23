package com.nebu965039.monsterraising.core.map

import com.nebu965039.monsterraising.core.exploration.ExplorationOverview
import com.nebu965039.monsterraising.core.exploration.ExplorationSite

/** 拠点の種類(基本設計書10.4節): 自宅・ゲーム拠点・探索拠点。 */
enum class LocationKind { HOME, GAME_BASE, EXPLORATION }

/**
 * ワールドマップ上の拠点の識別子。将来、拠点を追加するときはここに足す(10.4節)。
 * [MOUNTAIN] は「森」に改名する前の名残(識別子はそのまま残す)。
 * ゲーム拠点は、ミニゲーム1本につき1つの建物として地図上に3つ分ける(8.1節・確定 2026-09-23)。
 */
enum class MapLocationId { HOME, GAME_PUZZLE, GAME_CARD, GAME_WALLBREAK, CAVE, COAST, MOUNTAIN }

/**
 * ワールドマップ上の拠点 1 つ。[x]・[y] は地図の左上を (0,0)、右下を (1,1) としたアイコンの中心位置。
 * [site] は、探索拠点のときに対応する探索の場所。
 */
data class MapLocation(
    val id: MapLocationId,
    val displayName: String,
    val kind: LocationKind,
    val icon: String,
    val x: Float,
    val y: Float,
    /** 未解放の拠点はグレーアウトして、選べない(10.4節) */
    val unlocked: Boolean,
    val site: ExplorationSite? = null,
)

/**
 * ワールドマップ(基本設計書10.4節)の拠点の配置と、現在地。UI・Android 非依存。
 * 拠点間を結ぶ固定のルートは持たない(プレイヤーが自由に行き来している感覚を優先する)。
 */
object WorldMap {
    /** すべての拠点。今は全部解放済み。将来の拠点追加や、解放条件のある拠点は [locations] の引数で制御する */
    val allIds: Set<MapLocationId> = MapLocationId.entries.toSet()

    /**
     * 拠点の定義(名称・アイコン・位置)。名称は、メイン画面の背景の拠点(廃屋など。10.3節)と重複しないようにする。
     * ゲーム拠点はミニゲーム1本につき1つの建物として、3つに分けて配置する(8.1節・確定 2026-09-23)。
     */
    private val definitions: List<MapLocation> = listOf(
        MapLocation(MapLocationId.HOME, "自宅", LocationKind.HOME, "🏠", 0.28f, 0.72f, true),
        MapLocation(MapLocationId.GAME_PUZZLE, "落ち物パズル", LocationKind.GAME_BASE, "🎮", 0.62f, 0.50f, true),
        MapLocation(MapLocationId.GAME_CARD, "カード × NPC 戦", LocationKind.GAME_BASE, "🎮", 0.86f, 0.52f, true),
        MapLocation(MapLocationId.GAME_WALLBREAK, "壁破りゲーム", LocationKind.GAME_BASE, "🎮", 0.76f, 0.82f, true),
        MapLocation(MapLocationId.CAVE, ExplorationSite.CAVE.displayName, LocationKind.EXPLORATION, "🕳️", 0.20f, 0.26f, true, ExplorationSite.CAVE),
        MapLocation(MapLocationId.MOUNTAIN, ExplorationSite.MOUNTAIN.displayName, LocationKind.EXPLORATION, "🌲", 0.50f, 0.12f, true, ExplorationSite.MOUNTAIN),
        MapLocation(MapLocationId.COAST, ExplorationSite.COAST.displayName, LocationKind.EXPLORATION, "🏖️", 0.80f, 0.30f, true, ExplorationSite.COAST),
    )

    /** 地図に並べる拠点。[unlocked] に含まれない拠点は未解放(グレーアウト)になる */
    fun locations(unlocked: Set<MapLocationId> = allIds): List<MapLocation> =
        definitions.map { it.copy(unlocked = it.id in unlocked) }

    fun locationOf(site: ExplorationSite): MapLocationId =
        definitions.first { it.site == site }.id

    /**
     * 現在地(育成中のキャラクターの小さいアイコンを表示する場所。10.4節)。
     * 探索に出ている間は、その探索拠点(複数なら、最も早く終わるもの)。終わって受け取り待ちの拠点も、受け取るまではそこにいる。
     * それ以外は自宅。
     */
    fun currentLocation(exploration: ExplorationOverview): MapLocationId {
        val site = exploration.inProgress.firstOrNull()?.site ?: exploration.finished.firstOrNull()?.site
        return site?.let { locationOf(it) } ?: MapLocationId.HOME
    }
}
