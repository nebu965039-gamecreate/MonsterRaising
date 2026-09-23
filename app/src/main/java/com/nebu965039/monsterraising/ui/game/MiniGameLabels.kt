package com.nebu965039.monsterraising.ui.game

import com.nebu965039.monsterraising.core.minigame.MiniGame

/** ミニゲームの表示名。画面タイトル・フレンド画面の記録表示などで共通に使う。 */
fun MiniGame.title(): String = when (this) {
    MiniGame.PUZZLE -> "落ち物パズル"
    MiniGame.CARD -> "カード × NPC 戦"
    MiniGame.WALL_BREAK -> "壁破りゲーム"
}
