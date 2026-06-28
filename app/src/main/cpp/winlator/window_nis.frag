#version 450

// =============================================================================
//  NVIDIA Image Scaling (NIS) - NVScaler spatial upscaler + adaptive sharpen.
//
//  Ported from NVIDIA's reference NVScaler:
//    NVIDIAGameWorks/NVIDIAImageScaling  NIS/NIS_Scaler.h  (+ NIS_Config.h)
//
//  The MIT License (MIT)
//  Copyright (c) 2022 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
//  SPDX-License-Identifier: MIT
//
//  Adapted for the Winlator native Vulkan compositor:
//   - #version 450 (Vulkan GLSL), single-pass FRAGMENT shader (the reference is
//     an LDS-tiled compute shader). The block/LDS cooperative tile loads are
//     replaced with direct per-fragment texture() fetches of the 6x6 luma
//     support, and the edge map is computed inline from that support. The math
//     (edge detection + 6-tap directional scale/USM blend) is the reference's.
//   - The kPhaseCount x 6 scaler/USM coefficient banks (coef_scale, coef_usm,
//     fp32 path) are baked below as const arrays.
//   - Sharpness-derived params are recomputed in-shader exactly as
//     NVScalerUpdateConfig does, driven by the existing sharpness slider.
//   - fp32 only (not the fp16/bit-packed NIS_USE_HALF_PRECISION path) so there
//     are no bitwise/bvec ops.
// =============================================================================

layout(binding = 0) uniform sampler2D texSampler;

layout(push_constant) uniform PC {
    float ndcX0;
    float ndcY0;
    float ndcX1;
    float ndcY1;
    float invSrcW;
    float invSrcH;
    float srcW;
    float srcH;
    float sharpness;
} pc;

layout(location = 0) in  vec2 fragTexCoord;
layout(location = 0) out vec4 outColor;

const float kDetectRatio      = 2.0 * 1127.0 / 1024.0;
const float kDetectThres      = 64.0 / 1024.0;
const float kMinContrastRatio = 2.0;
const float kMaxContrastRatio = 10.0;
const float kRatioNorm        = 1.0 / (kMaxContrastRatio - kMinContrastRatio);
const float kContrastBoost    = 1.0;
const float kEps              = 1.0 / 255.0;
const float kSharpStartY      = 0.45;
const float kSharpEndY        = 0.9;
const float kSharpScaleY      = 1.0 / (kSharpEndY - kSharpStartY);
const int   kPhaseCount       = 64;

float gSharpStrengthMin;
float gSharpStrengthScale;
float gSharpLimitMin;
float gSharpLimitScale;

