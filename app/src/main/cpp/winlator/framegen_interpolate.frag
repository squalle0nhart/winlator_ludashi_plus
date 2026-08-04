#version 450

layout(location = 0) in highp vec2 vUV;
layout(location = 0) out vec4 outColor;

layout(set = 0, binding = 0) uniform sampler2D prevFrame;
layout(set = 0, binding = 1) uniform sampler2D currFrame;
layout(set = 0, binding = 2) uniform sampler2D motionField;
layout(set = 0, binding = 3) uniform sampler2D motionFieldFwd;

layout(push_constant) uniform PC {
    vec2 resolution;
    float phase;
    float smoothness;
    float confidenceScale;
    float mode;
} pc;

bool offFrame(highp vec2 uv) {
    return uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0;
}

#define FM_S2(a, b) { vec2 t = min(a, b); b = max(a, b); a = t; }
#define FM_MN3(a, b, c) FM_S2(a, b); FM_S2(a, c)
#define FM_MX3(a, b, c) FM_S2(b, c); FM_S2(a, c)
#define FM_MNMX3(a, b, c) FM_MX3(a, b, c); FM_S2(a, b)
#define FM_MNMX4(a, b, c, d) FM_S2(a, b); FM_S2(c, d); FM_S2(a, c); FM_S2(b, d)
#define FM_MNMX5(a, b, c, d, e) FM_S2(a, b); FM_S2(c, d); FM_MN3(a, c, e); FM_MX3(b, d, e)
#define FM_MNMX6(a, b, c, d, e, f) FM_S2(a, d); FM_S2(b, e); FM_S2(c, f); FM_MN3(a, b, c); FM_MX3(d, e, f)

vec2 medianFlow(highp vec2 uv, highp vec2 texel, out float spread) {
    vec2 v0 = texture(motionField, uv + vec2(-texel.x, -texel.y)).xy;
    vec2 v1 = texture(motionField, uv + vec2(0.0, -texel.y)).xy;
    vec2 v2 = texture(motionField, uv + vec2(texel.x, -texel.y)).xy;
    vec2 v3 = texture(motionField, uv + vec2(-texel.x, 0.0)).xy;
    vec2 v4 = texture(motionField, uv).xy;
    vec2 v5 = texture(motionField, uv + vec2(texel.x, 0.0)).xy;
    vec2 v6 = texture(motionField, uv + vec2(-texel.x, texel.y)).xy;
    vec2 v7 = texture(motionField, uv + vec2(0.0, texel.y)).xy;
    vec2 v8 = texture(motionField, uv + vec2(texel.x, texel.y)).xy;
    vec2 low = min(min(min(v0, v1), min(v2, v3)), min(min(v4, v5), min(v6, v7)));
    vec2 high = max(max(max(v0, v1), max(v2, v3)), max(max(v4, v5), max(v6, v7)));
    spread = length(max(high, v8) - min(low, v8));
    FM_MNMX6(v0, v1, v2, v3, v4, v5);
    FM_MNMX5(v1, v2, v3, v4, v6);
    FM_MNMX4(v2, v3, v4, v7);
    FM_MNMX3(v3, v4, v8);
    return v4;
}

void main() {
    float t = clamp(pc.phase, 0.0, 1.0);
    float steadier = clamp(pc.smoothness, 0.0, 1.0);
    vec2 flowTexel = 1.0 / vec2(textureSize(motionField, 0));
    vec2 imageTexel = 1.0 / vec2(textureSize(currFrame, 0));
    float flowSpread;
    vec2 flow = medianFlow(vUV, flowTexel, flowSpread);
    vec2 normalizedFlow = flow * flowTexel;

    highp vec2 prevPos = vUV + t * normalizedFlow;
    highp vec2 currPos = vUV - (1.0 - t) * normalizedFlow;
    vec3 prevWarp = texture(prevFrame, prevPos).rgb;
    vec3 currWarp = texture(currFrame, currPos).rgb;
    vec3 prevFlat = texture(prevFrame, vUV).rgb;
    vec3 currFlat = texture(currFrame, vUV).rgb;

    float prevValid = offFrame(prevPos) ? 0.0 : 1.0;
    float currValid = offFrame(currPos) ? 0.0 : 1.0;
    float prevWeight = (1.0 - t) * prevValid;
    float currWeight = t * currValid;
    vec3 warped = (prevWarp * prevWeight + currWarp * currWeight)
                / max(prevWeight + currWeight, 1.0e-6);

    vec4 masks = texture(motionField, vUV);
    float staticPixel = max(masks.z,
        1.0 - smoothstep(0.02, 0.06, length(currFlat - prevFlat)));
    float confidence = smoothstep(0.08, 0.35, masks.w);
    float flowCoherence = 1.0 - smoothstep(2.0, 8.0, flowSpread);
    float sideDisagreement = abs(dot(prevWarp - currWarp, vec3(0.299, 0.587, 0.114)));
    float edgeTrust = 1.0 - smoothstep(0.05, 0.22, sideDisagreement);
    float trust = confidence * flowCoherence * mix(0.65, 1.0, edgeTrust);
    trust *= mix(1.0, 0.65, steadier);

    vec3 held = t < 0.5 ? prevWarp : currWarp;
    vec3 color = mix(held, warped, trust);

    vec3 prevBlur = (texture(prevFrame, prevPos + vec2(imageTexel.x, 0.0)).rgb
                   + texture(prevFrame, prevPos - vec2(imageTexel.x, 0.0)).rgb
                   + texture(prevFrame, prevPos + vec2(0.0, imageTexel.y)).rgb
                   + texture(prevFrame, prevPos - vec2(0.0, imageTexel.y)).rgb) * 0.25;
    vec3 currBlur = (texture(currFrame, currPos + vec2(imageTexel.x, 0.0)).rgb
                   + texture(currFrame, currPos - vec2(imageTexel.x, 0.0)).rgb
                   + texture(currFrame, currPos + vec2(0.0, imageTexel.y)).rgb
                   + texture(currFrame, currPos - vec2(0.0, imageTexel.y)).rgb) * 0.25;
    vec3 detail = ((prevWarp - prevBlur) * prevWeight + (currWarp - currBlur) * currWeight)
                / max(prevWeight + currWeight, 1.0e-6);
    color += (0.95 - 0.30 * steadier) * trust * clamp(detail, -0.25, 0.25);

    vec3 crossFade = mix(prevFlat, currFlat, t);
    color = mix(color, crossFade, staticPixel);
    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
