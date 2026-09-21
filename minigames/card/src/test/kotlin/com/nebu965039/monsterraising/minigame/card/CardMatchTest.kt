package com.nebu965039.monsterraising.minigame.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardMatchTest {
    private fun c(v: Int, a: Attribute = Attribute.SUNNY) = Card(a, v)

    /**
     * 決め打ちの山札。配札の順は プレイヤー・NPC・プレイヤー・NPC、そのあとは引いた順(プレイヤー → NPC の順に使われる)。
     * 使い切ったら、最後の値を繰り返す(山札切れのテスト以外では使わない)。
     */
    private fun match(
        vararg values: Int,
        level: NpcLevel = NpcLevel.BEGINNER,
        monster: MonsterCard? = null,
    ): CardMatch = CardMatch(level, monster, CardConfig()) { ArrayDeque(values.map { c(it) } + List(20) { c(1) }) }

    private fun CardMatch.round(bet: Int, vararg actions: String) {
        assertTrue(placeBet(bet))
        for (a in actions) when (a) {
            "draw" -> draw()
            "stand" -> stand()
            "monster" -> useMonsterCard()
        }
    }

    // --- 山札・設定 ---

    @Test
    fun deck_hasFiftyCards_fiveAttributesByTenNumbers() {
        val deck = Decks.standard()
        assertEquals(50, deck.size)
        assertEquals(50, deck.toSet().size)
        for (a in Attribute.entries) assertEquals((1..10).toList(), deck.filter { it.attribute == a }.map { it.value })
    }

    @Test
    fun deck_shuffleIsReproducibleWithTheSameSeed() {
        assertEquals(Decks.shuffled(Random(5)).toList(), Decks.shuffled(Random(5)).toList())
        assertFalse(Decks.shuffled(Random(5)).toList() == Decks.standard())
    }

    @Test
    fun npcThresholds_matchTheDesign() {
        assertEquals(15, NpcLevel.BEGINNER.stopAt)
        assertEquals(17, NpcLevel.INTERMEDIATE.stopAt)
        assertEquals(19, NpcLevel.ADVANCED.stopAt)
    }

    @Test
    fun startsWithFiveCoinsEachAndBetsUpToThree() {
        val m = match(1, 1, 1, 1)
        assertEquals(5, m.playerCoins)
        assertEquals(5, m.npcCoins)
        assertEquals(3, m.maxBet())
        assertEquals(Phase.Betting, m.phase)
    }

    @Test
    fun invalidBetsAreRejected() {
        val m = match(1, 1, 1, 1)
        assertFalse(m.placeBet(0))
        assertFalse(m.placeBet(4))
        assertEquals(Phase.Betting, m.phase)
        assertTrue(m.placeBet(3))
        assertEquals(Phase.PlayerTurn, m.phase)
        assertFalse(m.placeBet(1)) // 賭けは 1 ラウンドに 1 回
    }

    // --- 配札 ---

    @Test
    fun dealsTwoCardsEach_andHidesTheNpcSecondCard() {
        val m = match(10, 7, 5, 6)
        m.placeBet(1)
        assertEquals(listOf(10, 5), m.playerHand.map { it.value })
        assertEquals(15, m.playerTotal)
        assertEquals(listOf(7, null), m.npcCardsShown().map { it?.value })
        assertEquals(7, m.npcShownTotal)
        assertEquals(13, m.npcTotal)
    }

    // --- ラウンドの勝敗(6.2) ---

    @Test
    fun higherTotalWins_andTakesTheBet() {
        val m = match(10, 8, 9, 8) // プレイヤー 19、NPC 16(初級は 15 以上で止める)
        m.round(2, "stand")
        assertEquals(RoundWinner.PLAYER, m.lastOutcome!!.winner)
        assertEquals(7, m.playerCoins)
        assertEquals(3, m.npcCoins)
        assertEquals(Phase.RoundOver, m.phase)
    }

    @Test
    fun lowerTotalLoses() {
        val m = match(5, 10, 6, 9) // プレイヤー 11、NPC 19
        m.round(1, "stand")
        assertEquals(RoundWinner.NPC, m.lastOutcome!!.winner)
        assertEquals(4, m.playerCoins)
        assertEquals(6, m.npcCoins)
    }

    @Test
    fun exceedingTheTarget_losesImmediately_andNpcDoesNotDraw() {
        val m = match(10, 5, 9, 4, 5) // プレイヤー 19 → 5 を引いて 24 で超過
        m.round(3, "draw")
        val o = m.lastOutcome!!
        assertTrue(o.playerBust)
        assertEquals(RoundWinner.NPC, o.winner)
        assertEquals(24, o.playerTotal)
        assertEquals(9, o.npcTotal) // NPC は引かない
        assertEquals(2, m.playerCoins)
        assertEquals(8, m.npcCoins)
    }

    @Test
    fun exactlyTheTarget_isNotABust() {
        val m = match(10, 5, 5, 4, 5) // 15 → 5 を引いて 20
        m.round(1, "draw")
        assertEquals(Phase.PlayerTurn, m.phase)
        assertEquals(20, m.playerTotal)
    }

    @Test
    fun npcDrawsUntilItsThreshold() {
        // NPC: 5 + 4 = 9 → 7 を引いて 16(初級の閾値 15 以上で止まる)
        val m = match(10, 5, 5, 4, 7, 3, 3)
        m.round(1, "stand")
        assertEquals(16, m.lastOutcome!!.npcTotal)
    }

    @Test
    fun npcStopsExactlyAtTheThreshold() {
        // NPC: 5 + 4 = 9 → 6 を引いて 15 ちょうどで止まる
        val m = match(10, 5, 5, 4, 6, 3)
        m.round(1, "stand")
        assertEquals(15, m.lastOutcome!!.npcTotal)
    }

    @Test
    fun harderNpcsKeepDrawingLonger() {
        val beginner = match(10, 8, 9, 8, 2, 2, level = NpcLevel.BEGINNER)
        beginner.round(1, "stand")
        assertEquals(16, beginner.lastOutcome!!.npcTotal) // 16 ≥ 15 で止まる
        val advanced = match(10, 8, 9, 8, 2, 2, level = NpcLevel.ADVANCED)
        advanced.round(1, "stand")
        assertEquals(20, advanced.lastOutcome!!.npcTotal) // 16 → 18 → 20(19 以上になるまで引く)
    }

    @Test
    fun npcExceedingTheTarget_losesTheRound() {
        // NPC: 6 + 5 = 11 → 10 を引いて 21 で超過
        val m = match(10, 6, 5, 5, 10)
        m.round(2, "stand")
        val o = m.lastOutcome!!
        assertTrue(o.npcBust)
        assertEquals(RoundWinner.PLAYER, o.winner)
        assertEquals(7, m.playerCoins)
    }

    @Test
    fun equalTotals_isADraw_andNoCoinsMove() {
        val m = match(10, 10, 6, 6) // 16 対 16(NPC は 15 以上なので引かない)
        m.round(3, "stand")
        assertEquals(RoundWinner.DRAW, m.lastOutcome!!.winner)
        assertEquals(5, m.playerCoins)
        assertEquals(5, m.npcCoins)
    }

    @Test
    fun npcHandIsFullyRevealedAtTheEndOfTheRound() {
        val m = match(10, 8, 9, 8)
        m.round(1, "stand")
        assertTrue(m.npcCardsShown().all { it != null })
    }

    // --- マッチの進行と終了(6.2) ---

    @Test
    fun nextRound_movesOnUntilFiveRoundsHavePassed() {
        val m = match(10, 10, 6, 6) // 毎ラウンド引き分け
        repeat(5) { r ->
            assertEquals(r + 1, m.round)
            m.round(1, "stand")
            assertEquals(r == 4, m.endsAfterThisRound())
            assertTrue(m.nextRound())
        }
        assertEquals(Phase.MatchOver, m.phase)
        assertEquals(MatchOutcome.DRAW, m.result()!!.outcome) // 無効試合
        assertEquals(5, m.result()!!.roundsPlayed)
    }

    @Test
    fun matchEndsEarlyWhenACoinStackHitsZero() {
        // NPC が常に勝つ山札(プレイヤー 4、NPC 19)で 3 枚、続けて残りの 2 枚を賭けて負ける
        val lose = CardMatch(NpcLevel.BEGINNER, null, CardConfig()) { ArrayDeque(listOf(c(2), c(10), c(2), c(9)) + List(30) { c(1) }) }
        lose.round(3, "stand") // 4 対 19 で負け
        lose.nextRound()
        assertEquals(2, lose.maxBet()) // 残り 2 枚
        lose.round(2, "stand")
        assertEquals(0, lose.playerCoins)
        assertTrue(lose.endsAfterThisRound())
        lose.nextRound()
        assertEquals(Phase.MatchOver, lose.phase)
        assertEquals(MatchOutcome.LOSE, lose.result()!!.outcome)
        assertEquals(2, lose.result()!!.roundsPlayed)
    }

    @Test
    fun betIsLimitedByWhatTheOpponentCanPay() {
        val win = CardMatch(NpcLevel.BEGINNER, null, CardConfig()) { ArrayDeque(listOf(c(10), c(5), c(9), c(4)) + List(30) { c(1) }) }
        win.round(3, "stand") // 19 対 15(NPC は 9 から 1 を引き続けて 15 で止まる)でプレイヤーの勝ち
        win.nextRound()
        assertEquals(2, win.npcCoins)
        assertEquals(2, win.maxBet()) // 相手の残りが 2 枚なので、賭けられるのは 2 枚まで
    }

    @Test
    fun result_isWinWhenMoreCoinsRemain() {
        val m = CardMatch(NpcLevel.BEGINNER, null, CardConfig()) { ArrayDeque(listOf(c(10), c(5), c(9), c(4)) + List(30) { c(1) }) }
        repeat(5) {
            if (m.phase == Phase.Betting) m.round(1, "stand")
            m.nextRound()
        }
        assertEquals(MatchOutcome.WIN, m.result()!!.outcome)
    }

    @Test
    fun operationsOutOfTurnAreIgnored() {
        val m = match(10, 5, 5, 4)
        assertFalse(m.draw())
        assertFalse(m.stand())
        assertFalse(m.nextRound())
        assertNull(m.result())
        m.placeBet(1)
        assertFalse(m.nextRound())
        m.stand()
        assertFalse(m.draw())
        assertFalse(m.placeBet(1)) // 結果を見て nextRound してから
    }

    // --- モンスターカード(6.4) ---

    @Test
    fun peek_revealsTheHiddenCardOnce() {
        val m = match(5, 5, 5, 9, monster = MonsterCard.PEEK)
        m.placeBet(1)
        assertTrue(m.canUseMonsterCard())
        assertTrue(m.useMonsterCard())
        assertEquals(listOf(5, 9), m.npcCardsShown().map { it?.value })
        assertFalse(m.canUseMonsterCard())
        assertFalse(m.useMonsterCard())
    }

    @Test
    fun redraw_replacesTheLastCard() {
        val m = match(5, 5, 5, 5, 10, 3, monster = MonsterCard.REDRAW)
        m.placeBet(1)
        m.draw() // 10 → 20
        m.useMonsterCard() // 引き直し: 直前の 10 を捨てて 3 を引く
        assertEquals(listOf(5, 5, 3), m.playerHand.map { it.value })
        assertEquals(13, m.playerTotal)
        assertFalse(m.canUseMonsterCard()) // 1 戦に 1 回
    }

    @Test
    fun redraw_canSaveAFreshlyBustedHand_byAnotherBustNo() {
        // 引き直しで別のカードが来ても超えるなら、そのラウンドは負け
        val m = match(10, 5, 9, 4, 8, 6, monster = MonsterCard.REDRAW)
        m.placeBet(1)
        m.useMonsterCard() // 直前の 9 を捨てて 8 を引く → 18
        assertEquals(Phase.PlayerTurn, m.phase)
        m.draw() // 6 → 24 で超過
        assertTrue(m.lastOutcome!!.playerBust)
    }

    @Test
    fun steady_drawsTwoAndKeepsTheOneCloserToTheTarget() {
        // 手札 10+5=15。次の 2 枚は 9(→24 超過)と 4(→19): 4 を選ぶ
        val m = match(10, 5, 5, 5, 9, 4, monster = MonsterCard.STEADY)
        m.placeBet(1)
        m.useMonsterCard()
        m.draw()
        assertEquals(19, m.playerTotal)
        assertEquals(Phase.PlayerTurn, m.phase)
    }

    @Test
    fun steady_prefersTheLargerCardThatStillFits() {
        val m = match(10, 5, 5, 5, 2, 5, monster = MonsterCard.STEADY) // 15 に 2 か 5: 5 を選び 20
        m.placeBet(1)
        m.useMonsterCard()
        m.draw()
        assertEquals(20, m.playerTotal)
    }

    @Test
    fun steady_whenBothExceed_takesTheSmallerOne() {
        val m = match(10, 5, 9, 5, 10, 8, monster = MonsterCard.STEADY) // 19 に 10 か 8: どちらも超えるので 8(27)
        m.placeBet(1)
        m.useMonsterCard()
        m.draw()
        assertEquals(27, m.playerTotal)
        assertTrue(m.lastOutcome!!.playerBust)
    }

    @Test
    fun endure_forgivesOneBust_thenStopsThere() {
        val m = match(10, 5, 9, 4, 5, monster = MonsterCard.ENDURE) // 19 に 5 で超過 → 耐える
        m.placeBet(2)
        m.draw()
        val o = m.lastOutcome!!
        assertFalse(o.playerBust)
        assertTrue(o.endured)
        assertEquals(19, o.playerTotal) // 超えたカードは捨てられる
        assertTrue(m.monsterCardUsed)
        assertEquals(Phase.RoundOver, m.phase)
    }

    @Test
    fun endure_worksOnlyOncePerMatch() {
        val m = match(10, 5, 9, 4, 5, 5, monster = MonsterCard.ENDURE)
        m.round(1, "draw") // 1 回目は耐える
        m.nextRound()
        m.round(1, "draw") // 2 回目は超過で負け
        assertTrue(m.lastOutcome!!.playerBust)
    }

    @Test
    fun endureIsNotManuallyUsable() {
        val m = match(10, 5, 9, 4, monster = MonsterCard.ENDURE)
        m.placeBet(1)
        assertFalse(m.canUseMonsterCard())
        assertFalse(m.useMonsterCard())
    }

    @Test
    fun noMonsterCard_meansNothingToUse() {
        val m = match(10, 5, 9, 4)
        m.placeBet(1)
        assertFalse(m.canUseMonsterCard())
    }

    @Test
    fun monsterCardUsageIsRecordedInTheResult() {
        val m = match(10, 10, 6, 6, monster = MonsterCard.PEEK)
        m.placeBet(1)
        m.useMonsterCard()
        m.stand()
        repeat(4) { m.nextRound(); m.round(1, "stand") }
        m.nextRound()
        val r = m.result()!!
        assertEquals(MonsterCard.PEEK, r.monsterCard)
        assertTrue(r.monsterCardUsed)
    }

    // --- 総当たり(不変条件) ---

    @Test
    fun randomPlay_neverBreaksInvariants() {
        for (seed in 1..200) {
            val rnd = Random(seed)
            val monster = listOf(null, *MonsterCard.entries.toTypedArray())[rnd.nextInt(5)]
            val level = NpcLevel.entries[rnd.nextInt(3)]
            val m = CardMatch(level, monster, CardConfig()) { Decks.shuffled(rnd) }
            var steps = 0
            while (!m.isMatchOver && steps < 500) {
                when (m.phase) {
                    Phase.Betting -> m.placeBet(rnd.nextInt(1, m.maxBet() + 1))
                    Phase.PlayerTurn -> when (rnd.nextInt(4)) {
                        0 -> m.useMonsterCard()
                        1, 2 -> m.draw()
                        else -> m.stand()
                    }
                    Phase.RoundOver -> m.nextRound()
                    Phase.MatchOver -> Unit
                }
                assertEquals(10, m.playerCoins + m.npcCoins) // コインは増減せず移動するだけ
                assertTrue(m.playerCoins in 0..10 && m.npcCoins in 0..10)
                assertTrue(m.round in 1..5)
                steps++
            }
            assertTrue("seed $seed did not finish", m.isMatchOver)
            assertTrue(m.history.size in 1..5)
        }
    }
}
