#version 450

// ============================================================
// 1. BlackHoleArgs 参数 UBO（set 2, binding 0）— 单位约定
//    cameraPos / blackHolePos / schwarzschildRadius: 世界坐标长度
//    diskInner/OuterRadius: Rs 的倍数（shader 内乘 Rs 换算为世界单位）
//    temperature: 开尔文
//    布局 std140:三个 vec3 后各跟一个 float 占尾、int/float 链 4B 对齐——
//    与原 push constant 字段序逐字节一致,240B（232 对齐 16）
// ============================================================
layout(set = 2, binding = 0) uniform BlackHoleArgs {
    mat4 inverseView;               // 视图逆矩阵（frag 未使用，顶点反投影用）
    mat4 inverseProj;               // 投影逆矩阵（frag 未使用，顶点反投影用）
    vec3 cameraPos;                 // 相机世界坐标（用于引力红移）
    float time;                     // 当前时间（秒）
    vec3 blackHolePos;              // 黑洞位置（世界坐标）
    float schwarzschildRadius;      // 史瓦西半径 Rs（世界坐标长度单位）
    float diskInnerRadius;          // 盘内半径（Rs 的倍数，shader 内乘 Rs 换算）
    float diskOuterRadius;          // 盘外半径（Rs 的倍数，shader 内乘 Rs 换算）
    float iExposure;                // 曝光增益：autoExposure 后的全局亮度乘子（原硬编码 2.0,
                                    //  槽位复用原 rotationSpeed 死字段,Phase 3）
    float temperature;              // 基准温度（内盘温度，开尔文）
    int   if_dopplerI;              // 多普勒亮度开关：切断轨道多普勒增亮项（A/B 实验）
    int   if_dopplerT;              // 多普勒温度开关：切断轨道多普勒²温移项（A/B 实验）
    float timeRate;                 // 时间速率（用于动画速度）
    float iTimeDelta;               // 帧间隔（秒），TAA blendWeight 用
    int   iFrame;                   // 全局帧计数，TAA 前 2 帧强制重置
    int   iCameraMoved;             // 1=相机/投影变化，重置 TAA 累积
    float iRenderTime;              // 渲染时间（墙钟，时间暂停时仍流动；TAA 抖动种子）
    float iFade;                    // 视界坠落淡出系数 0..1（1=全黑），兼作对齐
    vec3  iCameraVel;               // 相机速度 β（单位 c，静态观者系；非测地模式为 0）
    float iCameraGamma;             // 相机洛伦兹因子 γ
    float iDiskScatter;             // 盘前向散射强度：被盘消光的背景光散射回视线的比例（0=关）
    float iDiskAmbient;             // 盘环境光强度：全天空辐照×盘密度并入发射的弥散项（0=关）
    float iShiftMax;                // 盘频移钳制上限（原硬编码 2.5）
    float iTaaTau;                  // TAA 静止累积时间常数 τ 基准秒（原硬编码 0.3）
    float iBloomThreshold;          // Bloom 亮部阈值（bloomComposite,原硬编码 1.0）
    float iBloomMix;                // Bloom 辉光混合系数（原硬编码 0.6）
    float iBloomMax;                // Bloom 色调映射输出上限（原硬编码 12.0）
    float iBackgroundBright;        // 背景亮度倍率（原硬编码 0.7;散射项随 Bg 同步缩放）
    float iToneMapStrength;         // 色调映射强度：1=全 ACES（原行为）,0=线性直出
    float iDiskHalfThickness;       // 盘半厚基准（Rs 倍数,默认 0.5=原 0.5·Rs 硬编码;垂直密度/厚度/尘埃层随动）
} pc;

// 相机速度多普勒因子 g = γ·(1-β·n̂)，n̂ = 光子传播方向（从光源指向观测者的单位向量）
// β=0 时恒为 1（非测地模式回归原行为）；朝光源运动（β·n̂<0）→ g>1 蓝移
float CameraDoppler(vec3 photonDir) {
    return pc.iCameraGamma * (1.0 - dot(pc.iCameraVel, photonDir));
}

