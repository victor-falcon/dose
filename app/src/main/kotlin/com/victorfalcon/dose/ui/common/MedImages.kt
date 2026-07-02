package com.victorfalcon.dose.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorfalcon.dose.R

/**
 * Resolves a stored medication image key to a drawable. Only the placeholder
 * exists today; predefined keys get added to the [when] once the art lands.
 */
@DrawableRes
fun medImageRes(key: String?): Int = when (key) {
    // ponytail: map predefined keys -> drawables here when the images exist.
    else -> R.drawable.ic_med_placeholder
}

/** Rounded image tile shown next to a medication in lists and the editor. */
@Composable
fun MedIcon(image: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(medImageRes(image)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
