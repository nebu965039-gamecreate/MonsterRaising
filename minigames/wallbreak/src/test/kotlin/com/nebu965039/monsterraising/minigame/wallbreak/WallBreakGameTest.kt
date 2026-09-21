package com.nebu965039.monsterraising.minigame.wallbreak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WallBreakGameTest {
    private fun game(
        difficulty: WallDifficulty = WallDifficulty.BEGINNER,
        nullify: Int = 0,
        rule: RuleKind = RuleKind.INK,
        seed: Int = 1,
        config: WallBreakConfig = WallBreakConfig(),
    ) = WallBreakGame(difficulty, nullify, config, Random(seed), rule)

    private fun WallBreakGame.answer() = choose(prompt.answerIndex)

    private fun WallBreakGame.wrongIndex() = prompt.walls.indices.first { it != prompt.answerIndex }

    private fun WallBreakGame.miss() = choose(wrongIndex())

    // --- 難易度・色・単語(7.2) ---

    @Test
    fun difficulties_matchTheDesign() {
        assertEquals(2, WallDifficulty.BEGINNER.wallCount)
        assertEquals(3, WallDifficulty.INTERMEDIATE.wallCount)
        assertEquals(4, WallDifficulty.ADVANCED.wallCount)
        assertEquals(15, WallDifficulty.BEGINNER.targetWalls)
        assertEquals(25, WallDifficulty.INTERMEDIATE.targetWalls)
        assertEquals(35, WallDifficulty.ADVANCED.targetWalls)
    }

    @Test
    fun palettes_growWithTheDifficulty() {
        val basic = setOf(WallColor.RED, WallColor.YELLOW, WallColor.BLUE, WallColor.GREEN, WallColor.BLACK)
        assertEquals(basic, WallDifficulty.BEGINNER.palette.toSet())
        assertEquals(basic + WallColor.PURPLE, WallDifficulty.INTERMEDIATE.palette.toSet())
        assertEquals(basic + WallColor.PURPLE + WallColor.ORANGE, WallDifficulty.ADVANCED.palette.toSet())
        assertEquals(5, WallDifficulty.BEGINNER.palette.size)
        assertEquals(6, WallDifficulty.INTERMEDIATE.palette.size)
        assertEquals(7, WallDifficulty.ADVANCED.palette.size)
    }

    @Test
    fun everyColorHasThreeScripts() {
        assertEquals("RED", WallColor.RED.word(Script.ENGLISH))
        assertEquals("赤", WallColor.RED.word(Script.KANJI))
        assertEquals("あか", WallColor.RED.word(Script.HIRAGANA))
        for (c in WallColor.entries) for (s in Script.entries) assertTrue(c.word(s).isNotEmpty())
        // 同じ表記の単語が別の色に重複しない
        for (s in Script.entries) assertEquals(WallColor.entries.size, WallColor.entries.map { it.word(s) }.toSet().size)
    }

    // --- 指令の生成(7.2) ---

    @Test
    fun prompts_alwaysHaveDifferentInkAndMeaning_andValidWalls() {
        for (d in WallDifficulty.entries) for (rule in RuleKind.entries) for (seed in 1..300) {
            val p = PromptGenerator.next(d, rule, Random(seed))
            assertTrue("ink must differ from meaning", p.ink != p.wordMeaning)
            assertEquals(d.wallCount, p.walls.size)
            assertEquals("walls must be distinct", p.walls.size, p.walls.toSet().size)
            assertTrue(d.palette.containsAll(p.walls))
            assertTrue(p.wordMeaning in d.palette && p.ink in d.palette)
            assertEquals(1, p.walls.count { it == p.answerColor }) // 正解は 1 枚だけ
            assertTrue(p.answerIndex in p.walls.indices)
        }
    }

    @Test
    fun answerFollowsTheRule() {
        val ink = PromptGenerator.next(WallDifficulty.ADVANCED, RuleKind.INK, Random(3))
        assertEquals(ink.ink, ink.answerColor)
        val word = PromptGenerator.next(WallDifficulty.ADVANCED, RuleKind.WORD, Random(3))
        assertEquals(word.wordMeaning, word.answerColor)
    }

    @Test
    fun wallsAlwaysContainTheDecoy_soThatTheOtherRuleIsAWrongChoice() {
        for (d in WallDifficulty.entries) for (rule in RuleKind.entries) for (seed in 1..200) {
            val p = PromptGenerator.next(d, rule, Random(seed))
            val decoy = if (rule == RuleKind.INK) p.wordMeaning else p.ink
            assertTrue(decoy in p.walls)
            assertTrue(decoy != p.answerColor)
        }
    }

    @Test
    fun allScriptsAppear() {
        val seen = (1..200).map { PromptGenerator.next(WallDifficulty.BEGINNER, RuleKind.INK, Random(it)).script }.toSet()
        assertEquals(Script.entries.toSet(), seen)
    }

    @Test
    fun wallPositionsVary() {
        val positions = (1..200).map { PromptGenerator.next(WallDifficulty.INTERMEDIATE, RuleKind.INK, Random(it)).answerIndex }.toSet()
        assertEquals(setOf(0, 1, 2), positions)
    }

    // --- ルールの固定 ---

    @Test
    fun ruleIsFixedForTheWholePlay() {
        val g = game(rule = RuleKind.WORD)
        repeat(50) {
            assertEquals(RuleKind.WORD, g.prompt.rule)
            g.answer()
        }
    }

    @Test
    fun ruleIsRandomWhenNotSpecified() {
        val rules = (1..50).map { WallBreakGame(WallDifficulty.BEGINNER, random = Random(it)).rule }.toSet()
        assertEquals(RuleKind.entries.toSet(), rules)
    }

    // --- 正解・不正解(7.2) ---

    @Test
    fun correct_breaksTheWallAndAdvances() {
        val g = game()
        assertEquals(ChoiceOutcome.CORRECT, g.answer())
        assertEquals(1, g.wallsBroken)
        assertEquals(1, g.combo)
        assertEquals(1, g.score)
        assertEquals(60_000L, g.timeLeftMs)
    }

    @Test
    fun wrong_costsThreeSecondsAndBreaksNothing() {
        val g = game()
        assertEquals(ChoiceOutcome.MISS, g.miss())
        assertEquals(0, g.wallsBroken)
        assertEquals(0, g.score)
        assertEquals(57_000L, g.timeLeftMs)
        assertEquals(1, g.mistakes)
    }

    @Test
    fun wrong_resetsTheCombo() {
        val g = game()
        repeat(3) { g.answer() }
        assertEquals(3, g.combo)
        g.miss()
        assertEquals(0, g.combo)
        assertEquals(3, g.maxCombo)
    }

    @Test
    fun aNewPromptFollowsEveryChoice() {
        val g = game()
        val before = g.prompt
        g.miss()
        assertTrue(g.prompt !== before)
    }

    @Test
    fun outOfRangeChoicesAreIgnored() {
        val g = game()
        assertEquals(ChoiceOutcome.IGNORED, g.choose(-1))
        assertEquals(ChoiceOutcome.IGNORED, g.choose(2))
        assertEquals(0, g.wallsBroken)
    }

    // --- ミス無効(7.3) ---

    @Test
    fun nullify_savesTheTime_breaksTheWall_butVoidsTheCombo() {
        val g = game(nullify = 1)
        repeat(2) { g.answer() }
        assertEquals(2, g.combo)
        assertEquals(ChoiceOutcome.NULLIFIED, g.miss())
        assertEquals(60_000L, g.timeLeftMs) // 時間は減らない
        assertEquals(3, g.wallsBroken) // 壁は破壊される
        assertEquals(0, g.combo) // コンボは無効
        assertEquals(0, g.mistakes)
        assertEquals(0, g.nullifyLeft)
    }

    @Test
    fun nullifiedWall_scoresOnlyTheBasePoint() {
        val g = game(nullify = 1)
        g.miss()
        assertEquals(1, g.score)
    }

    @Test
    fun nullify_isUsedUpAndThenMissesCostTime() {
        val g = game(nullify = 2)
        assertEquals(ChoiceOutcome.NULLIFIED, g.miss())
        assertEquals(ChoiceOutcome.NULLIFIED, g.miss())
        assertEquals(ChoiceOutcome.MISS, g.miss())
        assertEquals(57_000L, g.timeLeftMs)
        assertEquals(2, g.result().nullifiedUsed)
    }

    @Test
    fun nullify_isNotSpentOnCorrectAnswers() {
        val g = game(nullify = 1)
        repeat(5) { g.answer() }
        assertEquals(1, g.nullifyLeft)
    }

    @Test
    fun negativeNullifyCountIsTreatedAsZero() {
        assertEquals(0, game(nullify = -3).nullifyLeft)
    }

    // --- スコア(7.4) ---

    @Test
    fun comboMultiplier_raisesThePointsPerWall_andIsCapped() {
        val g = game(difficulty = WallDifficulty.ADVANCED)
        repeat(11) { g.answer() }
        // 1 + 1.1 + … + 2.0 = 16.5
        assertEquals(16, g.score)
        repeat(4) { g.answer() } // 倍率は 2.0 で頭打ち: +8 → 24.5
        assertEquals(24, g.score)
        assertEquals(15, g.wallsBroken)
    }

    @Test
    fun scoreFloorsToAnInteger() {
        val g = game()
        g.answer() // 1.0
        g.answer() // +1.1 = 2.1
        assertEquals(2, g.score)
    }

    @Test
    fun theComboStartsOverAfterAMiss() {
        val g = game()
        repeat(5) { g.answer() }
        g.miss()
        val before = g.score
        g.answer() // 等倍に戻る
        assertEquals(before + 1, g.score)
    }

    // --- クリア(7.2) ---

    @Test
    fun clear_isLatchedAtTheTargetWallCount() {
        val g = game(difficulty = WallDifficulty.BEGINNER)
        repeat(14) { g.answer() }
        assertNull(g.clearedAtMs)
        assertFalse(g.result().cleared)
        g.tick(2_000)
        g.answer()
        assertNotNull(g.clearedAtMs)
        assertEquals(2_000L, g.clearedAtMs)
        assertTrue(g.result().cleared)
        assertEquals(WallBreakStatus.Playing, g.status) // 目標に届いても制限時間まで続けられる
    }

    @Test
    fun clear_countsNullifiedWalls() {
        val g = game(nullify = 3)
        repeat(12) { g.answer() }
        repeat(3) { g.miss() }
        assertTrue(g.result().cleared)
    }

    @Test
    fun clear_isBasedOnWallsBroken_notOnTheComboScore() {
        val g = game()
        repeat(15) { g.answer() }
        assertTrue(g.score > 15) // コンボでスコアは突破数を上回るが、クリアは突破数で判定する
        assertEquals(15, g.wallsBroken)
        assertTrue(g.result().cleared)
    }

    // --- 時間(7.2) ---

    @Test
    fun timeUp_endsTheGameAtSixtySeconds() {
        val g = game()
        g.tick(59_999)
        assertEquals(WallBreakStatus.Playing, g.status)
        g.tick(10)
        assertEquals(WallBreakStatus.TimeUp, g.status)
        assertEquals(0L, g.timeLeftMs)
        assertEquals(60_000L, g.elapsedMs)
    }

    @Test
    fun penalties_shortenTheGame() {
        val g = game()
        g.miss()
        g.tick(56_999)
        assertEquals(WallBreakStatus.Playing, g.status)
        g.tick(1)
        assertEquals(WallBreakStatus.TimeUp, g.status)
    }

    @Test
    fun aPenaltyThatEmptiesTheClock_endsTheGameImmediately() {
        val g = game()
        g.tick(58_000)
        g.miss() // 残り 2 秒 → -3 秒で 0
        assertEquals(WallBreakStatus.TimeUp, g.status)
        assertEquals(0L, g.timeLeftMs)
    }

    @Test
    fun afterTheEnd_choicesAreIgnored() {
        val g = game()
        g.tick(70_000)
        assertEquals(ChoiceOutcome.IGNORED, g.answer())
        assertEquals(0, g.wallsBroken)
        g.tick(1_000)
        assertEquals(60_000L, g.elapsedMs)
    }

    @Test
    fun result_carriesEverything() {
        val g = game(difficulty = WallDifficulty.INTERMEDIATE, nullify = 1, rule = RuleKind.WORD)
        g.answer()
        g.miss() // ミス無効
        g.miss() // ミス
        val r = g.result()
        assertEquals(WallDifficulty.INTERMEDIATE, r.difficulty)
        assertEquals(RuleKind.WORD, r.rule)
        assertEquals(2, r.wallsBroken)
        assertEquals(1, r.mistakes)
        assertEquals(1, r.nullifiedUsed)
        assertEquals(1, r.maxCombo)
    }

    // --- 総当たり(不変条件) ---

    @Test
    fun randomPlay_keepsTheInvariants() {
        for (seed in 1..100) {
            val rnd = Random(seed)
            val d = WallDifficulty.entries[rnd.nextInt(3)]
            val g = WallBreakGame(d, nullifyCount = rnd.nextInt(4), random = Random(seed))
            var steps = 0
            var last = 0
            while (!g.isFinished && steps < 3_000) {
                when (rnd.nextInt(3)) {
                    0 -> g.tick(rnd.nextLong(1, 800))
                    1 -> g.choose(g.prompt.answerIndex)
                    else -> g.choose(rnd.nextInt(-1, d.wallCount + 1))
                }
                assertTrue(g.timeLeftMs in 0..60_000)
                assertTrue(g.wallsBroken >= last)
                assertTrue(g.score >= 0 && g.score >= g.wallsBroken.coerceAtMost(1) - 1)
                assertTrue(g.nullifyLeft >= 0)
                last = g.wallsBroken
                steps++
            }
        }
    }
}