layout(location = 0) in vec2 inUV;
layout(location = 1) in vec3 inRayOrigin;
layout(location = 2) in vec3 inRayDir;
layout(location = 0) out vec4 outColor;   // HDR 输出（预色调映射；bloom 合成 pass 消费）

// 背景星空 cubemap（set 0 = 管线描述符布局 0；NPGS Universe0Skybox）
layout(set = 0, binding = 0) uniform samplerCube uSkybox;
// TAA 上一帧累积结果（set 1 = 管线描述符布局 1，按帧插槽 ping-pong）
layout(set = 1, binding = 0) uniform sampler2D uPrevFrame;

#define PI 3.141592653589
#define MAX_STEPS 4096
// （原 GLOBAL_EXPOSURE 2.0 死定义已删：曝光增益现走 UBO 字段 iExposure,GUI 可调）

// 盘环境光弥散项的相位折扣：侧向入射光散射进视线的效率低于前向背光（相位函数前向
// 峰化），故乘此折扣，与背光项（背景合成处的 iDiskScatter）拼成完整的单次散射两块
#define AMBIENT_PHASE 0.25
// 每像素 main 开头算一次的全天空辐照（ambient-cube 6 向平均 × 相位折扣），DiskColor 内零开销复用
vec3 gDiskAmbientSky = vec3(0.0);

float softHold(float x) { return 1.0 - 1.0 / (max(x, 0.0) + 1.0); }

float Shape(float x, float a, float b) {
    float k = pow(a+b, a+b) / (pow(a,a) * pow(b,b));
    return k * pow(x, a) * pow(1.0-x, b);
}

// 史瓦西圆轨道坐标角速度（几何单位 c=1，M = Rs/2）：Ω = √(M/r³) = √(Rs/(2r³))。
// （原实现 √(Rs/r³) 比物理值快 √2，2026-08-30 对齐；内盘动画随之慢约 29%）
float omega(float r, float Rs) { return sqrt(Rs / (2.0 * r * r * r)); }

// 黑体颜色函数（带低温衰减）
vec3 RGB(float T) {
    if (T < 400.0) return vec3(0.0);
    float _ = (T - 6500.0) / (6500.0 * T * 2.2);
    float R = exp(2.05539304e4 * _);
    float G = exp(2.63463675e4 * _);
    float B = exp(3.30145739e4 * _);
    float LmulRate = 1.0 / max(max(R, G), B);
    if (T < 1000.0) LmulRate *= (T - 400.0) / 600.0;
    return vec3(R, G, B) * LmulRate;
}

// ---- 从早期版本移植的噪声函数 ----
float CubicInterpolate(float x) {
    return 3.0 * pow(x, 2) - 2.0 * pow(x, 3);
}

float perlin(vec3 Position) {
    vec3 PosInt   = floor(Position);
    vec3 PosFloat = fract(Position);
    float Sx = CubicInterpolate(PosFloat.x);
    float Sy = CubicInterpolate(PosFloat.y);
    float Sz = CubicInterpolate(PosFloat.z);
    float v000 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y,       PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v100 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y,       PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v010 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y + 1.0, PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v110 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y + 1.0, PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v001 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y,       PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v101 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y,       PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v011 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y + 1.0, PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v111 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y + 1.0, PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    return mix(mix(mix(v000, v100, Sx), mix(v010, v110, Sx), Sy),
               mix(mix(v001, v101, Sx), mix(v011, v111, Sx), Sy), Sz);
}

float GenerateDiskNoise(vec3 Position, int NoiseStartLevel, int NoiseEndLevel, float ContrastLevel) {
    float NoiseAccumulator = 10.0;
    for (int Level = NoiseStartLevel; Level < NoiseEndLevel; ++Level) {
        float NoiseFrequency = pow(3.0, float(Level));
        vec3 ScaledPosition = vec3(NoiseFrequency * Position.x,
                                   NoiseFrequency * Position.y,
                                   NoiseFrequency * Position.z);
        NoiseAccumulator *= (1.0 + 0.1 * perlin(ScaledPosition));
    }
    return log(1.0 + pow(0.1 * NoiseAccumulator, ContrastLevel));
}