const float coefScale[384] = float[384](
    0.0, 0.0, 1.0000, 0.0, 0.0, 0.0,
    0.0029, -0.0127, 1.0000, 0.0132, -0.0034, 0.0,
    0.0063, -0.0249, 0.9985, 0.0269, -0.0068, 0.0,
    0.0088, -0.0361, 0.9956, 0.0415, -0.0103, 0.0005,
    0.0117, -0.0474, 0.9932, 0.0562, -0.0142, 0.0005,
    0.0142, -0.0576, 0.9897, 0.0713, -0.0181, 0.0005,
    0.0166, -0.0674, 0.9844, 0.0874, -0.0220, 0.0010,
    0.0186, -0.0762, 0.9785, 0.1040, -0.0264, 0.0015,
    0.0205, -0.0850, 0.9727, 0.1206, -0.0308, 0.0020,
    0.0225, -0.0928, 0.9648, 0.1382, -0.0352, 0.0024,
    0.0239, -0.1006, 0.9575, 0.1558, -0.0396, 0.0029,
    0.0254, -0.1074, 0.9487, 0.1738, -0.0439, 0.0034,
    0.0264, -0.1138, 0.9390, 0.1929, -0.0488, 0.0044,
    0.0278, -0.1191, 0.9282, 0.2119, -0.0537, 0.0049,
    0.0288, -0.1245, 0.9170, 0.2310, -0.0581, 0.0059,
    0.0293, -0.1294, 0.9058, 0.2510, -0.0630, 0.0063,
    0.0303, -0.1333, 0.8926, 0.2710, -0.0679, 0.0073,
    0.0308, -0.1367, 0.8789, 0.2915, -0.0728, 0.0083,
    0.0308, -0.1401, 0.8657, 0.3120, -0.0776, 0.0093,
    0.0313, -0.1426, 0.8506, 0.3330, -0.0825, 0.0103,
    0.0313, -0.1445, 0.8354, 0.3540, -0.0874, 0.0112,
    0.0313, -0.1460, 0.8193, 0.3755, -0.0923, 0.0122,
    0.0313, -0.1470, 0.8022, 0.3965, -0.0967, 0.0137,
    0.0308, -0.1479, 0.7856, 0.4185, -0.1016, 0.0146,
    0.0303, -0.1479, 0.7681, 0.4399, -0.1060, 0.0156,
    0.0298, -0.1479, 0.7505, 0.4614, -0.1104, 0.0166,
    0.0293, -0.1470, 0.7314, 0.4829, -0.1147, 0.0181,
    0.0288, -0.1460, 0.7119, 0.5049, -0.1187, 0.0190,
    0.0278, -0.1445, 0.6929, 0.5264, -0.1226, 0.0200,
    0.0273, -0.1431, 0.6724, 0.5479, -0.1260, 0.0215,
    0.0264, -0.1411, 0.6528, 0.5693, -0.1299, 0.0225,
    0.0254, -0.1387, 0.6323, 0.5903, -0.1328, 0.0234,
    0.0244, -0.1357, 0.6113, 0.6113, -0.1357, 0.0244,
    0.0234, -0.1328, 0.5903, 0.6323, -0.1387, 0.0254,
    0.0225, -0.1299, 0.5693, 0.6528, -0.1411, 0.0264,
    0.0215, -0.1260, 0.5479, 0.6724, -0.1431, 0.0273,
    0.0200, -0.1226, 0.5264, 0.6929, -0.1445, 0.0278,
    0.0190, -0.1187, 0.5049, 0.7119, -0.1460, 0.0288,
    0.0181, -0.1147, 0.4829, 0.7314, -0.1470, 0.0293,
    0.0166, -0.1104, 0.4614, 0.7505, -0.1479, 0.0298,
    0.0156, -0.1060, 0.4399, 0.7681, -0.1479, 0.0303,
    0.0146, -0.1016, 0.4185, 0.7856, -0.1479, 0.0308,
    0.0137, -0.0967, 0.3965, 0.8022, -0.1470, 0.0313,
    0.0122, -0.0923, 0.3755, 0.8193, -0.1460, 0.0313,
    0.0112, -0.0874, 0.3540, 0.8354, -0.1445, 0.0313,
    0.0103, -0.0825, 0.3330, 0.8506, -0.1426, 0.0313,
    0.0093, -0.0776, 0.3120, 0.8657, -0.1401, 0.0308,
    0.0083, -0.0728, 0.2915, 0.8789, -0.1367, 0.0308,
    0.0073, -0.0679, 0.2710, 0.8926, -0.1333, 0.0303,
    0.0063, -0.0630, 0.2510, 0.9058, -0.1294, 0.0293,
    0.0059, -0.0581, 0.2310, 0.9170, -0.1245, 0.0288,
    0.0049, -0.0537, 0.2119, 0.9282, -0.1191, 0.0278,
    0.0044, -0.0488, 0.1929, 0.9390, -0.1138, 0.0264,
    0.0034, -0.0439, 0.1738, 0.9487, -0.1074, 0.0254,
    0.0029, -0.0396, 0.1558, 0.9575, -0.1006, 0.0239,
    0.0024, -0.0352, 0.1382, 0.9648, -0.0928, 0.0225,
    0.0020, -0.0308, 0.1206, 0.9727, -0.0850, 0.0205,
    0.0015, -0.0264, 0.1040, 0.9785, -0.0762, 0.0186,
    0.0010, -0.0220, 0.0874, 0.9844, -0.0674, 0.0166,
    0.0005, -0.0181, 0.0713, 0.9897, -0.0576, 0.0142,
    0.0005, -0.0142, 0.0562, 0.9932, -0.0474, 0.0117,
    0.0005, -0.0103, 0.0415, 0.9956, -0.0361, 0.0088,
    0.0, -0.0068, 0.0269, 0.9985, -0.0249, 0.0063,
    0.0, -0.0034, 0.0132, 1.0000, -0.0127, 0.0029
);

