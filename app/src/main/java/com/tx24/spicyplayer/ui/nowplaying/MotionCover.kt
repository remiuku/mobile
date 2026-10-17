package com.tx24.spicyplayer.ui.nowplaying

import android.content.Context
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tx24.spicyplayer.network.data.TrafficCounter
import com.tx24.spicyplayer.network.data.counted
import com.tx24.spicyplayer.network.motion.MotionCoverFinder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt
import okhttp3.OkHttpClient

/** What the animated cover is looked up by. */
data class MotionCoverQuery(val artist: String, val album: String, val title: String)

/**
 * The record's animated cover stream, or null while there is none (setting off, still searching,
 * or the album has no animation). Resets to null the moment the record changes.
 */
@Composable
fun rememberMotionCoverUrl(enabled: Boolean, query: MotionCoverQuery): String? {
    val context = LocalContext.current
    val artist = leadArtist(query.artist)
    val key = MotionCoverFinder.key(artist, query.album, query.title)
    return produceState<String?>(null, enabled, key) {
        value = null
        if (!enabled || key == null) return@produceState
        value = MotionCovers.finder(context).find(artist, query.album, query.title)
    }.value
}

/**
 * Loops [url] muted, filling its box. Nothing shows until the first frame has been drawn, then it
 * fades in over whatever is beneath (the still cover); [visible] false fades it back out without
 * stopping it. A stream that fails to play calls [onFailed]. Every view of one [url] shows the
 * same decode ([MotionCoverStream]).
 *
 * With [cropFromTop] set, the video keeps its own shape instead, as big as it takes to cover the
 * box, centred across and with that share of the overflow cut from the top ([assumedAspect] until
 * the video reports its size).
 *
 * A TextureView rather than a SurfaceView, so the cover's rounded clip and alpha apply to it.
 */
@Composable
fun MotionCoverVideo(
    url: String,
    visible: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn through this paint, e.g. a colour filter. */
    layerPaint: Paint? = null,
    cropFromTop: Float? = null,
    assumedAspect: Float = 1f,
) = key(url) {
    val stream = rememberMotionCoverStream(url, onFailed)
    var firstFrame by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (firstFrame && visible) 1f else 0f,
        animationSpec = tween(FADE_MS),
        label = "motionCoverAlpha",
    )
    AndroidView(
        factory = {
            TextureView(it).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        stream.addView(surface) { firstFrame = true }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        stream.resizeView(surface)
                    }

                    // The stream releases it, after it has stopped drawing into it.
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        stream.removeView(surface)
                        return false
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = { it.setLayerPaint(layerPaint) },
        modifier = modifier
            .then(
                if (cropFromTop == null) Modifier else Modifier.layout { measurable, constraints ->
                    val aspect = stream.videoAspect.takeIf { it > 0f } ?: assumedAspect
                    val (width, height) = coverSize(constraints.maxWidth, constraints.maxHeight, aspect)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            (constraints.maxWidth - width) / 2,
                            ((constraints.maxHeight - height) * cropFromTop).roundToInt(),
                        )
                    }
                },
            )
            .graphicsLayer { this.alpha = alpha },
    )
}

/**
 * The shared stream of [url], held while this is composed: one player for every view and sampler
 * of it, looping only while the app is on screen. A stream that fails to play calls [onFailed].
 */
@Composable
internal fun rememberMotionCoverStream(url: String, onFailed: () -> Unit): MotionCoverStream {
    val context = LocalContext.current
    val stream = remember(url) { MotionCoverLease(MotionCovers.acquire(context, url)) }.stream
    val latestOnFailed by rememberUpdatedState(onFailed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(stream) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                latestOnFailed()
            }
        }
        stream.player.addListener(listener)
        onDispose { stream.player.removeListener(listener) }
    }
    DisposableEffect(stream, lifecycle) {
        // Only loops while the app is on screen.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> stream.player.play()
                Lifecycle.Event.ON_STOP -> stream.player.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return stream
}