float Vec2ToTheta(vec2 v1, vec2 v2) {
    float VecDot   = dot(v1, v2);
    float VecCross = v1.x * v2.y - v1.y * v2.x;
    float Angle    = asin(0.999999 * VecCross / (length(v1) * length(v2)));
    float Dx = step(0.0, VecDot);
    float Cx = step(0.0, VecCross);
    return mix(mix(-PI - Angle, PI - Angle, Cx), Angle, Dx);
}

// ---- 波长转 RGB（用于背景频移） ----
vec3 WavelengthToRgb(float wavelength) {
    vec3 color = vec3(0.0);
    if (wavelength < 380.0 || wavelength > 750.0) return color;
    if (wavelength < 440.0) {
        color.r = -(wavelength - 440.0) / (440.0 - 380.0);
        color.g = 0.0;
        color.b = 1.0;
    } else if (wavelength < 490.0) {
        color.r = 0.0;
        color.g = (wavelength - 440.0) / (490.0 - 440.0);
        color.b = 1.0;
    } else if (wavelength < 510.0) {
        color.r = 0.0;
        color.g = 1.0;
        color.b = -(wavelength - 510.0) / (510.0 - 490.0);
    } else if (wavelength < 580.0) {
        color.r = (wavelength - 510.0) / (580.0 - 510.0);
        color.g = 1.0;
        color.b = 0.0;
    } else if (wavelength < 645.0) {
        color.r = 1.0;
        color.g = -(wavelength - 645.0) / (645.0 - 580.0);
        color.b = 0.0;
    } else {
        color.r = 1.0;
        color.g = 0.0;
        color.b = 0.0;
    }
    float factor = 0.0;
    if (wavelength < 420.0) factor = 0.3 + 0.7 * (wavelength - 380.0) / (420.0 - 380.0);
    else if (wavelength < 645.0) factor = 1.0;
    else factor = 0.3 + 0.7 * (750.0 - wavelength) / (750.0 - 645.0);
    return color * factor / pow(color.r*color.r + 2.25*color.g*color.g + 0.36*color.b*color.b, 0.5) * (0.1*(color.r+color.g+color.b)+0.9);
}

float RandomStep(vec2 xy, float seed) {
    return fract(sin(dot(xy + fract(11.4514 * sin(seed)), vec2(12.9898, 78.233))) * 43758.5453);
}

// ============================================================
// 4. 吸积盘颜色（移植自早期版本；长度均为世界单位，厚度等以 Rs 表达）
// ============================================================
// ---- 单层盘采样（原 Color0/Color1 两段逐行重复的结构,仅时间相位不同） ----
// FadeOffset: 层 0 传 0.0,层 1 传 0.5（两层时间相位相差半周期,交替明灭）
vec4 DiskLayerColor(float ThetaWithoutTime, float AngularVelocity,
                    float EffectiveTime, float Phase, float HalfPiTimeInside,
                    float EffectiveRadius, float PosY, float PosR, float Rs,
                    vec3 DirOnDisk, float Doppler, float FadeOffset) {
    vec4 C = vec4(0.0);
    float PosTheta = fract((ThetaWithoutTime + AngularVelocity * EffectiveTime + Phase) / (2.0 * PI)) * 2.0 * PI;
    float DustBound = 1.0 - 5.0 * pow(2.0 * (1.0 - EffectiveRadius), 2.0);

    float Density = Shape(EffectiveRadius, 4.0, 0.9);
    if (abs(PosY) < pc.iDiskHalfThickness * Rs * Density)
    {
        float Thick = pc.iDiskHalfThickness * Rs * Density * (0.4 + 0.6 * softHold(GenerateDiskNoise(vec3(1.5 * PosTheta, PosR / Rs, 1.0), 1, 3, 80.0)));
        float VerticalMixFactor = max(0.0, (1.0 - abs(PosY) / Thick));
        Density *= 0.7 * VerticalMixFactor * Density;
        C = vec4(GenerateDiskNoise(vec3(1.0 * PosR / Rs, 1.0 * PosY / Rs, 0.5 * PosTheta), 3, 6, 80.0));
        C.xyz *= Density * 1.4 * (0.2 + 0.8 * VerticalMixFactor + (0.8 - 0.8 * VerticalMixFactor) *
                    GenerateDiskNoise(vec3(PosR / Rs, 1.5 * PosTheta, PosY / Rs), 1, 3, 80.0));
        C.a *= Density;
    }
    if (abs(PosY) < pc.iDiskHalfThickness * Rs * DustBound)
    {
        float DustColor = max(1.0 - pow(PosY / (pc.iDiskHalfThickness * Rs * max(DustBound, 0.0001)), 2.0), 0.0) *
                GenerateDiskNoise(vec3(1.5 * fract((1.5 * ThetaWithoutTime + PI / HalfPiTimeInside * EffectiveTime + Phase) / (2.0 * PI)) * 2.0 * PI, PosR / Rs, PosY / Rs), 0, 6, 80.0);
        C += 0.02 * vec4(vec3(DustColor), 0.2 * DustColor) * sqrt(1.0001 - DirOnDisk.y * DirOnDisk.y) * min(1.0, Doppler * Doppler);
    }
    C *= 0.5 - 0.5 * cos(2.0 * PI * fract(pc.timeRate * pc.time / HalfPiTimeInside + FadeOffset));
    return C;
}

