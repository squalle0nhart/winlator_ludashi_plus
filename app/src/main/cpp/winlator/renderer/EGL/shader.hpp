#pragma once

#include <string>
#include <fstream>
#include <unordered_map>
#include <GLES2/gl2.h>

class Shader {
    protected:
        std::unordered_map<std::string, int> uniformLocations;
        std::unordered_map<std::string, int> attributeLocations;
        int programId;
        GLuint bufferId;
        float quads[8] = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
        };

    public:
        Shader(const char *vertexShader, const char *fragmentShader);
        virtual ~Shader();
        void use();
        void disable();
        int getUniformLoc(const std::string& name);
        int getAttributeLoc(const std::string& name);
};

class DrawableShader : public Shader {
    private:
        static constexpr const char *drawable_vert = R"GLSL(
            precision highp float;
            uniform float xform[6];
            uniform vec2 viewSize;
            attribute vec2 position;
            varying vec2 vUV;

            vec2 applyXForm(vec2 p, float xform[6]) {
                return vec2(xform[0] * p.x + xform[2] * p.y + xform[4], xform[1] * p.x + xform[3] * p.y + xform[5]);
            }

            void main() {
                vUV = position;
                vec2 transformedPos = applyXForm(position, xform);
                gl_Position = vec4(2.0 * transformedPos.x / viewSize.x - 1.0, 1.0 - 2.0 * transformedPos.y / viewSize.y, 0.0, 1.0);
            }
        )GLSL";

        static constexpr const char *drawable_frag = R"GLSL(
            precision mediump float;

            uniform sampler2D texture;
            varying vec2 vUV;
            uniform int is_cursor;
            uniform int swap_colors;
            uniform int filter_mode;
            uniform vec2 texture_size;
            uniform float sharpness;

            vec3 sgsr(vec2 uv, vec3 center, bool edgeDirected) {
                vec2 px = 1.0 / max(texture_size, vec2(1.0));
                vec3 n = texture2D(texture, uv - vec2(0.0, px.y)).rgb;
                vec3 s = texture2D(texture, uv + vec2(0.0, px.y)).rgb;
                vec3 w = texture2D(texture, uv - vec2(px.x, 0.0)).rgb;
                vec3 e = texture2D(texture, uv + vec2(px.x, 0.0)).rgb;
                float edge = abs(e.g - w.g) + abs(s.g - n.g);
                if (edge <= 8.0 / 255.0) return center;

                vec3 blur;
                if (edgeDirected) {
                    vec2 normal = normalize(vec2(e.g - w.g, s.g - n.g) + vec2(0.00001));
                    vec2 tangent = vec2(-normal.y, normal.x) * px;
                    blur = (texture2D(texture, uv - tangent).rgb
                          + texture2D(texture, uv + tangent).rgb) * 0.375
                         + (texture2D(texture, uv - tangent * 2.0).rgb
                          + texture2D(texture, uv + tangent * 2.0).rgb) * 0.125;
                } else {
                    blur = (n + s + w + e) * 0.25;
                }
                return clamp(center + (center - blur) * mix(0.25, 0.65, sharpness), 0.0, 1.0);
            }

            void main() {
                vec4 color = vec4(0, 0, 0, 0);
                    
                if (is_cursor == 0) {
                    vec3 rgb = texture2D(texture, vUV).rgb;
                    if (filter_mode == 2 || filter_mode == 5)
                        rgb = sgsr(vUV, rgb, filter_mode == 5);
                    color = vec4(rgb, 1.0);
                } else
                    color = texture2D(texture, vUV);
                
                if (swap_colors == 1) color.rgba = color.bgra;
                gl_FragColor = color;
            }
        )GLSL";

    public:
        DrawableShader();
};
