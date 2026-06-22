#version 450

layout(binding = 0) uniform sampler2D texSampler;

layout(push_constant) uniform PC {
    float ndcX0;
    float ndcY0;
    float ndcX1;
    float ndcY1;
    int   useTexAlpha;
    float strength;
    float profile;
} pc;

layout(location = 0) in  vec2 fragTexCoord;
layout(location = 0) out vec4 outColor;

const float PI = 3.14159265358979323846;

float hybrid_curve(float x, float prof) {
    float sin_shape = sin(0.5 * PI * x);
    float x2 = x * x;
    float x5 = x2 * x2 * x;
    float quintic = 0.5 * x5 - 1.5 * x2 * x + 2.0 * x;
    return mix(sin_shape, quintic, prof);
}

void main() {
    float x = fragTexCoord.x * 2.0 - 1.0;
    float curved = hybrid_curve(x, pc.profile);
    float warped = mix(x, curved, pc.strength);
    float u = clamp(0.5 * warped + 0.5, 0.0, 1.0);

    vec4 c = texture(texSampler, vec2(u, fragTexCoord.y));
    outColor = vec4(c.rgb, pc.useTexAlpha != 0 ? c.a : 1.0);
}