vec4 DiskColor(vec4 BaseColor, float StepLength,
               vec3 RayPos, vec3 LastRayPos,
               vec3 RayDir, vec3 LastRayDir,
               vec3 BlackHolePos,
               float Rs,
               float RIn, float ROut,
               float ShiftMax)
{
    float TimeRate = pc.timeRate;
    // 换系到黑洞中心；噪声坐标以 Rs 归一，保证盘纹理随 Rs 缩放形态不变
    vec3 PosOnDisk = RayPos - BlackHolePos;
    float PosR = length(PosOnDisk.xz);
    float PosY = PosOnDisk.y;
    vec3 DirOnDisk = RayDir;

    vec4 Color = vec4(0.0);
    if (abs(PosY) < pc.iDiskHalfThickness * Rs && PosR < ROut && PosR > RIn)
    {
        float EffectiveRadius = 1.0 - ((PosR - RIn) / (ROut - RIn) * 0.5);
        if ((ROut - RIn) > 9.0 * Rs)
        {
            if (PosR < 5.0 * Rs + RIn)
                EffectiveRadius = 1.0 - ((PosR - RIn) / (9.0 * Rs) * 0.5);
            else
                EffectiveRadius = 1.0 - (0.5/0.9*0.5 + ((PosR-RIn)/(ROut-RIn) - 5.0*Rs/(ROut-RIn)) / (1.0 - 5.0*Rs/(ROut-RIn)) * 0.5);
        }

        if ((abs(PosY) < pc.iDiskHalfThickness * Rs * Shape(EffectiveRadius, 4.0, 0.9)) ||
            (PosY < pc.iDiskHalfThickness * Rs * (1.0 - 5.0 * pow(2.0 * (1.0 - EffectiveRadius), 2.0))))
        {
            float AngularVelocity  = omega(PosR, Rs);
            float HalfPiTimeInside = PI / omega(3.0 * Rs, Rs);
            float EffectiveTime0   = fract(TimeRate * pc.time / HalfPiTimeInside) * HalfPiTimeInside;
            float EffectiveTime1   = fract(TimeRate * pc.time / HalfPiTimeInside + 0.5) * HalfPiTimeInside;
            float PhaseTimeIndex0  = trunc(TimeRate * pc.time / HalfPiTimeInside);
            float PhaseTimeIndex1  = trunc(TimeRate * pc.time / HalfPiTimeInside + 0.5);
            float Phase0           = 2.0 * PI * fract(43758.5453 * sin(PhaseTimeIndex0));
            float Phase1           = 2.0 * PI * fract(43758.5453 * sin(PhaseTimeIndex1));

            float PosThetaWithoutTime = Vec2ToTheta(PosOnDisk.zx, vec2(1.0, 0.0));

            // 温度模型对齐文章：T = Tpeak · (RIn/r)^0.75 · max(1-√(RIn/r), 0)^0.25
            // 内盘边缘（趋近 ISCO）温度趋零——物质 plunge 区不再辐射，内缘变暗
            float DiskTemperature = pc.temperature * pow(RIn / PosR, 0.75)
                                    * pow(max(1.0 - sqrt(RIn / PosR), 0.0), 0.25);
            vec3 CloudVelocity    = AngularVelocity * cross(vec3(0.0, 1.0, 0.0), PosOnDisk);
            float RelativeVelocity = dot(-DirOnDisk, CloudVelocity);

            float Doppler   = sqrt((1.0 + RelativeVelocity) / max((1.0 - RelativeVelocity), 0.00001));
            float CamR = length(pc.cameraPos - BlackHolePos);
            float GravRed = sqrt(max(1.0 - Rs / PosR, 0.000001)) / sqrt(max(1.0 - Rs / max(CamR, 0.001), 0.000001));
            // 相机速度多普勒：光子自盘命中点飞向观测者，传播方向 = -DirOnDisk
            float CamG = CameraDoppler(-DirOnDisk);
            float RedShift = Doppler * GravRed * CamG;
            RedShift = min(RedShift, ShiftMax);

            // 两个时间相位的盘层（交替明灭,消除纹理拖尾）
            vec4 Color0 = DiskLayerColor(PosThetaWithoutTime, AngularVelocity, EffectiveTime0, Phase0,
                                         HalfPiTimeInside, EffectiveRadius, PosY, PosR, Rs,
                                         DirOnDisk, Doppler, 0.0);
            vec4 Color1 = DiskLayerColor(PosThetaWithoutTime, AngularVelocity, EffectiveTime1, Phase1,
                                         HalfPiTimeInside, EffectiveRadius, PosY, PosR, Rs,
                                         DirOnDisk, Doppler, 0.5);
            Color = Color1 + Color0;
            Color *= 1.0 + 20.0 * exp(-10.0 * (PosR - RIn) / (ROut - RIn));

            float QuadraticedPeakTemperature = pow(pc.temperature, 4);  // 峰值温度四次方

            float BrightWithoutRedShift = 4.5 * pow(DiskTemperature, 4) / QuadraticedPeakTemperature;
            // if_dopplerT：轨道多普勒²温移项 A/B 开关（关=温移只剩 RedShift 链,渐近侧不再偏冷）
            if (DiskTemperature > 1000.0)
                DiskTemperature = max(1000.0, DiskTemperature * RedShift *
                                       mix(1.0, Doppler * Doppler, float(pc.if_dopplerT)));

            DiskTemperature = min(100000.0, DiskTemperature);

            Color.xyz *= BrightWithoutRedShift * min(1.0, 1.8 * (ROut - PosR) / (ROut - RIn)) *
                         RGB(DiskTemperature / exp((PosR - RIn) / (0.6 * (ROut - RIn))));
            // if_dopplerI：轨道多普勒相对论束流增亮 A/B 开关（关=趋近侧不再偏亮;
            //  RedShift 链仍含多普勒色移,故不对称不会完全消失）
            Color.xyz *= min(ShiftMax, RedShift) * mix(1.0, min(ShiftMax, Doppler), float(pc.if_dopplerI))
                       * clamp(CamG * CamG * CamG, 0.1, 10.0);
            RedShift = min(RedShift, ShiftMax);
            Color.xyz *= pow((1.0 - (1.0 - min(1.0, RedShift)) * (PosR - RIn) / (ROut - RIn)), 9.0);
            Color.xyz *= min(1.0, 1.0 + 0.5 * ((PosR - RIn) / RIn + RIn / (PosR - RIn)) - max(1.0, RedShift));
            // 环境光弥散项：全天空辐照（gDiskAmbientSky 已含相位折扣）× 本步不透明度随
            // StepLength 积分。冷暗盘区发射趋零而密度仍在 → 显形为均匀背景色补底，与背光项
            // （背景合成处的 iDiskScatter）互补；表现项，不做频移
            Color.xyz += pc.iDiskAmbient * gDiskAmbientSky * Color.a;

            Color *= StepLength / Rs; // 步长以 Rs 归一（与文章 steplength/Rs 一致）
        }
    }
    return BaseColor + Color * (1.0 - BaseColor.a);
}

