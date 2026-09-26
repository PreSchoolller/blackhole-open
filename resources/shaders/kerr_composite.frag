#version 450
// 克尔合成 pass —— NPGS Bloom(mip 树) + ColorBlend 调色链移植（kerr_bloom_colorblend_plan.md 方案 A）：
// 读本帧 TAA 历史（kerr.frag 在 frag 内已完成 tonemap + TAA 混合，LDR 域，与 NPGS bloom 作用域一致），
// 辉光取同一图像的 mip 链（Java 侧每帧 vkCmdBlitImage 逐级减半生成，lod o 即 NPGS 的 octave o = ÷2^o），
// 按 NPGS 权重求和叠加后走 ColorBlend 调色链（ColorBlend.frag.glsl main() 逐行照搬），写交换链。
// 强度=0 时输出 = 纯调色链（无辉光）。
// 与 NPGS 的已知采样差异（计划书 §3.1 接受项）：NPGS octave = box 过采样 + atlas 5-tap 高斯，
// 真实 mip 链 blit ≈ box 双线性，大 lod 本身高模糊，视觉差异可忽略；Bicubic 由 mip 线性过滤等效。

layout(push_constant) uniform PushConstants {
    float iBloomStrength;    // 辉光总强度乘子：0=无辉光；1.0 = NPGS 原始基准（×0.08）
} pc;

layout(location = 0) in vec2 inUV;
layout(location = 0) out vec4 outColor;

// 本帧刚写完的 TAA 历史（ping-pong 写入槽，含完整 mip 链）
layout(set = 0, binding = 0) uniform sampler2D uScene;

vec3 Saturate(vec3 x)
{
    return clamp(x, vec3(0.0), vec3(1.0));
}

vec3 GetBloom(vec2 TexCoord)
{
    // NPGS ColorBlend 八 octave 权重 {1,1.5,1,1.5,1.8,1,1,1}
    vec3 Bloom = textureLod(uScene, TexCoord, 1.0).rgb * 1.0;
    Bloom += textureLod(uScene, TexCoord, 2.0).rgb * 1.5;
    Bloom += textureLod(uScene, TexCoord, 3.0).rgb * 1.0;
    Bloom += textureLod(uScene, TexCoord, 4.0).rgb * 1.5;
    Bloom += textureLod(uScene, TexCoord, 5.0).rgb * 1.8;
    Bloom += textureLod(uScene, TexCoord, 6.0).rgb * 1.0;
    Bloom += textureLod(uScene, TexCoord, 7.0).rgb * 1.0;
    Bloom += textureLod(uScene, TexCoord, 8.0).rgb * 1.0;
    return Bloom;
}

void main()
{
    vec3 Color = texture(uScene, inUV).rgb;
    Color += GetBloom(inUV) * (pc.iBloomStrength * 0.08);

    // ---- Tonemapping and color grading（NPGS ColorBlend.frag.glsl main() 逐行照搬）----
    Color = pow(Color, vec3(1.5));
    Color = Color / (1.0 + Color);
    Color = pow(Color, vec3(1.0 / 1.5));

    Color = mix(Color, Color * Color * (3.0 - 2.0 * Color), vec3(1.0));
    Color = pow(Color, vec3(1.3, 1.20, 1.0));

    Color = Saturate(Color * 1.01);

    Color = pow(Color, vec3(0.7 / 2.2));

    outColor = vec4(Color, 1.0);
}
