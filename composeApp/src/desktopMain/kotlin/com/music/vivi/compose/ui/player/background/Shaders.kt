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
    BLUR(SHADER_COMMON + IMAGE_COMMON + BLUR_BODY, usesImage = true, imageEdge = 32),
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

/** Mesh gradient: five drifting colour points blended by inverse distance, warped by noise. */
private const val MESH_BODY = """
half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float t = iTime * 0.18;
    float2 w = float2(fbm(uv * 2.0 + t), fbm(uv * 2.0 + float2(4.0, 7.0) - t)) - 0.5;
    uv += w * 0.35;

    float3 acc = float3(0.0);
    float ws = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float2 pt = float2(0.5 + 0.42 * sin(t * (0.9 + 0.21 * fi) + fi * 1.9),
                           0.5 + 0.42 * cos(t * (0.7 + 0.17 * fi) + fi * 2.7));
        float d = length(uv - pt);
        float wgt = 1.0 / (pow(d, 2.4) + 0.015);
        acc += pick(i) * wgt;
        ws += wgt;
    }
    float3 col = acc / ws;
    col = mix(col * 0.55, col, smoothstep(0.0, 1.0, col.r + col.g + col.b));
    col += 0.04 * sin(uv.x * 6.0 + uv.y * 4.0 + iTime * 0.4);
    return finish(col * 0.9, fc);
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

/** PlayStation-style XMB: translucent ribbons with bright edges over a tinted gradient, plus drifting dust. */
private const val XMB_BODY = """
half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float aspect = iResolution.x / iResolution.y;
    float t = iTime * 0.25;

    float3 col = mix(c0 * 0.45, c1 * 0.18, uv.y) + c2 * 0.05 * sin(uv.x * 3.0 + t);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float x = uv.x * aspect;
        float y = 0.5 + 0.18 * sin(x * 1.2 + t * (1.0 + 0.3 * fi) + fi * 2.0) * cos(x * 0.5 - t * 0.6 + fi)
                + 0.07 * sin(x * 3.1 - t * 1.4 + fi * 1.7) + (fi - 1.5) * 0.07;
        float d = abs(uv.y - y);
        float thick = 0.09 + 0.05 * sin(x * 1.7 + t + fi * 3.0);
        float ribbon = smoothstep(thick, 0.0, d);
        float edge = exp(-d * 140.0);
        float3 rc = palette(fi / 4.0 + 0.15 + 0.1 * sin(t + fi));
        col += rc * ribbon * ribbon * 0.28;
        col += mix(rc, float3(1.0), 0.6) * edge * 0.35;
    }

    float2 g = uv * float2(aspect, 1.0) * 14.0;
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float rnd = hash21(id);
    float2 jitter = float2(hash21(id + 7.0), hash21(id + 13.0)) - 0.5;
    float tw = 0.5 + 0.5 * sin(iTime * (0.6 + rnd * 1.8) + rnd * 40.0);
    float dust = smoothstep(0.06, 0.0, length(f - jitter * 0.6)) * step(0.82, rnd) * tw;
    col += palette(rnd) * dust * 0.8;

    col *= 0.8 + 0.2 * smoothstep(1.2, 0.2, length(uv - 0.5) * 1.4);
    return finish(col, fc);
}
"""

/** Heavily blurred cover (a tiny bilinear-filtered copy), slowly drifting and warped. */
private const val BLUR_BODY = """
uniform shader img;
half4 main(float2 fc) {
    float2 p = (fc - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
    float t = iTime * 0.08;
    float2 w = float2(fbm(p * 1.4 + t), fbm(p * 1.4 + float2(3.7, 1.9) - t)) - 0.5;
    float2 q = fc + w * iResolution * 0.22;
    float z = 1.15 + 0.06 * sin(iTime * 0.11);
    q = (q - 0.5 * iResolution) / z + 0.5 * iResolution;

    float3 col = float3(img.eval(coverUV(q)).rgb);
    float l = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(l), col, 1.35) * 0.62;
    col += palette(fbm(p * 2.0 + t * 3.0)) * 0.04;
    col *= 0.75 + 0.25 * smoothstep(1.3, 0.2, length(p));
    return finish(col, fc);
}
"""

/** Cover art rippled by layered sine + noise displacement, with a chromatic split. */
private const val DISTORT_BODY = """
uniform shader img;
half4 main(float2 fc) {
    float2 uv = fc / iResolution;
    float m = min(iResolution.x, iResolution.y);
    float t = iTime * 0.35;

    float2 d = float2(sin(uv.y * 7.0 + t * 1.3) + 0.6 * sin(uv.y * 13.0 - t * 0.9),
                      cos(uv.x * 6.0 + t * 1.1) + 0.6 * cos(uv.x * 11.0 + t * 0.7));
    float2 n = float2(fbm(uv * 3.0 + t * 0.5), fbm(uv * 3.0 + 9.0 - t * 0.5)) - 0.5;
    float2 off = (d * 0.5 + n * 2.0) * 0.03 * m;

    float2 split = off * 0.35;
    float r = img.eval(coverUV(fc + off + split)).r;
    float g = img.eval(coverUV(fc + off)).g;
    float b = img.eval(coverUV(fc + off - split)).b;
    float3 col = float3(r, g, b);

    // Soft tint from the palette keeps it cohesive with the rest of the UI.
    col = mix(col, col * (0.6 + palette(fbm(uv * 2.0 + t)) * 0.8), 0.35);
    col *= 0.72 * (0.78 + 0.22 * smoothstep(1.3, 0.2, length(uv - 0.5) * 1.6));
    return finish(col, fc);
}
"""
