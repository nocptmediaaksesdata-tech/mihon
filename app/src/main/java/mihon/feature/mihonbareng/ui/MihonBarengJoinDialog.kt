package mihon.feature.mihonbareng.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import mihon.feature.mihonbareng.MihonBarengManager
import mihon.feature.mihonbareng.MihonBarengPreferences
import mihon.feature.mihonbareng.MihonBarengResolver
import mihon.icons.materialsymbols.rounded.Refresh

@Composable
fun MihonBarengJoinDialog(
    onDismissRequest: () -> Unit,
    manager: MihonBarengManager,
    preferences: MihonBarengPreferences,
    onOpenReader: (mangaId: Long, chapterId: Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var roomCodeInput by remember { mutableStateOf(preferences.lastRoomCode.get()) }
    var userNameInput by remember { mutableStateOf(preferences.userName.get()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lastCode = remember { preferences.lastRoomCode.get().trim() }
    val lastHost = remember { preferences.lastRoomHostName.get().trim() }
    val lastManga = remember { preferences.lastRoomMangaTitle.get().trim() }
    val hasRecentRoom = lastCode.isNotEmpty()

    fun performJoin(targetCode: String) {
        val code = targetCode.trim()
        if (code.isEmpty()) return

        isLoading = true
        errorMessage = null

        scope.launch {
            val result = manager.joinRoom(code)
            isLoading = false

            if (result.isFailure) {
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Gagal bergabung ke room"
                return@launch
            }

            val roomInfo = result.getOrNull()
            if (roomInfo != null) {
                val graph = context.appGraph
                MihonBarengResolver.resolveAndOpen(
                    graph = graph,
                    roomInfo = roomInfo,
                    onSuccess = { mangaId, chapterId ->
                        onDismissRequest()
                        onOpenReader(mangaId, chapterId)
                    },
                    onError = { message ->
                        onDismissRequest()
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismissRequest()
        },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Gabung MihonBareng",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (hasRecentRoom) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Room Terakhir",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = lastCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (lastManga.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastManga,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (lastHost.isNotEmpty()) {
                                Text(
                                    text = "Host: $lastHost",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { performJoin(lastCode) },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1-Tap Rejoin")
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = " atau gabung room lain ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Masukkan kode room dari temanmu untuk mulai membaca bersama secara realtime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

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

                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = { roomCodeInput = it.uppercase().trim() },
                    label = { Text("Kode Room") },
                    placeholder = { Text("MB-XXXX") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { performJoin(roomCodeInput) },
                enabled = !isLoading && roomCodeInput.isNotBlank(),
            ) {
                Text("Gabung")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isLoading,
            ) {
                Text("Batal")
            }
        },
    )
}
