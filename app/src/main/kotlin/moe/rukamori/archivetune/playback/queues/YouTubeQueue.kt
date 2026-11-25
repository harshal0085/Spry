/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata

class YouTubeQueue(
    internal var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    internal val followAutomixPreview: Boolean = false,
    private val expandToFullQueueWhenAutoLoadMoreDisabled: Boolean = false,
) : Queue {
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        // Spry: one failed request used to leave the queue at a single song (Next did nothing), so retry briefly.
        val nextResult =
            withContext(IO) {
                suspend fun fetch() = YouTube.next(endpoint = endpoint, continuation = continuation, followAutomixPreview = followAutomixPreview)
                var result = fetch()
                for (wait in longArrayOf(800L, 2500L)) {
                    if (result.isSuccess) break
                    kotlinx.coroutines.delay(wait)
                    result = fetch()
                }
                result.getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return Queue.Status(
            title = nextResult.title,
            items = nextResult.items.map { it.toMediaItem() },
            mediaItemIndex = nextResult.currentIndex ?: 0,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = expandToFullQueueWhenAutoLoadMoreDisabled

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult =
            withContext(IO) {
                YouTube
                    .next(
                        endpoint = endpoint,
                        continuation = continuation,
                        followAutomixPreview = followAutomixPreview,
                    ).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaItem() }
    }

    companion object {
        fun playlist(
            endpoint: WatchEndpoint,
            preloadItem: MediaMetadata? = null,
        ) = YouTubeQueue(
            endpoint = endpoint,
            preloadItem = preloadItem,
            expandToFullQueueWhenAutoLoadMoreDisabled = true,
        )

        fun radio(song: MediaMetadata) =
            YouTubeQueue(
                endpoint = WatchEndpoint(videoId = song.id),
                preloadItem = song,
                followAutomixPreview = true,
            )
    }
}
