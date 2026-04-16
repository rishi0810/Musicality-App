package com.proj.Musicality.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

private val CtaHapticType = HapticFeedbackType.VirtualKey

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        interactionSource = resolvedInteractionSource,
        indication = indication
    ) {
        haptics.performHapticFeedback(CtaHapticType)
        onClick()
    }
}

fun Modifier.hapticCombinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        interactionSource = resolvedInteractionSource,
        indication = indication,
        onLongClick = if (onLongClick != null) {
            {
                haptics.performHapticFeedback(CtaHapticType)
                onLongClick()
            }
        } else {
            null
        }
    ) {
        haptics.performHapticFeedback(CtaHapticType)
        onClick()
    }
}

@Composable
fun HapticIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(CtaHapticType)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun HapticTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    TextButton(
        onClick = {
            haptics.performHapticFeedback(CtaHapticType)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun HapticFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptics.performHapticFeedback(CtaHapticType)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun HapticFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalIconButton(
        onClick = {
            haptics.performHapticFeedback(CtaHapticType)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun HapticOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(CtaHapticType)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