const float coefUsm[384] = float[384](
    0.0, -0.6001, 1.2002, -0.6001, 0.0, 0.0,
    0.0029, -0.6084, 1.1987, -0.5903, -0.0029, 0.0,
    0.0049, -0.6147, 1.1958, -0.5791, -0.0068, 0.0005,
    0.0073, -0.6196, 1.1890, -0.5659, -0.0103, 0.0,
    0.0093, -0.6235, 1.1802, -0.5513, -0.0151, 0.0,
    0.0112, -0.6265, 1.1699, -0.5352, -0.0195, 0.0005,
    0.0122, -0.6270, 1.1582, -0.5181, -0.0259, 0.0005,
    0.0142, -0.6284, 1.1455, -0.5005, -0.0317, 0.0005,
    0.0156, -0.6265, 1.1274, -0.4790, -0.0386, 0.0005,
    0.0166, -0.6235, 1.1089, -0.4570, -0.0454, 0.0010,
    0.0176, -0.6187, 1.0879, -0.4346, -0.0532, 0.0010,
    0.0181, -0.6138, 1.0659, -0.4102, -0.0615, 0.0015,
    0.0190, -0.6069, 1.0405, -0.3843, -0.0698, 0.0015,
    0.0195, -0.6006, 1.0161, -0.3574, -0.0796, 0.0020,
    0.0200, -0.5928, 0.9893, -0.3286, -0.0898, 0.0024,
    0.0200, -0.5820, 0.9580, -0.2988, -0.1001, 0.0029,
    0.0200, -0.5728, 0.9292, -0.2690, -0.1104, 0.0034,
    0.0200, -0.5620, 0.8975, -0.2368, -0.1226, 0.0039,
    0.0205, -0.5498, 0.8643, -0.2046, -0.1343, 0.0044,
    0.0200, -0.5371, 0.8301, -0.1709, -0.1465, 0.0049,
    0.0195, -0.5239, 0.7944, -0.1367, -0.1587, 0.0054,
    0.0195, -0.5107, 0.7598, -0.1021, -0.1724, 0.0059,
    0.0190, -0.4966, 0.7231, -0.0649, -0.1865, 0.0063,
    0.0186, -0.4819, 0.6846, -0.0288, -0.1997, 0.0068,
    0.0186, -0.4668, 0.6460, 0.0093, -0.2144, 0.0073,
    0.0176, -0.4507, 0.6055, 0.0479, -0.2290, 0.0083,
    0.0171, -0.4370, 0.5693, 0.0859, -0.2446, 0.0088,
    0.0161, -0.4199, 0.5283, 0.1255, -0.2598, 0.0098,
    0.0161, -0.4048, 0.4883, 0.1655, -0.2754, 0.0103,
    0.0151, -0.3887, 0.4497, 0.2041, -0.2910, 0.0107,
    0.0142, -0.3711, 0.4072, 0.2446, -0.3066, 0.0117,
    0.0137, -0.3555, 0.3672, 0.2852, -0.3228, 0.0122,
    0.0132, -0.3394, 0.3262, 0.3262, -0.3394, 0.0132,
    0.0122, -0.3228, 0.2852, 0.3672, -0.3555, 0.0137,
    0.0117, -0.3066, 0.2446, 0.4072, -0.3711, 0.0142,
    0.0107, -0.2910, 0.2041, 0.4497, -0.3887, 0.0151,
    0.0103, -0.2754, 0.1655, 0.4883, -0.4048, 0.0161,
    0.0098, -0.2598, 0.1255, 0.5283, -0.4199, 0.0161,
    0.0088, -0.2446, 0.0859, 0.5693, -0.4370, 0.0171,
    0.0083, -0.2290, 0.0479, 0.6055, -0.4507, 0.0176,
    0.0073, -0.2144, 0.0093, 0.6460, -0.4668, 0.0186,
    0.0068, -0.1997, -0.0288, 0.6846, -0.4819, 0.0186,
    0.0063, -0.1865, -0.0649, 0.7231, -0.4966, 0.0190,
    0.0059, -0.1724, -0.1021, 0.7598, -0.5107, 0.0195,
    0.0054, -0.1587, -0.1367, 0.7944, -0.5239, 0.0195,
    0.0049, -0.1465, -0.1709, 0.8301, -0.5371, 0.0200,
    0.0044, -0.1343, -0.2046, 0.8643, -0.5498, 0.0205,
    0.0039, -0.1226, -0.2368, 0.8975, -0.5620, 0.0200,
    0.0034, -0.1104, -0.2690, 0.9292, -0.5728, 0.0200,
    0.0029, -0.1001, -0.2988, 0.9580, -0.5820, 0.0200,
    0.0024, -0.0898, -0.3286, 0.9893, -0.5928, 0.0200,
    0.0020, -0.0796, -0.3574, 1.0161, -0.6006, 0.0195,
    0.0015, -0.0698, -0.3843, 1.0405, -0.6069, 0.0190,
    0.0015, -0.0615, -0.4102, 1.0659, -0.6138, 0.0181,
    0.0010, -0.0532, -0.4346, 1.0879, -0.6187, 0.0176,
    0.0010, -0.0454, -0.4570, 1.1089, -0.6235, 0.0166,
    0.0005, -0.0386, -0.4790, 1.1274, -0.6265, 0.0156,
    0.0005, -0.0317, -0.5005, 1.1455, -0.6284, 0.0142,
    0.0005, -0.0259, -0.5181, 1.1582, -0.6270, 0.0122,
    0.0005, -0.0195, -0.5352, 1.1699, -0.6265, 0.0112,
    0.0, -0.0151, -0.5513, 1.1802, -0.6235, 0.0093,
    0.0, -0.0103, -0.5659, 1.1890, -0.6196, 0.0073,
    0.0005, -0.0068, -0.5791, 1.1958, -0.6147, 0.0049,
    0.0, -0.0029, -0.5903, 1.1987, -0.6084, 0.0029
);

