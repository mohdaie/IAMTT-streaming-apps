package com.iamtt.streaming.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.iamtt.streaming.R

/** The IAMTT wordmark. [height] sets its size; the width follows the logo's shape. */
@Composable
fun IamttLogo(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.iamtt_logo),
        contentDescription = "IAMTT",
        modifier = modifier.height(height),
    )
}
