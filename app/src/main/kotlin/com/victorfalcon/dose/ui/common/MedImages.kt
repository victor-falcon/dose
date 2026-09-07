package com.victorfalcon.dose.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorfalcon.dose.R

/** The pill shapes a user can pick for a medication. Stored as [Medication.image]. */
val medImageKeys: List<String> = listOf(
    "round", "round_scored", "oval", "capsule", "oblong",
    "powder",
)

/** Resolves a stored medication image key to a pill-shape drawable. */
@DrawableRes
fun medImageRes(key: String?): Int = when (key) {
    "round" -> R.drawable.ic_pill_round
    "round_scored" -> R.drawable.ic_pill_round_scored
    "oval" -> R.drawable.ic_pill_oval
    "capsule" -> R.drawable.ic_pill_capsule
    "oblong" -> R.drawable.ic_pill_oblong
    "powder" -> R.drawable.ic_pill_powder
    else -> R.drawable.ic_med_placeholder // no shape chosen yet
}

/**
 * Rounded tile showing a medication's pill shape: a tinted silhouette on the primary container,
 * with the corner scaled to the tile so a 38 dp row icon and an 80 dp header icon read the same.
 * Pass [size] = null to let the caller size the tile via [modifier].
 */
@Composable
fun MedIcon(
    image: String?,
    modifier: Modifier = Modifier,
    size: Dp? = 48.dp,
    shape: Shape = RoundedCornerShape((size ?: 48.dp) * 0.3f),
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = if (size != null) modifier.size(size) else modifier,
        shape = shape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(medImageRes(image)),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.fillMaxSize(0.55f),
            )
        }
    }
}