float getY(vec3 rgb) {
    return 0.2126 * rgb.x + 0.7152 * rgb.y + 0.0722 * rgb.z;
}

vec4 edgeAt(float p00, float p01, float p02,
            float p10, float p11, float p12,
            float p20, float p21, float p22) {
    float g_0   = abs(p00 + p01 + p02 - p20 - p21 - p22);
    float g_45  = abs(p10 + p00 + p01 - p21 - p22 - p12);
    float g_90  = abs(p00 + p10 + p20 - p02 - p12 - p22);
    float g_135 = abs(p10 + p20 + p21 - p01 - p02 - p12);

    float g_0_90_max   = max(g_0, g_90);
    float g_0_90_min   = min(g_0, g_90);
    float g_45_135_max = max(g_45, g_135);
    float g_45_135_min = min(g_45, g_135);

    if (g_0_90_max + g_45_135_max == 0.0) {
        return vec4(0.0);
    }

    float e_0_90   = min(g_0_90_max / (g_0_90_max + g_45_135_max), 1.0);
    float e_45_135 = 1.0 - e_0_90;

    bool c_0_90     = (g_0_90_max > (g_0_90_min * kDetectRatio)) && (g_0_90_max > kDetectThres) && (g_0_90_max > g_45_135_min);
    bool c_45_135   = (g_45_135_max > (g_45_135_min * kDetectRatio)) && (g_45_135_max > kDetectThres) && (g_45_135_max > g_0_90_min);
    bool c_g_0_90   = (g_0_90_max == g_0);
    bool c_g_45_135 = (g_45_135_max == g_45);

    float f_e_0_90   = (c_0_90 && c_45_135) ? e_0_90 : 1.0;
    float f_e_45_135 = (c_0_90 && c_45_135) ? e_45_135 : 1.0;

    float w0   = (c_0_90 && c_g_0_90)      ? f_e_0_90   : 0.0;
    float w90  = (c_0_90 && !c_g_0_90)     ? f_e_0_90   : 0.0;
    float w45  = (c_45_135 && c_g_45_135)  ? f_e_45_135 : 0.0;
    float w135 = (c_45_135 && !c_g_45_135) ? f_e_45_135 : 0.0;

    return vec4(w0, w90, w45, w135);
}

