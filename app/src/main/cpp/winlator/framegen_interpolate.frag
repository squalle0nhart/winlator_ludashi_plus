#version 450

layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D prevFrame;
layout(set = 0, binding = 1) uniform sampler2D currFrame;
layout(set = 0, binding = 2) uniform sampler2D motionField;

layout(push_constant) uniform PC {
    vec2 resolution;
    float phase;
    float occlusionLo;
    float occlusionHi;
    float _pad;
} pc;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

bool offFrame(vec2 uv) {
    return any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)));
}

void main() {
    float t = clamp(pc.phase, 0.0, 1.0);
    float lo = pc.occlusionLo > 0.0 ? pc.occlusionLo : 0.06;
    float hi = pc.occlusionHi > lo ? pc.occlusionHi : 0.25;
    vec2 mvNorm = texture(motionField, vUV).xy * 2.0 / pc.resolution;
    vec2 prevPos = vUV + t * mvNorm;
    vec2 currPos = vUV - (1.0 - t) * mvNorm;
    vec3 cPrev = texture(prevFrame, prevPos).rgb;
    vec3 cCurr = texture(currFrame, currPos).rgb;
    vec3 compensated = mix(cPrev, cCurr, t);
    float disagree = abs(luma(cPrev) - luma(cCurr));
    float trust = 1.0 - smoothstep(lo, hi, disagree);
    if (offFrame(prevPos) || offFrame(currPos)) trust = 0.0;
    vec3 nearest = t < 0.5 ? texture(prevFrame, vUV).rgb : texture(currFrame, vUV).rgb;
    outColor = vec4(clamp(mix(nearest, compensated, trust), 0.0, 1.0), 1.0);
}
