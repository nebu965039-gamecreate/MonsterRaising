package com.nebu965039.monsterraising.minigame.puzzle

import kotlin.random.Random

/** ブロックの供給元。ゲーム本体は [PieceBag] を使い、テストでは順番を決め打ちにした実装に差し替える。 */
fun interface PieceSource {
    fun next(): PieceType
}

/** 7 種を 1 巡ごとにシャッフルして順に出す(同じ形が偏らない)。乱数を渡せるのでテストで再現できる。 */
class PieceBag(private val random: Random = Random.Default) : PieceSource {
    private val queue = ArrayDeque<PieceType>()

    override fun next(): PieceType {
        if (queue.isEmpty()) queue.addAll(PieceType.entries.shuffled(random))
        return queue.removeFirst()
    }
}
