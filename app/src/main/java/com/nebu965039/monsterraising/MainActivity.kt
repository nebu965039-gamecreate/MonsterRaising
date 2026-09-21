package com.nebu965039.monsterraising

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.nebu965039.monsterraising.ui.nav.AppRoot

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
                Surface { AppRoot(resumeTick = resumeTick) }
            }
        }
    }
}
