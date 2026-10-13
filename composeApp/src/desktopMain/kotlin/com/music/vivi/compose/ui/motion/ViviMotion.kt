package com.music.vivi.compose.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

/**
 * The app's motion language. Everything that moves should pull its spec from here so the whole
 * app feels the same: the lyrics layout shift, the side queue rail, the Now Playing controls
 * gliding between positions, menus popping from their anchor, press feedback.
 *
 * Rule of thumb: **layout and position → [glide]**, large surfaces entering/leaving → [rail],
 * small anchored popups → [menuEnter]/[menuExit], touch feedback → [press].
 */
object ViviMotion {
    // ── Springs ──────────────────────────────────────────────────────────────────────────────

    /** Signature glide: soft, a hair of give, no visible overshoot. Resizing/repositioning layout. */
    fun <T> glide(): SpringSpec<T> =
        spring(dampingRatio = GlideDamping, stiffness = Spring.StiffnessMediumLow)

    /** Critically damped version of [glide] for weights/fractions that must never exceed their range. */
    fun <T> settle(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Slow, heavy glide for big surfaces (side rails, panels) sliding and resizing. */
    fun <T> rail(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    /** Lively spring for press/hover feedback. */
    fun <T> press(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 500f)

    /** Playful bounce for small selected-state icons. */
    fun <T> bounce(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 400f)

    // ── Easings & tweens (for fades and anything that must not spring) ──────────────────────

    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val FadeInMs = 220
    const val FadeOutMs = 120

    fun <T> fadeInSpec(delayMs: Int = 0): FiniteAnimationSpec<T> =
        tween(FadeInMs, delayMillis = delayMs, easing = EmphasizedDecelerate)

    fun <T> fadeOutSpec(): FiniteAnimationSpec<T> = tween(FadeOutMs, easing = EmphasizedAccelerate)

    // ── Anchored popups (same feel as Material's DropdownMenu) ──────────────────────────────

    fun menuEnter(origin: TransformOrigin): EnterTransition =
        fadeIn(tween(30)) + scaleIn(
            initialScale = 0.8f,
            transformOrigin = origin,
            animationSpec = tween(120, easing = LinearOutSlowInEasing),
        )

    fun menuExit(origin: TransformOrigin): ExitTransition =
        fadeOut(tween(75)) + scaleOut(targetScale = 0.8f, transformOrigin = origin, animationSpec = tween(75))

    private const val GlideDamping = 0.86f
}
