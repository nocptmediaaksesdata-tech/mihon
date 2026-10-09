package mihon.feature.mihonbareng.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mihon.feature.mihonbareng.MihonBarengManager
import mihon.feature.mihonbareng.MihonBarengPreferences
import mihon.feature.mihonbareng.model.BarengSessionState
import mihon.feature.mihonbareng.model.BarengSyncMode
import mihon.feature.mihonbareng.model.Participant
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.PeopleAlt
import mihon.icons.materialsymbols.rounded.Person
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihonBarengSheet(
    onDismissRequest: () -> Unit,
    manager: MihonBarengManager,
    preferences: MihonBarengPreferences,
    currentManga: Manga?,
    currentChapter: Chapter?,
    sourceId: Long,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionState by manager.sessionState.collectAsState()
    val participants by manager.participants.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.PeopleAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "MihonBareng",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Baca manga bareng teman secara realtime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            when (val state = sessionState) {
                is BarengSessionState.Idle -> {
                    IdleView(
                        preferences = preferences,
                        onCreateRoom = { syncMode ->
                            scope.launch {
                                val result = manager.createRoom(
                                    mangaTitle = currentManga?.title ?: "Manga",
                                    mangaUrl = currentManga?.url ?: "",
                                    sourceId = sourceId,
                                    chapterUrl = currentChapter?.url ?: "",
                                    chapterName = currentChapter?.name ?: "Chapter 1",
                                    syncMode = syncMode,
                                )
                                if (result.isFailure) {
                                    Toast.makeText(
                                        context,
                                        result.exceptionOrNull()?.localizedMessage ?: "Gagal membuat room",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                        onJoinRoom = { code ->
                            scope.launch {
                                val result = manager.joinRoom(code)
                                if (result.isFailure) {
                                    Toast.makeText(
                                        context,
                                        result.exceptionOrNull()?.localizedMessage ?: "Gagal gabung room",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    )
                }

                is BarengSessionState.Connecting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is BarengSessionState.Active -> {
                    ActiveRoomView(
                        state = state,
                        participants = participants,
                        onCopyCode = { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", code))
                            Toast.makeText(context, "Kode room $code disalin!", Toast.LENGTH_SHORT).show()
                        },
                        onLeaveRoom = {
                            manager.leaveRoom()
                            onDismissRequest()
                        },
                    )
                }

                is BarengSessionState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { manager.leaveRoom() }) {
                            Text("Kembali")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IdleView(
    preferences: MihonBarengPreferences,
    onCreateRoom: (BarengSyncMode) -> Unit,
    onJoinRoom: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var joinCodeInput by remember { mutableStateOf("") }
    var selectedSyncMode by remember { mutableStateOf(preferences.defaultSyncMode.get()) }
    var userNameInput by remember { mutableStateOf(preferences.userName.get()) }
    var showSettings by remember { mutableStateOf(false) }

    TabRow(selectedTabIndex = selectedTab) {
        Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Bikin Room") },
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Gabung Room") },
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Profile name field
    OutlinedTextField(
        value = userNameInput,
        onValueChange = {
            userNameInput = it
            preferences.userName.set(it)
        },
        label = { Text("Nama Kamu") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (selectedTab == 0) {
        // Tab Bikin Room
        Text(
            text = "Pilih Mode Sinkronisasi:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedSyncMode = BarengSyncMode.STRICT },
        ) {
            RadioButton(
                selected = selectedSyncMode == BarengSyncMode.STRICT,
                onClick = { selectedSyncMode = BarengSyncMode.STRICT },
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Strict Sync (Host Nyetir)", fontWeight = FontWeight.Bold)
                Text(
                    "Layar teman otomatis mengikuti halaman host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedSyncMode = BarengSyncMode.LOOSE },
        ) {
            RadioButton(
                selected = selectedSyncMode == BarengSyncMode.LOOSE,
                onClick = { selectedSyncMode = BarengSyncMode.LOOSE },
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("Loose Sync (Bebas Baca)", fontWeight = FontWeight.Bold)
                Text(
                    "Masing-masing baca sendiri, ada indikator posisi teman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onCreateRoom(selectedSyncMode) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Buat Room & Dapatkan Kode")
        }
    } else {
        // Tab Gabung Room
        OutlinedTextField(
            value = joinCodeInput,
            onValueChange = { joinCodeInput = it.uppercase() },
            label = { Text("Kode Room (misal: MB-ABCD)") },
            placeholder = { Text("MB-XXXX") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onJoinRoom(joinCodeInput) },
            enabled = joinCodeInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Gabung ke Room")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Expandable Firebase config
    TextButton(
        onClick = { showSettings = !showSettings },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(if (showSettings) "Tutup Pengaturan Lanjutan" else "Pengaturan Lanjutan (Firebase URL)")
    }

    AnimatedVisibility(visible = showSettings) {
        var dbUrlInput by remember { mutableStateOf(preferences.customFirebaseDatabaseUrl.get()) }
        Column(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = dbUrlInput,
                onValueChange = {
                    dbUrlInput = it
                    preferences.customFirebaseDatabaseUrl.set(it)
                },
                label = { Text("Firebase Database URL (Opsional)") },
                placeholder = { Text("https://xxx-rtdb.firebaseio.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Kosongkan jika proyek sudah memiliki google-services.json bawaan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActiveRoomView(
    state: BarengSessionState.Active,
    participants: List<Participant>,
    onCopyCode: (String) -> Unit,
    onLeaveRoom: () -> Unit,
) {
    Column {
        // Room Code Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { onCopyCode(state.roomInfo.roomId) }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "KODE ROOM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = state.roomInfo.roomId,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Ketuk untuk menyalin kode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = MaterialSymbols.Rounded.ContentCopy,
                    contentDescription = "Salin Kode",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mode badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            val modeLabel = if (state.roomInfo.mode == BarengSyncMode.STRICT) {
                "Strict Sync (Host Mengontrol)"
            } else {
                "Loose Sync (Bebas)"
            }
            Text(
                text = "Mode: $modeLabel",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Participants List
        Text(
            text = "Pembaca Terhubung (${participants.size}):",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(participants) { participant ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = participant.name + if (participant.isHost) " (Host)" else "",
                        fontWeight = if (participant.isHost) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Hal ${participant.currentPage + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLeaveRoom,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Keluar dari Sesi MihonBareng")
        }
    }
}
