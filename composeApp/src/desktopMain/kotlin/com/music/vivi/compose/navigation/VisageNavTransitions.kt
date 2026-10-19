package com.music.vivi.compose.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable as navComposable
import com.music.vivi.compose.ui.motion.VisageMotion

// M3 emphasized easing curves.
private val EmphasizedDecelerate = VisageMotion.EmphasizedDecelerate
private val EmphasizedAccelerate = VisageMotion.EmphasizedAccelerate

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

val visageEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
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

val visageExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        opensPlayer() -> fadeOut(tween(ExitMs + 80, easing = EmphasizedAccelerate))
        isTabSwitch() -> fadeOut(tween(ExitMs - 40, easing = EmphasizedAccelerate))
        else ->
            fadeOut(tween(ExitMs, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(EnterMs, easing = EmphasizedDecelerate)) { -axisOffset(it) }
    }
}

// Back (pop) pages are shared by every kind of back (Esc, Alt+Left, mouse button, in-app button
// and the touchpad swipe, which seeks these same specs with the fingers), so they all look alike.
// The curves stay close to linear so finger displacement maps to page displacement.
//
//  - leaving page: slides out to the right at full width and fades to alpha 0 over the last part
//    of the transition (so nothing sits at partial alpha when it is removed -> no end flicker);
//    it is drawn on an opaque surface (see [composable]) and no scale/corner rounding is applied,
//    the main pane's own clip already gives the outer shape.
//  - revealed page: parallax in from slightly left, always fully opaque underneath.
private val FollowEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
private const val PopMs = 320
private const val PopFadeDelayMs = 110

val visagePopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        closesPlayer() -> fadeIn(tween(FadeThroughInMs, easing = EmphasizedDecelerate))
        isTabSwitch() -> fadeIn(tween(FadeThroughInMs, delayMillis = 60, easing = EmphasizedDecelerate))
        else -> slideInHorizontally(tween(PopMs, easing = FollowEasing)) { -(it / 7) }
    }
}

val visagePopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        closesPlayer() ->
            fadeOut(tween(ExitMs + 60, easing = EmphasizedAccelerate)) +
                slideOutVertically(tween(300, easing = EmphasizedAccelerate)) { it / 12 }
        isTabSwitch() -> fadeOut(tween(ExitMs - 40, easing = EmphasizedAccelerate))
        else ->
            slideOutHorizontally(tween(PopMs, easing = FollowEasing)) { it } +
                fadeOut(tween(PopMs - PopFadeDelayMs, delayMillis = PopFadeDelayMs, easing = LinearEasing))
    }
}

/**
 * Drop-in for `androidx.navigation.compose.composable`. Every page is drawn on an opaque theme
 * surface so a page that is transparent in places (Home, Library...) never lets the page behind
 * it show through or merge with it while transitioning.
 */
fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    navComposable(route, arguments, deepLinks) { entry ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content(entry) }
    }
}
