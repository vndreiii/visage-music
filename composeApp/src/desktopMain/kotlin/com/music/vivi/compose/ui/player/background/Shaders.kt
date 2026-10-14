package com.music.vivi.compose.ui.player.background

/**
 * SkSL runtime-effect sources for the Now Playing / mini-player backgrounds.
 *
 * Every shader gets the same inputs from [ShaderBackground]:
 *  - `iResolution`, `iTime`
 *  - `c0..c4`: five colours derived from the cover (vibrant → dark)
 *  - optionally `img` + `imgSize`: a pre-shrunk copy of the cover (bilinear-filtered, so sampling
 *    it already is a blur), for the shaders that set `usesImage`.
 */
internal enum class BackgroundShader(val sksl: String, val usesImage: Boolean = false, val imageEdge: Int = 0) {
    AURORA(SHADER_COMMON + AURORA_BODY),
    GLOW(SHADER_COMMON + GLOW_BODY),
    MESH(SHADER_COMMON + MESH_BODY),
    WAVE(SHADER_COMMON + WAVE_BODY),
    XMB(SHADER_COMMON + XMB_BODY),
    BLUR(SHADER_COMMON + IMAGE_COMMON + BLUR_BODY, usesImage = true, imageEdge = 14),
    DISTORT(SHADER_COMMON + IMAGE_COMMON + DISTORT_BODY, usesImage = true, imageEdge = 256),
}

private const val SHADER_COMMON = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 c4;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.02 + float2(17.3, 9.1);
        a *= 0.5;
    }
    return v;
}

// Smoothly cycles through the five cover colours; t wraps every 1.0.
float3 palette(float t) {
    float x = fract(t) * 5.0;
    float3 a = c0;
    float3 b = c1;
    if (x >= 1.0) { a = c1; b = c2; }
    if (x >= 2.0) { a = c2; b = c3; }
    if (x >= 3.0) { a = c3; b = c4; }
    if (x >= 4.0) { a = c4; b = c0; }
    return mix(a, b, smoothstep(0.0, 1.0, fract(x)));
}

float3 pick(int i) {
    if (i == 0) return c0;
    if (i == 1) return c1;
    if (i == 2) return c2;
    if (i == 3) return c3;
    return c4;
}

