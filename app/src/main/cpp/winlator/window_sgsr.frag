#version 450

precision mediump float;
precision highp int;

layout(push_constant) uniform PC {
    float ndcX0;
    float ndcY0;
    float ndcX1;
    float ndcY1;
    int   useTexAlpha;
    float invSrcW;
    float invSrcH;
    float srcW;
    float srcH;
    int   effectId;   
    float resW;       
    float sharpness;
} pc;

layout(binding = 0) uniform mediump sampler2D texSampler;
layout(location = 0) in  highp vec2 fragTexCoord;
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

vec3 applyDLS(vec3 center, vec2 uv, float sharp) {
    vec2 texel  = vec2(pc.invSrcW, pc.invSrcH);
    float SAT   = 1.0 + sharp * 0.20;
    float CON   = 1.0 + sharp * 0.12;
    float SHARP = sharp * 1.2;
    vec3 c = clamp((center - 0.5) * CON + 0.5, 0.0, 1.0);
    float gray = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(gray), c, SAT);

    vec3 blur = (texture(texSampler, uv + vec2( 0.0,    -texel.y)).rgb
               + texture(texSampler, uv + vec2( 0.0,     texel.y)).rgb
               + texture(texSampler, uv + vec2(-texel.x,  0.0   )).rgb
               + texture(texSampler, uv + vec2( texel.x,  0.0   )).rgb) * 0.25;
    return clamp(c + (center - blur) * SHARP, 0.0, 1.0);
}

vec3 applyCRT(vec3 center, vec2 uv) {

    const float CA = 1.0025;
    float r = texture(texSampler, (uv - 0.5) * CA + 0.5).r;
    float b = texture(texSampler, (uv - 0.5) / CA + 0.5).b;
    vec3 fc  = vec3(r, center.g, b);
    float sx = abs(sin(uv.x * 1024.0) * 0.5 * 0.125);
    float sy = abs(sin(uv.y * 1024.0) * 0.5 * 0.375);
    return mix(fc, vec3(0.0), sx + sy);
}

vec3 applyHDR(vec3 center, vec2 uv) {
    vec2 texel = vec2(pc.invSrcW, pc.invSrcH);
    const float r1 = 0.793, r2 = 0.870;
    vec3 b1 = vec3(0.0), b2 = vec3(0.0);
    vec2 offs[8] = vec2[](
        vec2( 1.5, -1.5), vec2(-1.5, -1.5), vec2( 1.5,  1.5), vec2(-1.5,  1.5),
        vec2( 0.0, -2.5), vec2( 0.0,  2.5), vec2(-2.5,  0.0), vec2( 2.5,  0.0)
    );
    for (int i = 0; i < 8; i++) {
        b1 += texture(texSampler, uv + offs[i] * r1 * texel).rgb;
        b2 += texture(texSampler, uv + offs[i] * r2 * texel).rgb;
    }
    b1 *= 0.005; b2 *= 0.010;
    vec3 hdr = (center + (b2 - b1)) * (r2 - r1);
    return clamp(pow(abs(hdr + center), vec3(1.30)) + hdr, 0.0, 1.0);
}

vec3 applyNatural(vec3 c) {
    mat3 toYIQ = mat3( 0.299,  0.596,  0.212,
                       0.587, -0.275, -0.523,
                       0.114, -0.321,  0.311);
    mat3 toRGB = mat3( 1.0,         1.0,         1.0,
                       0.95568806, -0.27158179, -1.10817732,
                       0.61985809, -0.64687381,  1.70506455);
    vec3 t = c * toYIQ;
    t = vec3(pow(t.r, 1.12), t.g * 1.2, t.b * 1.2);
    return clamp(t * toRGB, 0.0, 1.0);
}

void applyPostFX(inout vec3 rgb, vec2 uv) {
    if      (pc.effectId == 1) rgb = applyDLS    (rgb, uv, pc.sharpness);
    else if (pc.effectId == 2) rgb = applyCRT    (rgb, uv);
    else if (pc.effectId == 3) rgb = applyHDR    (rgb, uv);
    else if (pc.effectId == 4) rgb = applyNatural(rgb);
}

void main() {
    highp vec2 step = vec2(pc.invSrcW, pc.invSrcH);

    vec4 center = textureLod(texSampler, fragTexCoord, 0.0);

    highp vec2 imgCoord      = fragTexCoord * vec2(pc.srcW, pc.srcH) + vec2(-0.5, 0.5);
    highp vec2 imgCoordFloor = floor(imgCoord);
    highp vec2 baseUV        = imgCoordFloor * step;
    vec2  pl                 = imgCoord - imgCoordFloor;

    vec4 left = textureGather(texSampler, baseUV, 1);

    float centerG  = center.g;
    float edgeVote = abs(left.z - left.y) + abs(centerG - left.y) + abs(centerG - left.z);

    const float EDGE_THRESHOLD = 12.0 / 255.0;

    if (edgeVote <= EDGE_THRESHOLD) {
        vec3 rgb = center.rgb;
        if (pc.effectId != 0) applyPostFX(rgb, fragTexCoord);
        outColor = vec4(rgb, (pc.useTexAlpha != 0) ? center.a : 1.0);
        return;
    }

    highp vec2 baseUV2 = baseUV + vec2(step.x, 0.0);

    vec4 right;
    right = textureGather(texSampler, baseUV2 + vec2(step.x, 0.0), 1);

    vec4 upDown;
    upDown.xy = textureGather(texSampler, baseUV + vec2(0.0, -step.y), 1).wz;
    upDown.zw = textureGather(texSampler, baseUV + vec2(0.0,  step.y), 1).yx;

    float mean = (left.y + left.z + right.x + right.w) * 0.25;
    left   -= vec4(mean);
    right  -= vec4(mean);
    upDown -= vec4(mean);
    float cDelta = centerG - mean;

    float sum =
        abs(left.x)   + abs(left.y)   + abs(left.z)   + abs(left.w)   +
        abs(right.x)  + abs(right.y)  + abs(right.z)  + abs(right.w)  +
        abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
    float std = 2.181818 / max(sum, 1.0e-6);

    float spatialFactor = mix(0.40, 0.65, clamp(pc.sharpness, 0.0, 1.0));

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

    float maxY = max(max(left.y, left.z), max(right.x, right.w)) + mean;
    float minY = min(min(left.y, left.z), min(right.x, right.w)) + mean;

    float edgeSharpness = mix(1.0, 2.0, clamp(pc.sharpness, 0.0, 1.0));
    finalY = clamp(edgeSharpness * finalY + mean, minY, maxY);

    float maxDelta = mix(16.0, 40.0, clamp(pc.sharpness, 0.0, 1.0)) / 255.0;
    float deltaY   = clamp(finalY - centerG, -maxDelta, maxDelta);

    vec4 result;
    result.rgb = clamp(center.rgb + vec3(deltaY), 0.0, 1.0);
    result.a   = (pc.useTexAlpha != 0) ? center.a : 1.0;

    if (pc.effectId != 0) applyPostFX(result.rgb, fragTexCoord);

    outColor = result;
}
