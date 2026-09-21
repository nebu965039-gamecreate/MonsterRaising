package com.nebu965039.monsterraising.minigame.wallbreak

/** 壁の色(基本設計書7.2節)。 */
enum class WallColor(private val english: String, private val kanji: String, private val hiragana: String) {
    RED("RED", "赤", "あか"),
    YELLOW("YELLOW", "黄", "きいろ"),
    BLUE("BLUE", "青", "あお"),
    GREEN("GREEN", "緑", "みどり"),
    BLACK("BLACK", "黒", "くろ"),
    PURPLE("PURPLE", "紫", "むらさき"),
    ORANGE("ORANGE", "橙", "おれんじ"),
    ;

    /** 指令に表示する単語(英語・漢字・ひらがなのいずれか) */
    fun word(script: Script): String = when (script) {
        Script.ENGLISH -> english
        Script.KANJI -> kanji
        Script.HIRAGANA -> hiragana
    }
}

/** 指令の単語の表記(7.2節)。表記の種類も判断材料の一つになる。 */
enum class Script { ENGLISH, KANJI, HIRAGANA }

/** プレイ中に固定されるルール(7.2節)。 */
enum class RuleKind {
    /** 文字色ルール: インクの色に合う壁を選ぶ */
    INK,

    /** 単語ルール: 書かれている色名の意味に合う壁を選ぶ */
    WORD,
}

/**
 * 難易度(7.2節): 壁の枚数・使う色・クリアに必要な突破数(叩き台)。
 * 基本 5 色(赤・黄・青・緑・黒)に、中級で紫、上級で紫・オレンジが加わる。
 */
enum class WallDifficulty(val wallCount: Int, val palette: List<WallColor>, val targetWalls: Int) {
    BEGINNER(2, BASIC, 15),
    INTERMEDIATE(3, BASIC + WallColor.PURPLE, 25),
    ADVANCED(4, BASIC + WallColor.PURPLE + WallColor.ORANGE, 35),
}

private val BASIC get() = listOf(WallColor.RED, WallColor.YELLOW, WallColor.BLUE, WallColor.GREEN, WallColor.BLACK)
