package mihon.feature.mihonbareng.model

import androidx.annotation.Keep
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

enum class BarengSyncMode(val label: String) {
    STRICT("Strict (Host Mengendalikan Halaman)"),
    LOOSE("Loose (Bebas Baca Masing-masing)"),
}

@Keep
@IgnoreExtraProperties
data class RoomInfo(
    var roomId: String = "",
    var hostUid: String = "",
    var hostName: String = "",
    var mangaTitle: String = "",
    var mangaUrl: String = "",
    var sourceId: Long = 0L,
    var chapterUrl: String = "",
    var chapterName: String = "",
    var syncMode: String = BarengSyncMode.STRICT.name,
    var createdAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = true,
) {
    @get:Exclude
    val mode: BarengSyncMode
        get() = try {
            BarengSyncMode.valueOf(syncMode)
        } catch (_: Exception) {
            BarengSyncMode.STRICT
        }
}

@Keep
@IgnoreExtraProperties
data class RoomState(
    var chapterUrl: String = "",
    var pageIndex: Int = 0,
    var updatedBy: String = "",
    var updatedAt: Long = System.currentTimeMillis(),
)

@Keep
@IgnoreExtraProperties
data class Participant(
    var uid: String = "",
    var name: String = "",
    var isHost: Boolean = false,
    var currentPage: Int = 0,
    var lastActive: Long = System.currentTimeMillis(),
)

@Keep
@IgnoreExtraProperties
data class LiveReaction(
    var id: String = "",
    var uid: String = "",
    var userName: String = "",
    var emoji: String = "",
    var timestamp: Long = System.currentTimeMillis(),
)

@Keep
@IgnoreExtraProperties
data class PointerPosition(
    var uid: String = "",
    var x: Float = 0f,
    var y: Float = 0f,
    var active: Boolean = false,
    var timestamp: Long = System.currentTimeMillis(),
)

sealed interface BarengSessionState {
    data object Idle : BarengSessionState
    data class Connecting(val message: String) : BarengSessionState
    data class Active(
        val roomInfo: RoomInfo,
        val isHost: Boolean,
        val currentUserId: String,
        val currentUserName: String,
    ) : BarengSessionState
    data class Error(val message: String) : BarengSessionState
}
