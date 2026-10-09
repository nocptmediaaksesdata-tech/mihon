package mihon.feature.mihonbareng

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import mihon.feature.mihonbareng.model.BarengSyncMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class MihonBarengPreferences(
    preferenceStore: PreferenceStore,
) {
    companion object {
        const val DEFAULT_FIREBASE_URL = "https://mihon-bareng-default-rtdb.asia-southeast1.firebasedatabase.app/"
    }

    val userName: Preference<String> = preferenceStore.getString(
        "pref_mihon_bareng_username",
        "Pembaca",
    )

    val defaultSyncMode: Preference<BarengSyncMode> = preferenceStore.getEnum(
        "pref_mihon_bareng_default_sync_mode",
        BarengSyncMode.STRICT,
    )

    val customFirebaseDatabaseUrl: Preference<String> = preferenceStore.getString(
        "pref_mihon_bareng_firebase_url",
        DEFAULT_FIREBASE_URL,
    )

    val lastRoomCode: Preference<String> = preferenceStore.getString(
        "pref_mihon_bareng_last_room_code",
        "",
    )

    val localUserId: Preference<String> = preferenceStore.getString(
        "pref_mihon_bareng_local_uid",
        "",
    )

    fun getOrCreateUserId(): String {
        val existing = localUserId.get().trim()
        if (existing.isNotEmpty()) return existing
        val newId = "user_${UUID.randomUUID().toString().take(8)}"
        localUserId.set(newId)
        return newId
    }
}
