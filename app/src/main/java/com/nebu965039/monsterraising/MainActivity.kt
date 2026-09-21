package com.nebu965039.monsterraising

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nebu965039.monsterraising.ui.demo.DemoScreen
import com.nebu965039.monsterraising.ui.demo.PetDemoScreen

class MainActivity : ComponentActivity() {
    /** 前面に戻るたびに増える。ウィジェットで操作された状態を画面に読み直させるために使う */
    private var resumeTick by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Column(Modifier.safeDrawingPadding()) {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("育成(Phase 2)") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("アニメ確認(Phase 1)") })
                        }
                        if (tab == 0) PetDemoScreen(resumeTick = resumeTick) else DemoScreen()
                    }
                }
            }
        }
    }
}