// ============================================================
// 5. 主函数与阶段函数
// ============================================================

// ---- 远处包围盒跳空：相机在 499 半径包围球外时,把光线起点推进到球面
//      （场景尺度常量,假定盘外缘远小于 500 世界单位）
//      DirectBg=true 表示射线与球不相交/球在身后 → 直接采背景
vec3 AdvanceToBoundingSphere(vec3 Origin, vec3 Dir, vec3 Center, out bool March, out bool DirectBg) {
    March = true;
    DirectBg = false;
    vec3 OC = Origin - Center;
    float r = 499.0;
    float a = dot(Dir, Dir);
    float b = 2.0 * dot(Dir, OC);
    float c = dot(OC, OC) - r*r;
    float delta = b*b - 4.0*a*c;
    if (delta < 0.0) {
        March = false;
        DirectBg = true;
        return Origin + Dir * 499.0;   // 直达背景：世界坐标取沿视线远点（供 TAA 参考起点）
    }
    float sqrtDelta = sqrt(delta);
    float t1 = (-b - sqrtDelta) / (2.0*a);
    float t2 = (-b + sqrtDelta) / (2.0*a);
    if (max(t1, t2) < 0.0) {
        March = false;
        DirectBg = true;
        return Origin + Dir * 499.0;   // 同上（包围球在相机身后）
    }
    return Origin + Dir * max(min(t1, t2), 0.0);
}

