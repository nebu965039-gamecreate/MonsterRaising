package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.dex.DexRecords
import com.nebu965039.monsterraising.core.dex.DexRecordsCodec
import com.nebu965039.monsterraising.core.friends.FriendshipState
import com.nebu965039.monsterraising.core.friends.FriendshipStateCodec
import com.nebu965039.monsterraising.core.settings.AppSettings
import com.nebu965039.monsterraising.core.settings.AppSettingsCodec

/** 図鑑(発見済みのキャラクター)の保存。 */
fun dexStore(context: Context) = JsonFileStore(context, "dex.json", DexRecordsCodec::decode, DexRecordsCodec::encode, ::DexRecords)

/** フレンドシップポイントと、今日送った「いいね」の保存。 */
fun friendshipStore(context: Context) =
    JsonFileStore(context, "friendship.json", FriendshipStateCodec::decode, FriendshipStateCodec::encode, ::FriendshipState)

/** アプリの設定の保存。 */
fun settingsStore(context: Context) = JsonFileStore(context, "settings.json", AppSettingsCodec::decode, AppSettingsCodec::encode, ::AppSettings)
