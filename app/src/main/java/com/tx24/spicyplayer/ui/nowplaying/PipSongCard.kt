package com.tx24.spicyplayer.ui.nowplaying

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tx24.spicyplayer.ui.theme.SpicyColors
import com.tx24.spicyplayer.ui.theme.SpicyType

/**
 * The floating window's song card, in place of the lyrics: the cover with the title and artist,
 * centred and sized to the window. A wide window puts the text beside the cover, any other under it.
 */
@Composable
internal fun PipSongCard(
    title: String,
    artist: String,
    artwork: Bitmap?,
    artworkUri: String?,
    hasSession: Boolean,
    modifier: Modifier = Modifier,
) {
    val cover = rememberSessionArtwork(artwork, artworkUri, maxDimension = PIP_ARTWORK_MAX_PX, hasSession = hasSession)
    BoxWithConstraints(modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val wide = maxWidth > maxHeight * 1.2f
        val coverSize = if (wide) minOf(maxHeight * 0.8f, maxWidth * 0.4f) else minOf(maxWidth * 0.7f, maxHeight * 0.55f)
        val textSp = (minOf(maxWidth, maxHeight).value / 9f).coerceIn(11f, 22f)
        val titleStyle = SpicyType.Body.copy(fontSize = textSp.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em)
        val artistStyle = SpicyType.Body.copy(fontSize = (textSp * 0.82f).sp, color = SpicyColors.TextSecondary, lineHeight = 1.2.em)
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cover(cover?.bitmap, coverSize)
                Column {
                    Text(title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(artist, style = artistStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(cover?.bitmap, coverSize)
                Text(
                    title,
                    modifier = Modifier.padding(top = 8.dp),
                    style = titleStyle.copy(textAlign = TextAlign.Center),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(artist, style = artistStyle.copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Cover(bitmap: Bitmap?, size: Dp) {
    val shape = RoundedCornerShape(size * 0.06f)
    if (bitmap == null) {
        Box(Modifier.size(size).clip(shape).background(SpicyColors.TintBg))
    } else {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    }
}

/**
 * The song's "title · artist" on one line along the bottom of a square or tall floating window,
 * over a light scrim, [height] tall.
 */
@Composable
internal fun PipTitleStrip(title: String, artist: String, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · "),
            modifier = Modifier.padding(horizontal = 10.dp),
            style = SpicyType.Caption.copy(fontSize = (height.value * 0.42f).sp, color = SpicyColors.TextSecondary, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val PIP_ARTWORK_MAX_PX = 512
