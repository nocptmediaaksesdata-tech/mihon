package mihon.feature.mihonbareng

import mihon.app.di.AppGraph
import mihon.feature.mihonbareng.model.RoomInfo
import tachiyomi.domain.manga.model.Manga

object MihonBarengResolver {

    suspend fun resolveAndOpen(
        graph: AppGraph,
        roomInfo: RoomInfo,
        onSuccess: (mangaId: Long, chapterId: Long) -> Unit,
        onError: (String) -> Unit,
    ) {
        var localManga = graph.getMangaByUrlAndSourceId.await(roomInfo.mangaUrl, roomInfo.sourceId)

        if (localManga == null) {
            val source = graph.sourceManager.get(roomInfo.sourceId)
            if (source == null) {
                onError(
                    "Komik tidak ditemukan di library/sumber, pastikan ekstensi sudah terpasang dan tambahkan dulu atau buka komik secara manual.",
                )
                return
            }

            try {
                val stub = Manga.create().copy(
                    url = roomInfo.mangaUrl,
                    title = roomInfo.mangaTitle.ifBlank { "Manga" },
                    source = roomInfo.sourceId,
                )
                localManga = graph.networkToLocalManga(stub)
                graph.updateMangaFromRemote(localManga, fetchDetails = true, fetchChapters = true)
            } catch (e: Exception) {
                onError("Gagal memuat data komik dari sumber: ${e.localizedMessage}")
                return
            }
        }

        var localChapter = graph.getChapterByUrlAndMangaId.await(roomInfo.chapterUrl, localManga.id)

        if (localChapter == null) {
            try {
                graph.updateMangaFromRemote(localManga, fetchDetails = false, fetchChapters = true)
                localChapter = graph.getChapterByUrlAndMangaId.await(roomInfo.chapterUrl, localManga.id)
            } catch (_: Exception) {
                // Ignore and proceed to check
            }
        }

        if (localChapter != null) {
            onSuccess(localManga.id, localChapter.id)
        } else {
            onError(
                "Komik tidak ditemukan di library/sumber, pastikan ekstensi sudah terpasang dan tambahkan dulu atau buka komik secara manual.",
            )
        }
    }
}