const float K_DENOM = 0.87137;   // = (COEF_R − COEF_B) / 14300 的相反数

float RecoverTemperature(vec3 c) {
    // 对暗像素/纯色不稳定的保护
    if (c.r < 1e-4 || c.b < 1e-4) return 6500.0;
    float denom = 1.0 + log(c.r / c.b) / K_DENOM;
    denom = clamp(denom, 0.05, 5.0);      // 大约 1300K ~ 20000K
    return 6500.0 / denom;
}

vec3 BlackbodyDoppler(vec3 skyColor, float Shift) {
    float T_src = RecoverTemperature(skyColor);
    float T_obs = clamp(T_src * Shift, 500.0, 30000.0);

    vec3 C_src = max(RGB(T_src), vec3(1e-3));
    vec3 C_obs = RGB(T_obs);

    // 色度用比例替换，强度按 Shift^4 放大
    return skyColor * (C_obs / C_src) * pow(Shift, 4.0);
}

vec4 BackgroundColor(vec3 Dir, float BlueShift) {
    vec3 skyColor = texture(uSkybox, Dir).rgb;
    float CamG = CameraDoppler(-Dir);
    float Shift = min(BlueShift * CamG, 8.0);

    vec3 result = BlackbodyDoppler(skyColor, Shift);
    // 背景亮度倍率（GUI 可调,原硬编码 0.7）;前向散射项经 Bg 同步缩放——
    // 背景调暗则剪影泛光同步变暗,黑洞阴影不受影响
    return pc.iBackgroundBright * vec4(result, 1.0);
}

