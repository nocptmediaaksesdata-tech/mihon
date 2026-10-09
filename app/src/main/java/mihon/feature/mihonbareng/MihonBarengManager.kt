package mihon.feature.mihonbareng

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import logcat.LogPriority
import mihon.feature.mihonbareng.model.BarengSessionState
import mihon.feature.mihonbareng.model.BarengSyncMode
import mihon.feature.mihonbareng.model.LiveReaction
import mihon.feature.mihonbareng.model.Participant
import mihon.feature.mihonbareng.model.PointerPosition
import mihon.feature.mihonbareng.model.RoomInfo
import mihon.feature.mihonbareng.model.RoomState
import tachiyomi.core.common.util.system.logcat
import java.util.UUID
import kotlin.random.Random

@Inject
@SingleIn(AppScope::class)
class MihonBarengManager(
    private val context: Context,
    private val preferences: MihonBarengPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow<BarengSessionState>(BarengSessionState.Idle)
    val sessionState = _sessionState.asStateFlow()

    private val _participants = MutableStateFlow<List<Participant>>(emptyList())
    val participants = _participants.asStateFlow()

    private val _remoteState = MutableStateFlow<RoomState?>(null)
    val remoteState = _remoteState.asStateFlow()

    private val _reactionsFlow = MutableSharedFlow<LiveReaction>(extraBufferCapacity = 64)
    val reactionsFlow = _reactionsFlow.asSharedFlow()

    private val _pointerFlow = MutableStateFlow<PointerPosition?>(null)
    val pointerFlow = _pointerFlow.asStateFlow()

    private var currentRoomRef: DatabaseReference? = null
    private var roomStateListener: ValueEventListener? = null
    private var participantsListener: ValueEventListener? = null
    private var reactionListener: ChildEventListener? = null
    private var pointerListener: ValueEventListener? = null

    private var currentUserId: String = ""

    init {
        ensureFirebaseInitialized()
    }

    private fun ensureFirebaseInitialized(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                val customUrl = preferences.customFirebaseDatabaseUrl.get().trim()
                    .ifEmpty { MihonBarengPreferences.DEFAULT_FIREBASE_URL }
                val options = FirebaseOptions.Builder()
                    .setDatabaseUrl(customUrl)
                    .setApiKey("AIzaSyMihonBarengCustomFallbackApiKey001")
                    .setApplicationId("1:123456789012:android:mihonbarengfallback")
                    .setProjectId("mihon-bareng")
                    .build()
                FirebaseApp.initializeApp(context, options)
                true
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize Firebase for MihonBareng" }
            false
        }
    }

    private fun getDatabase(): FirebaseDatabase? {
        if (!ensureFirebaseInitialized()) {
            return null
        }
        val customUrl = preferences.customFirebaseDatabaseUrl.get().trim()
            .ifEmpty { MihonBarengPreferences.DEFAULT_FIREBASE_URL }
        return try {
            FirebaseDatabase.getInstance(customUrl)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error obtaining FirebaseDatabase instance with URL: $customUrl" }
            try {
                FirebaseDatabase.getInstance()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private suspend fun obtainUserId(): String {
        if (currentUserId.isNotEmpty()) return currentUserId

        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                currentUserId = user.uid
                return currentUserId
            }
            val authResult = auth.signInAnonymously().await()
            val uid = authResult.user?.uid
            if (!uid.isNullOrEmpty()) {
                currentUserId = uid
                return currentUserId
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) {
                "Firebase Anonymous Auth not available, using persistent device UUID fallback"
            }
        }

        currentUserId = preferences.getOrCreateUserId()
        return currentUserId
    }

    fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "MB-$code"
    }

    suspend fun createRoom(
        mangaTitle: String,
        mangaUrl: String,
        sourceId: Long,
        chapterUrl: String,
        chapterName: String,
        syncMode: BarengSyncMode = preferences.defaultSyncMode.get(),
    ): Result<RoomInfo> {
        val db = getDatabase()
            ?: return Result.failure(
                IllegalStateException(
                    "Firebase Realtime Database belum terkonfigurasi. Pastikan google-services.json ada atau masukkan Database URL di Pengaturan.",
                ),
            )

        _sessionState.value = BarengSessionState.Connecting("Membuat room MihonBareng...")

        val uid = obtainUserId()
        val userName = preferences.userName.get().ifBlank { "Host" }
        val roomId = generateRoomCode()

        val roomInfo = RoomInfo(
            roomId = roomId,
            hostUid = uid,
            hostName = userName,
            mangaTitle = mangaTitle,
            mangaUrl = mangaUrl,
            sourceId = sourceId,
            chapterUrl = chapterUrl,
            chapterName = chapterName,
            syncMode = syncMode.name,
            createdAt = System.currentTimeMillis(),
            isActive = true,
        )

        val roomRef = db.getReference("mihonbareng/rooms").child(roomId)
        currentRoomRef = roomRef

        return try {
            roomRef.child("info").setValue(roomInfo).await()
            roomRef.child("state").setValue(RoomState(chapterUrl = chapterUrl, pageIndex = 0, updatedBy = uid)).await()

            val hostParticipant = Participant(
                uid = uid,
                name = userName,
                isHost = true,
                currentPage = 0,
                lastActive = System.currentTimeMillis(),
            )
            val participantRef = roomRef.child("participants").child(uid)
            participantRef.setValue(hostParticipant).await()
            participantRef.onDisconnect().removeValue()

            attachRoomListeners(roomRef, uid)

            preferences.lastRoomCode.set(roomId)
            _sessionState.value = BarengSessionState.Active(
                roomInfo = roomInfo,
                isHost = true,
                currentUserId = uid,
                currentUserName = userName,
            )

            Result.success(roomInfo)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to create room: $roomId" }
            _sessionState.value = BarengSessionState.Error("Gagal membuat room: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    suspend fun joinRoom(roomCode: String): Result<RoomInfo> {
        val db = getDatabase()
            ?: return Result.failure(IllegalStateException("Firebase Realtime Database belum terkonfigurasi."))

        val cleanCode = roomCode.trim().uppercase()
        _sessionState.value = BarengSessionState.Connecting("Mencari room $cleanCode...")

        val uid = obtainUserId()
        val userName = preferences.userName.get().ifBlank { "Pembaca" }

        val roomRef = db.getReference("mihonbareng/rooms").child(cleanCode)
        currentRoomRef = roomRef

        return try {
            val snapshot = roomRef.child("info").get().await()
            val roomInfo = snapshot.getValue(RoomInfo::class.java)

            if (roomInfo == null || !roomInfo.isActive) {
                val err = "Room $cleanCode tidak ditemukan atau sudah ditutup."
                _sessionState.value = BarengSessionState.Error(err)
                return Result.failure(IllegalArgumentException(err))
            }

            val isHost = (roomInfo.hostUid == uid)
            val participant = Participant(
                uid = uid,
                name = userName,
                isHost = isHost,
                currentPage = 0,
                lastActive = System.currentTimeMillis(),
            )

            val participantRef = roomRef.child("participants").child(uid)
            participantRef.setValue(participant).await()
            participantRef.onDisconnect().removeValue()

            attachRoomListeners(roomRef, uid)

            preferences.lastRoomCode.set(cleanCode)
            _sessionState.value = BarengSessionState.Active(
                roomInfo = roomInfo,
                isHost = isHost,
                currentUserId = uid,
                currentUserName = userName,
            )

            Result.success(roomInfo)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to join room: $cleanCode" }
            _sessionState.value = BarengSessionState.Error("Gagal bergabung ke room: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    private fun attachRoomListeners(roomRef: DatabaseReference, uid: String) {
        detachListeners()

        // 1. Listen for Room State changes (Chapter & Page)
        roomStateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(RoomState::class.java) ?: return
                _remoteState.value = state
            }

            override fun onCancelled(error: DatabaseError) {
                logcat(LogPriority.WARN) { "Room state listener cancelled: ${error.message}" }
            }
        }.also { roomRef.child("state").addValueEventListener(it) }

        // 2. Listen for Participants changes
        participantsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Participant>()
                for (child in snapshot.children) {
                    child.getValue(Participant::class.java)?.let { list.add(it) }
                }
                _participants.value = list
            }

            override fun onCancelled(error: DatabaseError) {
                logcat(LogPriority.WARN) { "Participants listener cancelled: ${error.message}" }
            }
        }.also { roomRef.child("participants").addValueEventListener(it) }

        // 3. Listen for Live Reactions
        reactionListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val reaction = snapshot.getValue(LiveReaction::class.java) ?: return
                // Only process recent reactions (< 15 seconds)
                if (System.currentTimeMillis() - reaction.timestamp < 15_000) {
                    scope.launch { _reactionsFlow.emit(reaction) }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }.also { roomRef.child("reactions").limitToLast(10).addChildEventListener(it) }

        // 4. Listen for Pointer
        pointerListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pointer = snapshot.getValue(PointerPosition::class.java)
                if (pointer != null && pointer.uid != uid) {
                    _pointerFlow.value = pointer
                } else if (pointer == null) {
                    _pointerFlow.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }.also { roomRef.child("pointer").addValueEventListener(it) }
    }

    private fun detachListeners() {
        val roomRef = currentRoomRef ?: return
        roomStateListener?.let { roomRef.child("state").removeEventListener(it) }
        participantsListener?.let { roomRef.child("participants").removeEventListener(it) }
        reactionListener?.let { roomRef.child("reactions").removeEventListener(it) }
        pointerListener?.let { roomRef.child("pointer").removeEventListener(it) }

        roomStateListener = null
        participantsListener = null
        reactionListener = null
        pointerListener = null
    }

    fun updatePage(pageIndex: Int, chapterUrl: String? = null) {
        val active = _sessionState.value as? BarengSessionState.Active ?: return
        val roomRef = currentRoomRef ?: return

        // Update participant's own page progress
        roomRef.child("participants").child(active.currentUserId).child("currentPage").setValue(pageIndex)
        roomRef.child(
            "participants",
        ).child(active.currentUserId).child("lastActive").setValue(System.currentTimeMillis())

        // If host, update room state
        if (active.isHost) {
            val state = RoomState(
                chapterUrl = chapterUrl ?: active.roomInfo.chapterUrl,
                pageIndex = pageIndex,
                updatedBy = active.currentUserId,
                updatedAt = System.currentTimeMillis(),
            )
            roomRef.child("state").setValue(state)
        }
    }

    fun updateChapter(chapterUrl: String, chapterName: String) {
        val active = _sessionState.value as? BarengSessionState.Active ?: return
        if (!active.isHost) return
        val roomRef = currentRoomRef ?: return

        roomRef.child("info").child("chapterUrl").setValue(chapterUrl)
        roomRef.child("info").child("chapterName").setValue(chapterName)
        roomRef.child("state").setValue(
            RoomState(
                chapterUrl = chapterUrl,
                pageIndex = 0,
                updatedBy = active.currentUserId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun sendReaction(emoji: String) {
        val active = _sessionState.value as? BarengSessionState.Active ?: return
        val roomRef = currentRoomRef ?: return

        val reaction = LiveReaction(
            id = UUID.randomUUID().toString(),
            uid = active.currentUserId,
            userName = active.currentUserName,
            emoji = emoji,
            timestamp = System.currentTimeMillis(),
        )

        roomRef.child("reactions").push().setValue(reaction)
    }

    fun sendPointer(x: Float, y: Float, active: Boolean) {
        val currentSession = _sessionState.value as? BarengSessionState.Active ?: return
        val roomRef = currentRoomRef ?: return

        val pointer = PointerPosition(
            uid = currentSession.currentUserId,
            x = x,
            y = y,
            active = active,
            timestamp = System.currentTimeMillis(),
        )

        roomRef.child("pointer").setValue(pointer)
    }

    fun leaveRoom() {
        val active = _sessionState.value as? BarengSessionState.Active
        val roomRef = currentRoomRef

        if (active != null && roomRef != null) {
            // Remove self from participants
            roomRef.child("participants").child(active.currentUserId).removeValue()

            // If host leaves and nobody else is there, mark room inactive
            if (active.isHost) {
                roomRef.child("info").child("isActive").setValue(false)
            }
        }

        detachListeners()
        currentRoomRef = null
        _sessionState.value = BarengSessionState.Idle
        _participants.value = emptyList()
        _remoteState.value = null
        _pointerFlow.value = null
    }
}
