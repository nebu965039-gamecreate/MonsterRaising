package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.minigame.puzzle.Difficulty
import com.nebu965039.monsterraising.minigame.puzzle.PuzzleResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 記録の更新結果。 */
data class PuzzleRecordUpdate(
    val records: PuzzleRecords,
    val isNewHighScore: Boolean,
    /** その難易度を初めてクリアした(上級の初回クリアの報酬判定用。8.5節) */
    val isFirstClear: Boolean,
)

/** 落ち物パズルのローカル記録(5.5節: ローカルのハイスコア)。難易度は列挙名をキーにして保存する。 */
@Serializable
data class PuzzleRecords(
    val highScores: Map<String, Int> = emptyMap(),
    val clearedDifficulties: Set<String> = emptySet(),
) {
    fun highScore(difficulty: Difficulty): Int = highScores[difficulty.name] ?: 0

    fun hasCleared(difficulty: Difficulty): Boolean = difficulty.name in clearedDifficulties

    /** 1 プレイの結果を記録に反映する。最終スコアは、そのプレイの結果として保存される(5.3節) */
    fun record(result: PuzzleResult): PuzzleRecordUpdate {
        val key = result.difficulty.name
        val isNewHigh = result.score > highScore(result.difficulty)
        val firstClear = result.cleared && !hasCleared(result.difficulty)
        val next = copy(
            highScores = if (isNewHigh) highScores + (key to result.score) else highScores,
            clearedDifficulties = if (result.cleared) clearedDifficulties + key else clearedDifficulties,
        )
        return PuzzleRecordUpdate(next, isNewHigh, firstClear)
    }
}

object PuzzleRecordsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(records: PuzzleRecords): String = json.encodeToString(PuzzleRecords.serializer(), records)

    /** 壊れたデータは null(呼び出し側で空の記録から始める) */
    fun decode(text: String): PuzzleRecords? = try {
        json.decodeFromString(PuzzleRecords.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
