#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTextureCoord;
uniform sampler2D uVideo;
uniform vec2 uTexel;
uniform int uEnabled;
// denoise, sharpen, contrast, saturation
uniform vec4 uAmounts;
uniform float uDetailLimit;

float similarity(vec3 a, vec3 b) {
    vec3 difference = abs(a - b);
    float distance = max(difference.r, max(difference.g, difference.b));
    return 1.0 - smoothstep(0.02, 0.10, distance);
}
vec3 sampleAt(vec2 offset) {
    // Clamp even if an upstream texture uses a different wrapping mode.
    return texture2D(uVideo, clamp(vTextureCoord + offset, uTexel * 0.5, vec2(1.0) - uTexel * 0.5)).rgb;
}
void main() {
    vec4 source = texture2D(uVideo, vTextureCoord);
    if (uEnabled == 0) { gl_FragColor = source; return; }
    vec3 c = source.rgb;
    vec3 n = sampleAt(vec2(0.0, uTexel.y));
    vec3 s = sampleAt(vec2(0.0, -uTexel.y));
    vec3 e = sampleAt(vec2(uTexel.x, 0.0));
    vec3 w = sampleAt(vec2(-uTexel.x, 0.0));
    vec4 weights = vec4(similarity(c,n), similarity(c,s), similarity(c,e), similarity(c,w));
    vec3 smoothColor = (c * 2.0 + n * weights.x + s * weights.y + e * weights.z + w * weights.w)
        / (2.0 + dot(weights, vec4(1.0)));
    vec3 clean = mix(c, smoothColor, uAmounts.x);
    vec3 detail = c - (c * 4.0 + n + s + e + w) / 8.0;
    // Do not sharpen tiny noise; bound halos around balls, shirts and court lines.
    float strength = smoothstep(0.008, 0.04, max(abs(detail.r), max(abs(detail.g), abs(detail.b))));
    vec3 result = clean + clamp(detail * uAmounts.y, vec3(-uDetailLimit), vec3(uDetailLimit)) * strength;
    result = (result - 0.5) * uAmounts.z + 0.5;
    float luma = dot(result, vec3(0.2126, 0.7152, 0.0722));
    result = mix(vec3(luma), result, uAmounts.w);
    gl_FragColor = vec4(clamp(result, 0.0, 1.0), source.a);
}