// Dithered output so slow gradients never band.
half4 finish(float3 col, float2 fc) {
    col += (hash21(fc + fract(iTime)) - 0.5) / 110.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

private const val IMAGE_COMMON = """
uniform shader img;
uniform float2 imgSize;

// Cover-fit mapping from a screen pixel into the (shrunk) artwork.
float2 coverUV(float2 fc) {
    float s = max(iResolution.x / imgSize.x, iResolution.y / imgSize.y);
    return clamp((fc - 0.5 * iResolution) / s + 0.5 * imgSize, float2(0.5), imgSize - 0.5);
}
"""

/** Flowing domain-warped colour field with bright ridges that pulse; the "flashy" gradient. */
private const val AURORA_BODY = """
half4 main(float2 fc) {
    float2 p = (fc - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
    float t = iTime * 0.12;
    float2 q = float2(fbm(p * 1.3 + t), fbm(p * 1.3 + float2(5.2, 1.3) - t));
    float2 r = float2(fbm(p * 1.6 + 3.0 * q + float2(1.7, 9.2) + t * 1.5),
                      fbm(p * 1.6 + 3.0 * q + float2(8.3, 2.8) - t * 1.2));
    float f = fbm(p * 1.2 + 3.0 * r);

    float3 col = palette(f * 1.2 + t * 0.6 + length(q) * 0.5);
    col = mix(c0 * 0.22, col, smoothstep(0.12, 0.85, f));

    float ridge = pow(1.0 - abs(f * 2.0 - 1.0), 6.0);
    col += palette(f + 0.5 + iTime * 0.05) * ridge * (0.35 + 0.25 * sin(iTime * 0.9));

    col *= 0.55 + 0.45 * smoothstep(1.3, 0.2, length(p));
    return finish(col, fc);
}
"""

/** Soft orbiting lights with bloom over a dark wash. */
private const val GLOW_BODY = """
half4 main(float2 fc) {
    float m = min(iResolution.x, iResolution.y);
    float2 p = (fc - 0.5 * iResolution) / m;
    float2 span = 0.5 * iResolution / m;
    float t = iTime * 0.25;

    float3 col = c0 * 0.10 + c1 * 0.05 * (0.5 + 0.5 * p.y);
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float2 o = float2(sin(t * (0.7 + 0.13 * fi) + fi * 2.1) * span.x * 0.85,
                          cos(t * (0.5 + 0.11 * fi) + fi * 1.3) * span.y * 0.85);
        float d = length(p - o);
        float r = 0.28 + 0.10 * sin(t * 1.3 + fi);
        float core = exp(-(d * d) / (r * r));
        float halo = exp(-d * 2.2) * 0.35;
        col += palette(fi / 6.0 + t * 0.05) * (core * 0.85 + halo * 0.5);
    }
    col += palette(fbm(p * 2.0 + t) * 1.5) * 0.06;
    col = 1.0 - exp(-col * 1.25);
    col *= 0.6 + 0.4 * smoothstep(1.4, 0.1, length(p));
    return finish(col, fc);
}
"""

/**
 * Live mesh: large, saturated colour fields drifting and slowly rotating over a deep base (the look of
 * Apple Music's lyric background). Each field is a soft Gaussian; colours are blended by weight and
 * only cover the base as much as the fields actually reach, so there are real dark regions and
 * vivid colour, not a washed-out average.
 */
private const val MESH_BODY = """
half4 main(float2 fc) {
    float m = min(iResolution.x, iResolution.y);
    float2 p = (fc - 0.5 * iResolution) / m;
    float t = iTime * 0.14;

    // Slow global rotation + organic warp.
    float a = t * 0.35;
    float ca = cos(a);
    float sa = sin(a);
    p = float2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
    p += (float2(fbm(p * 1.5 + t), fbm(p * 1.5 + float2(6.0, 2.0) - t)) - 0.5) * 0.45;

    float3 acc = float3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float2 o = float2(0.55 * sin(t * (0.8 + 0.17 * fi) + fi * 2.4),
                          0.42 * cos(t * (0.6 + 0.21 * fi) + fi * 1.7));
        float r = 0.42 + 0.10 * sin(t * 1.1 + fi * 3.0);
        float d = length(p - o);
        float g = exp(-(d * d) / (r * r));
        float3 c = pick(i - (i / 5) * 5);
        acc += c * g;
        wsum += g;
    }
    float3 field = acc / max(wsum, 0.0001);
    float coverage = 1.0 - exp(-wsum * 1.6);
    float3 base = mix(c4, c0, 0.4) * 0.16;
    float3 col = mix(base, field, coverage);

    // A touch more saturation and a soft light sweep.
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, 1.25);
    col += palette(t * 0.2) * 0.05 * (0.5 + 0.5 * sin(p.x * 3.0 - iTime * 0.3));
    col *= 0.78 + 0.22 * smoothstep(1.3, 0.1, length(p));
    return finish(col, fc);
}
"""

/** Layered rolling waves with luminous crests, back to front. */
private const val WAVE_BODY = """
half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float aspect = iResolution.x / iResolution.y;
    float t = iTime * 0.35;

    float3 col = mix(c0 * 0.35, c1 * 0.10, uv.y);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float depth = fi / 4.0;
        float y = 0.30 + 0.13 * fi
                + 0.07 * sin(uv.x * (2.2 + 0.5 * fi) * aspect * 0.6 + t * (0.8 + 0.25 * fi) + fi * 1.7)
                + 0.04 * sin(uv.x * (5.0 + fi) * aspect * 0.5 - t * (1.1 + 0.2 * fi) + fi);
        float d = uv.y - y;
        float body = smoothstep(0.0, 0.012, d);
        float3 layer = palette(fi / 5.0 + 0.1) * mix(0.75, 0.25, depth) * (1.0 - smoothstep(0.0, 0.55, d));
        float crest = exp(-abs(d) * 60.0) * 0.6;
        col = mix(col, layer + palette(fi / 5.0) * 0.12, body * 0.82);
        col += palette(fi / 5.0 + 0.5) * crest * (0.4 + 0.2 * sin(t + fi));
    }
    return finish(col, fc);
}
"""

/**
 * PlayStation 3 XMB wave: broad, silky translucent ribbons sweeping diagonally from the lower left.
 * Each ribbon swells wide, then pinches to a bright thread where it twists; edges glow, the body
 * is a soft gradient, and a few sparkles drift over a deep tinted background.
 */
private const val XMB_BODY = """
half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float aspect = iResolution.x / iResolution.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);

    // The XMB wave runs up and to the right.
    float ang = 0.30;
    float ca = cos(ang);
    float sa = sin(ang);
    float2 r = float2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
    float t = iTime * 0.16;

    float3 col = mix(c0 * 0.60, c1 * 0.18, clamp(uv.y * 1.1, 0.0, 1.0));
    col += c2 * 0.16 * exp(-pow(length(uv - float2(0.12, 0.92)) * 1.5, 2.0));

    for (int i = 0; i < 6; i++) {
        float fi = float(i);
        float ph = fi * 1.13;
        float center = 0.20 * sin(r.x * 1.1 + t * (0.9 + 0.12 * fi) + ph)
                     + 0.09 * sin(r.x * 2.3 - t * (0.7 + 0.10 * fi) + ph * 1.7)
                     + (fi - 2.5) * 0.085;
        float swell = 0.5 + 0.5 * sin(r.x * 0.9 + t * 0.6 + ph * 2.1);
        float width = 0.012 + 0.115 * swell * swell;
        float dy = r.y - center;

        float body = smoothstep(width, width * 0.15, abs(dy));
        float shade = 0.5 + 0.5 * clamp(dy / width, -1.0, 1.0);
        float edge = exp(-abs(abs(dy) - width) * 85.0);
        float haze = exp(-abs(dy) * 7.0) * 0.05;

        float3 rc = palette(fi / 6.0 + 0.1 + 0.05 * sin(t));
        col += rc * body * (0.07 + 0.23 * shade);
        col += mix(rc, float3(1.0), 0.75) * edge * 0.26 * (0.35 + 0.65 * smoothstep(0.02, 0.10, width));
        col += rc * haze;
    }

    // Sparkles.
    float2 g = float2(uv.x * aspect, uv.y) * 16.0;
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float rnd = hash21(id);
    float2 jitter = float2(hash21(id + 7.0), hash21(id + 13.0)) - 0.5;
    float tw = 0.5 + 0.5 * sin(iTime * (0.5 + rnd * 1.5) + rnd * 40.0);
    float dust = smoothstep(0.05, 0.0, length(f - jitter * 0.6)) * step(0.86, rnd) * tw;
    col += mix(palette(rnd), float3(1.0), 0.6) * dust * 0.7;

    col *= 0.78 + 0.22 * smoothstep(1.3, 0.2, length(uv - 0.5) * 1.5);
    return finish(col, fc);
}
"""

/** Heavily blurred cover (a tiny bilinear-filtered copy), slowly drifting and warped. */
private const val BLUR_BODY = """
half4 main(float2 fc) {
    float2 p = (fc - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
    float t = iTime * 0.08;
    float2 w = float2(fbm(p * 1.4 + t), fbm(p * 1.4 + float2(3.7, 1.9) - t)) - 0.5;
    float2 q = fc + w * iResolution * 0.22;
    float z = 1.15 + 0.06 * sin(iTime * 0.11);
    q = (q - 0.5 * iResolution) / z + 0.5 * iResolution;

    // Bilinear filtering of a tiny copy is already a blur; extra taps remove its blocky seams.
    float2 uv0 = coverUV(q);
    float k = 0.9;
    float3 col = float3(img.eval(uv0).rgb) * 0.4
        + float3(img.eval(clamp(uv0 + float2(k, 0.0), float2(0.5), imgSize - 0.5)).rgb) * 0.15
        + float3(img.eval(clamp(uv0 - float2(k, 0.0), float2(0.5), imgSize - 0.5)).rgb) * 0.15
        + float3(img.eval(clamp(uv0 + float2(0.0, k), float2(0.5), imgSize - 0.5)).rgb) * 0.15
        + float3(img.eval(clamp(uv0 - float2(0.0, k), float2(0.5), imgSize - 0.5)).rgb) * 0.15;
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, 1.35) * 0.62;
    col += palette(fbm(p * 2.0 + t * 3.0)) * 0.04;
    col *= 0.75 + 0.25 * smoothstep(1.3, 0.2, length(p));
    return finish(col, fc);
}
"""

/**
 * Liquid distort: the cover flowed through layered domain warping and a slow swirl, so it melts like
 * marbled ink but stays recognisable. The image is mirrored at its edges (no clamped streaks), colour
 * channels separate very slightly, and a glossy sheen rides the flow.
 */
private const val DISTORT_BODY = """
float2 fold(float2 uv) {
    return abs(fract(uv * 0.5) * 2.0 - 1.0);
}

float3 coverAt(float2 qf) {
    float s = max(iResolution.x / imgSize.x, iResolution.y / imgSize.y);
    float2 n = fold(((qf - 0.5 * iResolution) / s + 0.5 * imgSize) / imgSize);
    return float3(img.eval(clamp(n * imgSize, float2(0.5), imgSize - 0.5)).rgb);
}

half4 main(float2 fc) {
    float m = min(iResolution.x, iResolution.y);
    float2 p = (fc - 0.5 * iResolution) / m;
    float t = iTime * 0.10;

    float2 w1 = float2(fbm(p * 1.6 + float2(0.0, t)), fbm(p * 1.6 + float2(5.2, 1.3) - float2(t, 0.0))) - 0.5;
    float2 w2 = float2(fbm(p * 2.8 + w1 * 2.0 + float2(t * 1.3, 3.1)),
                       fbm(p * 2.8 + w1 * 2.0 + float2(8.3, 2.8) - float2(0.0, t * 1.1))) - 0.5;
    float2 q = p + w1 * 0.60 + w2 * 0.32;

    float a = 0.45 * sin(iTime * 0.07) * smoothstep(1.3, 0.0, length(p));
    float ca = cos(a);
    float sa = sin(a);
    q = float2(ca * q.x - sa * q.y, sa * q.x + ca * q.y);
    float2 qf = q * m + 0.5 * iResolution;

    float2 split = w2 * 0.012 * m;
    float3 col = float3(coverAt(qf + split).r, coverAt(qf).g, coverAt(qf - split).b);
    float soft = 0.012 * m;
    col = col * 0.5
        + 0.125 * (coverAt(qf + float2(soft, 0.0)) + coverAt(qf - float2(soft, 0.0))
                 + coverAt(qf + float2(0.0, soft)) + coverAt(qf - float2(0.0, soft)));

    float h = w1.x + w2.y;
    float sheen = pow(0.5 + 0.5 * sin(h * 11.0 + iTime * 0.25), 7.0) * 0.12;
    col += palette(h + 0.3) * sheen;

    col *= 0.74 * (0.80 + 0.20 * smoothstep(1.3, 0.2, length(p) * 1.4));
    return finish(col, fc);
}
"""
