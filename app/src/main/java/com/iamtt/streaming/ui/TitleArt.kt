package com.iamtt.streaming.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iamtt.streaming.data.CatalogTitle
import com.iamtt.streaming.data.TitleInfo

/**
 * A title's poster: the real artwork when it has been found, drawn over the generated poster,
 * which shows while it loads, when offline, or when nothing matched.
 */
@Composable
fun TitlePoster(
    title: CatalogTitle,
    info: TitleInfo?,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    fallbackTitleSize: TextUnit = 15.sp,
    fallbackTitleBottomPadding: Dp = 10.dp,
) {
    Box(modifier) {
        PosterArt(title.name, Modifier.fillMaxSize(), titleSize = fallbackTitleSize, titleBottomPadding = fallbackTitleBottomPadding)
        val url = if (large) info?.posterLarge ?: info?.poster else info?.poster
        if (url != null) {
            AsyncImage(model = url, contentDescription = title.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** An episode's still (16:9), or the show's generated art when there isn't one. */
@Composable
fun EpisodeStill(showName: String, stillUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        PosterArt(showName, Modifier.fillMaxSize(), titleSize = 11.sp, badge = false)
        if (stillUrl != null) {
            AsyncImage(model = stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A poster in a home row, with a progress bar for Continue Watching. Works by touch and remote. */
@Composable
fun PosterCard(card: HomeCard, info: TitleInfo?, width: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .pressable(shape, onClick),
    ) {
        TitlePoster(card.title, info, Modifier.fillMaxSize())
        card.watchedFraction()?.let { WatchBar(it, Modifier.align(Alignment.BottomCenter)) }
    }
}
