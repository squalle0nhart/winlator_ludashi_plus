#version 450

layout(binding = 0) uniform sampler2D texSampler;

layout(push_constant) uniform PC {
    float ndcX0, ndcY0, ndcX1, ndcY1;
    int   effectId;
    float sharpness;
    float resW;
    float resH;
} pc;

layout(location = 0) in vec2 fragTexCoord;
layout(location = 0) out vec4 outColor;

vec3 applyFSR(vec2 uv, float sharp) {
    vec2 texel = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    vec3 c = texture(texSampler, uv).rgb;
    vec3 t = texture(texSampler, uv + vec2(0.0, -texel.y)).rgb;
    vec3 b = texture(texSampler, uv + vec2(0.0,  texel.y)).rgb;
    vec3 l = texture(texSampler, uv + vec2(-texel.x, 0.0)).rgb;
    vec3 r = texture(texSampler, uv + vec2( texel.x, 0.0)).rgb;

    vec3 mnRGB = min(c, min(min(t, b), min(l, r)));
    vec3 mxRGB = max(c, max(max(t, b), max(l, r)));

    vec3 num   = min(mnRGB, 1.0 - mxRGB);
    vec3 denom = mxRGB;
    vec3 wRGB  = sqrt(clamp(num / max(denom, 1e-4), 0.0, 1.0));
    float w    = (wRGB.r + wRGB.g + wRGB.b) * 0.333;

    float lobe = w * mix(-0.125, -0.200, sharp);
    return clamp((lobe * (t + b + l + r) + c) / (1.0 + 4.0 * lobe), 0.0, 1.0);
}

vec3 applyDLS(vec2 uv, float sharp) {
    vec2 texel  = 1.0 / max(vec2(pc.resW, pc.resH), vec2(1.0));
    float sat   = 1.0 + sharp * 0.20;
    float con   = 1.0 + sharp * 0.12;
    float sharpBoost = sharp * 1.2;

    vec3 orig = texture(texSampler, uv).rgb;
    vec3 c    = clamp((orig - 0.5) * con + 0.5, 0.0, 1.0);
    float gray = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(gray), c, sat);

    vec3 blur = (texture(texSampler, uv + vec2( 0.0,    -texel.y)).rgb
               + texture(texSampler, uv + vec2( 0.0,     texel.y)).rgb
               + texture(texSampler, uv + vec2(-texel.x,  0.0   )).rgb
               + texture(texSampler, uv + vec2( texel.x,  0.0   )).rgb) * 0.25;
    return clamp(c + (orig - blur) * sharpBoost, 0.0, 1.0);
}

void main() {
    vec3 rgb;
    if      (pc.effectId == 1) rgb = applyFSR(fragTexCoord, pc.sharpness);
    else if (pc.effectId == 2) rgb = applyDLS(fragTexCoord, pc.sharpness);
    else                       rgb = texture(texSampler, fragTexCoord).rgb;
    outColor = vec4(rgb, 1.0);
}
