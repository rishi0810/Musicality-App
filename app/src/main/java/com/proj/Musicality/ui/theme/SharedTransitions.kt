package com.proj.Musicality.ui.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

val MediaBoundsSpring = BoundsTransform { _, _ ->
    spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
