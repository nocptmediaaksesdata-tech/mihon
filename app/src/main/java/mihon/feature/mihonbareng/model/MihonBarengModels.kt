package mihon.feature.mihonbareng.model

import androidx.annotation.Keep

enum class BarengSyncMode(val label: String) {
    STRICT("Strict (Host Mengendalikan Halaman)"),
    LOOSE("Loose (Bebas Baca Masing-masing)"),
}

@Keep
data class RoomInfo(
    val roomId: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val mangaTitle: String = "",
    val mangaUrl: String = "",
    val sourceId: Long = 0L,
    val chapterUrl: String = "",
    val chapterName: String = "",
    val syncMode: String = BarengSyncMode.STRICT.name,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
) {
    val mode: BarengSyncMode
        get() = try {
            BarengSyncMode.valueOf(syncMode)
        } catch (_: Exception) {
            BarengSyncMode.STRICT
        }
}

@Keep
data class RoomState(
    val chapterUrl: String = "",
    val pageIndex: Int = 0,
    val updatedBy: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Keep
data class Participant(
    val uid: String = "",
    val name: String = "",
    val isHost: Boolean = false,
    val currentPage: Int = 0,
    val lastActive: Long = System.currentTimeMillis(),
)

@Keep
data class LiveReaction(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val emoji: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

@Keep
data class PointerPosition(
    val uid: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val active: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
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
