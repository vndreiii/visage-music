package com.music.vivi.compose.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

// M3 emphasized easing curves.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val EnterMs = 340
private const val ExitMs = 160
private const val FadeThroughInMs = 220

private val tabRoutes = setOf(Routes.HOME, Routes.SEARCH, Routes.LIBRARY, Routes.SETTINGS)
private val playerRoutes = setOf(Routes.NOW_PLAYING, Routes.NOW_PLAYING_FULLSCREEN)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.opensPlayer() =
    targetState.destination.route in playerRoutes && initialState.destination.route !in playerRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.closesPlayer() =
    initialState.destination.route in playerRoutes && targetState.destination.route !in playerRoutes

/** Offset used by the shared-axis X pattern: a nudge, not a full-page slide. */
private fun axisOffset(fullWidth: Int) = (fullWidth / 14).coerceAtMost(96)

val viviEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        opensPlayer() ->
            fadeIn(tween(FadeThroughInMs, easing = EmphasizedDecelerate)) +
                slideInVertically(tween(420, easing = EmphasizedDecelerate)) { it / 10 }
        isTabSwitch() ->
            fadeIn(tween(FadeThroughInMs, delayMillis = 60, easing = EmphasizedDecelerate)) +
                scaleIn(tween(FadeThroughInMs + 60, delayMillis = 60, easing = EmphasizedDecelerate), initialScale = 0.985f)
        else ->
            fadeIn(tween(FadeThroughInMs, delayMillis = 40, easing = EmphasizedDecelerate)) +
                slideInHorizontally(tween(EnterMs, easing = EmphasizedDecelerate)) { axisOffset(it) }
    }
}

val viviExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        opensPlayer() -> fadeOut(tween(ExitMs + 80, easing = EmphasizedAccelerate))
        isTabSwitch() -> fadeOut(tween(ExitMs - 40, easing = EmphasizedAccelerate))
        else ->
            fadeOut(tween(ExitMs, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(EnterMs, easing = EmphasizedDecelerate)) { -axisOffset(it) }
    }
}

val viviPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        closesPlayer() -> fadeIn(tween(FadeThroughInMs, easing = EmphasizedDecelerate))
        isTabSwitch() -> fadeIn(tween(FadeThroughInMs, delayMillis = 60, easing = EmphasizedDecelerate))
        else ->
            fadeIn(tween(FadeThroughInMs, delayMillis = 40, easing = EmphasizedDecelerate)) +
                slideInHorizontally(tween(EnterMs, easing = EmphasizedDecelerate)) { -axisOffset(it) }
    }
}

val viviPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        closesPlayer() ->
            fadeOut(tween(ExitMs + 60, easing = EmphasizedAccelerate)) +
                slideOutVertically(tween(300, easing = EmphasizedAccelerate)) { it / 12 }
        isTabSwitch() -> fadeOut(tween(ExitMs - 40, easing = EmphasizedAccelerate))
        else ->
            fadeOut(tween(ExitMs, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(EnterMs, easing = EmphasizedDecelerate)) { axisOffset(it) } +
                scaleOut(tween(EnterMs), targetScale = 0.99f)
    }
}
