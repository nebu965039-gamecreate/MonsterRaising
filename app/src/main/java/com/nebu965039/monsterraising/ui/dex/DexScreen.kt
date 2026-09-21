package com.nebu965039.monsterraising.ui.dex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.dex.Dex
import com.nebu965039.monsterraising.core.dex.Species
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.dexStore
import com.nebu965039.monsterraising.ui.common.label
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 図鑑画面(基本設計書10.5節)。これまでに進化・出現したキャラクターの一覧。
 * - 未発見のキャラクターは、名前を「???」、姿をシルエットで表示する
 * - 進化因子(そのキャラクターに進化するための条件)は、未発見のものも含めてすべて表示する
 * 発見済みの記録は、世代交代(死亡・寿命)をまたいで残る。キャラクターの絵は、本番のアセットができるまで全種類とも同じ仮の絵。
 */
@Composable
fun DexScreen() {
    val context = LocalContext.current
    val store = remember { dexStore(context) }
    val petStore = remember { PetStore(context) }
    var records by remember { mutableStateOf(store.load()) }
    val sprite by produceState<LoadedSprite?>(null) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, "fox") }
    }
    // 開いたときに、いまの育成の状態で発見を記録する
    LaunchedEffect(Unit) {
        val pet = petStore.load() ?: return@LaunchedEffect
        val found = Dex.discoveredBy(pet)
        records = if (store.load().discovered.containsAll(found)) store.load() else store.update { it.discover(found) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("発見 ${records.count} / ${records.total}", style = MaterialTheme.typography.titleMedium)
        Text(
            "未発見のキャラクターは「???」とシルエットで表示されます。進化因子は、未発見のものも確認できます。",
            style = MaterialTheme.typography.bodySmall,
        )
        Dex.species.forEach { species ->
            SpeciesCard(species, discovered = records.isDiscovered(species.id), sprite = sprite)
        }
    }
}

@Composable
private fun SpeciesCard(species: Species, discovered: Boolean, sprite: LoadedSprite?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).background(Color(0xFFC8DCC8), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                val bitmap = sprite?.frames?.get("normal")
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        // 未発見はシルエット(絵の輪郭だけを黒く塗る)
                        colorFilter = if (discovered) null else ColorFilter.tint(Color(0xFF26282B), BlendMode.SrcIn),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "No.${species.number}  ${if (discovered) species.displayName else "???"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (discovered) Text(species.stage.label(), style = MaterialTheme.typography.labelMedium)
                Text("進化因子: ${species.condition}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