vec4 getInterpEdgeMap(vec4 e00, vec4 e01, vec4 e10, vec4 e11, float fx, float fy) {
    vec4 h0 = mix(e00, e01, fx);
    vec4 h1 = mix(e10, e11, fx);
    return mix(h0, h1, fy);
}

float calcLTI(float p0, float p1, float p2, float p3, float p4, float p5, int phaseIndex) {
    bool selector = (phaseIndex <= kPhaseCount / 2);
    float sel = selector ? p0 : p3;
    float aMin = min(min(p1, p2), sel);
    float aMax = max(max(p1, p2), sel);
    sel = selector ? p2 : p5;
    float bMin = min(min(p3, p4), sel);
    float bMax = max(max(p3, p4), sel);

    float aCont = aMax - aMin;
    float bCont = bMax - bMin;

    float contRatio = max(aCont, bCont) / (min(aCont, bCont) + kEps);
    return (1.0 - clamp((contRatio - kMinContrastRatio) * kRatioNorm, 0.0, 1.0)) * kContrastBoost;
}

float evalPoly6(float pxl[6], int phaseInt) {
    phaseInt = clamp(phaseInt, 0, 63);
    float y = 0.0;
    for (int i = 0; i < 6; ++i) {
        y += coefScale[phaseInt * 6 + i] * pxl[i];
    }
    float yUsm = 0.0;
    for (int i = 0; i < 6; ++i) {
        yUsm += coefUsm[phaseInt * 6 + i] * pxl[i];
    }

    float yScale = 1.0 - clamp((y - kSharpStartY) * kSharpScaleY, 0.0, 1.0);
    float ySharpness = yScale * gSharpStrengthScale + gSharpStrengthMin;
    yUsm *= ySharpness;

    float ySharpnessLimit = (yScale * gSharpLimitScale + gSharpLimitMin) * y;
    yUsm = min(ySharpnessLimit, max(-ySharpnessLimit, yUsm));
    yUsm *= calcLTI(pxl[0], pxl[1], pxl[2], pxl[3], pxl[4], pxl[5], phaseInt);

    return y + yUsm;
}

float filterNormal(float p[6][6], int phaseXInt, int phaseYInt) {
    phaseXInt = clamp(phaseXInt, 0, 63);
    phaseYInt = clamp(phaseYInt, 0, 63);
    float hAcc = 0.0;
    for (int j = 0; j < 6; ++j) {
        float vAcc = 0.0;
        for (int i = 0; i < 6; ++i) {
            vAcc += p[i][j] * coefScale[phaseYInt * 6 + i];
        }
        hAcc += vAcc * coefScale[phaseXInt * 6 + j];
    }
    return hAcc;
}

