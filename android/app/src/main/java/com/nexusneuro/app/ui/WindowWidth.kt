package com.nexusneuro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Phone < 600dp, tablet portrait / small fold ~600–839, large tablet / landscape ≥ 840. */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

fun windowWidthClass(maxWidth: Dp): WindowWidthClass = when {
    maxWidth < 600.dp -> WindowWidthClass.Compact
    maxWidth < 840.dp -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

val WindowWidthClass.isTablet: Boolean
    get() = this != WindowWidthClass.Compact

@Composable
fun contentHorizontalPadding(widthClass: WindowWidthClass): Dp = when (widthClass) {
    WindowWidthClass.Compact -> 16.dp
    WindowWidthClass.Medium -> 24.dp
    WindowWidthClass.Expanded -> 32.dp
}
