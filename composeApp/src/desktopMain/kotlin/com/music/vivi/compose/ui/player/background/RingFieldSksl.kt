package com.music.vivi.compose.ui.player.background

/**
 * The ring field both the Rings background and the fused Cuts lyrics evaluate, so their rings coincide.
 * Rings are a radial spectrum: the inner rings follow the bass, the middle ones the mids, the outer ones the highs;
 * kicks snap the whole field and push a pulse outward.
 */
internal const val RING_FIELD_SKSL = """
float ringEnergy(float dist, float step, float bass, float mid, float high) {
    float rr = clamp(dist / (step * 12.0), 0.0, 1.0);
    float eb = max(0.0, 1.0 - rr * 2.0);
    float em = 1.0 - abs(rr * 2.0 - 1.0);
    float eh = max(0.0, rr * 2.0 - 1.0);
    // Mostly the low end (kicks, bass) on every ring, a little more of it in the centre; mids are a light touch,
    // highs barely register.
    return bass * (0.62 + 0.38 * eb) + mid * 0.22 * em + high * 0.07 * eh;
}

float ringDistance(float dist, float step, float diag, float scale, float t, float e, float kick) {
    float wave = sin(dist / diag * 46.0 / scale - t * 1.6) + 0.5 * sin(dist / diag * 27.0 / scale - t * 0.9);
    return dist * (1.0 - 0.02 * kick) + wave * step * (0.04 + 0.12 * e)
        - kick * step * 0.20 * (0.5 + 0.5 * sin(dist / diag * 18.0 / scale - t * 7.0));
}
"""
