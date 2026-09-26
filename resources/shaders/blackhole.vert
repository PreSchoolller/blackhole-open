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

// Push Constants：从 CPU 传递的快速常量数据（204 字节）
// 单位约定：cameraPos/blackHolePos/schwarzschildRadius 为世界坐标长度；
//           diskInner/OuterRadius 为 Rs 的倍数（frag 内乘 Rs 换算）
layout(push_constant) uniform PushConstants {
    mat4 inverseView;           // 视图矩阵的逆矩阵（世界 → 视图的逆操作）
    mat4 inverseProj;           // 投影矩阵的逆矩阵（裁剪 → 视图的逆操作）
    vec3 cameraPos;             // 相机世界坐标位置
    float time;                 // 自启动以来的时间（秒）
    vec3 blackHolePos;          // 黑洞世界坐标位置
    float schwarzschildRadius;  // 史瓦西半径 Rs（世界坐标长度单位）
    float diskInnerRadius;      // 吸积盘内半径（Rs 的倍数）
    float diskOuterRadius;      // 吸积盘外半径（Rs 的倍数）
    float rotationSpeed;        // 吸积盘旋转速度
    float temperature;          // 基础温度 (K)，默认 5000
    int   if_dopplerI;          // 多普勒亮度调制开关
    int   if_dopplerT;          // 多普勒温度偏移开关
    float timeRate;                 // 时间速率（用于动画速度）
    float iTimeDelta;               // 帧间隔（秒），TAA blendWeight 用
    int   iFrame;                   // 全局帧计数，TAA 前 2 帧强制重置
    int   iCameraMoved;             // TAA 三态：0=静止全量累积 2=平滑运动部分混合 1=硬重置
    float place_holder4;            // 对齐 pad
    float iFade;                    // 视界坠落淡出系数 0..1（1=全黑），兼作对齐
    vec3  iCameraVel;               // 相机速度 β（单位 c，静态观者系；非测地模式为 0）
    float iCameraGamma;             // 相机洛伦兹因子 γ
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