float addDirFilters(float p[6][6], float phaseXFrac, float phaseYFrac,
                    int phaseXFracInt, int phaseYFracInt, vec4 w) {
    float f = 0.0;
    if (w.x > 0.0) {
        float interp0Deg[6];
        for (int i = 0; i < 6; ++i) {
            interp0Deg[i] = mix(p[i][2], p[i][3], phaseXFrac);
        }
        f += evalPoly6(interp0Deg, phaseYFracInt) * w.x;
    }
    if (w.y > 0.0) {
        float interp90Deg[6];
        for (int i = 0; i < 6; ++i) {
            interp90Deg[i] = mix(p[2][i], p[3][i], phaseYFrac);
        }
        f += evalPoly6(interp90Deg, phaseXFracInt) * w.y;
    }
    if (w.z > 0.0) {
        float pphaseB45 = 0.5 + 0.5 * (phaseXFrac - phaseYFrac);

        float tempInterp45Deg[7];
        tempInterp45Deg[1] = mix(p[2][1], p[1][2], pphaseB45);
        tempInterp45Deg[3] = mix(p[3][2], p[2][3], pphaseB45);
        tempInterp45Deg[5] = mix(p[4][3], p[3][4], pphaseB45);
        {
            pphaseB45 = pphaseB45 - 0.5;
            float a = (pphaseB45 >= 0.0) ? p[0][2] : p[2][0];
            float b = (pphaseB45 >= 0.0) ? p[1][3] : p[3][1];
            float c = (pphaseB45 >= 0.0) ? p[2][4] : p[4][2];
            float d = (pphaseB45 >= 0.0) ? p[3][5] : p[5][3];
            tempInterp45Deg[0] = mix(p[1][1], a, abs(pphaseB45));
            tempInterp45Deg[2] = mix(p[2][2], b, abs(pphaseB45));
            tempInterp45Deg[4] = mix(p[3][3], c, abs(pphaseB45));
            tempInterp45Deg[6] = mix(p[4][4], d, abs(pphaseB45));
        }

        float interp45Deg[6];
        float pphaseP45 = phaseXFrac + phaseYFrac;
        if (pphaseP45 >= 1.0) {
            for (int i = 0; i < 6; ++i) {
                interp45Deg[i] = tempInterp45Deg[i + 1];
            }
            pphaseP45 = pphaseP45 - 1.0;
        } else {
            for (int i = 0; i < 6; ++i) {
                interp45Deg[i] = tempInterp45Deg[i];
            }
        }

        f += evalPoly6(interp45Deg, int(pphaseP45 * 64.0)) * w.z;
    }
    if (w.w > 0.0) {
        float pphaseB135 = 0.5 * (phaseXFrac + phaseYFrac);

        float tempInterp135Deg[7];
        tempInterp135Deg[1] = mix(p[3][1], p[4][2], pphaseB135);
        tempInterp135Deg[3] = mix(p[2][2], p[3][3], pphaseB135);
        tempInterp135Deg[5] = mix(p[1][3], p[2][4], pphaseB135);
        {
            pphaseB135 = pphaseB135 - 0.5;
            float a = (pphaseB135 >= 0.0) ? p[5][2] : p[3][0];
            float b = (pphaseB135 >= 0.0) ? p[4][3] : p[2][1];
            float c = (pphaseB135 >= 0.0) ? p[3][4] : p[1][2];
            float d = (pphaseB135 >= 0.0) ? p[2][5] : p[0][3];
            tempInterp135Deg[0] = mix(p[4][1], a, abs(pphaseB135));
            tempInterp135Deg[2] = mix(p[3][2], b, abs(pphaseB135));
            tempInterp135Deg[4] = mix(p[2][3], c, abs(pphaseB135));
            tempInterp135Deg[6] = mix(p[1][4], d, abs(pphaseB135));
        }

        float interp135Deg[6];
        float pphaseP135 = 1.0 + (phaseXFrac - phaseYFrac);
        if (pphaseP135 >= 1.0) {
            for (int i = 0; i < 6; ++i) {
                interp135Deg[i] = tempInterp135Deg[i + 1];
            }
            pphaseP135 = pphaseP135 - 1.0;
        } else {
            for (int i = 0; i < 6; ++i) {
                interp135Deg[i] = tempInterp135Deg[i];
            }
        }

        f += evalPoly6(interp135Deg, int(pphaseP135 * 64.0)) * w.w;
    }
    return f;
}