/** Gives the stream back when it leaves the composition, or never made it in. */
private class MotionCoverLease(val stream: MotionCoverStream) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = MotionCovers.release(stream)
    override fun onAbandoned() = MotionCovers.release(stream)
}

/**
 * The first credited artist: the session joins several with ", ", and Apple files the record
 * under its lead.
 */
internal fun leadArtist(artists: String): String =
    artists.split(", ").first().split(Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE))
        .first().trim()

private const val FADE_MS = 600

/** The size, at [aspect] (width over height), that just covers a [width]×[height] box. */
internal fun coverSize(width: Int, height: Int, aspect: Float): Pair<Int, Int> {
    if (width <= 0 || height <= 0 || aspect <= 0f) return width to height
    return if (width.toFloat() / height > aspect) {
        width to ceil(width / aspect).toInt()
    } else {
        ceil(height * aspect).toInt() to height
    }
}

/**
 * The process-wide finder and stream cache, and the shared streams built on them: animated covers
 * and Spotify Canvas videos alike.
 */
@OptIn(UnstableApi::class)
internal object MotionCovers {
    @Volatile private var finder: MotionCoverFinder? = null
    @Volatile private var cache: SimpleCache? = null

    fun finder(context: Context): MotionCoverFinder = finder ?: synchronized(this) {
        finder ?: MotionCoverFinder(
            client = OkHttpClient.Builder()
                .counted()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build(),
            memoFile = File(context.cacheDir, "motion/known.json"),
        ).also { finder = it }
    }

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "motion/streams"),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    /** Streams by URL, with how many hold each. Main thread. */
    private val streams = HashMap<String, MotionCoverStream>()
    private val main = Handler(Looper.getMainLooper())

    fun acquire(context: Context, url: String): MotionCoverStream =
        streams.getOrPut(url) { MotionCoverStream(url, player(context.applicationContext, url)) }.also { it.users++ }

    /**
     * Lets go of [stream]; the last one out releases it a moment later, so a view handing over to
     * another (a background type swap) keeps the same player.
     */
    fun release(stream: MotionCoverStream) {
        stream.users--
        if (stream.users > 0) return
        main.post {
            if (stream.users > 0) return@post
            if (streams[stream.url] === stream) streams.remove(stream.url)
            stream.release()
        }
    }

    private fun player(context: Context, url: String): ExoPlayer {
        val upstream = DefaultHttpDataSource.Factory().setUserAgent(BROWSER_UA).setTransferListener(CountedTransfers)
        val source = CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Apple's animated covers are HLS streams; a Spotify Canvas is a single MP4 file.
        val hls = isHls(url)
        val sources = if (hls) HlsMediaSource.Factory(source) else ProgressiveMediaSource.Factory(source)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(sources)
            .build()
            .apply {
                // Never asks for audio focus (the default), so the music app is left alone.
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .apply {
                        // Apple offers up to 1080² at 10 Mbit/s. One steady rendition this side of
                        // the cap keeps a loop to a few MB and caches cleanly.
                        if (hls) {
                            setMaxVideoSize(MAX_VIDEO_PX, MAX_VIDEO_PX)
                            setMaxVideoBitrate(MAX_VIDEO_BITRATE)
                            setForceHighestSupportedBitrate(true)
                        }
                    }
                    .build()
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    private fun isHls(url: String) = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    private const val CACHE_BYTES = 256L shl 20
    private const val MAX_VIDEO_PX = 1080
    private const val MAX_VIDEO_BITRATE = 3_600_000
    private const val BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}

/** Counts what the cover videos download (cache hits aren't network, so don't count). */
@OptIn(UnstableApi::class)
private object CountedTransfers : TransferListener {
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) TrafficCounter.add(dataSpec.uri.host, bytesTransferred.toLong())
    }
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}

/** Forgets a remembered stream that failed, so the next song change searches afresh. */
internal suspend fun forgetMotionCover(context: Context, query: MotionCoverQuery) {
    MotionCovers.finder(context).forget(leadArtist(query.artist), query.album, query.title)
}
