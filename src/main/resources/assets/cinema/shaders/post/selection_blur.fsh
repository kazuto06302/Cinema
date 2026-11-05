#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// ぼかす範囲(ピクセル)
const int RADIUS = 2;

void main(){
    vec2 texel = 1.0 / InSize;
    vec3 colorSum = vec3(0.0);
    float colorWeight = 0.0;
    float alphaSum = 0.0;
    int count = 0;

    for (int x = -RADIUS; x <= RADIUS; x++) {
        for (int y = -RADIUS; y <= RADIUS; y++) {
            vec4 s = texture(InSampler, texCoord + vec2(x, y) * texel);
            alphaSum += s.a;
            colorSum += s.rgb * s.a;
            colorWeight += s.a;
            count++;
        }
    }

    // 色は、輪郭のある画素の色の平均(=元の色のまま)。ぼけるのはアルファだけ
    vec3 rgb = colorWeight > 0.0001 ? colorSum / colorWeight : vec3(0.0);
    fragColor = vec4(rgb, alphaSum / float(count));
}