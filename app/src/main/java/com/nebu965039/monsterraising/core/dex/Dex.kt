package com.nebu965039.monsterraising.core.dex

import com.nebu965039.monsterraising.core.pet.Evolution
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Route
import com.nebu965039.monsterraising.core.pet.Stage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 図鑑に載るキャラクター 1 種(基本設計書10.5節)。名前は、本番のキャラクターができるまでの仮の名前。
 * [condition] は「進化因子」(そのキャラクターに進化するための条件)。未発見のキャラクターも含めて、すべて表示する(10.5節)。
 */
data class Species(
    val id: String,
    /** 図鑑の番号(No.) */
    val number: Int,
    val displayName: String,
    val stage: Stage,
    val condition: String,
)

/** 図鑑の一覧と、発見の判定。UI・Android 非依存。 */
object Dex {
    const val NO_ZERO = "no0"

    /**
     * 図鑑に載るキャラクター。今は、DEMO キャラ 1 系統の分だけ:各段階と、成熟期の各ルート(通常ルート・特別ルートの枠)。
     * 特別ルートの進化は、まだ実装されていないので、未発見のまま残る(進化因子の表示は先に用意している)。
     */
    val species: List<Species> = listOf(
        Species("egg", 1, "たまご", Stage.EGG, "最初の姿。1 分で孵化する"),
        Species("infant", 2, "幼年期のキツネ", Stage.INFANT, "卵が孵化する"),
        Species("growth1", 3, "成長期Ⅰのキツネ", Stage.GROWTH_1, "幼年期に 1 日以上滞在し、その時点で満腹度・清潔度が 60 以上"),
        Species("growth2", 4, "成長期Ⅱのキツネ", Stage.GROWTH_2, "成長期Ⅰに 3 日以上滞在し、その時点で満腹度・清潔度が 60 以上"),
        Species("mature_intellect", 5, "成熟キツネ(知力)", Stage.MATURE, "成長期Ⅱに 1 週間以上滞在し、その時点で満腹度・清潔度が 60 以上、かつ知力系有効度が筋力系を上回っている"),
        Species("mature_strength", 6, "成熟キツネ(筋力)", Stage.MATURE, "成長期Ⅱに 1 週間以上滞在し、その時点で満腹度・清潔度が 60 以上、かつ筋力系有効度が知力系を上回っている"),
        Species("mature_weather", 7, "成熟キツネ(天候)", Stage.MATURE, "特定の天候での累積ケア回数が閾値を超える(閾値は未定)"),
        Species("mature_item", 8, "成熟キツネ(アイテム)", Stage.MATURE, "特定のアイテムを所持した状態で進化する(アイテム・条件は未定)"),
        Species("mature_friend_1", 9, "成熟キツネ(フレンドⅠ)", Stage.MATURE, "フレンドシップポイントが 10,000P を超える"),
        Species("mature_friend_2", 10, "成熟キツネ(フレンドⅡ)", Stage.MATURE, "フレンドシップポイントが 100,000P を超える"),
        Species(NO_ZERO, 0, "No.0", Stage.MATURE, "図鑑のすべてのキャラクターを発見する"),
    )

    fun find(id: String): Species? = species.firstOrNull { it.id == id }

    /**
     * いまの育成の状態から、図鑑に載る(発見済みになる)キャラクター。今の段階までに通ってきた段階はすべて含める。
     * 成熟期は、通常ルート(知力/筋力の優劣。同数は知力ルート扱い)。特別ルートの進化は、未実装。
     */
    fun discoveredBy(state: PetState): Set<String> {
        val ids = mutableSetOf("egg")
        if (state.stage.ordinal >= Stage.INFANT.ordinal) ids += "infant"
        if (state.stage.ordinal >= Stage.GROWTH_1.ordinal) ids += "growth1"
        if (state.stage.ordinal >= Stage.GROWTH_2.ordinal) ids += "growth2"
        if (state.stage == Stage.MATURE) {
            ids += if (Evolution.normalRoute(state.stats) == Route.STRENGTH) "mature_strength" else "mature_intellect"
        }
        return ids
    }
}

/** 発見済みのキャラクター。世代交代をまたいで残る(図鑑は育成データのリセットで消えない)。 */
@Serializable
data class DexRecords(val discovered: Set<String> = emptySet()) {
    fun isDiscovered(id: String): Boolean = id in discovered

    /** [ids] を発見済みに加える(図鑑にないものは無視する) */
    fun discover(ids: Set<String>): DexRecords = copy(discovered = discovered + ids.filter { Dex.find(it) != null })

    val count: Int get() = discovered.count { Dex.find(it) != null }

    val total: Int get() = Dex.species.size
}

object DexRecordsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(records: DexRecords): String = json.encodeToString(DexRecords.serializer(), records)

    fun decode(text: String): DexRecords? = try {
        json.decodeFromString(DexRecords.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
