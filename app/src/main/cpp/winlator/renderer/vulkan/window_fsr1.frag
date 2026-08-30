#version 450

// WinZ FSR mode: single-pass edge-aware spatial reconstruction.
// It deliberately follows the proven SGSR sampling/reconstruction path, but
// uses a lower edge threshold and stronger reconstruction limits so mode 3 is
// the sharper/aggressive alternative without an intermediate image or RCAS.

precision mediump float;
precision highp int;

layout(push_constant) uniform PC {
    float ndcX0;
    float ndcY0;
    float ndcX1;
    float ndcY1;
    int   useTexAlpha;
    float srcW;
    float srcH;
    float outW;
    float outH;
    int   effectId;
    float sharpness;
} pc;

layout(binding = 0) uniform mediump sampler2D texSampler;
layout(location = 0) in highp vec2 fragTexCoord;
layout(location = 0) out vec4 outColor;

float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}

vec2 weightY(float dx, float dy, float c, float std, float spatialFactor) {
    float x = (dx * dx + dy * dy) * spatialFactor + clamp(abs(c) * std, 0.0, 1.0);
    float w = fastLanczos2(x);
    return vec2(w, w * c);
}

void main() {
    highp vec2 srcSize = max(vec2(pc.srcW, pc.srcH), vec2(1.0));
    highp vec2 step = 1.0 / srcSize;
    float userSharp = clamp(pc.sharpness, 0.0, 1.0);

    vec4 center = textureLod(texSampler, fragTexCoord, 0.0);

    highp vec2 imgCoord = fragTexCoord * srcSize + vec2(-0.5, 0.5);
    highp vec2 imgCoordFloor = floor(imgCoord);
    highp vec2 baseUV = imgCoordFloor * step;
    vec2 pl = imgCoord - imgCoordFloor;

    // Green is used as the inexpensive luminance proxy, matching the SGSR
    // path that already behaves well on the target mobile drivers.
    vec4 left = textureGather(texSampler, baseUV, 1);
    float centerG = center.g;
    float edgeVote = abs(left.z - left.y)
                   + abs(centerG - left.y)
                   + abs(centerG - left.z);

    // SGSR uses 12/255. FSR deliberately engages reconstruction earlier.
    const float EDGE_THRESHOLD = 8.0 / 255.0;
    if (edgeVote <= EDGE_THRESHOLD) {
        outColor = vec4(center.rgb, (pc.useTexAlpha != 0) ? center.a : 1.0);
        return;
    }

    highp vec2 baseUV2 = baseUV + vec2(step.x, 0.0);
    vec4 right = textureGather(texSampler, baseUV2 + vec2(step.x, 0.0), 1);

    vec4 upDown;
    upDown.xy = textureGather(texSampler, baseUV + vec2(0.0, -step.y), 1).wz;
    upDown.zw = textureGather(texSampler, baseUV + vec2(0.0,  step.y), 1).yx;

    float mean = (left.y + left.z + right.x + right.w) * 0.25;
    left   -= vec4(mean);
    right  -= vec4(mean);
    upDown -= vec4(mean);

    float sum =
        abs(left.x)   + abs(left.y)   + abs(left.z)   + abs(left.w)   +
        abs(right.x)  + abs(right.y)  + abs(right.z)  + abs(right.w)  +
        abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
    float std = 2.181818 / max(sum, 1.0e-6);

    // Narrower reconstruction kernel than SGSR (0.40..0.65), especially as
    // the shared sharpness slider rises.
    float spatialFactor = mix(0.52, 0.86, userSharp);

    vec2 aWY = weightY(pl.x,       pl.y + 1.0, upDown.x, std, spatialFactor);
    aWY += weightY(pl.x - 1.0, pl.y + 1.0, upDown.y, std, spatialFactor);
    aWY += weightY(pl.x - 1.0, pl.y - 2.0, upDown.z, std, spatialFactor);
    aWY += weightY(pl.x,       pl.y - 2.0, upDown.w, std, spatialFactor);
    aWY += weightY(pl.x + 1.0, pl.y - 1.0, left.x,   std, spatialFactor);
    aWY += weightY(pl.x,       pl.y - 1.0, left.y,   std, spatialFactor);
    aWY += weightY(pl.x,       pl.y,       left.z,   std, spatialFactor);
    aWY += weightY(pl.x + 1.0, pl.y,       left.w,   std, spatialFactor);
    aWY += weightY(pl.x - 1.0, pl.y - 1.0, right.x,  std, spatialFactor);
    aWY += weightY(pl.x - 2.0, pl.y - 1.0, right.y,  std, spatialFactor);
    aWY += weightY(pl.x - 2.0, pl.y,       right.z,  std, spatialFactor);
    aWY += weightY(pl.x - 1.0, pl.y,       right.w,  std, spatialFactor);

    float finalY = aWY.y / max(aWY.x, 1.0e-6);

    // Clamp against the real local range before applying the stronger edge
    // response. This keeps the aggressive mode from producing RCAS-like halos.
    float maxY = max(max(left.y, left.z), max(right.x, right.w)) + mean;
    float minY = min(min(left.y, left.z), min(right.x, right.w)) + mean;

    float edgeSharpness = mix(1.35, 2.75, userSharp);
    finalY = clamp(edgeSharpness * finalY + mean, minY, maxY);

    // SGSR is 16..40/255. FSR gets more room for visible fine-detail recovery.
    float maxDelta = mix(28.0, 64.0, userSharp) / 255.0;
    float deltaY = clamp(finalY - centerG, -maxDelta, maxDelta);

    vec3 result = clamp(center.rgb + vec3(deltaY), 0.0, 1.0);
    outColor = vec4(result, (pc.useTexAlpha != 0) ? center.a : 1.0);
}