// ---- TAA 时域累积（色调映射前,HDR 域混合） ----
// 两态：仅相机完全静止（iCameraMoved==0）时同位全量累积；运动中硬重置直出当前帧。
// （世界坐标/方向重投影均已实测放弃：透镜化 raymarch 的射线终点对初始条件混沌敏感，
//  不存在稳定的"像素内容位置"，重投影键不可靠——运动噪声可接受，变形游动不可接受）
vec4 ApplyTAA(vec4 Current) {
    if (pc.iFrame >= 2 && pc.iCameraMoved == 0) {
        // 历史时间常数 τ：越大降噪越强、拖影越长；动画加速时按 timeRate 缩短
        // （基准 τ 来自 UBO iTaaTau,GUI 可调;原硬编码 0.3）
        float Tau = clamp(pc.iTaaTau / max(pc.timeRate, 0.1), 0.02, pc.iTaaTau);
        float BlendWeight = 1.0 - pow(0.5, clamp(pc.iTimeDelta, 0.0001, 0.1) / Tau);
        vec4 PrevColor = texelFetch(uPrevFrame, ivec2(gl_FragCoord.xy), 0);
        // 防御：历史中的异常值（如未初始化内存的大数）不进入累积
        PrevColor = clamp(PrevColor, vec4(0.0), vec4(1.0e4));
        return mix(PrevColor, Current, BlendWeight);
    }
    return Current;
}