void main() {
    float sharp = clamp(pc.sharpness, 0.0, 1.0);
    float sharpenSlider = sharp - 0.5;
    float maxScale = (sharpenSlider >= 0.0) ? 1.25 : 1.75;
    float minScale = (sharpenSlider >= 0.0) ? 1.25 : 1.0;
    float limitScale = (sharpenSlider >= 0.0) ? 1.25 : 1.0;

    gSharpStrengthMin = max(0.0, 0.4 + sharpenSlider * minScale * 1.2);
    float sharpStrengthMax = 1.6 + sharpenSlider * maxScale * 1.8;
    gSharpStrengthScale = sharpStrengthMax - gSharpStrengthMin;
    gSharpLimitMin = max(0.1, 0.14 + sharpenSlider * limitScale * 0.32);
    float sharpLimitMax = 0.5 + sharpenSlider * limitScale * 0.6;
    gSharpLimitScale = sharpLimitMax - gSharpLimitMin;

    vec2 inSize = vec2(pc.srcW, pc.srcH);
    vec2 invIn = vec2(pc.invSrcW, pc.invSrcH);

    float srcX = fragTexCoord.x * inSize.x - 0.5;
    float srcY = fragTexCoord.y * inSize.y - 0.5;
    float fsx = floor(srcX);
    float fsy = floor(srcY);
    float fx = srcX - fsx;
    float fy = srcY - fsy;
    int fxInt = int(fx * float(kPhaseCount));
    int fyInt = int(fy * float(kPhaseCount));

    float p[6][6];
    for (int i = 0; i < 6; ++i) {
        for (int j = 0; j < 6; ++j) {
            vec2 tc = (vec2(fsx + float(j) - 2.0, fsy + float(i) - 2.0) + 0.5) * invIn;
            p[i][j] = getY(textureLod(texSampler, tc, 0.0).rgb);
        }
    }

    vec4 e00 = edgeAt(p[1][1], p[1][2], p[1][3],  p[2][1], p[2][2], p[2][3],  p[3][1], p[3][2], p[3][3]);
    vec4 e01 = edgeAt(p[1][2], p[1][3], p[1][4],  p[2][2], p[2][3], p[2][4],  p[3][2], p[3][3], p[3][4]);
    vec4 e10 = edgeAt(p[2][1], p[2][2], p[2][3],  p[3][1], p[3][2], p[3][3],  p[4][1], p[4][2], p[4][3]);
    vec4 e11 = edgeAt(p[2][2], p[2][3], p[2][4],  p[3][2], p[3][3], p[3][4],  p[4][2], p[4][3], p[4][4]);
    vec4 w = getInterpEdgeMap(e00, e01, e10, e11, fx, fy);

    float baseWeight = 1.0 - w.x - w.y - w.z - w.w;
    float opY = filterNormal(p, fxInt, fyInt) * baseWeight;
    opY += addDirFilters(p, fx, fy, fxInt, fyInt, w);

    vec4 op = texture(texSampler, fragTexCoord);
    float y = getY(op.rgb);
    float corr = opY - y;
    op.rgb += vec3(corr);

    outColor = vec4(clamp(op.rgb, 0.0, 1.0), 1.0);
}
