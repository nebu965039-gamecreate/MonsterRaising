package com.nebu965039.monsterraising.minigame.wallbreak

import kotlin.random.Random

/**
 * 1 回分の指令と、正面に並ぶ壁(基本設計書7.2節)。
 * 指令は、色名の単語([wordMeaning])を、その意味とは異なるインク色([ink])で表示したもの。
 */
data class Prompt(
    /** 書かれている単語が表す色 */
    val wordMeaning: WallColor,
    /** 単語を表示するインクの色(単語の意味とは必ず異なる) */
    val ink: WallColor,
    val script: Script,
    /** 左から順に並ぶ壁の色(重複なし) */
    val walls: List<WallColor>,
    val rule: RuleKind,
) {
    val word: String get() = wordMeaning.word(script)

    /** 正解の壁の色: 文字色ルールならインクの色、単語ルールなら単語の意味 */
    val answerColor: WallColor get() = if (rule == RuleKind.INK) ink else wordMeaning

    val answerIndex: Int get() = walls.indexOf(answerColor)
}

object PromptGenerator {
    /**
     * 指令を 1 つ作る。壁には、正解の色に加えて、もう一方のルールでの正解の色(ひっかけ)を必ず含める。
     * 残りの壁は、使える色からランダムに選ぶ。壁の並び順もランダム。
     */
    fun next(difficulty: WallDifficulty, rule: RuleKind, random: Random): Prompt {
        val palette = difficulty.palette
        val meaning = palette.random(random)
        val ink = (palette - meaning).random(random)
        val script = Script.entries.random(random)
        val answer = if (rule == RuleKind.INK) ink else meaning
        val decoy = if (rule == RuleKind.INK) meaning else ink
        val others = (palette - answer - decoy).shuffled(random).take(difficulty.wallCount - 2)
        val walls = (listOf(answer, decoy) + others).shuffled(random)
        return Prompt(meaning, ink, script, walls, rule)
    }
}