// ACES 电影级色调映射，能把 HDR 平滑压进 0-1
vec3 ACESFilm(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec4  Result = vec4(0.0);
    vec2  uv = inUV;
    // Rs 为世界坐标长度，直接参与下方所有公式（不再是隐含的 1.0）
    float Rs = max(pc.schwarzschildRadius, 1e-4);

    vec3 BHPos = pc.blackHolePos;
    vec3 RayPos = inRayOrigin;          // 世界坐标（长度单位与 Rs 同一量纲）
    vec3 RayDir = normalize(inRayDir);

    // 盘半径以 Rs 的倍数传入，换算为世界单位（Rs=1 时与旧值一致）
    float RIn = pc.diskInnerRadius * Rs;
    float ROut = pc.diskOuterRadius * Rs;

    // ---- 光线步进 ----
    vec3 PosToBH = RayPos - BHPos;
    float Dis = length(PosToBH);
    int count = 0;
    bool bShouldContinueMarchRay = true;
    bool bWaitCalBack = false;
    float ShiftMax = pc.iShiftMax;   // 红/蓝移上限:留出相机多普勒动态范围(原 1.5 会把趋近侧钳死)
    float CamR = length(pc.cameraPos - BHPos);
    // 背景引力蓝移：√(1-Rs/CamR) 的倒数（相机越近蓝移越强）
    float BackgroundBlueShift = min(1.0 / sqrt(1.0 - Rs / max(CamR, 1.001 * Rs) + 0.005), 2.0);

    if (CamR > 200.0) {
        bool march, directBg;
        RayPos = AdvanceToBoundingSphere(RayPos, RayDir, BHPos, march, directBg);
        bShouldContinueMarchRay = march;
        bWaitCalBack = directBg;
    }

    float SmallStepBoundary = max(ROut, 12.0);
    vec3 LastRayPos = RayPos;
    vec3 LastRayDir = RayDir;
    float StepLength = 0.0;

    // 盘环境光弥散项的全天空辐照：每像素一次 6 向 cubemap 采样，DiskColor 内零开销复用
    if (pc.iDiskAmbient > 0.0) {
        gDiskAmbientSky = (AMBIENT_PHASE / 6.0) * (
            textureLod(uSkybox, vec3( 1.0, 0.0, 0.0), 0.0).rgb + textureLod(uSkybox, vec3(-1.0, 0.0, 0.0), 0.0).rgb +
            textureLod(uSkybox, vec3(0.0,  1.0, 0.0), 0.0).rgb + textureLod(uSkybox, vec3(0.0, -1.0, 0.0), 0.0).rgb +
            textureLod(uSkybox, vec3(0.0, 0.0,  1.0), 0.0).rgb + textureLod(uSkybox, vec3(0.0, 0.0, -1.0), 0.0).rgb);
    }

    while (bShouldContinueMarchRay && count < MAX_STEPS) {
        PosToBH = RayPos - BHPos;
        Dis = length(PosToBH);
        vec3 NPosToBH = PosToBH / Dis;

        // 逃逸判断
        if (Dis > 500.0 || bWaitCalBack) {
            bShouldContinueMarchRay = false;
            bWaitCalBack = true;
            break;
        }
        if (Dis < 0.01 * Rs) {
            bShouldContinueMarchRay = false;
            Result = vec4(0.0, 0.0, 0.0, 1.0);
            break;
        }

        // 吸积盘采样
        Result = DiskColor(Result, StepLength, RayPos, LastRayPos, RayDir, LastRayDir,
                           BHPos, Rs, RIn, ROut, ShiftMax);

        if (Result.a > 0.99) break;

        LastRayPos = RayPos;
        LastRayDir = RayDir;

        // 步长
        float CosTheta = length(cross(NPosToBH, RayDir));
        // 引力偏折率：dφ/dl = -cos³θ · 1.5·Rs/r²（Rs 显式参与；CubicInterpolate 为远场阻尼调优项）
        float DeltaPhiRate = -1.0 * pow(CosTheta, 3) * (1.5 * Rs / Dis) * CubicInterpolate(max(min(1.0 - (0.01*Dis - 1.0)/4.0, 1.0), 0.0));
        // 第一步随机抖动的种子：硬重置帧用固定种子；其余帧随渲染时间变化（时间暂停时仍流动，
        // 保证 TAA 定格累积的抖动多样性），由 TAA（全量或部分混合）时域平均
        float JitterSeed = (pc.iCameraMoved == 1) ? 0.5 : fract(pc.iRenderTime * 1.0);
        float RayStep;
        if (count == 0) RayStep = RandomStep(uv, JitterSeed);
        else RayStep = 1.0;
        RayStep *= 0.15 + 0.25 * min(max(0.0, 0.5 * (0.5 * Dis / SmallStepBoundary - 1.0)), 1.0);

        if (Dis >= 2.0 * SmallStepBoundary)
            RayStep *= Dis;
        else if (Dis >= SmallStepBoundary)
            RayStep *= ((1.0) * (2.0 * SmallStepBoundary - Dis) + Dis * (Dis - SmallStepBoundary)) / SmallStepBoundary;
        else
            RayStep *= min(1.0, Dis);

        float DeltaPhi = RayStep / Dis * DeltaPhiRate;
        // 辛格式（文章 2025.12.19 修正）：先用当前位置的曲率更新方向，再移动位置
        RayPos += RayDir * RayStep;
        RayDir = normalize(RayDir + (DeltaPhi + DeltaPhi * DeltaPhi * DeltaPhi / 3.0) *
                     cross(cross(RayDir, NPosToBH), RayDir) / CosTheta);
        StepLength = RayStep;
        count++;
    }

    // ---- 背景采样（若逃逸） ----
    if (bWaitCalBack) {
        vec4 Bg = BackgroundColor(normalize(RayDir), BackgroundBlueShift);
        // 前向散射：被盘消光的那部分背景光（≈Bg·Result.a）按 iDiskScatter 比例单次散射回视线
        // （albedo ≤1 时能量守恒：透射 (1-a) + 散射 σ·a ≤ 入射）。仅在逃逸路径执行，视界捕获
        // （bWaitCalBack=false）不经过此处，阴影保持纯黑；亮背景下冷暗盘区自动泛背景微光
        Result.rgb += Bg.rgb * pc.iDiskScatter * Result.a;
        Result += Bg * (1.0 - Result.a);
    }

    // 1. 物理 HDR 值此时可能高达 50+
    // 2. 自动曝光补偿（防瞎眼）
    float autoExposure = 1.0 / (1.0 + Result.r + Result.g + Result.b); // 或者用 shift 反比
    Result.rgb *= autoExposure * pc.iExposure; // 控制整体亮度（GUI 可调,原硬编码 2.0）

    // 3. 色调映射（把 HDR 压缩进 LDR；强度可调——1=全 ACES 原行为,0=线性直出观察高光硬钳）
    Result.rgb = mix(Result.rgb, ACESFilm(Result.rgb), pc.iToneMapStrength);

    // ---- TAA 时域累积 → 输出 HDR（预色调映射;Bloom 合成 pass 负责色调映射 + bloom + 淡出） ----
    outColor = ApplyTAA(Result);
}