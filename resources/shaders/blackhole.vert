#version 450

/**
 * 黑洞模拟 —— 顶点着色器
 *
 * 功能：生成全屏三角形并将屏幕坐标反投影为世界空间射线。
 *
 * 反投影流程（Clip → View → World）：
 *   1. 屏幕坐标 → 裁剪空间（直接使用）
 *   2. 裁剪空间 → 视图空间（乘以逆投影矩阵）
 *   3. 视图空间 → 世界空间（乘以逆视图矩阵）
 *   4. 归一化得到世界空间射线方向
 */

// BlackHoleArgs 参数 UBO（set 2, binding 0，VERTEX|FRAGMENT 两阶段可见）：
// 顶点只消费 inverseView/inverseProj/cameraPos;块布局必须与 blackhole.frag 的
// 声明逐字段一致（std140,232B 数据对齐 16 → 240B）,vert 未访问尾部字段属正常
layout(set = 2, binding = 0) uniform BlackHoleArgs {
    mat4 inverseView;           // 视图矩阵的逆矩阵（世界 → 视图的逆操作）
    mat4 inverseProj;           // 投影矩阵的逆矩阵（裁剪 → 视图的逆操作）
    vec3 cameraPos;             // 相机世界坐标位置
    float time;                 // 自启动以来的时间（秒）
    vec3 blackHolePos;          // 黑洞世界坐标位置
    float schwarzschildRadius;  // 史瓦西半径 Rs（世界坐标长度单位）
    float diskInnerRadius;      // 吸积盘内半径（Rs 的倍数）
    float diskOuterRadius;      // 吸积盘外半径（Rs 的倍数）
    float iExposure;            // 曝光增益（vert 未使用）
    float temperature;          // 基础温度 (K)，默认 5000
    int   if_dopplerI;          // 多普勒亮度调制开关
    int   if_dopplerT;          // 多普勒温度偏移开关
    float timeRate;                 // 时间速率（用于动画速度）
    float iTimeDelta;               // 帧间隔（秒），TAA blendWeight 用
    int   iFrame;                   // 全局帧计数，TAA 前 2 帧强制重置
    int   iCameraMoved;             // TAA 三态：0=静止全量累积 2=平滑运动部分混合 1=硬重置
    float iRenderTime;              // 渲染时间（墙钟，TAA 抖动种子；vert 未使用）
    float iFade;                    // 视界坠落淡出系数 0..1（1=全黑），兼作对齐
    vec3  iCameraVel;               // 相机速度 β（单位 c，静态观者系；非测地模式为 0）
    float iCameraGamma;             // 相机洛伦兹因子 γ
    float iDiskScatter;             // 盘前向散射强度（0=关；vert 未使用）
    float iDiskAmbient;             // 盘环境光强度（0=关；vert 未使用）
    float iShiftMax;                // 盘频移钳制上限（vert 未使用）
    float iTaaTau;                  // TAA 静止累积 τ 基准秒（vert 未使用）
    float iBloomThreshold;          // Bloom 亮部阈值（vert 未使用）
    float iBloomMix;                // Bloom 辉光混合系数（vert 未使用）
    float iBloomMax;                // Bloom 色调映射输出上限（vert 未使用）
    float iBackgroundBright;        // 背景亮度倍率（vert 未使用）
    float iToneMapStrength;         // 色调映射强度（vert 未使用）
    float iDiskHalfThickness;       // 盘半厚基准（vert 未使用）
} pc;

// 输出到片段着色器的数据
layout(location = 0) out vec2 outUV;         // 屏幕 UV 坐标 [0,1]
layout(location = 1) out vec3 outRayOrigin;   // 射线起点（相机位置）
layout(location = 2) out vec3 outRayDir;       // 射线方向（世界空间，归一化）

void main() {
    // 全屏四边形的 6 个顶点（两个三角形，使用 gl_TRIANGLES）
    vec2 positions[6] = vec2[](
        vec2(-1.0, -1.0),  // vertex 0: 左下 (triangle 1)
        vec2( 1.0, -1.0),  // vertex 1: 右下
        vec2( 1.0,  1.0),  // vertex 2: 右上
        vec2(-1.0,  1.0),  // vertex 3: 左上 (triangle 2)
        vec2(-1.0, -1.0),  // vertex 4: 左下（重复）
        vec2( 1.0,  1.0)   // vertex 5: 右上（重复）
    );

    vec2 pos = positions[gl_VertexIndex];
    // 裁剪空间坐标：Z=0, W=1（正交投影的近平面）
    gl_Position = vec4(pos, 0.0, 1.0);

    // 将 [-1,1] 的 NDC 坐标转换为 [0,1] 的 UV 坐标
    outUV = pos * 0.5 + 0.5;

    // === 反投影：裁剪空间 → 世界空间 ===

    // 步骤1：裁剪空间坐标（Z=0 表示在近平面上）
    vec4 clipPos = vec4(pos, 0.0, 1.0);

    // 步骤2：乘以逆投影矩阵，得到视图空间坐标
    // 透视除法：除以 w 分量，得到透视正确的 3D 坐标
    vec4 viewPos = pc.inverseProj * clipPos;
    viewPos.xyz /= viewPos.w;

    // 步骤3：乘以逆视图矩阵，得到世界空间方向
    // w=0 表示这是一个方向向量（不受平移影响）
    vec4 worldDir = pc.inverseView * vec4(viewPos.xyz, 0.0);

    // 步骤4：归一化射线方向
    outRayDir = normalize(worldDir.xyz);

    // 射线起点就是相机位置
    outRayOrigin = pc.cameraPos;
}
