#version 450

// Bloom 合成 pass —— 读取主 pass 输出的 HDR 历史（TAA 混合后、预色调映射），
// 亮部提取 + 黄金角圆盘模糊（24 tap，绕开 RGBA32F 不可 blit 的 mip 方案），
// 色调映射公式自主 frag 迁移，最后应用视界坠落淡出，写入交换链。

// BlackHoleArgs 参数 UBO（bloom 管线布局 set 1, binding 0；主管线为 set 2——
// 同一 DescSet 绑到不同管线布局的不同 set 号）。块布局与 blackhole.frag 逐字段
// 一致（std140）——三个 shader 的成员清单由 src/test/java/UboBlockCheck.java 守卫,
// 改任何一处必须三处同步,否则字段错位读垃圾（Phase 3 曾因此全黑）。
// 本 pass 消费 iFade 与 iBloom 三参数,未访问其余字段属正常
layout(set = 1, binding = 0) uniform BlackHoleArgs {
    mat4 inverseView;               // （未使用）
    mat4 inverseProj;               // （未使用）
    vec3 cameraPos;                 // （未使用）
    float time;
    vec3 blackHolePos;
    float schwarzschildRadius;
    float diskInnerRadius;
    float diskOuterRadius;
    float iExposure;                // 曝光增益（本 pass 未使用；槽位复用原 rotationSpeed）
    float temperature;
    int   if_dopplerI;
    int   if_dopplerT;
    float timeRate;
    float iTimeDelta;
    int   iFrame;
    int   iCameraMoved;
    float iRenderTime;
    float iFade;                    // 视界坠落淡出系数 0..1
    vec3  iCameraVel;
    float iCameraGamma;
    float iDiskScatter;
    float iDiskAmbient;
    float iShiftMax;                // （本 pass 未使用）
    float iTaaTau;                  // （本 pass 未使用）
    float iBloomThreshold;          // Bloom 亮部阈值（原硬编码 1.0）
    float iBloomMix;                // Bloom 辉光混合系数（原硬编码 0.6）
    float iBloomMax;                // Bloom 色调映射输出上限（原硬编码 12.0）
    float iBackgroundBright;        // 背景亮度倍率（本 pass 未使用）
    float iToneMapStrength;         // 色调映射强度（本 pass 未使用）
    float iDiskHalfThickness;       // 盘半厚基准（本 pass 未使用）
} pc;

layout(location = 0) in vec2 inUV;
layout(location = 0) out vec4 outColor;

// HDR 历史（含 mip 无关的全分辨率 TAA 结果）
layout(set = 0, binding = 0) uniform sampler2D uSceneHDR;

void main() {
    vec2 res = vec2(textureSize(uSceneHDR, 0));
    vec2 uv = gl_FragCoord.xy / res;

    // 当前帧 HDR 场景（线性采样，1:1 像素对应）
    vec3 hdr = texture(uSceneHDR, uv).rgb;

    // ---- Bloom：亮部提取（阈值 UBO iBloomThreshold 可调）+ 黄金角圆盘模糊 ----
    vec3 bloom = vec3(0.0);
    const int TAPS = 24;
    const float RADIUS = 24.0;
    for (int i = 0; i < TAPS; i++) {
        float t = (float(i) + 0.5) / float(TAPS);
        float ang = t * 81.0;                       // ~12.9 圈黄金角散布，避免环状伪影
        vec2 off = vec2(cos(ang), sin(ang)) * (RADIUS * sqrt(t));
        vec3 c = texture(uSceneHDR, uv + off / res).rgb;
        bloom += max(c - pc.iBloomThreshold, vec3(0.0));
    }
    bloom /= float(TAPS);

    // ---- 色调映射（公式自主 frag 原样迁移，作用于 hdr + bloom；混合系数/输出上限 UBO 可调） ----
    vec3 Result = hdr + pc.iBloomMix * bloom;
    float Sum = Result.r + Result.g + Result.b + 1e-10;
    float RedFactor   = 3.0 * Result.r / Sum;
    float GreenFactor = 3.0 * Result.g / Sum;
    float BlueFactor  = 3.0 * Result.b / Sum;
    float BloomMax = pc.iBloomMax;
    Result.r = min(-4.0 * log(1.0 - pow(Result.r, 2.2)), BloomMax * RedFactor);
    Result.g = min(-4.0 * log(1.0 - pow(Result.g, 2.2)), BloomMax * GreenFactor);
    Result.b = min(-4.0 * log(1.0 - pow(Result.b, 2.2)), BloomMax * BlueFactor);

    outColor = vec4(Result, 1.0);

    // 视界坠落演出：淡出仅作用于最终显示
    outColor *= (1.0 - pc.iFade);
}
