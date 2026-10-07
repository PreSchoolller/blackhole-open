#version 450
#ifndef COORDCONVERTER_GLSL_
#define COORDCONVERTER_GLSL_

vec3 FragUvToDir(vec2 FragUv, float Fov, vec2 NdcResolution)
{
    return normalize(vec3(Fov * (2.0 * FragUv.x - 1.0),
                          Fov * (2.0 * FragUv.y - 1.0) * NdcResolution.y / NdcResolution.x,
                          -1.0));
}

vec2 PosToNdc(vec4 Pos, vec2 NdcResolution)
{
    return vec2(-Pos.x / Pos.z, -Pos.y / Pos.z * NdcResolution.x / NdcResolution.y);
}

vec2 DirToNdc(vec3 Dir, vec2 NdcResolution)
{
    return vec2(-Dir.x / Dir.z, -Dir.y / Dir.z * NdcResolution.x / NdcResolution.y);
}

vec2 DirToFragUv(vec3 Dir, vec2 NdcResolution)
{
    return vec2(0.5 - 0.5 * Dir.x / Dir.z, 0.5 - 0.5 * Dir.y / Dir.z * NdcResolution.x / NdcResolution.y);
}

vec2 PosToFragUv(vec4 Pos, vec2 NdcResolution)
{
    return vec2(0.5 - 0.5 * Pos.x / Pos.z, 0.5 - 0.5 * Pos.y / Pos.z * NdcResolution.x / NdcResolution.y);
}

#endif // !COORDCONVERTER_GLSL_
#ifndef NUMERICCONSTANTS_GLSL_
#define NUMERICCONSTANTS_GLSL_

const float kPi          = 3.1415926535897932384626433832795;
const float k2Pi         = 6.283185307179586476925286766559;
const float kEuler       = 2.7182818284590452353602874713527;
const float kRadToDegree = 57.295779513082320876798154814105;
const float kDegreeToRad = 0.017453292519943295769236907684886;

const float kGravityConstant = 6.6743e-11;
const float kSpeedOfLight    = 299792458.0;
const float kSolarMass       = 1.9884e30;

#endif // !NUMERICCONSTANTS_GLSL_





// =============================================================================
// SECTION 1:  Uniform 定义
// =============================================================================


layout(set = 0, binding = 0) uniform GameArgs
{
    vec2  iResolution;
    float iFovRadians;
    float iTime;
    float iGameTime;
    float iTimeDelta;
    float iTimeRate;
};

layout(set = 0, binding = 1) uniform BlackHoleArgs
{
    mat4x4 iInverseCamRot;               
    vec4  iBlackHoleRelativePosRs;       //黑洞在相机系下位置。单位倍Rs。三维观者模式下直接传入相机三维坐标，着色器会自动将传入的三维信息解析为in/out的空间部分；四维情况下需要传入iCamDataCoordisOutgoing对应的系下的空间坐标。
    vec4  iBlackHoleRelativeDiskNormal;  //黑洞在相机系下吸积盘法向兼自旋正方向。单位倍Rs
    vec4  iBlackHoleRelativeDiskTangen;  //黑洞在相机系下吸积盘切向。单位倍Rs

    vec4  iCameraVelocity;               //相机坐标速度（仅有xyz非空。这里使用vec4因为我不知道这个数据怎么对齐的。）

    vec4  ie1_up;                        //四维相机数据（在mode=-1被使用
    vec4  ie2_up;                        //四维相机数据（在mode=-1被使用
    vec4  ie3_up;                        //四维相机数据（在mode=-1被使用
    vec4  iU_up ;                        //四维相机数据（在mode=-1被使用

    int   iCamDataCoordisOutgoing;       //1代表上述相机和标架信息在outgoing系，0代表ingoing
    int   iDEBUG;
    int   iPrepass;                      //使用低分辨率插值
    int   iWhitehole;                    //最大延拓  
    int   iInWhichUniverse;              //当前最大延拓宇宙编号.分界是II区上边界，即应该在向内进入内视界切换
    int   iGrid;                         //绘制网格
    int   iEnableHeatHaze;               //热折射
    int   iEnableShadowCulling;          //剔除
    int   iObserverMode;                 //观者模式，0静态，1落体，2/3正向/反向使用外传相机三维平直坐标速度，自动解析为ingoing分量或outgoing分量；-1使用外部传入的四维相机数据而非三维的。
    int   iPolarization;                 //输出偏振
    int   iUseImageDisk;                 //在赤道展示图片

    float iQuality;
    float iUniverseSign;                 //相机所在空间侧。 +1.0正宇宙  -1.0反宇宙
    float iBlackHoleTime;                //时间。单位 c*s/Rs。三维观者模式下直接传入世界时间，着色器会自动将传入的值解析为in/out ks系的t；四维情况下需要传入iCamDataCoordisOutgoing对应的系下的无穷远坐标时。
    float iBlackHoleMassSol;             //质量，单位倍太阳质量
    float iSpin;                         //无量纲自旋a*   
    float iQ;                            //无量纲电荷Q*
    float iMu;                           //吸积物质比荷
    float iAccretionRate;                //吸积率 单位倍爱丁顿吸积率
    float iBackShiftMax;			     //背景最大频移
    float iDensestarsurfaceR;            //致密星表面半径。非0值为启用。单位倍Rs
    float iDensestarBlackbodyIntensityExponent;   //致密星表面温度——黑体颜色指数
    float iDensestarRedShiftColorExponent;        //致密星表面频移——温度指数
    float iDensestarRedShiftIntensityExponent;    //致密星表面频移——亮度指数
    float iDensestarBrightmut;                    //致密星表面亮度乘数
    float iInterRadiusRs;                //吸积盘内半径。单位倍Rs
    float iOuterRadiusRs;                //吸积盘外半径。单位倍Rs
    float iThinRs;                       //吸积盘半厚度。单位倍Rs
    float iHopper;                       //吸积盘厚度随半径增加斜率
    float iBrightmut;                    //吸积盘亮度乘数
    float iDarkmut;                      //吸积盘不透明度乘数
    float iReddening;                    //吸积盘红化系数
    float iSaturation;                   //吸积盘饱和度
    float iBlackbodyIntensityExponent;   //吸积盘温度——黑体颜色指数
    float iRedShiftColorExponent;        //吸积盘频移——温度指数
    float iRedShiftIntensityExponent;    //吸积盘频移——亮度指数
    float iImageRotationSpeed;           //图片整体自转角速度
    float iPolarizationAngle;            //偏振片模式偏振片角度
    float iHeatHaze;				     //热气流扰动强度
    float iBackgroundBrightmut;		     //背景亮度乘数
    float iPhotonRingBoost;              //光子环亮度增亮
    float iPhotonRingColorTempBoost;     //光子环颜色增蓝
    float iBoostRot;                     //增强在自旋非0的非对称程度
    //吸积盘的亮度限制貌似写死2.2了
    //应该添加背景的频移 亮度和颜色限制系数
    float iJetRedShiftIntensityExponent; //喷流频移——亮度指数
    float iJetBrightmut;                 //喷流亮度乘数
    float iJetSaturation;                //喷流饱和度
    float iJetShiftMax;                  //喷流蓝移限制
    float iBlendWeight;                  //TAA前后帧混合权重。
    float iNoiseLut;                     //噪声哈希查表开关(本项目扩展字段,追加于 NPGS 布局末尾:0=程序化 sin 哈希,1=64³ LUT)
    float iDiskScatter;                  //盘前向散射强度(本项目扩展字段,追加于 NPGS 布局末尾:被盘消光的背景光散射回视线的比例,0=关)
    float iDiskAmbient;                  //盘环境光强度(本项目扩展字段,追加于 NPGS 布局末尾:全天空辐照×盘密度并入发射的弥散项,0=关)
    // 落点白点开关（本项目扩展字段）：NPGS 原版此调用是注释状态（默认不画），本项目改为可开关。
    // **占用 std140 结构对齐产生的尾部填充**（前面字段实际 396B、缓冲区 400B），故不改变任何
    // 既有字段偏移，也不影响 NPGS 原版（其声明更短，自然忽略尾部字节）。
    int iShowFallingDot;
};

layout(set = 1, binding = 0) uniform sampler2D iHistoryTex;
// 六套天空盒（NPGS 逐字布局）：Universe0/1/2 宇宙变体星空 + Antiverse0/1/2 反宇宙，
// 按 int(iInWhichUniverse+3+useContground)%3 选层——是"宇宙变体选层"而非分辨率 LOD。
// useContground 由 Status 的高位（逃逸自上个宇宙的光，Status>3）反推，见 SampleBackground。
layout(set = 1, binding = 1) uniform samplerCube iBackground0;
layout(set = 1, binding = 2) uniform samplerCube iAntiground0;
layout(set = 1, binding = 3) uniform samplerCube iBackground1;
layout(set = 1, binding = 4) uniform samplerCube iAntiground1;
layout(set = 1, binding = 5) uniform samplerCube iBackground2;
layout(set = 1, binding = 6) uniform samplerCube iAntiground2;
// 贴图盘（NPGS set1.b9，iUseImageDisk 开关控制采样；ImageDiskColor）
layout(set = 1, binding = 9) uniform sampler2D iImageTexture;
// 低分扭曲场（NPGS prepass/composite）：xyz=EscapeDir*FreqShift（频移编码进向量长度），
// w=Status 状态位。全程 texelFetch 逐纹素取数，采样器滤波参数不参与
layout(set = 1, binding = 7) uniform sampler2D iPrepassDistortion;
// 低分体积色（吸积盘等 AccumColor，未 tonemap 的 HDR 线性域）
layout(set = 1, binding = 8) uniform sampler2D iPrepassVolumetric;
// 噪声哈希 LUT（64³ R8）：PerlinNoise 的 sin 哈希查表化，与 iNoiseLut 开关配合实时 A/B
layout(set = 1, binding = 10) uniform sampler3D iNoiseHashLut;

const float CONST_M = 0.5; // [PHYS] Mass M = 0.5
const float EPSILON = 1e-6;
// 自旋/电荷 量纲化
float PhysicalSpinA = iSpin * CONST_M;
float PhysicalQ     = iQ * CONST_M;

// 盘环境光弥散项（每像素 TraceRay 开头算一次的常量，供 DiskColor 逐样本消费）：
// 全天空 6 向采样平均（ambient-cube 近似）× AMBIENT_PHASE 相位折扣。
// 侧向入射光散射进视线的效率低于前向背光（相位函数前向峰化），故乘折扣，与
// 背光项（main 合成处的 iDiskScatter）拼成完整的单次散射两块
#define AMBIENT_PHASE 0.25
vec3 gDiskAmbientSky = vec3(0.0);

// =============================================================================
// SECTION 2: 基础工具函数 (噪声、插值、随机)
// =============================================================================
float det3(vec3 a, vec3 b, vec3 c) {
    return dot(a, cross(b, c));
}
float RandomStep(vec2 Input, float Seed)
{
    return fract(sin(dot(Input + fract(11.4514 * sin(Seed)), vec2(12.9898, 78.233))) * 43758.5453);
}

float CubicInterpolate(float x)
{
    return 3.0 * pow(x, 2.0) - 2.0 * pow(x, 3.0);
}

// PerlinNoise 为值噪声：8 个格点角的哈希值做三次衰减三线性插值。
// 哈希双路径（iNoiseLut 运行时开关，GUI 可实时 A/B）：
//   0 = 程序化 sin 哈希（NPGS 原版）
//   1 = 64³ 哈希 LUT 查表（8 次 texelFetch 替代 8 条 sin 超越函数链，Java 侧按同式预计算；
//       格点坐标 &63 周期化——重复周期远大于盘面低频噪声的有效范围，插值结构与值域不变）
float PerlinNoise(vec3 Position)
{
    vec3 PosInt   = floor(Position);
    vec3 PosFloat = fract(Position);

    float Sx = CubicInterpolate(PosFloat.x);
    float Sy = CubicInterpolate(PosFloat.y);
    float Sz = CubicInterpolate(PosFloat.z);

    if (iNoiseLut > 0.5)
    {
        ivec3 c = ivec3(PosInt);
        ivec3 n = (c + 1) & 63;
        ivec3 b = c & 63;
        float v000 = 2.0 * texelFetch(iNoiseHashLut, ivec3(b.x, b.y, b.z), 0).r - 1.0;
        float v100 = 2.0 * texelFetch(iNoiseHashLut, ivec3(n.x, b.y, b.z), 0).r - 1.0;
        float v010 = 2.0 * texelFetch(iNoiseHashLut, ivec3(b.x, n.y, b.z), 0).r - 1.0;
        float v110 = 2.0 * texelFetch(iNoiseHashLut, ivec3(n.x, n.y, b.z), 0).r - 1.0;
        float v001 = 2.0 * texelFetch(iNoiseHashLut, ivec3(b.x, b.y, n.z), 0).r - 1.0;
        float v101 = 2.0 * texelFetch(iNoiseHashLut, ivec3(n.x, b.y, n.z), 0).r - 1.0;
        float v011 = 2.0 * texelFetch(iNoiseHashLut, ivec3(b.x, n.y, n.z), 0).r - 1.0;
        float v111 = 2.0 * texelFetch(iNoiseHashLut, ivec3(n.x, n.y, n.z), 0).r - 1.0;

        return mix(mix(mix(v000, v100, Sx), mix(v010, v110, Sx), Sy),
                   mix(mix(v001, v101, Sx), mix(v011, v111, Sx), Sy), Sz);
    }

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

float SoftSaturate(float x)
{
    return 1.0 - 1.0 / (max(x, 0.0) + 1.0);
}

float PerlinNoise1D(float Position)
{
    float PosInt   = floor(Position);
    float PosFloat = fract(Position);
    float v0 = 2.0 * fract(sin(PosInt * 12.9898) * 43758.5453) - 1.0;
    float v1 = 2.0 * fract(sin((PosInt + 1.0) * 12.9898) * 43758.5453) - 1.0;
    return v1 * CubicInterpolate(PosFloat) + v0 * CubicInterpolate(1.0 - PosFloat);
}

float GenerateAccretionDiskNoise(vec3 Position, float NoiseStartLevel, float NoiseEndLevel, float ContrastLevel)
{
    float NoiseAccumulator = 10.0;
    float start = NoiseStartLevel;
    float end = NoiseEndLevel;
    int iStart = int(floor(start));
    int iEnd = int(ceil(end));
    
    int maxIterations = iEnd - iStart;
    for (int delta = 0; delta < maxIterations; delta++)
    {
        int i = iStart + delta;
        float iFloat = float(i);
        float w = max(0.0, min(end, iFloat + 1.0) - max(start, iFloat));
        if (w <= 0.0) continue;
        
        float NoiseFrequency = pow(3.0, iFloat);
        vec3 ScaledPosition = NoiseFrequency * Position;
        float noise = PerlinNoise(ScaledPosition);
        NoiseAccumulator *= (1.0 + 0.1 * noise * w);
    }
    return log(1.0 + pow(0.1 * NoiseAccumulator, ContrastLevel));
}

float Vec2ToTheta(vec2 v1, vec2 v2)
{
    float VecDot   = dot(v1, v2);
    float VecCross = v1.x * v2.y - v1.y * v2.x;
    float Angle    = asin(0.999999 * VecCross / (length(v1) * length(v2)));
    float Dx = step(0.0, VecDot);
    float Cx = step(0.0, VecCross);
    return mix(mix(-kPi - Angle, kPi - Angle, Cx), Angle, Dx);
}

float Shape(float x, float Alpha, float Beta)
{
    float k = pow(Alpha + Beta, Alpha + Beta) / (pow(Alpha, Alpha) * pow(Beta, Beta));
    return k * pow(x, Alpha) * pow(1.0 - x, Beta);
}



// =============================================================================
// SECTION 3: 颜色与光谱函数     采样与后处理
// =============================================================================

vec3 KelvinToRgb(float Kelvin)
{
    if (Kelvin < 400.01) return vec3(0.0);
    float Teff     = (Kelvin - 6500.0) / (6500.0 * Kelvin * 2.2);
    vec3  RgbColor = vec3(0.0);
    RgbColor.r = exp(2.05539304e4 * Teff);
    RgbColor.g = exp(2.63463675e4 * Teff);
    RgbColor.b = exp(3.30145739e4 * Teff);
    float BrightnessScale = 1.0 / max(max(1.5 * RgbColor.r, RgbColor.g), RgbColor.b);
    if (Kelvin < 1000.0) BrightnessScale *= (Kelvin - 400.0) / 600.0;
    RgbColor *= BrightnessScale;
    return RgbColor;
}

vec3 WavelengthToRgb(float wavelength) {
    vec3 color = vec3(0.0);
    if (wavelength <= 380.0 ) {
        color.r = 1.0; color.g = 0.0; color.b = 1.0;
    } else if (wavelength >= 380.0 && wavelength < 440.0) {
        color.r = -(wavelength - 440.0) / (440.0 - 380.0); color.g = 0.0; color.b = 1.0;
    } else if (wavelength >= 440.0 && wavelength < 490.0) {
        color.r = 0.0; color.g = (wavelength - 440.0) / (490.0 - 440.0); color.b = 1.0;
    } else if (wavelength >= 490.0 && wavelength < 510.0) {
        color.r = 0.0; color.g = 1.0; color.b = -(wavelength - 510.0) / (510.0 - 490.0);
    } else if (wavelength >= 510.0 && wavelength < 580.0) {
        color.r = (wavelength - 510.0) / (580.0 - 510.0); color.g = 1.0; color.b = 0.0;
    } else if (wavelength >= 580.0 && wavelength < 645.0) {
        color.r = 1.0; color.g = -(wavelength - 645.0) / (645.0 - 580.0); color.b = 0.0;
    } else if (wavelength >= 645.0 && wavelength <= 750.0) {
        color.r = 1.0; color.g = 0.0; color.b = 0.0;
    } else if (wavelength >= 750.0) {
        color.r = 1.0; color.g = 0.0; color.b = 0.0;
    }
    float factor = 0.3;
    if (wavelength >= 380.0 && wavelength < 420.0) factor = 0.3 + 0.7 * (wavelength - 380.0) / (420.0 - 380.0);
    else if (wavelength >= 420.0 && wavelength < 645.0) factor = 1.0;
    else if (wavelength >= 645.0 && wavelength <= 750.0) factor = 0.3 + 0.7 * (750.0 - wavelength) / (750.0 - 645.0);
    
    return color * factor / pow(color.r * color.r + 2.25 * color.g * color.g + 0.36 * color.b * color.b, 0.5) * (0.1 * (color.r + color.g + color.b) + 0.9);
}

// 单层天空盒采样：Status 的宇宙/反宇宙选层已裁剪（只有一套资源），仅保留三波段频移物理
vec4 SampleBackground(vec3 Dir, float Shift, float Status)
{
    // 六套盒选层（NPGS 逐行移植）：rStatus>3 表示光线逃逸自"上个宇宙"（最大延拓下穿过
    // 虫洞喉道），偏移出一层；isAntiverse 判定落在反宇宙（Status=2,5,8…）。
    // 取盒只按 iInWhichUniverse 选宇宙变体（0/1/2），与分辨率 LOD 无关。
    // 与 NPGS 的唯一差异：此处固定 LOD 0（NPGS 用 textureQueryLod 逐面取 mip），
    // 本移植版盒未生 mip 链，固定 0 既正确又省一次 LOD 查询。
    vec4 Backcolor;

    float rStatus = round(Status);
    int offset = 0;
    if (rStatus > 3.0) {
        offset = int(round((rStatus - 1.0) / 3.0));
    }
    int useContground = -offset;

    // Background 与 Antiground 的选层逻辑
    bool isNegativeMass = (iBlackHoleMassSol < 0.0);
    bool isAntiverse    = (mod(rStatus, 3.0) == 2.0); // 囊括 2, 5, 8… 所有反宇宙

    if (isNegativeMass != isAntiverse)
    {
        if      (int(iInWhichUniverse+3+useContground)%3==0) Backcolor = textureLod(iAntiground0, Dir, 0.0);
        else if (int(iInWhichUniverse+3+useContground)%3==1) Backcolor = textureLod(iAntiground1, Dir, 0.0);
        else                                                 Backcolor = textureLod(iAntiground2, Dir, 0.0);
    }
    else
    {
        if      (int(iInWhichUniverse+3+useContground)%3==0) Backcolor = textureLod(iBackground0, Dir, 0.0);
        else if (int(iInWhichUniverse+3+useContground)%3==1) Backcolor = textureLod(iBackground1, Dir, 0.0);
        else                                                 Backcolor = textureLod(iBackground2, Dir, 0.0);
    }

    float BackgroundShift = Shift;
    vec3 Rcolor = Backcolor.r * 1.0 * WavelengthToRgb(max(453.0, 645.0 / BackgroundShift));
    vec3 Gcolor = Backcolor.g * 1.5 * WavelengthToRgb(max(416.0, 510.0 / BackgroundShift));
    vec3 Bcolor = Backcolor.b * 0.6 * WavelengthToRgb(max(380.0, 440.0 / BackgroundShift));
    vec3 Scolor = Rcolor + Gcolor + Bcolor;
    float OStrength = 0.3 * Backcolor.r + 0.6 * Backcolor.g + 0.1 * Backcolor.b;
    float RStrength = 0.3 * Scolor.r + 0.6 * Scolor.g + 0.1 * Scolor.b;
    Scolor *= OStrength / max(RStrength, 0.001);
    
    return iBackgroundBrightmut * vec4(Scolor, Backcolor.a) * pow(Shift, 4.0);
}


vec4 ApplyToneMapping(vec4 Result,float shift)
{
    // 安全域限制：pow(x,2.2)>1 时 log(1-x) 产生 NaN，混入 TAA 历史后永久自我延续（全黑屏）
    Result = clamp(Result, vec4(0.0), vec4(0.9999, 0.9999, 0.9999, 1.0));
    float RedFactor   = 3.0 * Result.r / (Result.r + Result.b + Result.g );
    float BlueFactor  = 3.0 * Result.b / (Result.r + Result.b + Result.g );
    float GreenFactor = 3.0 * Result.g / (Result.r + Result.b + Result.g );
    float BloomMax    = max(8.0,shift)+log(max(shift-8.0+1.0,1.0));
    vec4 Mapped;
    Mapped.r = min(-4.0 * log( 1.0 - pow(Result.r, 2.2)), BloomMax * RedFactor);
    Mapped.g = min(-4.0 * log( 1.0 - pow(Result.g, 2.2)), BloomMax * GreenFactor);
    Mapped.b = min(-4.0 * log( 1.0 - pow(Result.b, 2.2)), BloomMax * BlueFactor);
    Mapped.a = min(-4.0 * log( 1.0 - pow(Result.a, 2.2)), 4.0);
    return Mapped;
}

// =============================================================================
// SECTION 3.5: 共享工具函数 —— 提取重复模式 (Phase 1.5 新增)
// =============================================================================

// 热折射常量（原为 #define，提前至此供共享函数使用）
const float HAZE_LAYER_THICKNESS = 0.8;
const float HAZE_RADIAL_EXPAND   = 0.8;
const int   HAZE_PROBE_STEPS     = 10;
const float HAZE_STEP_SIZE       = 0.05;

// 自适应步长：根据到黑洞距离动态调整采样步长
// 用于 DiskColor、JetColor 的体积采样
float ComputeAdaptiveStepSize(float DistanceToBlackHole, float OuterRadius) {
    float SmallStepBoundary = max(OuterRadius, 12.0);
    float StepSize = 1.0;
    StepSize *= 0.15 + 0.25 * min(max(0.0, 0.5 * (0.5 * DistanceToBlackHole / max(10.0, SmallStepBoundary) - 1.0)), 1.0);
    if ((DistanceToBlackHole) >= 2.0 * SmallStepBoundary) StepSize *= DistanceToBlackHole;
    else if ((DistanceToBlackHole) >= 1.0 * SmallStepBoundary) StepSize *= ((1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0)) * (2.0 * SmallStepBoundary - DistanceToBlackHole) + DistanceToBlackHole * (DistanceToBlackHole - SmallStepBoundary)) / SmallStepBoundary;
    else StepSize *= min(1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0), DistanceToBlackHole);
    return max(0.01, StepSize);
}

// 赤道穿越检测：检查光线是否穿过赤道面并翻转宇宙符号
// 返回翻转后的 CurrentUniverseSign
float CheckEquatorialCrossing(vec4 LastPos, vec4 CurrPos, float CurrentUniverseSign) {
    if (LastPos.y * CurrPos.y < 0.0) {
        float t_cross = LastPos.y / (LastPos.y - CurrPos.y);
        float rho_cross = length(mix(LastPos.xz, CurrPos.xz, t_cross));
        if (rho_cross < abs(PhysicalSpinA)) {
            return -CurrentUniverseSign;
        }
    }
    return CurrentUniverseSign;
}

// 盘遮罩采样：平滑过渡的盘厚度/半径遮罩
// 用于 Haze、GetHazeForce 的盘贡献计算
float SampleDiskMask(vec3 pos_Rg, float InterRadius, float OuterRadius, float Thin, float Hopper) {
    float r = length(pos_Rg.xz);
    float y = abs(pos_Rg.y);
    float GeometricThin = Thin + max(0.0, (r - 3.0) * Hopper);
    float diskThickRef = GeometricThin;
    float boundaryY = max(0.2, diskThickRef * HAZE_LAYER_THICKNESS);
    float vMaskDisk = 1.0 - smoothstep(boundaryY * 0.5, boundaryY * 1.5, y);
    float rMaskDisk = smoothstep(InterRadius * 0.3, InterRadius * 0.8, r) *
                      (1.0 - smoothstep(OuterRadius * HAZE_RADIAL_EXPAND * 0.75, OuterRadius * HAZE_RADIAL_EXPAND, r));
    return vMaskDisk * rMaskDisk;
}

// 喷流遮罩采样：锥形喷流的平滑遮罩
// 用于 Haze、GetHazeForce 的喷流贡献计算
float SampleJetMask(vec3 pos_Rg, float InterRadius, float OuterRadius, float SpinA) {
    float r = length(pos_Rg.xz);
    float y = abs(pos_Rg.y);
    float rSphere = length(pos_Rg);

    float jetRadiusCore = sqrt(max(0.0, 2.0 * InterRadius * InterRadius + 0.03 * 0.03 * y * y));
    float maskCore = smoothstep(jetRadiusCore * 1.2, jetRadiusCore * 0.8, r);
    maskCore *= smoothstep(0.0, 0.3 * InterRadius, y);

    float Wid = y;
    float shellInner = max(0.0, 0.7 * InterRadius + 0.15 * Wid);
    float shellOuter = 1.3 * InterRadius + 0.25 * Wid;
    float maskShell = smoothstep(shellInner * 0.8, shellInner, r) *
                      (1.0 - smoothstep(shellOuter, shellOuter * 1.2, r));
    maskShell *= smoothstep(0.5 * InterRadius, 1.0 * InterRadius, y);

    float mask = max(maskCore, maskShell * 0.5);
    mask *= smoothstep(OuterRadius * 1.5, OuterRadius * 3.0, rSphere);
    mask *= (1.0 - smoothstep(0.0, 0.1 * InterRadius, y));
    return mask;
}

// 最大步数公式：统一的 MaxStep 计算
float ComputeMaxStep(float spin, float q, bool whitehole, bool nakedSingularity) {
    float MaxStep = 150.0 + 300.0 / (1.0 + 1000.0 * (1.0 - spin*spin - q*q) * (1.0 - spin*spin - q*q));
    if (whitehole) MaxStep = 1145.0;
    if (nakedSingularity) MaxStep = 450.0;
    return MaxStep;
}

// =============================================================================
// SECTION 4: 广相计算。Y为自旋方向，ins/outgoing方向笛卡尔形式kerrscild系。+++-。
// =============================================================================

// [TENSOR] Flat Space Metric eta_uv = diag(1, 1, 1, -1)
const mat4 MINKOWSKI_METRIC = mat4(
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    0, 0, 0, -1
);

//PhysicalSpinA和PhysicalQ是有量纲量（无量纲量乘M，即乘0.5）。

float GetKeplerianAngularVelocity(float Radius, float Rs) 
{
    float M = 0.5 * Rs;
    float Mr_minus_Q2 = M * Radius - PhysicalQ * PhysicalQ;
    if (Mr_minus_Q2 < 0.0) return 0.0;
    float sqrt_Term = sqrt(Mr_minus_Q2);
    float denominator = Radius * Radius + PhysicalSpinA * sqrt_Term;
    return sqrt_Term / max(EPSILON, denominator);
}

//输入X^mu空间部分，输出bl系参数r
float KerrSchildRadius(vec3 p, float r_sign) {
    float r_sign_len = r_sign * length(p);
    if (PhysicalSpinA == 0.0) return r_sign_len; 

    float a2 = PhysicalSpinA * PhysicalSpinA;
    float rho2 = dot(p.xz, p.xz); // x^2 + z^2
    float y2 = p.y * p.y;
    
    float b = rho2 + y2 - a2;
    float det = sqrt(b * b + 4.0 * a2 * y2);
    
    float r2;
    if (b >= 0.0) {
        r2 = 0.5 * (b + det);
    } else {
        r2 = (2.0 * a2 * y2) / max(1e-20, det - b);
    }
    return r_sign * sqrt(r2);
}
// 计算 ZAMO (零角动量观测者) 的角速度 Omega
float GetZamoOmega(float r, float a, float Q, float y) {
    float r2 = r * r;
    float a2 = a * a;
    float y2 = y * y;
    float cos2 = min(1.0, y2 / (r2 + 1e-9)); 
    float sin2 = 1.0 - cos2;
    
    // Delta = r^2 - 2Mr + a^2 + Q^2 (M=0.5)
    float Delta = r2 - r + a2 + Q * Q;
    
    // Sigma = r^2 + a^2 cos^2 theta
    float Sigma = r2 + a2 * cos2;
    
    // metric term A = (r^2+a^2)^2 - Delta * a^2 * sin^2 theta
    float A_metric = (r2 + a2) * (r2 + a2) - Delta * a2 * sin2;
    
    // Omega_ZAMO = 2Mra / A (for Q=0), with Q: a(2Mr - Q^2) / A
    // 2Mr = r (since M=0.5, 2M=1.0) -> r
    return a * (r - Q * Q) / max(1e-9, A_metric);
}

// 求解射线与 Kerr-Schild 常数 r 椭球面的交点
// 方程: (x^2 + z^2)/(r^2 + a^2) + y^2/r^2 = 1
// 返回 vec2(t1, t2)，如果没有交点返回 vec2(-1.0)
vec2 IntersectKerrEllipsoid(vec3 O, vec3 D, float r, float a) {
    float r2 = r * r;
    float a2 = a * a;
    float R_eq_sq = r2 + a2; // 赤道半径平方
    float R_pol_sq = r2;     // 极半径平方
    
    // 椭球方程: B(x^2 + z^2) + A(y^2) = A*B
    // 其中 A = R_eq_sq, B = R_pol_sq
    float A = R_eq_sq;
    float B = R_pol_sq;
    
    // 代入射线 P = O + D*t
    // (B*Dx^2 + B*Dz^2 + A*Dy^2) t^2 + ...
    float qa = B * (D.x * D.x + D.z * D.z) + A * D.y * D.y;
    float qb = 2.0 * (B * (O.x * D.x + O.z * D.z) + A * O.y * D.y);
    float qc = B * (O.x * O.x + O.z * O.z) + A * O.y * O.y - A * B;
    
    if (abs(qa) < 1e-9) return vec2(-1.0); // 线性退化，忽略
    
    float disc = qb * qb - 4.0 * qa * qc;
    if (disc < 0.0) return vec2(-1.0);
    
    float sqrtDisc = sqrt(disc);
    float t1 = (-qb - sqrtDisc) / (2.0 * qa);
    float t2 = (-qb + sqrtDisc) / (2.0 * qa);
    
    return vec2(t1, t2);
}

struct KerrGeometry {
    float r;
    float r2;
    float a2;
    float f;              
    vec3  grad_r;         
    vec3  grad_f;         
    vec4  l_up;           // l^u = (lx, ly, lz, -1)
    vec4  l_down;         // l_u = (lx, ly, lz, 1)
    float inv_r2_a2;
    float inv_den_f;      
    float num_f;          
};

//fade用于在接近包围盒边界时强行过渡为平直时空。直接乘在f上。下文中gravityfade同。

void ComputeGeometryScalars(vec3 X, float fade, float r_sign, bool isOutgoing, out KerrGeometry geo) {
    geo.a2 = PhysicalSpinA * PhysicalSpinA;
    
    // 决定流入还是流出
    float dirSign = isOutgoing ? -1.0 : 1.0;
    
    if (PhysicalSpinA == 0.0) {
        geo.r = r_sign * length(X);
        geo.r2 = geo.r * geo.r;
        float inv_r = 1.0 / geo.r;
        float inv_r2 = inv_r * inv_r;
        
        // 乘上 dirSign
        geo.l_up = vec4(dirSign * X * inv_r, -1.0);
        geo.l_down = vec4(dirSign * X * inv_r, 1.0);
        
        geo.num_f = (2.0 * CONST_M * geo.r - PhysicalQ * PhysicalQ);
        geo.f = (2.0 * CONST_M * inv_r - (PhysicalQ * PhysicalQ) * inv_r2) * fade;
        
        geo.inv_r2_a2 = inv_r2; 
        geo.inv_den_f = inv_r2 * inv_r2; 
        return;
    }

    geo.r = KerrSchildRadius(X, r_sign);
    geo.r2 = geo.r * geo.r;
    float r3 = geo.r2 * geo.r;
    float z_coord = X.y; 
    float z2 = z_coord * z_coord;
    
    geo.inv_r2_a2 = 1.0 / (geo.r2 + geo.a2);
    
    // 修改处：向外/向内仅反转径向成分，保留自旋成分
    float lx = (dirSign * geo.r * X.x - PhysicalSpinA * X.z) * geo.inv_r2_a2;
    float ly = (dirSign * X.y) / geo.r;
    float lz = (dirSign * geo.r * X.z + PhysicalSpinA * X.x) * geo.inv_r2_a2;
    
    geo.l_up = vec4(lx, ly, lz, -1.0);
    geo.l_down = vec4(lx, ly, lz, 1.0); 
    
    geo.num_f = 2.0 * CONST_M * r3 - PhysicalQ * PhysicalQ * geo.r2;
    float den_f = geo.r2 * geo.r2 + geo.a2 * z2;
    geo.inv_den_f = 1.0 / max(1e-20, den_f);
    geo.f = (geo.num_f * geo.inv_den_f) * fade;
}


void ComputeGeometryGradients(vec3 X, float fade, inout KerrGeometry geo) {
    float inv_r = 1.0 / geo.r;
    
    if (PhysicalSpinA == 0.0) {

        float inv_r2 = inv_r * inv_r;
        geo.grad_r = X * inv_r;
        float df_dr = (-2.0 * CONST_M + 2.0 * PhysicalQ * PhysicalQ * inv_r) * inv_r2 * fade;
        geo.grad_f = df_dr * geo.grad_r;
        return;
    }

    float inv_denom_grad = geo.r * geo.inv_den_f;
    
    geo.grad_r = vec3(
        X.x * geo.r2,
        X.y * (geo.r2 + geo.a2),
        X.z * geo.r2
    ) * inv_denom_grad;
    
    float z_coord = X.y;
    float z2 = z_coord * z_coord;
    
    float term_M  = -2.0 * CONST_M * geo.r2 * geo.r2 * geo.r;
    float term_Q  = 2.0 * PhysicalQ * PhysicalQ * geo.r2 * geo.r2;
    float term_Ma = 6.0 * CONST_M * geo.a2 * geo.r * z2;
    float term_Qa = -2.0 * PhysicalQ * PhysicalQ * geo.a2 * z2;
    
    float df_dr_num_reduced = term_M + term_Q + term_Ma + term_Qa;
    float df_dr = (geo.r * df_dr_num_reduced) * (geo.inv_den_f * geo.inv_den_f);
    
    float df_dy = -(geo.num_f * 2.0 * geo.a2 * z_coord) * (geo.inv_den_f * geo.inv_den_f);
    
    geo.grad_f = df_dr * geo.grad_r;
    geo.grad_f.y += df_dy;
    geo.grad_f *= fade;
}

//ks系的形式使得度规与矢量的乘法可以优化
//升指标和降指标。虽然变量名用的P，但是可以用于任何符合变换规则的矢量
//  P^u = g^uv P_v
// g^uv = eta^uv - f * l^u * l^v
vec4 RaiseIndex(vec4 P_cov, KerrGeometry geo) {
    // eta^uv = diag(1, 1, 1, -1)
    vec4 P_flat = vec4(P_cov.xyz, -P_cov.w); 

    float L_dot_P = dot(geo.l_up, P_cov);
    
    return P_flat - geo.f * L_dot_P * geo.l_up;
}

// P_u = g_uv P^v
// g_uv = eta_uv + f * l_u * l_v
vec4 LowerIndex(vec4 P_contra, KerrGeometry geo) {
    // eta_uv = diag(1, 1, 1, -1)
    vec4 P_flat = vec4(P_contra.xyz, -P_contra.w);
    
    float L_dot_P = dot(geo.l_down, P_contra);
    
    return P_flat + geo.f * L_dot_P * geo.l_down;
}

//inout换系

// 物理常量与数值容差定义
const float EPS = 1e-16; // 防止除0和对数域爆炸的安全容差

//
// 笛卡尔 Kerr-Schild 坐标系 Ingoing/Outgoing 相互变换 (Y轴自旋版)
// 
// @param X          inout: 四坐标 (x, y, z, T)  (注：y为自旋轴)
// @param r_sign     in:    径向符号 (+1.0 或 -1.0)
// @param P          inout: 协变四动量 (p_x, p_y, p_z, p_T)
// @param M, a, Q    in:    黑洞三毛参数
// @param out_to_in  in:    false: In->Out;  true: Out->In
//
void transformKerrSchild_YSpin(inout vec4 X, in float r_sign, inout vec4 P, in bool out_to_in) 
{
    float M = CONST_M; float a = iSpin * CONST_M; float Q = iQ * CONST_M;
    // 1. 提取变量
    float x = X.x, y = X.y, z = X.z, t = X.w;
    float px = P.x, py = P.y, pz = P.z, pt = P.w;
    
    float a2 = a * a;
    float M2 = M * M;
    float Q2 = Q * Q;

    // 2. 解径向方程求 r (Y 为自旋轴)
    float R2 = x*x + y*y + z*z;
    float u = R2 - a2;
    float v = 4.0 * a2 * y * y; 
    
    float r2;
    if (u >= 0.0) {
        r2 = 0.5 * (u + sqrt(u*u + v));
    } else {
        r2 = 0.5 * v / max(1e-20, sqrt(u*u + v) - u);
    }
    
    float r = r_sign * sqrt(max(r2, 0.0));

    // 3. 计算视界函数 Delta 与空间底度规项 D
    float Delta = r*r - 2.0*M*r + a2 + Q2;
    float safe_Delta = sign(Delta) * max(abs(Delta), EPS);
    if (safe_Delta == 0.0) safe_Delta = EPS;

    float r3 = r * r * r;
    float D = r3 * r + a2 * y * y; // r^4 + a^2 y^2
    float safe_D = max(D, 1e-12);

    // 4. 计算径向坐标的梯度 r_i (经过 x->z, y->x, z->y 置换)
    vec3 grad_r = vec3(
        r3 * x / safe_D,                 // 对应原 y 的梯度形式
        r * (r*r + a2) * y / safe_D,     // 对应原 z 的梯度形式 (极轴方向)
        r3 * z / safe_D                  // 对应原 x 的梯度形式
    );

    // 5. 分析黑洞类型并计算积分函数 F(r) 和 g(r)
    float delta_disc = M2 - a2 - Q2; 
    float F_r = 0.0;
    float g_r = 0.0;
    float abs_Delta_safe = max(abs(Delta), EPS);

    if (delta_disc > EPS) {
        // [情形 A: 非极端黑洞]
        float K = sqrt(delta_disc);
        float r_plus = M + K;
        float r_minus = M - K;
        
        float frac = abs(r - r_plus) / max(abs(r - r_minus), EPS);
        float ln_arg = log(max(frac, EPS));
        float ln_Delta = log(abs_Delta_safe);

        F_r = 2.0 * M * ln_Delta + ((2.0 * M2 - Q2) / K) * ln_arg;
        g_r = (a / K) * ln_arg;
        
    } else if (delta_disc < -EPS) {
        // [情形 B: 裸奇点 (无视界)]
        float K = sqrt(-delta_disc);
        float ln_Delta = log(abs_Delta_safe);
        float atan_arg = atan((r - M) / K);

        F_r = 2.0 * M * ln_Delta + (2.0 * (2.0 * M2 - Q2) / K) * atan_arg;
        g_r = (2.0 * a / K) * atan_arg;
        
    } else {
        // [情形 C: 极端黑洞]
        float rM = r - M;
        float safe_rM = sign(rM) * max(abs(rM), EPS);
        if (safe_rM == 0.0) safe_rM = EPS;
        float ln_rM = log(max(abs(rM), EPS));

        F_r = 4.0 * M * ln_rM - 2.0 * (2.0 * M2 - Q2) / safe_rM;
        g_r = -2.0 * a / safe_rM;
    }

    g_r += 2.0 * atan(a, r); 
    // 6. 计算导数与动量标量 K_p
    float F_prime = 2.0 * (2.0 * M * r - Q2) / safe_Delta;
    
    float g_prime = 2.0 * a / safe_Delta - 2.0 * a / (r * r + a * a);

    // 角动量 Ly (自旋轴为 y，对应的轨道角动量守恒量)
    float Ly = z * px - x * pz; 
    float K_p = F_prime * pt + g_prime * Ly;

    // 7. 处理换系方向逻辑
    float dir = out_to_in ? -1.0 : 1.0; 
    
    // 旋转角与时间延迟
    float angle      = -dir * g_r;
    float time_shift = -dir * F_r;
    
    // 8. 修正空间动量 (利用新的径向梯度)
    vec3 P_tilde = vec3(px, py, pz) + dir * grad_r * K_p;

    // 9. 旋转矩阵 (作用于 Z-X 平面，绕 Y 轴)
    float cos_a = cos(angle);
    float sin_a = sin(angle);

    // 10. 赋值回原变量 (应用 Z-X 平面的旋转和时间偏移)
    X.x = x * cos_a + z * sin_a; // 原公式的 y'
    X.y = y;                     // Y 坐标(自旋轴)不变
    X.z = z * cos_a - x * sin_a; // 原公式的 x'
    X.w = t + time_shift;

    P.x =   P_tilde.z * sin_a + P_tilde.x * cos_a;
    P.y =   P_tilde.y;             // P_y(自旋方向动量)不变
    P.z = - P_tilde.x * sin_a + P_tilde.z * cos_a;
    P.w = pt;                    // 能量 Pt 不变
}
// 调试函数：检查初始动量合法性并可视化局部动量方向
// =============================================================================



// =============================================================================
// Walker-Penrose 常数计算 (无坐标奇点优化版)
// 输入:
//   X:     光子位置 (Cartesian KS, Y轴为自旋轴)
//   P_cov: 光子下指标动量 P_mu
//   F_cov: 光子下指标偏振 f_mu
//   Physicala: 有量纲自旋参数（0.5a*）,M=0.5
//   PhysicalQ: 有量纲电荷参数（0.5Q*）
//   r:     由 KerrSchildRadius 算得的有符号半径
// 返回:
//   vec2:  Walker-Penrose 常数的 (实部, 虚部)
// =============================================================================
vec2 GetWalkerPenrose(vec4 X, vec4 P_cov, vec4 F_cov, float Physicala, float PhysicalQ, float r) {
    // 提取坐标成分
    float x = X.x;
    float y = X.y;
    float z = X.z;

    // 提取下指标动量与偏振成分
    float px = P_cov.x;
    float py = P_cov.y;
    float pz = P_cov.z;
    float pt = P_cov.w; // 时间分量 P_t

    float fx = F_cov.x;
    float fy = F_cov.y;
    float fz = F_cov.z;
    float ft = F_cov.w; // 时间分量 F_t

    // 由于 Kerr-Schild 坐标系的特殊性质，其复 Killing-Yano 张量的逆变分量 Y^uv
    // 与平直时空完全相同（H l^u l^v 项的缩并严格为0）。
    // 因此我们可以直接使用平直时空的逆变度规提升指标计算守恒量。
    // 在度规 +++- 符号下，P^i = P_i, P^t = -P_t。

    // 定义电型自旋向量 S_E^i = P^t F^i - F^t P^i = (-P_t) F_i - (-F_t) P_i = F_t P_i - P_t F_i
    float S_Ex = ft * px - pt * fx;
    float S_Ey = ft * py - pt * fy;
    float S_Ez = ft * pz - pt * fz;

    // 定义磁型自旋向量 S_B^i = (P \times F)^i (纯空间叉乘)
    float S_Bx = py * fz - pz * fy;
    float S_By = pz * fx - px * fz;
    float S_Bz = px * fy - py * fx;

    float a = Physicala;

    // 自旋方向为 Y 轴，即 a 向量为 (0, a, 0)
    // 根据平直时空 Y_uv P^u F^v 缩并推导：

    // K_1 (实部) = - [ a \cdot S_E + r \cdot S_B ]
    float K_re = -(a * S_Ey + x * S_Bx + y * S_By + z * S_Bz);

    // K_2 (虚部) = - [ r \cdot S_E + a \cdot S_B ]
    float K_im = -(x * S_Ex + y * S_Ey + z * S_Ez + a * S_By);

    return vec2(K_re, K_im);
}

// =============================================================================
// 解算偏振在相机屏幕上的二维投影 (科学严谨版：基于守恒律的投影归一化)
// =============================================================================
vec2 SolvePolarization(vec2 K_photon, vec2 K_right, vec2 K_up) {
    float det = K_right.x * K_up.y - K_right.y * K_up.x;

    // 降低退化阈值。在 Float32 下，det 可能会小到 1e-20 量级
    if (abs(det) < 1e-25) {
        return vec2(1.0, 0.0);
    }

    float inv_det = 1.0 / det;

    float alpha = ( K_up.y * K_photon.x - K_up.x * K_photon.y) * inv_det;
    float beta  = (-K_right.y * K_photon.x + K_right.x * K_photon.y) * inv_det;

    vec2 result = vec2(alpha, beta);

    // 【核心修复】：物理守恒律约束
    // 平行移动保持极化矢量的模长不变。因为发射点和相机处的极化基底均已归一化，
    // 理论上必定有 alpha^2 + beta^2 = 1。
    // Float32 在处理趋近径向光线的 WP 常数时发生灾难性相消，导致振幅失真甚至爆炸。
    // 在此将结果重新归一化，既消除了数值发散，又保留了精确的偏振方向（相位）信息。
    float mag = length(result);
    if (mag > 1e-19) {
        result /= mag;
    } else {
        // 如果连方向都被噪声完全摧毁(极其罕见)，指定一个默认方向
        result = vec2(1.0, 0.0);
    }

    return result;
}


// =============================================================================
// 由观测者四维速度 U_up 与光子上指标动量 P_up，把两个候选矢量 (Right/Up)
// 正交化成与光子动量横向的四维偏振矢量 F_down（满足 F·P = 0、F·F = 1）。
// 投影算子取 (P_up - P_up·U_down · U_up)：因为 D ≡ P + E·U（E = -P·U）与
// P - (P·U)U 只差整体符号，而归一化后符号无影响；故此处形式最简且无需显式求 E。
// 仅 iPolarization != 0 时被调用。
// =============================================================================
void BuildTransversePolarizationBasis(vec4 P_cov_base, vec4 U_up, vec4 U_down, KerrGeometry geo,
                                      vec4 Right_up, vec4 Up_up,
                                      out vec4 FX_down, out vec4 FY_down) {
    vec4 P_up = RaiseIndex(P_cov_base, geo);

    vec4 D_up   = P_up - dot(P_up, U_down) * U_up;
    vec4 D_down = LowerIndex(D_up, geo);
    float invE2 = 1.0 / max(1e-12, dot(P_up, U_down) * dot(P_up, U_down));

    vec4 FX_up = Right_up - (dot(Right_up, D_down) * invE2) * D_up;
    vec4 FY_up = Up_up    - (dot(Up_up,    D_down) * invE2) * D_up;

    FX_down = LowerIndex(FX_up, geo);
    FY_down = LowerIndex(FY_up, geo);

    FX_down /= sqrt(max(1e-12, dot(FX_up, FX_down)));
    FY_down /= sqrt(max(1e-12, dot(FY_up, FY_down)));
}

//初始化ingoing系下光子动量P_u，以向心矢量为主轴做施密特正交化
vec4 GetInitialMomentum(
    vec3 RayDir,          
    vec4 X,               
    float GravityFade,
    bool isOutgoing,
    out vec2 WP_CamX_out,   // 偏振基底：相机屏幕 right 轴的 Walker-Penrose 常数（仅 iPolarization≠0 写入）
    out vec2 WP_CamY_out    // 偏振基底：相机屏幕 up 轴的 Walker-Penrose 常数（仅 iPolarization≠0 写入）
)
{
    // 默认零基底：iPolarization==0 时下游不使用，iObserverMode==-1 分支单独处理
    WP_CamX_out = vec2(0.0);
    WP_CamY_out = vec2(0.0);

    float universesign = iUniverseSign;
    if (iObserverMode == -1) {
        // 在 C++ 中，RayDir传入的是 ViewDirLocal（屏幕视锥方向）
        vec3 v = normalize(RayDir);
        
        // 利用平移输运过来的标架生成光子的四维动量。
        // 负号与静态观者分支同式(见 P_up = U_up - (k_r*e1 + k_theta*e2 + k_phi*e3)):
        // v 是屏幕"向前"视方向,而积分步长为 -dLambda(沿动量反方向步进),
        // 故光子动量须取视方向反向(朝向观者飞来)。漏掉负号 = 采集身后半球。
        // 宿主 ie1/2/3_up = 相机 (right, up, back) 标架,v 为相机系视方向(-Z 前)。
        vec4 P_up_cam = iU_up - (v.x * ie1_up + v.y * ie2_up + v.z * ie3_up);
        
        bool camIsOutgoing = (iCamDataCoordisOutgoing == 1);
        
        // 获取相机所在系的度规
        KerrGeometry geo_cam;
        ComputeGeometryScalars(X.xyz, GravityFade, universesign, camIsOutgoing, geo_cam);
        
        // 降为下指标动量
        vec4 P_cov_cam = LowerIndex(P_up_cam, geo_cam);
        
        // 如果 Shader 当前要求评估的系 (isOutgoing) 与相机所在系不一致，在此换系
        if (isOutgoing != camIsOutgoing) {
            vec4 dummyX = X;
            transformKerrSchild_YSpin(dummyX, universesign, P_cov_cam, isOutgoing);
        }

        // 偏振基底（四维标架观者）：宿主 ie1/2_up = 相机 (right, up) 方向，
        // 用与静态观者分支相同的四维 Gram-Schmidt 造横向偏振矢量。
        if (iPolarization != 0) {
            // U_up（四维速度）在该分支由宿主的 iU_up 提供
            vec4 FX_down, FY_down;
            BuildTransversePolarizationBasis(P_cov_cam, iU_up, LowerIndex(iU_up, geo_cam), geo_cam,
                                             ie1_up, ie2_up, FX_down, FY_down);

            float r_start = KerrSchildRadius(X.xyz, universesign);
            WP_CamX_out = GetWalkerPenrose(X, P_cov_cam, FX_down, PhysicalSpinA, PhysicalQ, r_start);
            WP_CamY_out = GetWalkerPenrose(X, P_cov_cam, FY_down, PhysicalSpinA, PhysicalQ, r_start);
        }

        return P_cov_cam;
    }
    KerrGeometry geo;
    ComputeGeometryScalars(X.xyz, GravityFade, universesign,isOutgoing, geo);

    //确定观者四维速度 U_up 
    vec4 U_up;
    // Static Observer
    float g_tt = -1.0 + geo.f;
    float time_comp = 1.0 / sqrt(max(1e-9, -g_tt));
    U_up = vec4(0.0, 0.0, 0.0, time_comp);
    if (iObserverMode == 1) {
        // Free-Falling Observer
        float r = geo.r; float r2 = geo.r2; float a = PhysicalSpinA; float a2 = geo.a2;
        float y_phys = X.y; 
        
        float rho2 = r2 + a2 * (y_phys * y_phys) / (r2 + 1e-9);
        float Q2 = PhysicalQ * PhysicalQ;
        float MassChargeTerm = 2.0 * CONST_M * r - Q2;
        float Xi = sqrt(max(0.0, MassChargeTerm * (r2 + a2)));
        float DenomPhi = rho2 * (MassChargeTerm + Xi);
        
        float U_phi_KS = (abs(DenomPhi) > 1e-9) ? (-MassChargeTerm * a / DenomPhi) : 0.0;
        float U_r_KS = -Xi / max(1e-9, rho2);
        
        float inv_r2_a2 = 1.0 / (r2 + a2);
        float Ux_rad = (r * X.x - a * X.z) * inv_r2_a2 * U_r_KS;
        float Uz_rad = (r * X.z + a * X.x) * inv_r2_a2 * U_r_KS;
        float Uy_rad = (X.y / r) * U_r_KS;
        float Ux_tan =  X.z * U_phi_KS;
        float Uz_tan = -X.x * U_phi_KS;
        
        vec3 U_spatial = vec3(Ux_rad + Ux_tan, Uy_rad, Uz_rad + Uz_tan);
        
        float l_dot_u_spatial = dot(geo.l_down.xyz, U_spatial);
        float U_spatial_sq = dot(U_spatial, U_spatial);
        float A = -1.0 + geo.f;
        float B = 2.0 * geo.f * l_dot_u_spatial;
        float C = U_spatial_sq + geo.f * (l_dot_u_spatial * l_dot_u_spatial) + 1.0; 
        
        float Det = max(0.0, B*B - 4.0 * A * C);
        float sqrtDet = sqrt(Det);
        
        float Ut;
        if (abs(A) < 1e-7) {
            Ut = -C / max(1e-19, B); 
        } else {
            if (B < 0.0) {
                 Ut = 2.0 * C / (-B + sqrtDet);
            } else {
                 Ut = (-B - sqrtDet) / (2.0 * A);
            }
        }
        U_up = mix(U_up,vec4(U_spatial, Ut),GravityFade);//在包围盒边界回退到静态观者

    }else if (iObserverMode == 2) {
        // --- 模式 2: 任意四速观测者 (基于坐标速度) ---
        vec3 v_in = iCameraVelocity.xyz;
        if (any(isnan(v_in)) || any(isinf(v_in))) {v_in=vec3(0.0);}
        vec4 V_up = vec4(v_in, 1.0);
        vec4 V_down = LowerIndex(V_up, geo);
        float V_sq = dot(V_up, V_down);
        if (V_sq < 0) {
            U_up = V_up * inversesqrt(-V_sq);
        } else {
            return vec4(114514.0);
        }
    }else if (iObserverMode == 3) {
        vec3 v_in = -iCameraVelocity.xyz;
        if (any(isnan(v_in)) || any(isinf(v_in))) {v_in=vec3(0.0);}
        vec4 V_up = vec4(v_in, 1.0);
        vec4 V_down = LowerIndex(V_up, geo);
        float V_sq = dot(V_up, V_down);
        if (V_sq < 0) {
            U_up = V_up * inversesqrt(-V_sq);
        } else {
            return vec4(114514.0);
        }
    }
       
    vec4 U_down = LowerIndex(U_up, geo);

    //构建平直空间参考基
    //主轴，径向
    vec3 m_r = -normalize(X.xyz);

    vec3 WorldUp = vec3(0.0, 1.0, 0.0);
    //副轴，环向或X。注意这个系只是中转，在极点强取方向不会改变视线朝向，只会改变畸变方向，而两极处在平行赤道面上正好没有畸变
    if (abs(dot(m_r, WorldUp)) > 0.999) {
        WorldUp = vec3(1.0, 0.0, 0.0);
    }
    vec3 m_phi = cross(WorldUp, m_r); 
    m_phi = normalize(m_phi);

    vec3 m_theta = cross(m_phi, m_r); 

    // 分解 RayDir 到这组基底
    float k_r     = dot(RayDir, m_r);
    float k_theta = dot(RayDir, m_theta);
    float k_phi   = dot(RayDir, m_phi);

    //构建弯曲时空物理基底

    vec4 e1 = vec4(m_r, 0.0);
    e1 += dot(e1, U_down) * U_up; 
    vec4 e1_d = LowerIndex(e1, geo);
    float n1 = sqrt(max(1e-9, dot(e1, e1_d)));
    e1 /= n1; e1_d /= n1;

    vec4 e2 = vec4(m_theta, 0.0);
    e2 += dot(e2, U_down) * U_up;
    e2 -= dot(e2, e1_d) * e1;
    vec4 e2_d = LowerIndex(e2, geo);
    float n2 = sqrt(max(1e-9, dot(e2, e2_d)));
    e2 /= n2; e2_d /= n2;

    vec4 e3 = vec4(m_phi, 0.0);
    e3 += dot(e3, U_down) * U_up;
    e3 -= dot(e3, e1_d) * e1;
    e3 -= dot(e3, e2_d) * e2;
    vec4 e3_d = LowerIndex(e3, geo);
    float n3 = sqrt(max(1e-9, dot(e3, e3_d)));
    e3 /= n3;



    vec4 P_up = U_up - (k_r * e1 + k_theta * e2 + k_phi * e3);

    if (iPolarization != 0) {
        // e3/e2 作为相机屏幕的 right/up 参考轴（与 NPGS 的 R_up/Y_up 语义一致）
        vec4 FX_down, FY_down;
        BuildTransversePolarizationBasis(LowerIndex(P_up, geo), U_up, U_down, geo, e3, e2, FX_down, FY_down);

        // 输出到调用方作用域的偏振基底（out 参数，避免全局变量状态污染）
        WP_CamX_out = GetWalkerPenrose(X, LowerIndex(P_up, geo), FX_down, PhysicalSpinA, PhysicalQ, geo.r);
        WP_CamY_out = GetWalkerPenrose(X, LowerIndex(P_up, geo), FY_down, PhysicalSpinA, PhysicalQ, geo.r);
    }

    // 返回协变动量 P_mu
    return LowerIndex(P_up, geo);
}
// =============================================================================

// =============================================================================
// 5.积分器
// =============================================================================
struct State {
    vec4 X; // x^u
    vec4 P; // p_u
};

//通过缩放动量空间部分修正哈密顿量
void ApplyHamiltonianCorrection(inout vec4 P, vec4 X, float E, float fade, float r_sign, bool isOutgoing) {
    P.w = -E;
    vec3 p = P.xyz;    
    
    KerrGeometry geo;
    // 增加入参 isOutgoing
    ComputeGeometryScalars(X.xyz, fade, r_sign, isOutgoing, geo);
    
    float L_dot_p_s = dot(geo.l_up.xyz, p);
    float Pt = P.w; 
    
    float p2 = dot(p, p);
    float Coeff_A = p2 - geo.f * L_dot_p_s * L_dot_p_s;
    float Coeff_B = 2.0 * geo.f * L_dot_p_s * Pt;
    float Coeff_C = -Pt * Pt * (1.0 + geo.f);
    
    float disc = Coeff_B * Coeff_B - 4.0 * Coeff_A * Coeff_C;
    
    if (disc >= 0.0) {
        float sqrtDisc = sqrt(disc);
        float denom = 2.0 * Coeff_A;
        if (abs(denom) > 1e-9) {
            float k1 = (-Coeff_B + sqrtDisc) / denom;
            float k2 = (-Coeff_B - sqrtDisc) / denom;
            float dist1 = abs(k1 - 1.0);
            float dist2 = abs(k2 - 1.0);
            float k = (dist1 < dist2) ? k1 : k2;
            P.xyz *= mix(k, 1.0, clamp(abs(k - 1.0) / 0.1 - 1.0, 0.0, 1.0));
        }
    }
}
//哈密顿量时空导数
State GetDerivativesAnalytic(State S, float fade, bool isOutgoing, inout KerrGeometry geo) {
    State deriv;
    
    ComputeGeometryGradients(S.X.xyz, fade, geo);
    
    float l_dot_P = dot(geo.l_up.xyz, S.P.xyz) + geo.l_up.w * S.P.w;
    
    vec4 P_flat = vec4(S.P.xyz, -S.P.w); 
    deriv.X = P_flat - geo.f * l_dot_P * geo.l_up;
    
    vec3 grad_A = (-2.0 * geo.r * geo.inv_r2_a2) * geo.inv_r2_a2 * geo.grad_r;
    
    float dirSign = isOutgoing ? -1.0 : 1.0;
    
    // 引入 dirSign 进行导数修正
    float rx_az = dirSign * geo.r * S.X.x - PhysicalSpinA * S.X.z;
    float rz_ax = dirSign * geo.r * S.X.z + PhysicalSpinA * S.X.x;
    
    vec3 d_num_lx = dirSign * S.X.x * geo.grad_r; 
    d_num_lx.x += dirSign * geo.r; 
    d_num_lx.z -= PhysicalSpinA;
    vec3 grad_lx = geo.inv_r2_a2 * d_num_lx + rx_az * grad_A;
    
    vec3 grad_ly = dirSign * (geo.r * geo.inv_den_f) * vec3(-S.X.x * S.X.y, geo.r2 - S.X.y * S.X.y, -S.X.z * S.X.y);
    
    vec3 d_num_lz = dirSign * S.X.z * geo.grad_r;
    d_num_lz.z += dirSign * geo.r;
    d_num_lz.x += PhysicalSpinA;
    vec3 grad_lz = geo.inv_r2_a2 * d_num_lz + rz_ax * grad_A;
    
    vec3 P_dot_grad_l = S.P.x * grad_lx + S.P.y * grad_ly + S.P.z * grad_lz;
    
    vec3 Force = 0.5 * ( (l_dot_P * l_dot_P) * geo.grad_f + (2.0 * geo.f * l_dot_P) * P_dot_grad_l );
    
    deriv.P = vec4(Force, 0.0); 
    
    return deriv;
}

//检测试探步是否穿过奇环面。Rk4里小步的符号需要实时更新不然会被弹飞
float GetIntermediateSign(vec4 StartX, vec4 CurrentX, float CurrentSign) {
    if (StartX.y * CurrentX.y < 0.0) {
        float t = StartX.y / (StartX.y - CurrentX.y);
        float rho_cross = length(mix(StartX.xz, CurrentX.xz, t));
        if (rho_cross < abs(PhysicalSpinA)) {
            return -CurrentSign;
        }
    }
    return CurrentSign;
}

//Rk4，第一步复用外部结果
void StepGeodesicRK4_Optimized(
    inout vec4 X, inout vec4 P, 
    float E, float dt, 
    float fade, float r_sign, 
    bool isOutgoing,      // <--- 新增
    KerrGeometry geo0, 
    State k1
) {
    State s0; s0.X = X; s0.P = P;
    // k2 Step
    State s1; 
    s1.X = s0.X + 0.5 * dt * k1.X; 
    s1.P = s0.P + 0.5 * dt * k1.P;
    float sign1 = GetIntermediateSign(s0.X, s1.X, r_sign);
    KerrGeometry geo1;
    ComputeGeometryScalars(s1.X.xyz, fade, sign1, isOutgoing, geo1);
    State k2 = GetDerivativesAnalytic(s1, fade, isOutgoing, geo1);
    // k3 Step
    State s2; 
    s2.X = s0.X + 0.5 * dt * k2.X; 
    s2.P = s0.P + 0.5 * dt * k2.P;
    float sign2 = GetIntermediateSign(s0.X, s2.X, r_sign);
    KerrGeometry geo2;
    ComputeGeometryScalars(s2.X.xyz, fade, sign2, isOutgoing, geo2);
    State k3 = GetDerivativesAnalytic(s2, fade, isOutgoing, geo2);
    // k4 Step
    State s3; 
    s3.X = s0.X + dt * k3.X; 
    s3.P = s0.P + dt * k3.P;
    float sign3 = GetIntermediateSign(s0.X, s3.X, r_sign);
    KerrGeometry geo3;
    ComputeGeometryScalars(s3.X.xyz, fade, sign3, isOutgoing, geo3);
    State k4 = GetDerivativesAnalytic(s3, fade, isOutgoing, geo3);
    vec4 finalX = s0.X + (dt / 6.0) * (k1.X + 2.0 * k2.X + 2.0 * k3.X + k4.X);
    vec4 finalP = s0.P + (dt / 6.0) * (k1.P + 2.0 * k2.P + 2.0 * k3.P + k4.P);
    
    float finalSign = GetIntermediateSign(s0.X, finalX, r_sign);
    if(finalSign > 0) { 
        ApplyHamiltonianCorrection(finalP, finalX, E, fade, finalSign, isOutgoing);
    }
    X = finalX;
    P = finalP;
}
// =============================================================================
// SECTION 6: 热折射，吸积盘与喷流,经纬网
// =============================================================================

#define HAZE_STRENGTH           0.2    // 折射强度
#define HAZE_SCALE              5.2     // 噪声频率
#define HAZE_DENSITY_THRESHOLD  0.1     // 密度阈值
// HAZE_LAYER_THICKNESS, HAZE_RADIAL_EXPAND, HAZE_PROBE_STEPS, HAZE_STEP_SIZE
// 已提升为 const 变量（见 §3.5 共享工具函数区）
#define HAZE_ROT_SPEED          0.2     // 盘热气旋转速度系数 (相对于开普勒速度)
#define HAZE_FLOW_SPEED         0.15     // 喷流速度系数
#define HAZE_DEBUG_MASK         0       // 1 = 显示热气遮罩 Debug
#define HAZE_DEBUG_VECTOR       0       // 1 = 显示力场向量 Debug

#define HAZE_DISK_DENSITY_REF   (iBrightmut * 30.0) 
#define HAZE_JET_DENSITY_REF    (iJetBrightmut * 1.0)


//heat haze基本函数
// heat haze热浪折射，噪声准备
float HazeNoise01(vec3 p) {
    return PerlinNoise(p) * 0.5 + 0.5;
}

// 基础 3D 噪声采样
float GetBaseNoise(vec3 p)
{
    float baseScale = HAZE_SCALE * 0.4; 
    vec3 pos = p * baseScale;
    const mat3 rotNoise = mat3(
         0.80,  0.60,  0.00,
        -0.48,  0.64,  0.60,
        -0.36,  0.48, -0.80
    );
    pos = rotNoise * pos;

    float n1 = HazeNoise01(pos); 
    float n2 = HazeNoise01(pos * 3.0 + vec3(13.5, -2.4, 4.1));

    return n1 * 0.6 + n2 * 0.4; 
}

// 计算吸积盘热浪遮罩
float GetDiskHazeMask(vec3 pos_Rg, float InterRadius, float OuterRadius, float Thin, float Hopper)
{
    float r = length(pos_Rg.xz);
    float y = abs(pos_Rg.y);
    
    float GeometricThin = Thin + max(0.0, (r - 3.0) * Hopper);
    float diskThickRef = GeometricThin; 
    
    float boundaryY = max(0.2, diskThickRef * HAZE_LAYER_THICKNESS);
    
    float vMaskDisk = 1.0 - smoothstep(boundaryY * 0.5, boundaryY * 1.5, y);
    float rMaskDisk = smoothstep(InterRadius * 0.3, InterRadius * 0.8, r) * 
                      (1.0 - smoothstep(OuterRadius * HAZE_RADIAL_EXPAND * 0.75, OuterRadius * HAZE_RADIAL_EXPAND, r));
    
    return vMaskDisk * rMaskDisk;
}

// 计算喷流热浪遮罩
float GetJetHazeMask(vec3 pos_Rg, float InterRadius, float OuterRadius)
{
    float r = length(pos_Rg.xz);
    float y = abs(pos_Rg.y);
    float RhoSq = r * r;

    //核心半径估计 (Jet Core)
    float coreRadiusLimit = sqrt(2.0 * InterRadius * InterRadius + 0.03 * 0.03 * y * y);
    
    //外壳半径估计 (Jet Shell)
    // 对应 JetColor: Rho < 1.3 * InterRadius + 0.25 * Wid
    float shellRadiusLimit = 1.3 * InterRadius + 0.25 * y;
    
    // 取两者较大的作为热浪边界，并稍微膨胀以覆盖辉光
    float maxJetRadius = max(coreRadiusLimit, shellRadiusLimit) * 1.2;
    
    //垂直长度限制
    // 喷流在 OuterRadius 附近开始衰减
    float jLen = OuterRadius * 0.8;
    
    float rMaskJet = 1.0 - smoothstep(maxJetRadius * 0.8, maxJetRadius * 1.1, r);
    float hMaskJet = 1.0 - smoothstep(jLen * 0.75, jLen * 1.0, y);
    
    float startYMask = smoothstep(InterRadius * 0.5, InterRadius * 1.5, y);
    
    return rMaskJet * hMaskJet * startYMask;
}

// 包围盒检测优化
bool IsInHazeBoundingVolume(vec3 pos, float probeDist, float OuterRadius) {
    float maxR = OuterRadius * 1.2;
    float maxY = maxR; 
    float r = length(pos);
    if (r > maxR + probeDist) return false;
    return true;
}

// 计算热浪偏移力
vec3 GetHazeForce(vec3 pos_Rg, float time)
{
    float InterRadius = iInterRadiusRs;
    float OuterRadius = iOuterRadiusRs;
    float Thin = iThinRs;
    float Hopper = iHopper;
    float AccretionRate = iAccretionRate;
    //吸积盘热浪强度计算
    float dDens = HAZE_DISK_DENSITY_REF;
    float dLimitAbs = 20.0;
    float dFactorAbs = clamp((log(dDens/dLimitAbs)) / 2.302585, 0.0, 1.0);
    //喷流密度仅作参考
    float jDensRef = HAZE_JET_DENSITY_REF; 
    float dFactorRel = 1.0;
    if (jDensRef > 1e-20) dFactorRel = clamp((log(dDens/jDensRef)) / 2.302585, 0.0, 1.0);
    float diskHazeStrength = dFactorAbs * dFactorRel;

    //喷流热浪强度计算
    float jetHazeStrength = 0.0;
    float JetThreshold = 1e-2;
    
    if (AccretionRate >= JetThreshold)
    {
        float logRate = log(AccretionRate);
        float logMin  = log(JetThreshold);
        float logMax  = log(1.0);
        
        float intensity = clamp((logRate - logMin) / (logMax - logMin), 0.0, 1.0);
        jetHazeStrength = intensity;
    }

    //退出优化
    if (diskHazeStrength <= 0.001 && jetHazeStrength <= 0.001) return vec3(0.0);

    vec3 totalForce = vec3(0.0);
    float eps = 0.1;

    //循环周期计算
    float rotSpeedBase = 100.0 * HAZE_ROT_SPEED; 
    float jetSpeedBase = 50.0 * HAZE_FLOW_SPEED;
    
    // 计算内边缘角速度
    float ReferenceOmega = GetKeplerianAngularVelocity(6.0, 1.0);
    
    //内圈每旋转一定圈数，噪声完成一次淡入淡出循环
    float AdaptiveFrequency = abs(ReferenceOmega * rotSpeedBase) / (2.0 * kPi * 5.14);
    
    AdaptiveFrequency = max(AdaptiveFrequency, 0.1);

    float flowTime = time * AdaptiveFrequency;
    
    float phase1 = fract(flowTime);
    float phase2 = fract(flowTime + 0.5);
    
    float weight1 = 1.0 - abs(2.0 * phase1 - 1.0);
    float weight2 = 1.0 - abs(2.0 * phase2 - 1.0);
    
    bool doLayer1 = weight1 > 0.05;
    bool doLayer2 = weight2 > 0.05;
    
    float wTotal = (doLayer1 ? weight1 : 0.0) + (doLayer2 ? weight2 : 0.0);
    float w1_norm = (doLayer1 && wTotal > 0.0) ? (weight1 / wTotal) : 0.0;
    float w2_norm = (doLayer2 && wTotal > 0.0) ? (weight2 / wTotal) : 0.0;

    // 时间偏移
    float t_offset1 = phase1 - 0.5;
    float t_offset2 = phase2 - 0.5;

    // 垂直漂移
    float VerticalDrift1 = t_offset1 * 1.0; 
    float VerticalDrift2 = t_offset2 * 1.0;
    
    //吸积盘热浪
    if (diskHazeStrength > 0.001)
    {
        float maskDisk = GetDiskHazeMask(pos_Rg, InterRadius, OuterRadius, Thin, Hopper);
        
        if (maskDisk > 0.001)
        {
            float r_local = length(pos_Rg.xz);
            float omega = GetKeplerianAngularVelocity(r_local, 1.0);
            
            vec3 gradWorldCombined = vec3(0.0);
            float valCombined = 0.0;

            if (doLayer1)
            {
                float angle1 = omega * rotSpeedBase * t_offset1;
                float c1 = cos(angle1); float s1 = sin(angle1);
                vec3 pos1 = pos_Rg;
                pos1.x = pos_Rg.x * c1 - pos_Rg.z * s1;
                pos1.z = pos_Rg.x * s1 + pos_Rg.z * c1;
                
                float val1 = GetBaseNoise(pos1);
                float nx1 = GetBaseNoise(pos1 + vec3(eps, 0.0, 0.0));
                float ny1 = GetBaseNoise(pos1 + vec3(0.0, eps, 0.0));
                float nz1 = GetBaseNoise(pos1 + vec3(0.0, 0.0, eps));
                vec3 grad1 = vec3(nx1 - val1, ny1 - val1, nz1 - val1);
                
                vec3 gradWorld1;
                gradWorld1.x = grad1.x * c1 + grad1.z * s1;
                gradWorld1.y = grad1.y;
                gradWorld1.z = -grad1.x * s1 + grad1.z * c1;
                
                gradWorldCombined += gradWorld1 * w1_norm;
                valCombined += val1 * w1_norm;
            }
            
            if (doLayer2)
            {
                float angle2 = omega * rotSpeedBase * t_offset2;
                float c2 = cos(angle2); float s2 = sin(angle2);
                vec3 pos2 = pos_Rg;
                pos2.x = pos_Rg.x * c2 - pos_Rg.z * s2;
                pos2.z = pos_Rg.x * s2 + pos_Rg.z * c2;
                
                float val2 = GetBaseNoise(pos2);
                float nx2 = GetBaseNoise(pos2 + vec3(eps, 0.0, 0.0));
                float ny2 = GetBaseNoise(pos2 + vec3(0.0, eps, 0.0));
                float nz2 = GetBaseNoise(pos2 + vec3(0.0, 0.0, eps));
                vec3 grad2 = vec3(nx2 - val2, ny2 - val2, nz2 - val2);
                
                vec3 gradWorld2;
                gradWorld2.x = grad2.x * c2 + grad2.z * s2;
                gradWorld2.y = grad2.y;
                gradWorld2.z = -grad2.x * s2 + grad2.z * c2;
                
                gradWorldCombined += gradWorld2 * w2_norm;
                valCombined += val2 * w2_norm;
            }
            
            float cloud = max(0.0, valCombined - HAZE_DENSITY_THRESHOLD);
            cloud /= (1.0 - HAZE_DENSITY_THRESHOLD);
            cloud = pow(cloud, 1.5);
            
            totalForce += gradWorldCombined * maskDisk * cloud * diskHazeStrength;
        }
    }

    //喷流热浪
    if (jetHazeStrength > 0.001)
    {
        float maskJet = GetJetHazeMask(pos_Rg, InterRadius, OuterRadius);
        
        if (maskJet > 0.001)
        {
            float v_jet_mag = 0.9; 
            
            float dist1 = v_jet_mag * jetSpeedBase * t_offset1;
            float dist2 = v_jet_mag * jetSpeedBase * t_offset2;
            
            vec3 gradCombined = vec3(0.0);
            float valCombined = 0.0;
            
            if (doLayer1)
            {
                vec3 pos1 = pos_Rg;
                pos1.y -= sign(pos_Rg.y) * dist1;
                float val1 = GetBaseNoise(pos1);
                float nx1 = GetBaseNoise(pos1 + vec3(eps, 0.0, 0.0));
                float ny1 = GetBaseNoise(pos1 + vec3(0.0, eps, 0.0));
                float nz1 = GetBaseNoise(pos1 + vec3(0.0, 0.0, eps));
                vec3 grad1 = vec3(nx1 - val1, ny1 - val1, nz1 - val1);
                gradCombined += grad1 * w1_norm;
                valCombined += val1 * w1_norm;
            }
            
            if (doLayer2)
            {
                vec3 pos2 = pos_Rg;
                pos2.y -= sign(pos_Rg.y) * dist2;
                float val2 = GetBaseNoise(pos2);
                float nx2 = GetBaseNoise(pos2 + vec3(eps, 0.0, 0.0));
                float ny2 = GetBaseNoise(pos2 + vec3(0.0, eps, 0.0));
                float nz2 = GetBaseNoise(pos2 + vec3(0.0, 0.0, eps));
                vec3 grad2 = vec3(nx2 - val2, ny2 - val2, nz2 - val2);
                gradCombined += grad2 * w2_norm;
                valCombined += val2 * w2_norm;
            }
            
            float cloud = max(0.0, valCombined - 0.3-0.7*HAZE_DENSITY_THRESHOLD); // 喷流的heat haze相比吸积盘需要更多空隙，不然看着怪
            cloud /= clamp((1.0 - 0.3-0.7*HAZE_DENSITY_THRESHOLD),0.0,1.0);
            cloud = pow(cloud, 1.5);
            
            totalForce += gradCombined * maskJet * cloud * jetHazeStrength;
        }
    }

    return totalForce;
}



// 吸积盘物态参量(开尔文量级):DiskArgument = kPhysicsFactor/M·(μ/η)·ṁ,η 由顺行 ISCO 给出;
// 峰值温度 = (DiskArgument×0.05665278)^0.25(与 NPGS TraceRay 主循环同源公式)
float ComputeDiskArgument()
{
    float SpinClamp = clamp(iSpin, -0.99, 0.99);
    float A2 = SpinClamp * SpinClamp;
    float AbsA = abs(SpinClamp);
    float CommonTerm = pow(1.0 - A2, 1.0/3.0);
    float Z1 = 1.0 + CommonTerm * (pow(1.0 + AbsA, 1.0/3.0) + pow(1.0 - AbsA, 1.0/3.0));
    float Z2 = sqrt(3.0 * A2 + Z1 * Z1);
    float RootTerm = sqrt(max(0.0, (3.0 - Z1) * (3.0 + Z1 + 2.0 * Z2)));
    float RmsM = 3.0 + Z2 - (sign(SpinClamp) * RootTerm);
    float AccretionEffective = sqrt(max(0.001, 1.0 - (2.0 / 3.0) / RmsM));
    const float kPhysicsFactor = 1.52491e30;
    return kPhysicsFactor / iBlackHoleMassSol * (iMu / AccretionEffective) * (iAccretionRate);
}

vec4 DiskColor(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
               vec4 iP_cov, vec4 lastiP_cov, float iE_obs,
               bool isoutgoing,
               float ThetaInShell,
               inout float RayMarchPhase,
               vec2 WP_CamX, vec2 WP_CamY, inout vec2 StokesQU
               ) 
{
    float InterRadius = iInterRadiusRs;
    float OuterRadius = iOuterRadiusRs;
    float Thin = iThinRs;
    float Hopper = iHopper;
    float Brightmut = iBrightmut;
    float Darkmut = iDarkmut;
    float Reddening = iReddening;
    float Saturation = iSaturation;
    float BlackbodyIntensityExponent = iBlackbodyIntensityExponent;
    float RedShiftColorExponent = iRedShiftColorExponent;
    float RedShiftIntensityExponent = iRedShiftIntensityExponent;
    float ShiftMax = 1.0;
    float DiskTemperatureArgument = ComputeDiskArgument();          // NPGS 传参:≈1e21 量级(μ/η·ṁ 换算)
    float PeakTemperature = pow(DiskTemperatureArgument * 0.05665278, 0.25);  // ≈1e5 K,勿写死字面量
    vec4 CurrentResult = BaseColor;

    float MaxDiskHalfHeight = Thin + max(0.0, Hopper * OuterRadius) + 2.0; 
    if (LastRayPos.y > MaxDiskHalfHeight && RayPos.y > MaxDiskHalfHeight) return BaseColor;
    if (LastRayPos.y < -MaxDiskHalfHeight && RayPos.y < -MaxDiskHalfHeight) return BaseColor;

    vec2 P0 = LastRayPos.xz;
    vec2 P1 = RayPos.xz;
    vec2 V  = P1 - P0;
    float LenSq = dot(V, V);
    float t_closest = (LenSq > 1e-8) ? clamp(-dot(P0, V) / LenSq, 0.0, 1.0) : 0.0;
    vec2 ClosestPoint = P0 + V * t_closest;
    if (dot(ClosestPoint, ClosestPoint) > (OuterRadius * 1.1) * (OuterRadius * 1.1)) return BaseColor;

    vec3 StartPos = LastRayPos.xyz; 
    vec3 EndPos   = RayPos.xyz;
    vec3 ChordDelta = EndPos - StartPos;
    vec3 ChordDir = length(ChordDelta) > 1e-8 ? normalize(ChordDelta) : vec3(0.0, 1.0, 0.0);

    // --- 【新增：利用局域度规计算空间固有距离（Proper Distance）】 ---
    vec3 MidPos = 0.5 * (StartPos + EndPos);
    KerrGeometry geo_mid;
    // 使用原始输入系下的 Geometry 评估中点度规项
    ComputeGeometryScalars(MidPos, 1.0, 1.0, isoutgoing, geo_mid);
    float l_dot_dx = dot(geo_mid.l_down.xyz, ChordDelta);
    // dl = sqrt( |dx|^2 + f * (l . dx)^2 )
    float proper_dist = sqrt(max(1e-9, dot(ChordDelta, ChordDelta) + geo_mid.f * l_dot_dx * l_dot_dx));

    float StartTimeLag = LastRayPos.w;
    float EndTimeLag   = RayPos.w;

    float R_Start = KerrSchildRadius(StartPos, 1.0);
    float R_End   = KerrSchildRadius(RayPos.xyz, 1.0);
    if (max(R_Start, R_End) < InterRadius * 0.9) return BaseColor;

    // 将原本的 StepLength 依赖彻底替换为计算出的物理固有距离
    float TotalDist = proper_dist; 
    float TraveledDist = 0.0;
    
    int SafetyLoopCount = 0;
    const int MaxLoops = 114514; 

    while (TraveledDist < TotalDist && SafetyLoopCount < MaxLoops)
    {
        if (CurrentResult.a > 0.99) break;
        SafetyLoopCount++;

        vec3 CurrentPos = mix(StartPos, EndPos, clamp(TraveledDist / max(1e-9, TotalDist), 0.0, 1.0));
        float DistanceToBlackHole = length(CurrentPos); 
        
        float SmallStepBoundary = max(OuterRadius, 12.0);
        float StepSize = 1.0; 
        
        StepSize *= 0.15 + 0.25 * min(max(0.0, 0.5 * (0.5 * DistanceToBlackHole / max(10.0 , SmallStepBoundary) - 1.0)), 1.0);
        if ((DistanceToBlackHole) >= 2.0 * SmallStepBoundary) StepSize *= DistanceToBlackHole;
        else if ((DistanceToBlackHole) >= 1.0 * SmallStepBoundary) StepSize *= ((1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0)) * (2.0 * SmallStepBoundary - DistanceToBlackHole) + DistanceToBlackHole * (DistanceToBlackHole - SmallStepBoundary)) / SmallStepBoundary;
        else StepSize *= min(1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0), DistanceToBlackHole);
        
        StepSize = max(0.01, StepSize); 

        float DistToNextSample = RayMarchPhase * StepSize;
        float NextTarget = min(TotalDist, TraveledDist + DistToNextSample);

        vec3 PosPrev = mix(StartPos, EndPos, clamp(TraveledDist / max(1e-9, TotalDist), 0.0, 1.0));
        vec3 PosNext = mix(StartPos, EndPos, clamp(NextTarget / max(1e-9, TotalDist), 0.0, 1.0));

        bool crossed = (PosPrev.y * PosNext.y < 0.0);
        bool shouldSample = false;
        vec3 SamplePos = PosNext;
        crossed = false;

        if (crossed)
        {
            float t_cross = abs(PosPrev.y) / max(1e-9, abs(PosPrev.y) + abs(PosNext.y));
            vec3 CPoint = mix(PosPrev, PosNext, t_cross);
            
            SamplePos = CPoint + min(Thin, length(CPoint - PosPrev)) * ChordDir * (-1.0 + 2.0 * RandomStep(10000.0 * (CPoint.zx / OuterRadius), fract(iTime * 1.0 + 0.5)));
            shouldSample = true;
            
            RayMarchPhase = 1.0;
            TraveledDist = NextTarget; 
        }
        else
        {
            if (NextTarget < TotalDist)
            {
                SamplePos = PosNext;
                shouldSample = true;
                RayMarchPhase = 1.0;
                TraveledDist = NextTarget;
            }
            else
            {
                float DistanceTraveled = TotalDist - TraveledDist;
                RayMarchPhase -= DistanceTraveled / StepSize;
                if (RayMarchPhase < 0.0) RayMarchPhase = 0.0;
                TraveledDist = TotalDist;
            }
        }

        if (shouldSample)
        {
            float TimeInterpolant = min(1.0, TraveledDist / max(1e-9, TotalDist));
            float CurrentRayTimeLag = mix(StartTimeLag, EndTimeLag, TimeInterpolant);
            
            vec4 Sample_X = vec4(SamplePos, CurrentRayTimeLag);
            vec4 Sample_P_cov = mix(lastiP_cov, iP_cov, TimeInterpolant);
            
            if (isoutgoing) {
                transformKerrSchild_YSpin(Sample_X, 1.0, Sample_P_cov, true);
            }
            
            SamplePos = Sample_X.xyz;
            float EmissionTime = iBlackHoleTime + Sample_X.w;

            float PosR = KerrSchildRadius(SamplePos, 1.0);
            float PosY = SamplePos.y;
            
            float GeometricThin = Thin + max(0.0, (length(SamplePos.xz) - 3.0) * Hopper);
            float InterCloudEffectiveRadius = (PosR - InterRadius) / min(OuterRadius - InterRadius, 12.0);
            float InnerCloudBound = max(GeometricThin, Thin * 1.0) * max(0.0, 1.0 - 5.0 * pow(InterCloudEffectiveRadius, 2.0));
            float UnionBound = max(GeometricThin * 1.5, max(0.0, InnerCloudBound));

            if (abs(PosY) < UnionBound && PosR < OuterRadius && PosR > InterRadius)
            {
                 KerrGeometry geo_emit;
                 ComputeGeometryScalars(SamplePos, 1.0, 1.0, false, geo_emit);
                 vec4 Sample_P_up = RaiseIndex(Sample_P_cov, geo_emit);
                 vec3 local_Dir = normalize(Sample_P_up.xyz);

                 float NoiseLevel = max(0.0, 2.0 - 0.6 * GeometricThin);
                 float x = (PosR - InterRadius) / max(1e-6, OuterRadius - InterRadius);
                 float a_param = max(1.0, (OuterRadius - InterRadius) / 10.0);
                 float EffectiveRadius = (-1.0 + sqrt(max(0.0, 1.0 + 4.0 * a_param * a_param * x - 4.0 * x * a_param))) / (2.0 * a_param - 2.0);
                 if(a_param == 1.0) EffectiveRadius = x;
                 
                 float DenAndThiFactor = Shape(EffectiveRadius, 0.9, 1.5);

                 float RotPosR_ForThick = PosR + 0.25 / 3.0 * EmissionTime;
                 float PosLogTheta_ForThick = Vec2ToTheta(SamplePos.zx, vec2(cos(-2.0 * log(max(1e-6, PosR))), sin(-2.0 * log(max(1e-6, PosR)))));
                 float ThickNoise = GenerateAccretionDiskNoise(vec3(1.5 * PosLogTheta_ForThick, RotPosR_ForThick, 0.0), -0.7 + NoiseLevel, 1.3 + NoiseLevel, 80.0);
                 float PerturbedThickness = max(1e-6, GeometricThin * DenAndThiFactor * (0.4 + 0.6 * clamp(GeometricThin - 0.5, 0.0, 2.5) / 2.5 + (1.0 - (0.4 + 0.6 * clamp(GeometricThin - 0.5, 0.0, 2.5) / 2.5)) * SoftSaturate(ThickNoise)));

                 if ((abs(PosY) < PerturbedThickness) || (abs(PosY) < InnerCloudBound))
                 {
                     float AngularVelocity = GetKeplerianAngularVelocity(max(InterRadius, PosR), 1.0);
                     
                     float u = sqrt(max(1e-6, PosR));
                     float k_cubed = PhysicalSpinA * 0.70710678;
                     float SpiralTheta;
                     if (abs(k_cubed) < 0.001 * u * u * u) {
                         float inv_u = 1.0 / u; float eps3 = k_cubed * pow(inv_u, 3.0);
                         SpiralTheta = -16.9705627 * inv_u * (1.0 - 0.25 * eps3 + 0.142857 * eps3 * eps3);
                     } else {
                         float k = sign(k_cubed) * pow(abs(k_cubed), 0.33333333);
                         float logTerm = (PosR - k*u + k*k) / max(1e-9, pow(u+k, 2.0));
                         SpiralTheta = (5.6568542 / k) * (0.5 * log(max(1e-9, logTerm)) + 1.7320508 * (atan(2.0*u - k, 1.7320508 * k) - 1.5707963));
                     }
                     float PosTheta = Vec2ToTheta(SamplePos.zx, vec2(cos(-SpiralTheta), sin(-SpiralTheta)));
                     float PosLogarithmicTheta = Vec2ToTheta(SamplePos.zx, vec2(cos(-2.0 * log(max(1e-6, PosR))), sin(-2.0 * log(max(1e-6, PosR)))));
                     
                     float inv_r = 1.0 / max(1e-6, PosR);
                     float inv_r2 = inv_r * inv_r;
                     float V_pot = inv_r - (PhysicalQ * PhysicalQ) * inv_r2;
                     
                     float g_tt = -(1.0 - V_pot);
                     float g_tphi = -PhysicalSpinA * V_pot; 
                     float g_phiphi = PosR * PosR + PhysicalSpinA * PhysicalSpinA + PhysicalSpinA * PhysicalSpinA * V_pot;
                     float norm_metric = g_tt + 2.0 * AngularVelocity * g_tphi + AngularVelocity * AngularVelocity * g_phiphi;
                     
                     float min_norm = -0.01; 
                     float u_t = inversesqrt(max(abs(min_norm), -norm_metric));
                     
                     float P_phi = - SamplePos.x * Sample_P_cov.z + SamplePos.z * Sample_P_cov.x;
                     float E_emit = u_t * (iE_obs - AngularVelocity * P_phi);
                     float FreqRatio = 1.0 / max(1e-6, E_emit);

                     float DiskTemperature = pow(DiskTemperatureArgument * pow(1.0 / max(1e-6, PosR), 3.0) * max(1.0 - sqrt(InterRadius / max(1e-6, PosR)), 0.000001), 0.25);
                     float VisionTemperature = DiskTemperature * pow(FreqRatio, RedShiftColorExponent); 
                     float BrightWithoutRedshift = 0.05 * min(OuterRadius / (1000.0), 1000.0 / OuterRadius) + 0.55 / exp(5.0 * EffectiveRadius) * mix(0.2 + 0.8 * abs(local_Dir.y), 1.0, clamp(GeometricThin - 0.8, 0.2, 1.0)); 
                     BrightWithoutRedshift *= pow(DiskTemperature / PeakTemperature, BlackbodyIntensityExponent); 
                     
                     float RotPosR = PosR + 0.25 / 3.0 * EmissionTime;
                     float Density = DenAndThiFactor;
                     vec4 SampleColor = vec4(0.0);

                     if (abs(PosY) < PerturbedThickness)
                     {
                         float Levelmut = 0.91 * log(1.0 + (0.06 / 0.91 * max(0.0, min(1000.0, PosR) - 10.0)));
                         float Conmut = 80.0 * log(1.0 + (0.1 * 0.06 * max(0.0, min(1000000.0, PosR) - 10.0)));
                         
                         SampleColor = vec4(GenerateAccretionDiskNoise(vec3(0.1 * RotPosR, 0.1 * PosY, 0.02 * pow(OuterRadius, 0.7) * PosTheta), NoiseLevel + 2.0 - Levelmut, NoiseLevel + 4.0 - Levelmut, 80.0 - Conmut)); 
                         
                         if(PosTheta + kPi < 0.1 * kPi) {
                             SampleColor *= (PosTheta + kPi) / (0.1 * kPi);
                             SampleColor += (1.0 - ((PosTheta + kPi) / (0.1 * kPi))) * vec4(GenerateAccretionDiskNoise(vec3(0.1 * RotPosR, 0.1 * PosY, 0.02 * pow(OuterRadius, 0.7) * (PosTheta + 2.0 * kPi)), NoiseLevel + 2.0 - Levelmut, NoiseLevel + 4.0 - Levelmut, 80.0 - Conmut));
                         }
                         
                         if(PosR > max(0.15379 * OuterRadius, 0.15379 * 64.0)) {
                             float TimeShiftedRadiusTerm = PosR * (4.65114e-6) - 0.1 / 3.0 * EmissionTime;
                             float Spir = (GenerateAccretionDiskNoise(vec3(0.1 * (TimeShiftedRadiusTerm - 0.08 * OuterRadius * PosLogarithmicTheta), 0.1 * PosY, 0.02 * pow(OuterRadius, 0.7) * PosLogarithmicTheta), NoiseLevel + 2.0 - Levelmut, NoiseLevel + 3.0 - Levelmut, 80.0 - Conmut)); 
                             if(PosLogarithmicTheta + kPi < 0.1 * kPi) {
                                 Spir *= (PosLogarithmicTheta + kPi) / (0.1 * kPi);
                                 Spir += (1.0 - ((PosLogarithmicTheta + kPi) / (0.1 * kPi))) * (GenerateAccretionDiskNoise(vec3(0.1 * (TimeShiftedRadiusTerm - 0.08 * OuterRadius * (PosLogarithmicTheta + 2.0 * kPi)), 0.1 * PosY, 0.02 * pow(OuterRadius, 0.7) * (PosLogarithmicTheta + 2.0 * kPi)), NoiseLevel + 2.0 - Levelmut, NoiseLevel + 3.0 - Levelmut, 80.0 - Conmut));
                             }
                             SampleColor *= (mix(1.0, clamp(0.7 * Spir * 1.5 - 0.5, 0.0, 3.0), 0.5 + 0.5 * max(-1.0, 1.0 - exp(-1.5 * 0.1 * (100.0 * PosR / max(OuterRadius, 64.0) - 20.0)))));
                         }

                         float VerticalMixFactor = max(0.0, (1.0 - abs(PosY) / PerturbedThickness)); 
                         Density *= 0.7 * VerticalMixFactor * Density;
                         SampleColor.xyz *= Density * 1.4;
                         SampleColor.a *= (Density) * (Density) / 0.3;
                         
                         float RelHeight = clamp(abs(PosY) / PerturbedThickness, 0.0, 1.0);
                         SampleColor.xyz *= max(0.0, (0.2 + 2.0 * sqrt(max(0.0, RelHeight * RelHeight + 0.001))));
                     }
        
                     SampleColor.xyz *= 1.0 + clamp(iPhotonRingBoost, 0.0, 10.0) * clamp(0.3 * ThetaInShell - 0.1, 0.0, 1.0);
                     VisionTemperature *= 1.0 + clamp(iPhotonRingColorTempBoost, 0.0, 10.0) * clamp(0.3 * ThetaInShell - 0.1, 0.0, 1.0);
                     
                     float InnerAngVel = GetKeplerianAngularVelocity(max(3.0, InterRadius), 1.0);
                     float InnerCloudTimePhase = kPi / (kPi / max(1e-6, InnerAngVel)) * EmissionTime; 
                     float InnerRotArg = 0.666666 * InnerCloudTimePhase;
                     float PosThetaForInnerCloud = Vec2ToTheta(SamplePos.zx, vec2(cos(InnerRotArg), sin(InnerRotArg)));

                     if (abs(PosY) < InnerCloudBound) 
                     {
                         float DustIntensity = max(1.0 - pow(PosY / (GeometricThin * max(1.0 - 5.0 * pow(InterCloudEffectiveRadius, 2.0), 0.0001)), 2.0), 0.0);
                         if (DustIntensity > 0.0) {
                            float DustNoise = GenerateAccretionDiskNoise(vec3(1.5 * fract((1.5 * PosThetaForInnerCloud + InnerCloudTimePhase) / 2.0 / kPi) * 2.0 * kPi, PosR, PosY), 0.0, 6.0, 80.0);
                            float DustVal = DustIntensity * DustNoise;
                            SampleColor += 0.02 * vec4(vec3(DustVal), 0.2 * DustVal) * sqrt(max(0.0, 1.0001 - local_Dir.y * local_Dir.y));
                         }
                     }

                     SampleColor.xyz *= BrightWithoutRedshift * KelvinToRgb(VisionTemperature); 
                     SampleColor.xyz *= min(pow(FreqRatio, RedShiftIntensityExponent), ShiftMax); 
                     SampleColor.xyz *= min(1.0, 1.3 * (OuterRadius - PosR) / (OuterRadius - InterRadius)); 
                     SampleColor.a   *= 0.125;
                     
                     // 1. 计算 DilutionOuterRadius 并替换 BoostFactor 中的 OuterRadius
                     float DilutionOuterRadius = mix(min(OuterRadius, 25.0), OuterRadius, smoothstep(6.0, max(0.05 * OuterRadius, 12.0), PosR));
                     vec4 BoostFactor = max(
                        mix(vec4(5.0 / (max(Thin, 0.2) + (0.0 + Hopper * 0.5) * DilutionOuterRadius)), vec4(vec3(0.3 + 0.7 * 5.0 / (Thin + (0.0 + Hopper * 0.5) * DilutionOuterRadius)), 1.0), 0.0),
                        mix(vec4(100.0 / DilutionOuterRadius), vec4(vec3(0.3 + 0.7 * 100.0 / DilutionOuterRadius), 1.0), exp(-pow(20.0 * PosR / DilutionOuterRadius, 2.0)))
                     );
                     SampleColor *= BoostFactor;
                     
                     // 2. 计算内圈增亮参数
                     float InnerBrightenFac = mix(3.0, 2.0, clamp((OuterRadius - 50.0) / 50.0, 0.0, 1.0));
                     float InnerBrightenRatio = 1.0 - clamp(6.0 * (PosR - InterRadius) / (OuterRadius - InterRadius), 0.0, 1.0);
                     InnerBrightenRatio *= InnerBrightenRatio;
                     
                     // 3. 分层应用颜色与透明度的微调 (保留了原有的分行清晰度)
                     SampleColor.xyz *= mix(1.0, max(1.0, abs(local_Dir.y) / 0.2), clamp(0.3 - 0.6 * (PerturbedThickness / max(1e-6, Density) - 1.0), 0.0, 0.3));
                     SampleColor.xyz *= 1.0 + 1.2 * max(0.0, max(0.0, min(1.0, 3.0 - 2.0 * Thin)) * min(0.5, 1.0 - 5.0 * Hopper));
                     SampleColor.xyz *= Brightmut * (1.0 + InnerBrightenFac * InnerBrightenRatio);
                     SampleColor.a   *= Darkmut * (1.0 + (1.0 + InnerBrightenFac) * InnerBrightenRatio);
                     
                     if (E_emit < 0.0) 
                     {
                         float cMax = max(max(SampleColor.r, SampleColor.g), SampleColor.b);
                         float cMin = min(min(SampleColor.r, SampleColor.g), SampleColor.b);
                         SampleColor.rgb = vec3(cMax + cMin) - SampleColor.rgb;
                         if(iWhitehole==0) SampleColor.rgba=vec4(0.0);
                     }
                     // 环境光弥散项：全天空辐照（gDiskAmbientSky 已含相位折扣）× 本步不透明度
                     // 随 StepSize 积分。冷暗盘区发射趋零而密度仍在 → 显形为均匀背景色补底，
                     // 与背光项（main 合成处的 iDiskScatter）互补；E_emit<0 清零样本 alpha=0 无贡献
                     SampleColor.rgb += iDiskAmbient * gDiskAmbientSky * SampleColor.a;

                     vec4 StepColor = SampleColor * StepSize;

                     // =========================================================
                     // 偏振积累（NPGS 逐行移植）：以盘内磁场方向 B 与流体四速 u 构造
                     // 发射偏振矢量 f，投影到相机屏幕基底（WP 常数）后累积 Stokes Q/U。
                     // 最终由 TraceRay 尾部按 iPolarization（1=色相显示 / 2=偏振片）消费。
                     // =========================================================
                     if (iPolarization != 0) {
                         float chi = -0.7;
                         float cosChi = cos(chi);
                         float sinChi = sin(chi);

                         vec4 B_tor = vec4(-SamplePos.z, 0.0, SamplePos.x, 0.0);
                         vec4 B_rad = vec4(SamplePos.x, SamplePos.y, SamplePos.z, 0.0);
                         vec4 B_up = normalize(B_tor) * cosChi + normalize(B_rad) * sinChi;
                         B_up.w = 0.0;

                         vec4 u_up_fluid = vec4(AngularVelocity * (-SamplePos.z), 0.0,
                                                AngularVelocity * SamplePos.x, 1.0) * u_t;

                         vec4 p_up = Sample_P_up;

                         vec4 f_down;
                         f_down.x =  det3(u_up_fluid.yzw, p_up.yzw, B_up.yzw);
                         f_down.y = -det3(u_up_fluid.xzw, p_up.xzw, B_up.xzw);
                         f_down.z =  det3(u_up_fluid.xyw, p_up.xyw, B_up.xyw);
                         f_down.w = -det3(u_up_fluid.xyz, p_up.xyz, B_up.xyz);

                         float f_norm = sqrt(max(1e-12, abs(dot(RaiseIndex(f_down, geo_emit), f_down))));
                         f_down /= f_norm;

                         vec4 Emit_X = vec4(SamplePos, EmissionTime);
                         vec2 WP_emit = GetWalkerPenrose(Emit_X, Sample_P_cov, f_down,
                                                         PhysicalSpinA, PhysicalQ, PosR);

                         vec2 ScreenAmps = SolvePolarization(WP_emit, WP_CamX, WP_CamY);

                         float weight = (SampleColor.r + SampleColor.g + SampleColor.b) * StepSize
                                      * pow(1.0 - CurrentResult.a, 1.0);
                         StokesQU.x += (ScreenAmps.x * ScreenAmps.x - ScreenAmps.y * ScreenAmps.y) * weight;
                         StokesQU.y += (2.0 * ScreenAmps.x * ScreenAmps.y) * weight;
                     }

                     float aR = 1.0 + Reddening * (1.0 - 1.0);
                     float aG = 1.0 + Reddening * (3.0 - 1.0);
                     float aB = 1.0 + Reddening * (6.0 - 1.0);
                     
                     float Sum_rgb = (StepColor.r + StepColor.g + StepColor.b) * pow(1.0 - CurrentResult.a, aG);
                     float Denominator = StepColor.r * pow(1.0 - CurrentResult.a, aR) + StepColor.g * pow(1.0 - CurrentResult.a, aG) + StepColor.b * pow(1.0 - CurrentResult.a, aB);
                     
                     float r001 = 0.0; float g001 = 0.0; float b001 = 0.0;
                     if (Denominator > 0.000001)
                     {
                         r001 = Sum_rgb * StepColor.r * pow(1.0 - CurrentResult.a, aR) / Denominator;
                         g001 = Sum_rgb * StepColor.g * pow(1.0 - CurrentResult.a, aG) / Denominator;
                         b001 = Sum_rgb * StepColor.b * pow(1.0 - CurrentResult.a, aB) / Denominator;
                         
                         r001 *= pow(3.0 * r001 / (r001 + g001 + b001), Saturation);
                         g001 *= pow(3.0 * g001 / (r001 + g001 + b001), Saturation);
                         b001 *= pow(3.0 * b001 / (r001 + g001 + b001), Saturation);
                     }
                     
                     CurrentResult.r += r001;
                     CurrentResult.g += g001;
                     CurrentResult.b += b001;
                     CurrentResult.a += StepColor.a * (1.0 - CurrentResult.a);
                 }
            }
        }
    }
    return CurrentResult;
}


vec4 JetColor(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
              vec4 iP_cov, vec4 lastiP_cov, float iE_obs,
              bool isoutgoing,
              inout float RayMarchPhase,
              vec2 WP_CamX, vec2 WP_CamY, inout vec2 StokesQU
              ) 
{
    float InterRadius = iInterRadiusRs;
    float OuterRadius = iOuterRadiusRs;
    float AccretionRate = iAccretionRate;
    float JetRedShiftIntensityExponent = iJetRedShiftIntensityExponent;
    float JetBrightmut = iJetBrightmut;
    float JetReddening = iReddening;
    float JetSaturation = iJetSaturation;
    float JetShiftMax = iJetShiftMax;
    vec4 CurrentResult = BaseColor;
    vec3 StartPos = LastRayPos.xyz; 
    vec3 EndPos   = RayPos.xyz;
    
    if (any(isnan(StartPos)) || any(isinf(StartPos))) return BaseColor;

    // --- 1. 计算局域度规并提取固有空间距离 ---
    vec3 ChordDelta = EndPos - StartPos;
    vec3 MidPos = 0.5 * (StartPos + EndPos);
    KerrGeometry geo_mid;
    ComputeGeometryScalars(MidPos, 1.0, 1.0, isoutgoing, geo_mid);
    
    float l_dot_dx = dot(geo_mid.l_down.xyz, ChordDelta);
    float proper_dist = sqrt(max(1e-9, dot(ChordDelta, ChordDelta) + geo_mid.f * l_dot_dx * l_dot_dx));

    float StartTimeLag = LastRayPos.w;
    float EndTimeLag   = RayPos.w;

    float TotalDist = proper_dist; // 彻底替换旧的 StepLength
    float TraveledDist = 0.0;
    
    float R_Start = length(StartPos.xz);
    float R_End   = length(RayPos.xz); 
    float MaxR_XZ = max(R_Start, R_End);
    float MaxY    = max(abs(StartPos.y), abs(RayPos.y));
    
    if (MaxR_XZ > OuterRadius * 1.5 && MaxY < OuterRadius) return BaseColor;

    int SafetyLoopCount = 0;
    const int MaxLoops = 114514; 
    
    while (TraveledDist < TotalDist && SafetyLoopCount < MaxLoops)
    {
        if (CurrentResult.a > 0.99) break;
        SafetyLoopCount++;

        // 利用插值比例计算当前检查点位置
        vec3 CurrentPos = mix(StartPos, EndPos, clamp(TraveledDist / max(1e-9, TotalDist), 0.0, 1.0));
        float DistanceToBlackHole = length(CurrentPos); 
        
        float SmallStepBoundary = max(OuterRadius, 12.0);
        float StepSize = 1.0; 
        
        StepSize *= 0.15 + 0.25 * min(max(0.0, 0.5 * (0.5 * DistanceToBlackHole / max(10.0 , SmallStepBoundary) - 1.0)), 1.0);
        if ((DistanceToBlackHole) >= 2.0 * SmallStepBoundary) StepSize *= DistanceToBlackHole;
        else if ((DistanceToBlackHole) >= 1.0 * SmallStepBoundary) StepSize *= ((1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0)) * (2.0 * SmallStepBoundary - DistanceToBlackHole) + DistanceToBlackHole * (DistanceToBlackHole - SmallStepBoundary)) / SmallStepBoundary;
        else StepSize *= min(1.0 + 0.25 * max(DistanceToBlackHole - 12.0, 0.0), DistanceToBlackHole);
        
        StepSize = max(0.01, StepSize); 

        float DistToNextSample = RayMarchPhase * StepSize;
        float NextTarget = min(TotalDist, TraveledDist + DistToNextSample);

        bool shouldSample = false;
        if (NextTarget < TotalDist)
        {
            shouldSample = true;
            RayMarchPhase = 1.0;
            TraveledDist = NextTarget;
        }
        else
        {
            float DistanceTraveled = TotalDist - TraveledDist;
            RayMarchPhase -= DistanceTraveled / StepSize;
            if(RayMarchPhase < 0.0) RayMarchPhase = 0.0;
            TraveledDist = TotalDist;
        }

        if (shouldSample)
        {
            // --- 2. 在原始坐标系下进行物理量插值 ---
            float TimeInterpolant = min(1.0, TraveledDist / max(1e-9, TotalDist));
            vec3 OrigSamplePos = mix(StartPos, EndPos, TimeInterpolant);
            float CurrentRayTimeLag = mix(StartTimeLag, EndTimeLag, TimeInterpolant);
            
            vec4 Sample_X = vec4(OrigSamplePos, CurrentRayTimeLag);
            vec4 Sample_P_cov = mix(lastiP_cov, iP_cov, TimeInterpolant);

            // --- 3. 统一转换到 Ingoing 坐标系 ---
            if (isoutgoing) {
                transformKerrSchild_YSpin(Sample_X, 1.0, Sample_P_cov, true);
            }
            
            vec3 SamplePos = Sample_X.xyz;
            float EmissionTime = iBlackHoleTime + Sample_X.w;

            float PosR = KerrSchildRadius(SamplePos, 1.0);
            float PosY = SamplePos.y;
            float RhoSq = dot(SamplePos.xz, SamplePos.xz);
            float Rho = sqrt(RhoSq);
            
            vec4 AccumColor = vec4(0.0);
            bool InJet = false;

            // 内部喷流核心
            if (RhoSq < 2.0 * InterRadius * InterRadius + 0.03 * 0.03 * PosY * PosY && PosR < sqrt(2.0) * OuterRadius)
            {
                InJet = true;
                float ShapeVal = 1.0 / sqrt(max(1e-9, InterRadius * InterRadius + 0.02 * 0.02 * PosY * PosY));
                float noiseInput = 0.3 * (EmissionTime - 1.0 / 0.8 * abs(abs(PosY) + 100.0 * (RhoSq / max(0.1, PosR)))) / max(1e-6, (OuterRadius / 100.0)) / (1.0 / 0.8);
                float a = mix(0.7 + 0.3 * PerlinNoise1D(noiseInput), 1.0, exp(-0.01 * 0.01 * PosY * PosY));
                
                vec4 Col = vec4(1.0, 1.0, 1.0, 0.5) * max(0.0, 1.0 - 5.0 * ShapeVal * abs(1.0 - pow(Rho * ShapeVal, 2.0))) * ShapeVal;
                Col *= a;
                Col *= max(0.0, 1.0 - 1.0 * exp(-0.0001 * PosY / max(1e-6, InterRadius) * PosY / max(1e-6, InterRadius)));
                Col *= exp(-4.0 / (2.0) * PosR / max(1e-6, OuterRadius) * PosR / max(1e-6, OuterRadius));
                Col *= 0.5;
                
                AccumColor += Col * StepSize; 
            }

            // 外围扭转喷流壳
            float Wid = abs(PosY);
            if (Rho < 1.3 * InterRadius + 0.25 * Wid && Rho > 0.7 * InterRadius + 0.15 * Wid && PosR < 30.0 * InterRadius)
            {
                InJet = true;
                float InnerTheta = 2.0 * GetKeplerianAngularVelocity(InterRadius, 1.0) * (EmissionTime - 1.0 / 0.8 * abs(PosY));
                float ShapeVal = 1.0 / max(1e-9, (InterRadius + 0.2 * Wid));
                
                float Twist = 0.2 * (1.1 - exp(-0.1 * 0.1 * PosY * PosY)) * (PerlinNoise1D(0.35 * (EmissionTime - 1.0 / 0.8 * abs(PosY)) / (1.0 / 0.8)) - 0.5);
                vec2 TwistedPos = SamplePos.xz + Twist * vec2(cos(0.666666 * InnerTheta), -sin(0.666666 * InnerTheta));
                
                vec4 Col = vec4(1.0, 1.0, 1.0, 0.5) * max(0.0, 1.0 - 2.0 * abs(1.0 - pow(length(TwistedPos) * ShapeVal, 2.0))) * ShapeVal;
                Col *= 1.0 - exp(-PosY / max(1e-6, InterRadius) * PosY / max(1e-6, InterRadius));
                Col *= exp(-0.005 * PosY / max(1e-6, InterRadius) * PosY / max(1e-6, InterRadius));
                Col *= 0.5;
                
                AccumColor += Col * StepSize; 
            }

            if (InJet)
            {
                // --- 4. 使用转换到 Ingoing 系后的物理计算 ---
                KerrGeometry geo_sample;
                // 注意：isoutgoing 必须填 false，因为我们已经在上文将其统一转换为了 Ingoing 坐标
                ComputeGeometryScalars(SamplePos, 1.0, 1.0, false, geo_sample);

                
                // 物理喷流速度 0.8c, 洛伦兹因子 Gamma = 1/sqrt(1 - 0.8^2) = 1.6666667
                float v_jet = 0.8;
                float Gamma = 1.6666667; 
                vec3 U_spatial = vec3(0.0, sign(PosY) * Gamma * v_jet, 0.0);
                
                // 根据归一化条件 g_uv U^u U^v = -1 求解时间分量 U^t
                float l_dot_u_sp = dot(geo_sample.l_down.xyz, U_spatial);
                float U_sp_sq = dot(U_spatial, U_spatial);
                
                float A = -1.0 + geo_sample.f;
                float B = 2.0 * geo_sample.f * l_dot_u_sp;
                float C = U_sp_sq + geo_sample.f * l_dot_u_sp * l_dot_u_sp + 1.0; 
                
                float Det = B * B - 4.0 * A * C;
                
                // 光锥约束保护
                if (Det < 0.0) {
                    if (A < 0.0) {
                        U_spatial *= 0.5; 
                    } else {
                        U_spatial = -1.5 * geo_sample.grad_r; 
                    }
                    l_dot_u_sp = dot(geo_sample.l_down.xyz, U_spatial);
                    U_sp_sq = dot(U_spatial, U_spatial);
                    C = U_sp_sq + geo_sample.f * l_dot_u_sp * l_dot_u_sp + 1.0;
                    B = 2.0 * geo_sample.f * l_dot_u_sp;
                    Det = max(0.0, B * B - 4.0 * A * C);
                }
                
                float sqrtDet = sqrt(Det);
                float Ut;
                if (abs(A) < 1e-7) {
                    Ut = -C / max(1e-19, B); 
                } else {
                    if (B < 0.0) {
                         Ut = 2.0 * C / (-B + sqrtDet);
                    } else {
                         Ut = (-B - sqrtDet) / (2.0 * A);
                    }
                }
                
                vec4 U_jet = vec4(U_spatial, Ut);
                
                // 严谨计算红移/蓝移 E = - p_u U^u
                // 使用刚刚插值并经过了换系处理的局域四动量 Sample_P_cov
                float E_emit = -dot(Sample_P_cov, U_jet);
                float FreqRatio = 1.0 / max(1e-6, E_emit);

                float JetTemperature = min(100000.0 * FreqRatio,100000.0); 
                AccumColor.xyz *= KelvinToRgb(JetTemperature);
                AccumColor.xyz *= min(pow(FreqRatio, JetRedShiftIntensityExponent), JetShiftMax);
                AccumColor *= JetBrightmut * (0.5 + 0.5 * tanh(log(max(1e-6, AccretionRate)) + 1.0));
                AccumColor.a *= 0.0; 
                
                bool IsPositiveEnergy = E_emit > 0.0;
                if (!IsPositiveEnergy) 
                {
                    float cMax = max(max(AccumColor.r, AccumColor.g), AccumColor.b);
                    float cMin = min(min(AccumColor.r, AccumColor.g), AccumColor.b);
                    AccumColor.rgb = vec3(cMax + cMin) - AccumColor.rgb;
                }
                // =========================================================
                // 偏振积累（NPGS 逐行移植，与 DiskColor 同构）：喷流四速 U_jet 与其
                // 位置径向/环向合成磁场方向构造发射偏振矢量 f，投影到相机屏幕基底
                // 后累积 Stokes Q/U（供 TraceRay 尾部 iPolarization 分支消费）。
                // =========================================================
                if (iPolarization != 0) {
                    float chi = -0.7;
                    float cosChi = cos(chi);
                    float sinChi = sin(chi);

                    vec4 B_tor = vec4(-SamplePos.z, 0.0, SamplePos.x, 0.0);
                    vec4 B_rad = vec4(SamplePos.x, SamplePos.y, SamplePos.z, 0.0);
                    vec4 B_up = normalize(B_tor) * cosChi + normalize(B_rad) * sinChi;
                    B_up.w = 0.0;

                    // 喷流轴向为 Y：用其空间速度构造流体四速（时间分量暂置 1，由归一化吸收）
                    vec4 u_up_fluid = vec4(U_spatial, 1.0) * Ut;

                    // 采样点光子上指标动量（喷射分支独立于盘分支，须自行提升指标）
                    vec4 Sample_P_up = RaiseIndex(Sample_P_cov, geo_sample);
                    vec4 p_up = Sample_P_up;

                    vec4 f_down;
                    f_down.x =  det3(u_up_fluid.yzw, p_up.yzw, B_up.yzw);
                    f_down.y = -det3(u_up_fluid.xzw, p_up.xzw, B_up.xzw);
                    f_down.z =  det3(u_up_fluid.xyw, p_up.xyw, B_up.xyw);
                    f_down.w = -det3(u_up_fluid.xyz, p_up.xyz, B_up.xyz);

                    float f_norm = sqrt(max(1e-12, abs(dot(RaiseIndex(f_down, geo_sample), f_down))));
                    f_down /= f_norm;

                    vec4 Emit_X = vec4(SamplePos, EmissionTime);
                    vec2 WP_emit = GetWalkerPenrose(Emit_X, Sample_P_cov, f_down,
                                                    PhysicalSpinA, PhysicalQ, PosR);

                    vec2 ScreenAmps = SolvePolarization(WP_emit, WP_CamX, WP_CamY);

                    float weight = (AccumColor.r + AccumColor.g + AccumColor.b) * StepSize
                                 * pow(1.0 - CurrentResult.a, 1.0);
                    StokesQU.x += (ScreenAmps.x * ScreenAmps.x - ScreenAmps.y * ScreenAmps.y) * weight;
                    StokesQU.y += (2.0 * ScreenAmps.x * ScreenAmps.y) * weight;
                }

                // ... (颜色累加不变)
                float aR = 1.0 + JetReddening * (1.0 - 1.0);
                float aG = 1.0 + JetReddening * (3.0 - 1.0);
                float aB = 1.0 + JetReddening * (6.0 - 1.0);
                float Sum_rgb = (AccumColor.r + AccumColor.g + AccumColor.b) * pow(1.0 - CurrentResult.a, aG);
                
                float Denominator = AccumColor.r * pow(1.0 - CurrentResult.a, aR) + AccumColor.g * pow(1.0 - CurrentResult.a, aG) + AccumColor.b * pow(1.0 - CurrentResult.a, aB);
                float r001 = 0.0; float g001 = 0.0; float b001 = 0.0;
                if (Denominator > 0.000001)
                {
                    r001 = Sum_rgb * AccumColor.r * pow(1.0 - CurrentResult.a, aR) / Denominator;
                    g001 = Sum_rgb * AccumColor.g * pow(1.0 - CurrentResult.a, aG) / Denominator;
                    b001 = Sum_rgb * AccumColor.b * pow(1.0 - CurrentResult.a, aB) / Denominator;
                    
                    r001 *= pow(3.0 * r001 / (r001 + g001 + b001), JetSaturation);
                    g001 *= pow(3.0 * g001 / (r001 + g001 + b001), JetSaturation);
                    b001 *= pow(3.0 * b001 / (r001 + g001 + b001), JetSaturation);
                }
                
                CurrentResult.r += r001;
                CurrentResult.g += g001;
                CurrentResult.b += b001;
                CurrentResult.a += AccumColor.a * (1.0 - CurrentResult.a);
            }
        }
    }
    return CurrentResult;
}

// 完美复刻原版逻辑的 3阶分形布朗运动 (3 Octaves FBM + Domain Warping)
// =====================================================================
// 辅助函数 1：获取在 Ingoing 坐标下，沿零矢量内落的粒子空间坐标
// M = 0.5 (rs = 1.0)，Y轴为自旋轴，沿赤道面内落
// =====================================================================

// =====================================================================
// 辅助函数 2：对光线参数 tau 进行插值、换系，并返回与光点的距离平方
// =====================================================================

// =====================================================================
// 主函数：绘制沿着零矢量内落的白色光点
// =====================================================================


// =============================================================================
// 时空网格（NPGS 逐行移植）：iGrid=1 → GridColor（真实的当前步坐标），
// iGrid=2 → GridColorSimple（简易版，接受 ShowInnerGrid 遮罩）。两者均不依赖
// 外部辅助函数，属自包含实现；由 TraceRay 主循环按 iGrid 分派调用。
// =============================================================================
vec4 GridColorSimple(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
               vec4 P_cov, vec4 LastP_cov, 
               float PhysicalSpinA, float PhysicalQ, bool isoutgoing,
               float EndStepSign, float dlambda,bool showInnerGrid)
               {
    vec4 CurrentResult = BaseColor;
    if (CurrentResult.a > 0.99) return CurrentResult;

    const int MaxGrids = 5; 
    
    float SignedGridRadii[MaxGrids]; 
    vec3  GridColors[MaxGrids];
    int   GridCount = 0;
    
    float StartStepSign = EndStepSign;
    bool bHasCrossed = false;
    float t_cross = -1.0;
    vec3 DiskHitPos = vec3(0.0);
    vec4 DiskHitX = vec4(0.0); // 新增：用于记录赤道盘相交时的四维坐标信息

    // --- 【修改 2：计算端点坐标速度与 Hermite 曲线切线】 ---
    // 获取起点和终点的几何信息并升指标，求出坐标对仿射参量的导数 dX/dlambda
    KerrGeometry geo_last;
    ComputeGeometryScalars(LastRayPos.xyz, 1.0, StartStepSign, isoutgoing, geo_last);
    vec4 V0 = RaiseIndex(LastP_cov, geo_last); 
    vec4 T0 = V0 * dlambda; // 转换为对插值参数 t(0~1) 的导数

    KerrGeometry geo_curr;
    ComputeGeometryScalars(RayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_curr);
    vec4 V1 = RaiseIndex(P_cov, geo_curr);     
    vec4 T1 = V1 * dlambda; 

// --- 【修改 3：赤道盘相交改为三次曲线求根】 ---
    if (LastRayPos.y * RayPos.y < 0.0) {
        float denom = (LastRayPos.y - RayPos.y);
        if(abs(denom) > 1e-9) {
            t_cross = LastRayPos.y / denom; // 线性初猜
            
            // 牛顿迭代求三次 Hermite 曲线在 y 轴的精确零点
            for(int iter = 0; iter < 3; iter++) {
                float t2 = t_cross * t_cross;
                float t3 = t2 * t_cross;
                
                // 基函数
                float h00 = 2.0*t3 - 3.0*t2 + 1.0;
                float h10 = t3 - 2.0*t2 + t_cross;
                float h01 = -2.0*t3 + 3.0*t2;
                float h11 = t3 - t2;
                float yt = h00*LastRayPos.y + h10*T0.y + h01*RayPos.y + h11*T1.y;
                
                // 导数
                float dh00 = 6.0*t2 - 6.0*t_cross;
                float dh10 = 3.0*t2 - 4.0*t_cross + 1.0;
                float dh01 = -6.0*t2 + 6.0*t_cross;
                float dh11 = 3.0*t2 - 2.0*t_cross;
                float dyt = dh00*LastRayPos.y + dh10*T0.y + dh01*RayPos.y + dh11*T1.y;
                
                t_cross -= yt / (dyt + 1e-12);
            }
            t_cross = clamp(t_cross, 0.0, 1.0);
            
            // 依据精确 t 计算交点四维坐标
            float t2 = t_cross * t_cross;
            float t3 = t2 * t_cross;
            vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t_cross, -2.0*t3 + 3.0*t2, t3 - t2);
            DiskHitX = H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1;
            DiskHitPos = DiskHitX.xyz;
            
            if (length(DiskHitPos.xz) < abs(PhysicalSpinA)) {
                StartStepSign = -EndStepSign;
                bHasCrossed = true;
            }
        }
    }
    // ---------------------------------------------

    bool CheckPositive = (StartStepSign > 0.0) || (EndStepSign > 0.0);
    bool CheckNegative = (StartStepSign < 0.0) || (EndStepSign < 0.0);

    float HorizonDiscrim = 0.25 - PhysicalSpinA * PhysicalSpinA - PhysicalQ * PhysicalQ;
    float RH_Outer = 0.5 + sqrt(max(0.0, HorizonDiscrim));
    float RH_Inner = 0.5 - sqrt(max(0.0, HorizonDiscrim));
    bool HasHorizon = HorizonDiscrim >= 0.0;

    if (CheckPositive) {
        SignedGridRadii[GridCount] = 70.0;
        GridColors[GridCount] = 0.3*vec3(0.0, 1.0, 1.0); 
        GridCount++;

        if (HasHorizon) {
            SignedGridRadii[GridCount] = RH_Outer * 1.06; 
            GridColors[GridCount] = 0.3*vec3(0.0, 1.0, 0.0); 
            GridCount++;
            if(showInnerGrid)
            {
                SignedGridRadii[GridCount] = RH_Inner * 0.94; 
                GridColors[GridCount] =0.3* vec3(1.0, 0.0, 0.0); 
                GridCount++;
            }
        }
    }
    
    if (CheckNegative) {
        SignedGridRadii[GridCount] = -70.0;  
        GridColors[GridCount] = 0.3*vec3(1.0, 0.0, 1.0); 
        GridCount++;
    }

    vec3 O = LastRayPos.xyz;
    vec3 D_vec = RayPos.xyz - LastRayPos.xyz;

    for (int i = 0; i < GridCount; i++) {
        if (CurrentResult.a > 0.99) break;

        float TargetSignedR = SignedGridRadii[i];
        float TargetGeoR = abs(TargetSignedR); 
        vec3  TargetColor = GridColors[i];

        vec2 roots = IntersectKerrEllipsoid(O, D_vec, TargetGeoR, PhysicalSpinA);
        
        float t_hits[2];
        t_hits[0] = roots.x;
        t_hits[1] = roots.y;
        if (t_hits[0] > t_hits[1]) {
            float temp = t_hits[0]; t_hits[0] = t_hits[1]; t_hits[1] = temp;
        }
        
        for (int j = 0; j < 2; j++) {
            float t = t_hits[j];
            
            if (t >= 0.0 && t <= 1.0) {
                
                float HitPointSign = StartStepSign;
                if (bHasCrossed) {
                    if (t > t_cross) {
                        HitPointSign = EndStepSign;
                    }
                }

                if (HitPointSign * TargetSignedR < 0.0) continue;

                // --- 【修改 4：曲线与椭球网格面相交的牛顿迭代修整】 ---
                for(int iter = 0; iter < 2; iter++) {
                    float t2 = t*t; float t3 = t2*t;
                    vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t, -2.0*t3 + 3.0*t2, t3 - t2);
                    vec3 pos = (H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1).xyz;
                    float curR = KerrSchildRadius(pos, HitPointSign);
                    
                    // 数值导数
                    float dt = 0.001;
                    float nt = t + dt;
                    float nt2 = nt*nt; float nt3 = nt2*nt;
                    vec4 nH = vec4(2.0*nt3 - 3.0*nt2 + 1.0, nt3 - 2.0*nt2 + nt, -2.0*nt3 + 3.0*nt2, nt3 - nt2);
                    vec3 npos = (nH.x*LastRayPos + nH.y*T0 + nH.z*RayPos + nH.w*T1).xyz;
                    float nextR = KerrSchildRadius(npos, HitPointSign);
                    
                    float dr_dt = (nextR - curR) / dt;
                    t -= (curR - TargetSignedR) / (dr_dt + 1e-12);
                }
                
                if (t < 0.0 || t > 1.0) continue; 
                
                // 依据精确 t 提取四维坐标
                float t2 = t*t; float t3 = t2*t;
                vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t, -2.0*t3 + 3.0*t2, t3 - t2);
                vec4 HitX = H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1;
                
                vec3 HitPos = HitX.xyz;
                float HitTime = HitX.w;
                // ----------------------------------------------------
                
                float CheckR = KerrSchildRadius(HitPos, HitPointSign);
                if (abs(CheckR - TargetSignedR) > 0.1 * TargetGeoR + 0.1) continue; 

                // 动量在此保持线性插值即可（已足够准确用于后续投影运算）
                vec4 HitP_cov = mix(LastP_cov, P_cov, t);
                // --- 几何图案部分：将坐标映射回统一的 Ingoing 参考系 ---
                vec3 PatternPos = HitPos;
                float PatternTime = HitTime;
                if (isoutgoing) {
                    vec4 tempX = vec4(HitPos, HitTime);
                    vec4 dummyP = vec4(0.0);
                    transformKerrSchild_YSpin(tempX, HitPointSign, dummyP, true);
                    PatternPos = tempX.xyz;
                    PatternTime = tempX.w;
                }

                float Omega = GetZamoOmega(TargetSignedR, PhysicalSpinA, PhysicalQ, PatternPos.y);

                float Phi_raw = Vec2ToTheta(normalize(PatternPos.zx), vec2(0.0, 1.0));
                float Phi = Phi_raw + Omega * PatternTime + iBlackHoleTime*GetZamoOmega(TargetSignedR, PhysicalSpinA, PhysicalQ, 0.0);
                
                float CosTheta = clamp(PatternPos.y / TargetGeoR, -1.0, 1.0);
                float Theta = acos(CosTheta);
                float SinTheta = sqrt(max(0.0, 1.0 - CosTheta * CosTheta));

                float DensityPhi = 24.0;
                float DensityTheta = 13.0;
                float DistFactor = min(20.0,length(PatternPos));
                float LineWidth = 0.002 * DistFactor; 
                LineWidth = clamp(LineWidth, 0.01, 0.15); 

                float PatternPhi = abs(fract(Phi / (2.0 * kPi) * DensityPhi) - 0.5);
                float GridPhi = smoothstep(LineWidth / max(0.005, SinTheta), 0.0, PatternPhi);

                float PatternTheta = abs(fract(Theta / kPi * DensityTheta) - 0.5);
                float GridTheta = smoothstep(LineWidth, 0.0, PatternTheta);
                
                float GridIntensity = max(GridPhi, GridTheta);

                // --- 【修改：新增计算网格点局部能量】 ---
                float Omega_zamo = GetZamoOmega(TargetSignedR, PhysicalSpinA, PhysicalQ, HitPos.y);
                vec3 VelSpatial = Omega_zamo * vec3(HitPos.z, 0.0, -HitPos.x);
                vec4 U_zamo_unnorm = vec4(VelSpatial, 1.0); 
                KerrGeometry geo_hit;
                ComputeGeometryScalars(HitPos, 1.0, HitPointSign, isoutgoing, geo_hit);
                vec4 U_zamo_lower = LowerIndex(U_zamo_unnorm, geo_hit);
                float norm_sq = dot(U_zamo_unnorm, U_zamo_lower);
                float norm = sqrt(max(1e-9, abs(norm_sq)));
                vec4 U_zamo = U_zamo_unnorm / norm;
                float E_emit = -dot(HitP_cov, U_zamo);
                // ------------------------------------

                if (GridIntensity > 0.01) {
                    vec4 GridCol = vec4(TargetColor * 2.0, 1.0);
                    
                    // --- 【修改：新增负能量判断与反色/剔除逻辑】 ---
                    if (E_emit < 0.0) {
                        float cMax = max(max(GridCol.r, GridCol.g), GridCol.b);
                        float cMin = min(min(GridCol.r, GridCol.g), GridCol.b);
                        GridCol.rgb = vec3(cMax + cMin) - GridCol.rgb;
                        if (iWhitehole == 0) GridCol.rgba = vec4(0.0);
                    }
                    // -----------------------------------------
                    
                    float Alpha = GridIntensity * 0.8; 
                    CurrentResult.rgb += GridCol.rgb * Alpha * (1.0 - CurrentResult.a);
                    CurrentResult.a   += Alpha * (1.0 - CurrentResult.a);
                }
            }
        }
    }

    if (bHasCrossed && CurrentResult.a < 0.99) {
        
        float HitRho = length(DiskHitPos.xz);
        float a_abs = abs(PhysicalSpinA);
        float HitTime_disk = DiskHitX.w;
        vec4 HitP_cov = mix(LastP_cov, P_cov, t_cross);
        // --- 提取统一网格相位的偏移 ---
        vec3 PatternPosDisk = DiskHitPos;
        if (isoutgoing) {
            vec4 tempX = vec4(DiskHitPos, HitTime_disk);
            vec4 dummyP = vec4(0.0);
            float diskSign = (length(DiskHitPos.xz) < abs(PhysicalSpinA)) ? -StartStepSign : StartStepSign;
            transformKerrSchild_YSpin(tempX, diskSign, dummyP, true);
            PatternPosDisk = tempX.xyz;
        }

        float Phi_raw = Vec2ToTheta(normalize(PatternPosDisk.zx), vec2(0.0, 1.0));
        float Phi = Phi_raw;
        
        float DensityPhi = 24.0;
        float DistFactor = length(DiskHitPos); 
        float LineWidth = 0.002 * DistFactor;
        LineWidth = clamp(LineWidth, 0.01, 0.1);

        float PatternPhi = abs(fract(Phi / (2.0 * kPi) * DensityPhi) - 0.5);
        float GridPhi = smoothstep(LineWidth / max(0.1, HitRho / a_abs), 0.0, PatternPhi);

        float NormalizedRho = HitRho / max(1e-6, a_abs);
        float DensityRho = 5.0; 
        float PatternRho = abs(fract(NormalizedRho * DensityRho) - 0.5);
        float GridRho = smoothstep(LineWidth, 0.0, PatternRho);
        
        float GridIntensity = max(GridPhi, GridRho);

        // --- 【修改：新增计算静态能量投影】 ---
        vec4 U_zero = vec4(0.0, 0.0, 0.0, 1.0); 
        float E_emit_disk = -dot(HitP_cov, U_zero); 
        // ---------------------------------

        if (GridIntensity > 0.01) {
            vec3 RingColor = 0.3*vec3(1.0, 1.0, 1.0);
            vec4 GridCol = vec4(RingColor * 5.0, 1.0);
            
            // --- 【修改：新增负能量判断与反色/剔除逻辑】 ---
            if (E_emit_disk < 0.0) {
                float cMax = max(max(GridCol.r, GridCol.g), GridCol.b);
                float cMin = min(min(GridCol.r, GridCol.g), GridCol.b);
                GridCol.rgb = vec3(cMax + cMin) - GridCol.rgb;
                if (iWhitehole == 0) GridCol.rgba = vec4(0.0);
            }
            // -----------------------------------------
            
            float Alpha = GridIntensity * 0.8;
            CurrentResult.rgb += GridCol.rgb * Alpha * (1.0 - CurrentResult.a);
            CurrentResult.a   += Alpha * (1.0 - CurrentResult.a);
        }
    }

    return CurrentResult;
}

vec4 GridColor(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
               vec4 iP_cov, float iE_obs,
               float PhysicalSpinA, float PhysicalQ, bool isoutgoing,
               float EndStepSign)
{
    vec4 CurrentResult = BaseColor;
    if (CurrentResult.a > 0.99) return CurrentResult;

    const int MaxGrids = 12; 
    float SignedGridRadii[MaxGrids]; 
    int GridCount = 0;
    
    float StartStepSign = EndStepSign;
    bool bHasCrossed = false;
    float t_cross = -1.0;
    vec3 DiskHitPos = vec3(0.0);
    
    if (LastRayPos.y * RayPos.y < 0.0) {
        float denom = (LastRayPos.y - RayPos.y);
        if(abs(denom) > 1e-9) {
            t_cross = LastRayPos.y / denom;
            DiskHitPos = mix(LastRayPos.xyz, RayPos.xyz, t_cross);
            
            if (length(DiskHitPos.xz) < abs(PhysicalSpinA)) {
                StartStepSign = -EndStepSign;
                bHasCrossed = true;
            }
        }
    }

    bool CheckPositive = (StartStepSign > 0.0) || (EndStepSign > 0.0);
    bool CheckNegative = (StartStepSign < 0.0) || (EndStepSign < 0.0);

    float HorizonDiscrim = 0.25 - PhysicalSpinA * PhysicalSpinA - PhysicalQ * PhysicalQ;
    float RH_Outer = 0.5 + sqrt(max(0.0, HorizonDiscrim));
    float RH_Inner = 0.5 - sqrt(max(0.0, HorizonDiscrim));

    if (CheckPositive) {
        SignedGridRadii[GridCount++] = RH_Outer * 1.06; 
        SignedGridRadii[GridCount++] = 20.0;
        
        if (HorizonDiscrim >= 0.0) {
           SignedGridRadii[GridCount++] = RH_Inner * 0.94; 
        }
    }
    
    if (CheckNegative) {
        SignedGridRadii[GridCount++] = -3.0;  
        SignedGridRadii[GridCount++] = -10.0; 
    }

    vec3 O = LastRayPos.xyz;
    vec3 D_vec = RayPos.xyz - LastRayPos.xyz;

    for (int i = 0; i < GridCount; i++) {
        if (CurrentResult.a > 0.99) break;

        float TargetSignedR = SignedGridRadii[i];
        float TargetGeoR = abs(TargetSignedR); 

        vec2 roots = IntersectKerrEllipsoid(O, D_vec, TargetGeoR, PhysicalSpinA);
        
        float t_hits[2];
        t_hits[0] = roots.x;
        t_hits[1] = roots.y;
        
        if (t_hits[0] > t_hits[1]) {
            float temp = t_hits[0]; t_hits[0] = t_hits[1]; t_hits[1] = temp;
        }
        
        for (int j = 0; j < 2; j++) {
            float t = t_hits[j];
            
            if (t >= 0.0 && t <= 1.0) {
                
                float HitPointSign = StartStepSign;
                if (bHasCrossed) {
                    if (t > t_cross) {
                        HitPointSign = EndStepSign;
                    }
                }

                if (HitPointSign * TargetSignedR < 0.0) continue;

                vec3 HitPos = O + D_vec * t;
                float CheckR = KerrSchildRadius(HitPos, HitPointSign);
                if (abs(CheckR - TargetSignedR) > 0.1 * TargetGeoR + 0.1) continue; 

                float HitTime = mix(LastRayPos.w, RayPos.w, t);

                // --- 物理计算部分：维持在当前的平滑坐标系（ HitPos 和 isoutgoing ）---
                float Omega = GetZamoOmega(TargetSignedR, PhysicalSpinA, PhysicalQ, HitPos.y);
                vec3 VelSpatial = Omega * vec3(HitPos.z, 0.0, -HitPos.x);
                vec4 U_zamo_unnorm = vec4(VelSpatial, 1.0); 
                
                KerrGeometry geo_hit;
                ComputeGeometryScalars(HitPos, 1.0, HitPointSign, isoutgoing, geo_hit);
                
                vec4 U_zamo_lower = LowerIndex(U_zamo_unnorm, geo_hit);
                float norm_sq = dot(U_zamo_unnorm, U_zamo_lower);
                float norm = sqrt(max(1e-9, abs(norm_sq)));
                vec4 U_zamo = U_zamo_unnorm / norm;

                float E_emit = -dot(iP_cov, U_zamo);
                float Shift = 1.0/ max(1e-6, abs(E_emit)); 

                // --- 几何图案部分：将坐标映射回统一的 Ingoing 参考系 ---
                vec3 PatternPos = HitPos;
                float PatternTime = HitTime;
                if (isoutgoing) {
                    vec4 tempX = vec4(HitPos, HitTime);
                    vec4 dummyP = vec4(0.0);
                    // out_to_in = true 转换为 Ingoing
                    transformKerrSchild_YSpin(tempX, HitPointSign, dummyP, true);
                    PatternPos = tempX.xyz;
                    PatternTime = tempX.w;
                }

                float Phi_raw = Vec2ToTheta(normalize(PatternPos.zx), vec2(0.0, 1.0));
                float Phi = Phi_raw + Omega * PatternTime + iBlackHoleTime*GetZamoOmega(TargetSignedR, PhysicalSpinA, PhysicalQ, 1.0);
                
                float CosTheta = clamp(PatternPos.y / TargetGeoR, -1.0, 1.0);
                float Theta = acos(CosTheta);
                float SinTheta = sqrt(max(0.0, 1.0 - CosTheta * CosTheta));

                float DensityPhi = 24.0;
                float DensityTheta = 12.0;
                float DistFactor = length(PatternPos);
                float LineWidth = 0.001 * DistFactor;
                LineWidth = clamp(LineWidth, 0.01, 0.1); 

                float PatternPhi = abs(fract(Phi / (2.0 * kPi) * DensityPhi) - 0.5);
                float GridPhi = smoothstep(LineWidth / max(0.005, SinTheta), 0.0, PatternPhi);

                float PatternTheta = abs(fract(Theta / kPi * DensityTheta) - 0.5);
                float GridTheta = smoothstep(LineWidth, 0.0, PatternTheta);
                
                float GridIntensity = max(GridPhi, GridTheta);

                if (GridIntensity > 0.01) {
                    float BaseTemp = 6500.0;
                    vec3 BlackbodyColor = KelvinToRgb(BaseTemp * Shift);
                    float Intensity = min(1.5 * pow(Shift, 4.0), 20.0);
                    vec4 GridCol = vec4(BlackbodyColor * Intensity, 1.0);
                    
                    float Alpha = GridIntensity * 0.5; 
                    CurrentResult.rgb += GridCol.rgb * Alpha * (1.0 - CurrentResult.a);
                    CurrentResult.a   += Alpha * (1.0 - CurrentResult.a);
                }
            }
        }
    }

    // --- 赤道面的网格处理同样映射为统一相空间 ---
    if (bHasCrossed && CurrentResult.a < 0.99) {
        
        float HitRho = length(DiskHitPos.xz);
        float a_abs = abs(PhysicalSpinA);
        float HitTime_disk = mix(LastRayPos.w, RayPos.w, t_cross);
        
        vec3 PatternPosDisk = DiskHitPos;
        if (isoutgoing) {
            vec4 tempX = vec4(DiskHitPos, HitTime_disk);
            vec4 dummyP = vec4(0.0);
            float diskSign = (length(DiskHitPos.xz) < abs(PhysicalSpinA)) ? -StartStepSign : StartStepSign;
            transformKerrSchild_YSpin(tempX, diskSign, dummyP, true);
            PatternPosDisk = tempX.xyz;
        }

        float Phi_raw = Vec2ToTheta(normalize(PatternPosDisk.zx), vec2(0.0, 1.0));
        float Phi = Phi_raw;
        
        float DensityPhi = 24.0;
        float DistFactor = length(DiskHitPos); 
        float LineWidth = 0.001 * DistFactor;
        LineWidth = clamp(LineWidth, 0.01, 0.1);

        float PatternPhi = abs(fract(Phi / (2.0 * kPi) * DensityPhi) - 0.5);
        float GridPhi = smoothstep(LineWidth / max(0.1, HitRho / a_abs), 0.0, PatternPhi);

        float NormalizedRho = HitRho / max(1e-6, a_abs);
        float DensityRho = 5.0; 
        float PatternRho = abs(fract(NormalizedRho * DensityRho) - 0.5);
        float GridRho = smoothstep(LineWidth, 0.0, PatternRho);
        
        float GridIntensity = max(GridPhi, GridRho);

        if (GridIntensity > 0.01) {
            // (频移由于和空间无关依然使用原始数据)
            vec4 U_zero = vec4(0.0, 0.0, 0.0, 1.0); 
            float E_emit = -dot(iP_cov, U_zero); 
            float Shift = 1.0 / max(1e-6, abs(E_emit));
            
            float BaseTemp = 6500.0; 
            vec3 BlackbodyColor = KelvinToRgb(BaseTemp * Shift);
            float Intensity = min(2.0 * pow(Shift, 4.0), 30.0);
            
            vec4 GridCol = vec4(BlackbodyColor * Intensity, 1.0);
            
            float Alpha = GridIntensity * 0.5;
            CurrentResult.rgb += GridCol.rgb * Alpha * (1.0 - CurrentResult.a);
            CurrentResult.a   += Alpha * (1.0 - CurrentResult.a);
        }
    }

    return CurrentResult;
}

// =============================================================================
// 致密星表面（NPGS 逐行移植）：iDensestarsurfaceR != 0 时渲染一颗黑体着色的
// 椭球"中子星"表面，含频移→温度→黑体色链路。依赖 Fbm_Standalone。
// =============================================================================
float Fbm_Standalone(vec3 x) {
    vec2 sum = vec2(0.0);
    float ampl = 1.0;
    for (int i = 0; i < 3; ++i) {
        sum += ampl * PerlinNoise(x);
        // 原版的域扭曲(Domain Warping)逻辑，增加流体感
        x += 1.4 * sum.xyx * vec3(0.7, 0.6, 1.3);
        ampl *= 0.74;
        x *= vec3(4.0, 4.0, 4.0);
    }
    return sum.x;
}

vec4 DensestarColor(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
                    vec4 P_cov, vec4 LastP_cov, 
                    float PhysicalSpinA, float PhysicalQ, bool isoutgoing,
                    float EndStepSign, float dlambda)
{
    vec4 CurrentResult = BaseColor;
    // 如果不透明度已满，或者未启用致密星渲染，则直接返回
    if (iDensestarsurfaceR == 0.0) return CurrentResult;

    // 获取起点和终点的几何信息并升指标，求出坐标对仿射参量的导数 dX/dlambda
    KerrGeometry geo_last;
    ComputeGeometryScalars(LastRayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_last);
    vec4 V0 = RaiseIndex(LastP_cov, geo_last); 
    vec4 T0 = V0 * dlambda; 

    KerrGeometry geo_curr;
    ComputeGeometryScalars(RayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_curr);
    vec4 V1 = RaiseIndex(P_cov, geo_curr);     
    vec4 T1 = V1 * dlambda; 

    float TargetSignedR = iDensestarsurfaceR; 
    float TargetGeoR = abs(TargetSignedR); 
    
    vec3 O = LastRayPos.xyz;
    vec3 D_vec = RayPos.xyz - LastRayPos.xyz;

    // 椭球面求交
    vec2 roots = IntersectKerrEllipsoid(O, D_vec, TargetGeoR, PhysicalSpinA);
    
    float t_hits[2];
    t_hits[0] = roots.x;
    t_hits[1] = roots.y;
    // 确保按射线前进方向排序
    if (t_hits[0] > t_hits[1]) {
        float temp = t_hits[0]; t_hits[0] = t_hits[1]; t_hits[1] = temp;
    }
    
    for (int j = 0; j < 2; j++) {
        float t = t_hits[j];
        
        if (t >= 0.0 && t <= 1.0) {
            float HitPointSign = EndStepSign;
            if (HitPointSign * TargetSignedR < 0.0) continue;

            // 曲线与椭球面相交的牛顿迭代修整
            for(int iter = 0; iter < 2; iter++) {
                float t2 = t*t; float t3 = t2*t;
                vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t, -2.0*t3 + 3.0*t2, t3 - t2);
                vec3 pos = (H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1).xyz;
                float curR = KerrSchildRadius(pos, HitPointSign);
                
                float dt = 0.001;
                float nt = t + dt;
                float nt2 = nt*nt; float nt3 = nt2*nt;
                vec4 nH = vec4(2.0*nt3 - 3.0*nt2 + 1.0, nt3 - 2.0*nt2 + nt, -2.0*nt3 + 3.0*nt2, nt3 - nt2);
                vec3 npos = (nH.x*LastRayPos + nH.y*T0 + nH.z*RayPos + nH.w*T1).xyz;
                float nextR = KerrSchildRadius(npos, HitPointSign);
                
                float dr_dt = (nextR - curR) / dt;
                t -= (curR - TargetSignedR) / (dr_dt + 1e-12);
            }
            
            if (t < 0.0 || t > 1.0) continue; 
            
            // 提取高精度四维坐标与动量
            float t2 = t*t; float t3 = t2*t;
            vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t, -2.0*t3 + 3.0*t2, t3 - t2);
            vec4 HitX = H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1;
            
            vec3 HitPos = HitX.xyz;
            float HitTime = HitX.w;
            
            float CheckR = KerrSchildRadius(HitPos, HitPointSign);
            if (abs(CheckR - TargetSignedR) > 0.1 * TargetGeoR + 0.1) continue; 

            vec4 HitP_cov = mix(LastP_cov, P_cov, t);

            // 映射参考系以提取局部坐标
            vec3 PatternPos = HitPos;
            float PatternTime = HitTime;
            if (isoutgoing) {
                vec4 tempX = vec4(HitPos, HitTime);
                vec4 dummyP = vec4(0.0);
                transformKerrSchild_YSpin(tempX, HitPointSign, dummyP, true);
                PatternPos = tempX.xyz;
                PatternTime = tempX.w;
            }

            // --- 【核心修改：刚体角速度与四维速度】 ---
            float R2 = TargetSignedR * TargetSignedR;
            float a2 = PhysicalSpinA * PhysicalSpinA;
            float Omega_star = PhysicalSpinA / (R2 + a2); // 星体指定等角速度刚体旋转

            float EmissionTime = iBlackHoleTime + PatternTime;
            
            
            // 1. 获取归一化的表面坐标
            vec3 pos_tex = normalize(PatternPos);
            
            // 2. 将 3D 坐标随刚体自旋绕 Y 轴旋转
            float rotAngle = Omega_star * EmissionTime;
            float c_rot = cos(rotAngle);
            float s_rot = sin(rotAngle);
            pos_tex.xz = mat2(c_rot, s_rot, -s_rot, c_rot) * pos_tex.xz;
            
            // 3. 应用中子星的纹理缩放倍数
            pos_tex *= 4.0;
            
            // 4. 表面等离子体沸腾/流动动画 (利用 EmissionTime 替代原版的 starTime)
            // 原版: sin(starTime / 128.0 * 3.14) * 50.0
            // 这里我们调整一下时间缩放比例，保证视觉流动速度适当
            float animSpeed = EmissionTime * 0.01; 
            
            vec3 noisePos = vec3(
                pos_tex.x + sin(animSpeed) * 2.0,
                pos_tex.y + cos(animSpeed) * 2.0, 
                pos_tex.z
            );
            
            // 5. 采样独立实现的 3D FBM 噪声
            float noiseVal = Fbm_Standalone(noisePos);
            
            // 6. 将噪声映射为温度调制系数 (0.8 ~ 1.0 波动区间，完美复刻 MOD)
            float tempMod = clamp(noiseVal * 0.2 + 0.8,0.5,1.5);
            
            // 7. 映射到发光体的物理静止系温度 (基准温度乘以调制系数)
            // 你可以把 6000.0 替换为你的恒星基础温度变量（如果外部有传如 iDensestarTemp 等）
            float BaseTemp_Kelvin = 6000.0 * tempMod; 
            
            // =================================================================

            // --- 【核心修改：频移系数 g 计算】 ---
            // 星体刚体旋转的局部四维速度 U_star
            vec3 VelSpatial = Omega_star * vec3(HitPos.z, 0.0, -HitPos.x);
            vec4 U_star_unnorm = vec4(VelSpatial, 1.0); 
            
            KerrGeometry geo_hit;
            ComputeGeometryScalars(HitPos, 1.0, HitPointSign, isoutgoing, geo_hit);
            vec4 U_star_lower = LowerIndex(U_star_unnorm, geo_hit);
            float norm_sq = dot(U_star_unnorm, U_star_lower);
            vec4 U_star = U_star_unnorm / sqrt(max(1e-9, abs(norm_sq)));

            // 发射能量与无穷远观测能量 (P_cov 的 w 分量为 -E_obs 守恒量)
            float E_emit_raw = -dot(HitP_cov, U_star);
            float g = 1.0 / max(1e-9, abs(E_emit_raw)); 


            // --- 【核心修改：计算频移后的温度与亮度】 ---
            // 观测温度 = 静止温度 * (g ^ 频移温度指数)
            float ObsTemp_Kelvin = BaseTemp_Kelvin * pow(g, iDensestarRedShiftColorExponent);
            
            // 获取对应色温的黑体颜色
            vec3 BlackBodyColor = KelvinToRgb(ObsTemp_Kelvin);

            // 观测亮度 = (自身静止温度导致的热辐射亮度基数) * (g ^ 频移亮度指数)
            float BaseIntensity = pow(BaseTemp_Kelvin / 6000.0, iDensestarBlackbodyIntensityExponent); 
            float RedshiftIntensity = pow(g, iDensestarRedShiftIntensityExponent);
            float FinalIntensity = BaseIntensity * RedshiftIntensity;
            // =================================================================
            // ↓↓↓ 测试/调试功能：八卦限颜色与经纬网棋盘格 (取消外部块注释即可生效) ↓↓↓
            if(iDEBUG==5)
            {
                // 获取归一化的随动局部坐标（因为上文 pos_tex 被 *= 4.0 放大过，需还原方向）
                vec3 unit_pos = normalize(pos_tex);
                
                // 1. 八卦限颜色：利用 step 判断 x, y, z 的正负号映射到 RGB
                vec3 octantColor = step(0.0, unit_pos); 
                // 为了避免纯黑(0,0,0)卦限看不见，将其映射到 0.2 ~ 1.0 范围
                octantColor = octantColor * 0.8 + 0.2;  

                // 2. 获取球坐标经纬度
// 2. 获取球坐标经纬度（包含针对 atan(0,0) 和 asin(>1) 的防 NaN 保护）
            float phi = atan(unit_pos.z, abs(unit_pos.x) < 1e-9 && abs(unit_pos.z) < 1e-9 ? 1e-9 : unit_pos.x); 
            float theta = asin(clamp(unit_pos.y, -1.0, 1.0));
                
                // 3. 计算棋盘格网格 (调节 6.0 这个系数可改变网格密度)
                float u = phi * 6.0;   
                float v = theta * 6.0; 
                float checker = mod(floor(u) + floor(v), 2.0); // 结果为 0.0 或 1.0
                
                // 4. 将棋盘格映射为亮度调节系数（例如暗格亮度 0.3，亮格亮度 1.0）
                float checkerIntensity = clamp(checker * 0.7 + 0.3,0.0,1.0);
                
                // 5. 覆写原物理计算结果 (保留了相对论红蓝移造成的 RedshiftIntensity，方便观察引力透镜和多普勒效应)
                BlackBodyColor = octantColor;
                FinalIntensity = checkerIntensity * RedshiftIntensity;
            }
            
            // ↑↑↑ 测试/调试功能结束 ↑↑↑
            // =================================================================
            vec4 StarCol = vec4(iDensestarBrightmut * BlackBodyColor * FinalIntensity, 1.0);
            
            // 处理负能量光线 / 反宇宙反色
            if (E_emit_raw < 0.0) {
                float cMax = max(max(StarCol.r, StarCol.g), StarCol.b);
                float cMin = min(min(StarCol.r, StarCol.g), StarCol.b);
                StarCol.rgb = vec3(cMax + cMin) - StarCol.rgb;
                if (iWhitehole == 0) StarCol.rgba = vec4(0.0);
            }
            
            // 将实体完全覆盖上去并退出循环
            CurrentResult.rgb += StarCol.rgb * StarCol.a * (1.0 - CurrentResult.a);
            CurrentResult.a   += StarCol.a * (1.0 - CurrentResult.a);
            
            if(CurrentResult.a > 0.99) break; 
        }
    }

    return CurrentResult;
}

// =============================================================================
// 沿零矢量内落的白色光点（NPGS 逐行移植，调试用）：几何体沿 Ingoing 主零矢量
// 下落，用于直观察看光线的传播与坐标系切换。依赖 GetIngoingNullParticlePos
// 与 GetDotDistSq（Hermite 插值求最近距离）。
// =============================================================================
// 辅助函数 1：获取在 Ingoing 坐标下，沿零矢量内落的粒子空间坐标
// M = 0.5 (rs = 1.0)，Y轴为自旋轴，沿赤道面内落
// =====================================================================
vec3 GetIngoingNullParticlePos(float time, float a) {
    // 设定周期，让光点不断生成并下落，方便持续观察
    float period = 15.0; 
    float t = mod(time, period);
    
    // 初始半径 r0 设为 10.0 rs。沿 Ingoing 主要零矢量下落，满足 dr/dt = -1
    float r0 = 10.0;
    float r = r0 - t; 
    
    // 固定坐标角 (赤道面 theta = pi/2, phi = 0)
    float theta = 1.57079632679; 
    float phi = 0.0; 
    
    float sinTh = sin(theta);
    float cosTh = cos(theta);
    float sinPh = sin(phi);
    float cosPh = cos(phi);
    
    // Kerr-Schild 笛卡尔坐标系映射 (Y-Spin)
    vec3 pos;
    pos.x = (r * cosPh + a * sinPh) * sinTh;
    pos.y = r * cosTh;
    pos.z = (r * sinPh - a * cosPh) * sinTh;
    
    return pos;
}

// =====================================================================
// 辅助函数 2：对光线参数 tau 进行插值、换系，并返回与光点的距离平方
// =====================================================================
float GetDotDistSq(float tau, vec4 LastRayPos, vec4 RayPos, vec4 T0, vec4 T1, 
                   float signR, float a, float Q, bool isoutgoing, out vec4 exactX_in) 
{
    // 1. 在入参所在系（原生系）进行 Hermite 插值
    float t2 = tau * tau; 
    float t3 = t2 * tau;
    vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + tau, -2.0*t3 + 3.0*t2, t3 - t2);
    vec4 X_nat = H.x * LastRayPos + H.y * T0 + H.z * RayPos + H.w * T1;
    
    exactX_in = X_nat;
    
    // 2. 坐标系转换：如果当前是 Outgoing 系，则转换为 Ingoing 系
    // 参考你原代码的写法，传入 true 执行转换
    if (isoutgoing) {
        vec4 dummyP = vec4(0.0);
        transformKerrSchild_YSpin(exactX_in, signR, dummyP, true);
    }
    
    // 3. 提取 Ingoing 系的全局时间
    float exactTime = iBlackHoleTime + exactX_in.w;
    
    // 4. 获取对应的光点物理位置
    vec3 dotP = GetIngoingNullParticlePos(exactTime, a);
    
    // 5. 返回欧氏距离平方
    vec3 diff = exactX_in.xyz - dotP;
    return dot(diff, diff);
}

vec4 DrawFallingWhiteDot(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
                         vec4 P_cov, vec4 LastP_cov, 
                         float PhysicalSpinA, float PhysicalQ, bool isoutgoing,
                         float EndStepSign, float dlambda)
{
    vec4 CurrentResult = BaseColor;
    if (CurrentResult.a > 0.99) return CurrentResult;

    // 1. 获取起点和终点的几何信息并升指标，求出 dX/dlambda
    KerrGeometry geo_last;
    ComputeGeometryScalars(LastRayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_last);
    vec4 V0 = RaiseIndex(LastP_cov, geo_last); 
    vec4 T0 = V0 * dlambda; 

    KerrGeometry geo_curr;
    ComputeGeometryScalars(RayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_curr);
    vec4 V1 = RaiseIndex(P_cov, geo_curr);     
    vec4 T1 = V1 * dlambda; 
    
    // 2. 为了防止大步长直接穿透 0.02 rs 的球体，取 tau = 0.0, 0.5, 1.0 采样
    vec4 dummyX;
    float d0  = GetDotDistSq(0.0, LastRayPos, RayPos, T0, T1, EndStepSign, PhysicalSpinA, PhysicalQ, isoutgoing, dummyX);
    float d05 = GetDotDistSq(0.5, LastRayPos, RayPos, T0, T1, EndStepSign, PhysicalSpinA, PhysicalQ, isoutgoing, dummyX);
    float d1  = GetDotDistSq(1.0, LastRayPos, RayPos, T0, T1, EndStepSign, PhysicalSpinA, PhysicalQ, isoutgoing, dummyX);
    
    // 3. 抛物线拟合 P(tau) = A*tau^2 + B*tau + C，寻找最近点参数 tau_min
    float A = 2.0 * d1 + 2.0 * d0 - 4.0 * d05;
    float B = 4.0 * d05 - d1 - 3.0 * d0;
    
    float tau_min = 0.5;
    if (abs(A) > 1e-9) {
        tau_min = clamp(-B / (2.0 * A), 0.0, 1.0);
    } else {
        tau_min = (d0 < d1) ? 0.0 : 1.0;
    }
    
    // 4. 精确计算最近点处的距离
    float distSq_min = GetDotDistSq(tau_min, LastRayPos, RayPos, T0, T1, EndStepSign, PhysicalSpinA, PhysicalQ, isoutgoing, dummyX);
    
    // 保险机制：防止拟合在极端扭曲下失效，确保拿到的确实是最小值
    float min_d = distSq_min;
    if(d0  < min_d) min_d = d0;
    if(d1  < min_d) min_d = d1;
    if(d05 < min_d) min_d = d05;
    
    float exactDist = sqrt(max(0.0, min_d));
    
    // 设定目标范围为 0.02 rs
    float threshold = 0.1;

    // 5. 如果光线确实掠过了该范围，则进行染色
    if (exactDist < threshold) {
        // 计算这一步跨越的空间真实长度
        float stepLength = length(RayPos.xyz - LastRayPos.xyz);
        
        // 计算光线在目标球体内截取的弦长
        float chordLength = 2.0 * sqrt(max(0.0, threshold * threshold - exactDist * exactDist));
        
        // 取步长与弦长的较小值，保证渲染不依赖于光追步长的大小 (防闪烁/过爆核心机制)
        float effectiveLength = min(stepLength, chordLength);
        
        // 外部柔和光晕
        float intensity = smoothstep(threshold, 0.0, exactDist);
        // 内部高亮核心
        float core = exp(-(exactDist * exactDist) / (0.005 * 0.005));
        
        // 亮度乘子，根据视觉反馈微调
        float density = intensity * 200.0 + core * 800.0;
        
        // 根据穿过的有效长度计算不透明度贡献
        float dotAlpha = clamp(density * effectiveLength, 0.0, 1.0);
        
        // 叠加白光
        vec3 whiteColor = vec3(1.0, 1.0, 1.0);
        CurrentResult.rgb += whiteColor * dotAlpha * (1.0 - CurrentResult.a);
        CurrentResult.a   += dotAlpha * (1.0 - CurrentResult.a);
    }

    return CurrentResult;
}

// =============================================================================
// 贴图盘（NPGS 逐行移植）：iUseImageDisk != 0 时在赤道面上铺一张贴图（Disk/R.jpg，
// 绑 set1.b9 = iImageTexture），随 iImageRotationSpeed 自转。用于把真实吸积盘影像
// 投到赤道面做对照。自包含实现，无外部辅助依赖。
// =============================================================================
vec4 ImageDiskColor(vec4 BaseColor, vec4 RayPos, vec4 LastRayPos,
                    vec4 P_cov, vec4 LastP_cov, 
                    float PhysicalSpinA, float PhysicalQ, bool isoutgoing,
                    float EndStepSign, float dlambda,
                    float InterRadius, float OuterRadius,
                    float RedShiftColorExponent, float RedShiftIntensityExponent)
{
    vec4 CurrentResult = BaseColor;
    // 如果不透明度已满，直接返回
    if (CurrentResult.a > 0.99) return CurrentResult;

    // 仅当光线穿过赤道面 (y=0) 时触发运算
    if (LastRayPos.y * RayPos.y >= 0.0) return CurrentResult;

    float StartStepSign = EndStepSign;
    float t_cross = -1.0;
    vec4 DiskHitX = vec4(0.0);
    vec3 DiskHitPos = vec3(0.0);

    // 获取起点和终点的几何信息并升指标，求出坐标对仿射参量的导数 dX/dlambda
    KerrGeometry geo_last;
    ComputeGeometryScalars(LastRayPos.xyz, 1.0, StartStepSign, isoutgoing, geo_last);
    vec4 V0 = RaiseIndex(LastP_cov, geo_last); 
    vec4 T0 = V0 * dlambda; 

    KerrGeometry geo_curr;
    ComputeGeometryScalars(RayPos.xyz, 1.0, EndStepSign, isoutgoing, geo_curr);
    vec4 V1 = RaiseIndex(P_cov, geo_curr);     
    vec4 T1 = V1 * dlambda; 

    // --- 赤道盘相交：三次 Hermite 曲线求根 ---
    float denom = (LastRayPos.y - RayPos.y);
    if(abs(denom) > 1e-9) {
        t_cross = LastRayPos.y / denom; // 线性初猜
        
        // 牛顿迭代求精确零点
        for(int iter = 0; iter < 3; iter++) {
            float t2 = t_cross * t_cross;
            float t3 = t2 * t_cross;
            
            float h00 = 2.0*t3 - 3.0*t2 + 1.0;
            float h10 = t3 - 2.0*t2 + t_cross;
            float h01 = -2.0*t3 + 3.0*t2;
            float h11 = t3 - t2;
            float yt = h00*LastRayPos.y + h10*T0.y + h01*RayPos.y + h11*T1.y;
            
            float dh00 = 6.0*t2 - 6.0*t_cross;
            float dh10 = 3.0*t2 - 4.0*t_cross + 1.0;
            float dh01 = -6.0*t2 + 6.0*t_cross;
            float dh11 = 3.0*t2 - 2.0*t_cross;
            float dyt = dh00*LastRayPos.y + dh10*T0.y + dh01*RayPos.y + dh11*T1.y;
            
            t_cross -= yt / (dyt + 1e-12);
        }
        t_cross = clamp(t_cross, 0.0, 1.0);
        
        // 依据精确 t 计算交点四维坐标
        float t2 = t_cross * t_cross;
        float t3 = t2 * t_cross;
        vec4 H = vec4(2.0*t3 - 3.0*t2 + 1.0, t3 - 2.0*t2 + t_cross, -2.0*t3 + 3.0*t2, t3 - t2);
        DiskHitX = H.x*LastRayPos + H.y*T0 + H.z*RayPos + H.w*T1;
        DiskHitPos = DiskHitX.xyz;
        
        if (length(DiskHitPos.xz) < abs(PhysicalSpinA)) {
            StartStepSign = -EndStepSign;
        }
    } else {
        return CurrentResult;
    }

    // --- 将坐标映射回统一的 Ingoing 参考系 ---
       // --- 将坐标映射回统一的 Ingoing 参考系 ---
    float HitTime_disk = DiskHitX.w;
    vec3 PatternPosDisk = DiskHitPos;
    if (isoutgoing) {
        vec4 tempX = vec4(DiskHitPos, HitTime_disk);
        vec4 dummyP = vec4(0.0);
        float diskSign = (length(DiskHitPos.xz) < abs(PhysicalSpinA)) ? -StartStepSign : StartStepSign;
        transformKerrSchild_YSpin(tempX, diskSign, dummyP, true);
        PatternPosDisk = tempX.xyz;
        HitTime_disk = tempX.w; // 顺手补上时间的更新，保证光线时间滞后计算精确
    }

    // --- 几何与纹理映射逻辑 ---
    float r_xz = length(PatternPosDisk.xz);
    // 挖去内部孔洞
    if (r_xz < InterRadius) return CurrentResult;

    // 【新增：计算旋转角并对坐标进行矩阵旋转】
    float EmissionTime = iBlackHoleTime + HitTime_disk; // 提取光线命中该位置时的物理世界时间
    float rotAngle = iImageRotationSpeed * EmissionTime; // <- 注意：请确保名字与你传入的 uniform 变量名一致
    float cosA = cos(rotAngle);
    float sinA = sin(rotAngle);
    
    // 对坐标进行反向矩阵旋转（等效于图片本身正向旋转）
    vec2 RotatedXZ = mat2(cosA, sinA, -sinA, cosA) * PatternPosDisk.xz;

    // 根据对角线为 OuterRadius 计算正方形边长
    // 对角线 D = OuterRadius，正方形边长 S = D / sqrt(2)
    float ImageWidth = OuterRadius * 0.70710678; 
    float HalfWidth = ImageWidth * 0.5;

    // 剔除正方形边界之外的区域 (改用旋转后的 RotatedXZ)
    if (abs(RotatedXZ.x) > HalfWidth || abs(RotatedXZ.y) > HalfWidth) return CurrentResult;

    // 映射到 [0, 1] 的 UV 坐标 (改用旋转后的 RotatedXZ)
    float U = (RotatedXZ.x + HalfWidth) / ImageWidth;
    float V = (RotatedXZ.y + HalfWidth) / ImageWidth;

    // 使用 textureLod 避免由于控制流分支导致计算 mipmap 梯度报错
    vec4 TexColor = textureLod(iImageTexture, vec2(U, V), 0.0);
    TexColor.xyz*=iBrightmut; 
    TexColor.a*=iDarkmut;   
    if (TexColor.a < 0.01) return CurrentResult; // 纯透明部分跳过

    // --- 提取四维动量用于频移计算 ---
    vec4 HitP_cov = mix(LastP_cov, P_cov, t_cross);

    // 计算静止物体 (U_spatial = 0) 局部能量 E_emit
    // 计算带有圆轨道速度（开普勒运动）的局部能量 E_emit
    KerrGeometry geo_hit;
    float hitSign = (length(DiskHitPos.xz) < abs(PhysicalSpinA)) ? -StartStepSign : StartStepSign;
    ComputeGeometryScalars(DiskHitPos, 1.0, hitSign, isoutgoing, geo_hit);

    // 获取当前交点的等效半径，并算出对应的开普勒角速度（内部有限制防止在半径极小处崩溃）
    float PosR = KerrSchildRadius(DiskHitPos, hitSign);
    float AngularVelocity = GetKeplerianAngularVelocity(max(InterRadius, PosR), 1.0);
    
    // 构造带圆轨道旋转的未归一化四维速度，自旋方向为Y轴 (v_x = -Omega * z, v_z = Omega * x)
    vec4 U_unnorm = vec4(AngularVelocity * DiskHitPos.z, 0.0, -AngularVelocity * DiskHitPos.x, 1.0);

    vec4 U_lower = LowerIndex(U_unnorm, geo_hit);
    float norm_sq = dot(U_unnorm, U_lower);
    float norm = sqrt(max(1e-9, abs(norm_sq))); // 取绝对值并加上容差避免极端超光速情况下的崩溃
    vec4 U_orbit = U_unnorm / norm;

    // 发射能量 = - P_mu * U^mu
    float E_emit = -dot(HitP_cov, U_orbit); 
    float Shift = 1.0 / max(1e-6, abs(E_emit));

    // --- 非黑体天空盒风格频移逻辑 ---
    float EffectiveColorShift = pow(Shift, RedShiftColorExponent);

    vec3 Rcolor = TexColor.r * 1.0 * WavelengthToRgb(max(453.0, 645.0 / EffectiveColorShift));
    vec3 Gcolor = TexColor.g * 1.5 * WavelengthToRgb(max(416.0, 510.0 / EffectiveColorShift));
    vec3 Bcolor = TexColor.b * 0.6 * WavelengthToRgb(max(380.0, 440.0 / EffectiveColorShift));
    vec3 Scolor = Rcolor + Gcolor + Bcolor;

    float OStrength = 0.3 * TexColor.r + 0.6 * TexColor.g + 0.1 * TexColor.b;
    float RStrength = 0.3 * Scolor.r + 0.6 * Scolor.g + 0.1 * Scolor.b;
    Scolor *= OStrength / max(RStrength, 0.001);

    // 亮度频移乘数
    Scolor *= pow(Shift, RedShiftIntensityExponent);

    if (E_emit < 0.0) {
        float cMax = max(max(Scolor.r, Scolor.g), Scolor.b);
        float cMin = min(min(Scolor.r, Scolor.g), Scolor.b);
        Scolor.rgb = vec3(cMax + cMin) - Scolor.rgb;
        if (iWhitehole == 0) {
            Scolor.rgb = vec3(0.0);
            TexColor.a = 0.0;
        }
    }

    // 混合到当前累计颜色
    CurrentResult.rgb += Scolor * TexColor.a * (1.0 - CurrentResult.a);
    CurrentResult.a   += TexColor.a * (1.0 - CurrentResult.a);

    return CurrentResult;
}

// =============================================================================
// 调试视图 2：初始动量可视化（NPGS 逐行移植）。把 TraceRay 入口处的光子四动量
// 与观者标架关系画成颜色，用于排查 GetInitialMomentum 的系/符号/正交化是否正确。
// 仅 iDEBUG==2 时由 TraceRay 调用并直接返回。
// =============================================================================
vec3 DebugInitialMomentum(
    vec4 P_cov, 
    vec4 X, 
    int ObserverMode, 
    float universesign, 
    float PhysicalSpinA, 
    float PhysicalQ, 
    float GravityFade, 
    bool isOutgoing, 
    vec3 CameraVelocity
) {
    if (P_cov == vec4(114514.0)) return vec3(0.0);

    KerrGeometry geo;
    ComputeGeometryScalars(X.xyz, GravityFade, universesign, isOutgoing, geo);

    // 升指标得到逆变动量，计算模长平方（测试类光条件）
    vec4 P_up = RaiseIndex(P_cov, geo);
    float norm_sq = dot(P_cov, P_up);

    // ====================================
    // 重建观者四维速度和局部平直标架
    // ====================================
    vec4 U_up;
    float g_tt = -1.0 + geo.f;
    float time_comp = 1.0 / sqrt(max(1e-9, -g_tt));
    U_up = vec4(0.0, 0.0, 0.0, time_comp);
    
    if (ObserverMode == 1) {
        float r = geo.r; float r2 = geo.r2; float a = PhysicalSpinA; float a2 = geo.a2;
        float y_phys = X.y; 
        float rho2 = r2 + a2 * (y_phys * y_phys) / (r2 + 1e-9);
        float Q2 = PhysicalQ * PhysicalQ;
        float MassChargeTerm = 2.0 * CONST_M * r - Q2;
        float Xi = sqrt(max(0.0, MassChargeTerm * (r2 + a2)));
        float DenomPhi = rho2 * (MassChargeTerm + Xi);
        float U_phi_KS = (abs(DenomPhi) > 1e-9) ? (-MassChargeTerm * a / DenomPhi) : 0.0;
        float U_r_KS = -Xi / max(1e-9, rho2);
        float inv_r2_a2 = 1.0 / (r2 + a2);
        float Ux_rad = (r * X.x - a * X.z) * inv_r2_a2 * U_r_KS;
        float Uz_rad = (r * X.z + a * X.x) * inv_r2_a2 * U_r_KS;
        float Uy_rad = (X.y / r) * U_r_KS;
        float Ux_tan =  X.z * U_phi_KS;
        float Uz_tan = -X.x * U_phi_KS;
        
        vec3 U_spatial = vec3(Ux_rad + Ux_tan, Uy_rad, Uz_rad + Uz_tan);
        float l_dot_u_spatial = dot(geo.l_down.xyz, U_spatial);
        float U_spatial_sq = dot(U_spatial, U_spatial);
        float A = -1.0 + geo.f;
        float B = 2.0 * geo.f * l_dot_u_spatial;
        float C = U_spatial_sq + geo.f * (l_dot_u_spatial * l_dot_u_spatial) + 1.0; 
        float Det = max(0.0, B*B - 4.0 * A * C);
        float Ut = (abs(A) < 1e-7) ? (-C / max(1e-19, B)) : ((B < 0.0) ? (2.0 * C / (-B + sqrt(Det))) : ((-B - sqrt(Det)) / (2.0 * A)));
        
        U_up = mix(vec4(0.0, 0.0, 0.0, time_comp), vec4(U_spatial, Ut), GravityFade);
    } else if (ObserverMode == 2) {
        vec3 v_in = CameraVelocity;
        if (any(isnan(v_in)) || any(isinf(v_in))) v_in = vec3(0.0);
        vec4 V_up = vec4(v_in, 1.0);
        vec4 V_down = LowerIndex(V_up, geo);
        float V_sq = dot(V_up, V_down);
        if (V_sq < 0.0) U_up = V_up * inversesqrt(-V_sq);
    }
    vec4 U_down = LowerIndex(U_up, geo);

    vec3 m_r = -normalize(X.xyz);
    vec3 WorldUp = vec3(0.0, 1.0, 0.0);
    if (abs(dot(m_r, WorldUp)) > 0.999) WorldUp = vec3(1.0, 0.0, 0.0);
    vec3 m_phi = normalize(cross(WorldUp, m_r)); 
    vec3 m_theta = cross(m_phi, m_r); 

    vec4 e1 = vec4(m_r, 0.0); e1 += dot(e1, U_down) * U_up; vec4 e1_d = LowerIndex(e1, geo); float n1 = sqrt(max(1e-9, dot(e1, e1_d))); e1 /= n1; e1_d /= n1;
    vec4 e2 = vec4(m_theta, 0.0); e2 += dot(e2, U_down) * U_up; e2 -= dot(e2, e1_d) * e1; vec4 e2_d = LowerIndex(e2, geo); float n2 = sqrt(max(1e-9, dot(e2, e2_d))); e2 /= n2; e2_d /= n2;
    vec4 e3 = vec4(m_phi, 0.0); e3 += dot(e3, U_down) * U_up; e3 -= dot(e3, e1_d) * e1; e3 -= dot(e3, e2_d) * e2; vec4 e3_d = LowerIndex(e3, geo); e3 /= sqrt(max(1e-9, dot(e3, e3_d)));

    // ====================================
    // 合法性检查与局部方向投影
    // ====================================
    // 计算光在观者局部正交标架下的空间动量: P_local^i = P_mu e_i^mu
    vec3 p_local = vec3(dot(P_cov, e1), dot(P_cov, e2), dot(P_cov, e3));
    vec3 local_dir = normalize(p_local); 

    // r通道：检查所有约束条件是否满足
    bool is_lightlike = abs(norm_sq) < 1e-4;    // 约束1：必须严格类光
    float E_loc = -dot(P_cov, U_up);
    bool is_energy_pos = true;//E_loc > 0.0;           // 约束2：观者测量的局部能量必须为正
    bool is_forward = true;//P_up.w > 0.0;             // 约束3：坐标时间分量一般向未来流动

    float valid_r = (is_lightlike && is_energy_pos && is_forward) ? 1.0 : 0.0;

    // g,b通道：将局部光线的x、y方向投影到 [0, 1] 颜色区间
    float g_chan = local_dir.x * 0.5 + 0.5;
    float b_chan = local_dir.y * 0.5 + 0.5;

    return vec3(valid_r, g_chan, b_chan);
}

// =============================================================================
// SECTION: 阴影剔除辅助函数（NPGS 逐行移植）
// 原为 Phase 1.5 的 return 0.0 空壳（当时 iEnableShadowCulling 恒 0、分支不可达）。
// 该开关已在本项目接通到 KerrParams.shadowCulling，空壳会让阴影几何退化（比较量恒 0），
// 故在此恢复 NPGS 原版实现：光子球/临界碰撞参数 → 静态观者角 → 落体观者光行差。
// =============================================================================
float SolveCubicMaxReal(float P, float K) {
    if (P >= 0.0) return 0.0; // 理论上黑洞情形 P 均为负
    float sqrt_term = sqrt(-P / 3.0);
    // 限制 acos 输入在 [-1, 1] 防止 NaN
    float val = (3.0 * K) / (2.0 * P) * sqrt(-3.0 / P);
    float acos_term = acos(clamp(val, -1.0, 1.0));
    return 2.0 * sqrt_term * cos(acos_term / 3.0);
}

float SolveQuarticU(float M, float Q, float a, float sign_term, bool is_max_root) {
    float M2 = M * M;
    float Q2 = Q * Q;

    // 系数
    float c2 = 2.0 * Q2 - 3.0 * M2;
    float c1 = sign_term * (-2.0 * a * M2);
    float c0 = Q2 * Q2 - M2 * Q2;

    // 初始猜测：
    // 顺行(A, 小根)，u 较小 (r 接近 M 或 2M)
    // 逆行(B, 大根)，u 较大 (r 接近 3M 或 4M)
    float u = is_max_root ? 2.2 * M : 0.8 * M;

    // 牛顿迭代求解
    for (int i = 0; i < 8; i++) {
        float u2 = u * u;
        float u3 = u2 * u;

        float f  = u2 * u2 + c2 * u2 + c1 * u + c0;
        float df = 4.0 * u3 + 2.0 * c2 * u + c1;

        if (abs(df) < 1e-6) break;
        u = u - f / df;
    }
    return abs(u);
}

float GetDropFrameAngle(float SinThetaStat, float CosThetaStat, float r, float M, float Q, float a, int ObserverMode) {
    // 静态观者 (ObserverMode == 0)
    if (ObserverMode == 0) {
        return atan(SinThetaStat, CosThetaStat);
    }

    // 落体观者 (ObserverMode == 1)
    float a2 = a * a;
    float r2 = r * r;
    float MassChargeTerm = 2.0 * M * r - Q * Q;

    float numerator_v = MassChargeTerm * (r2 + a2);
    float denominator_v = r2 * (r2 + a2) + a2 * MassChargeTerm;

    float v_sq = numerator_v / max(1e-9, denominator_v);
    v_sq = (1.0 + 0.05 * a) * min(0.9999, max(0.0, v_sq)); // 略微加速、增强收缩，作为冗余
    float v = sqrt(v_sq);

    // 应用相对论光行差
    // sin(θ') = sin(θ) * sqrt(1-v^2) / (1 + v*cos(θ))
    // cos(θ') = (cos(θ) + v) / (1 + v*cos(θ))
    float denom = 1.0 + v * CosThetaStat;
    float sin_fall = SinThetaStat * sqrt(max(0.0, 1.0 - v_sq));
    float cos_fall = CosThetaStat + v;

    return atan(sin_fall, cos_fall);
}

float GetShadowHalfAngleRN(float r, float M, float Q, int ObserverMode)
{
    float M2 = M * M;
    float Q2 = Q * Q;
    float r2 = r * r;

    // 光子球半径 r_ps
    float term_root = sqrt(max(0.0, 9.0 * M2 - 8.0 * Q2));
    float r_ps = 0.5 * (3.0 * M + term_root);

    // 临界碰撞参数 b_c
    float metric_factor_ps = 1.0 - 2.0 * M / r_ps + Q2 / (r_ps * r_ps);
    float b_c = r_ps / sqrt(max(1e-6, metric_factor_ps));

    // 计算静态观者的 Sin 和 Cos
    // f(r) = 1 - 2M/r + Q^2/r^2
    float f_r = 1.0 - 2.0 * M / r + Q2 / r2;
    float sqrt_f = sqrt(max(0.0, f_r));

    // Sin = (b_c / r) * sqrt(f)
    float sin_theta_stat = (b_c / r) * sqrt_f;

    // 判断光子球内外来决定 Cos 的符号
    // r < r_ps 时，阴影遮挡超过半个天空，为钝角 (Cos < 0)
    // 增加一个微小的 epsilon 防止 r == r_ps 时闪烁
    float cos_sign = (r >= r_ps - 1e-4) ? 1.0 : -1.0;

    // 计算 Cos
    float cos_theta_stat = cos_sign * sqrt(max(0.0, 1.0 - sin_theta_stat * sin_theta_stat));

    // 换坐标系
    return GetDropFrameAngle(sin_theta_stat, cos_theta_stat, r, M, Q, 0.0, ObserverMode);
}

// =============================================================================
// SECTION7: KN阴影计算
// =============================================================================

bool IsAccretionDiskVisible(float InterR, float OuterR, float Thin, float Hopper, float Bright, float Dark)
{
    if(iUseImageDisk==0){
    if (InterR >= OuterR) return false;
    if (Thin <= 0.0 && Hopper == 0.0) return false;
    if (Bright <= 0.0 && Dark < 0.0) return false;
    }else{
    if (InterR >= OuterR) return false;
    if (Bright <= 0.0 && Dark < 0.0) return false;
    }
    return true;
}

bool IsJetVisible(float AccretionRate, float JetBright)
{
    if (AccretionRate < 1e-2) return false;
    if (JetBright <= 0.0) return false;
    return true;
}

// 求解极轴视角的临界半径 (三次方程最大实根)
// x^3 + Px + K = 0, x = r - M

// 求解赤道视角光子球参数 u (四次方程)

// 将静态观测者的正弦值转换为落体观测者

// 计算 R-N (a=0) 黑洞的阴影半张角 


// =============================================================================
// SECTION8: 初始射线构造与主追踪
// =============================================================================

// TAA 亚像素抖动（单位:像素）：时间种子使每帧每像素偏移不同，配合历史混合收敛
vec2 ScreenJitter(vec2 Uv, vec2 iResolution)
{
    return vec2(RandomStep(Uv, fract(iTime * 1.0 + 0.5)), RandomStep(Uv, fract(iTime * 1.0))) / iResolution;
}

// 屏幕 UV（TraceRay 入口已翻转为下→上惯例）→ 相机系视方向（-Z 前，+X 右，+Y 上）
vec3 BuildViewDirLocal(vec2 FragUv)
{
    float Fov = tan(iFovRadians / 2.0); // iFovRadians: 相机垂直视场角（弧度）
    return FragUvToDir(FragUv + 0.25 * ScreenJitter(FragUv, iResolution), Fov, iResolution);
}

// 由宿主传入的吸积盘法向(=自旋轴)/切向（相机系）构造黑洞局部正交标架：
//   out LocalToWorld: 局部(Y=自旋轴,X=盘切向,Z=右手补全) → 世界 的旋转；返回其转置(世界→局部)
mat3 BuildWorldToLocal(out mat3 LocalToWorld)
{
    vec3 DiskNormalWorld  = normalize((iInverseCamRot * vec4(iBlackHoleRelativeDiskNormal.xyz, 0.0)).xyz);
    vec3 DiskTangentWorld = normalize((iInverseCamRot * vec4(iBlackHoleRelativeDiskTangen.xyz, 0.0)).xyz);
    vec3 BH_Y = DiskNormalWorld;
    vec3 BH_X = normalize(DiskTangentWorld - dot(DiskTangentWorld, BH_Y) * BH_Y); // 切向对法向正交化
    vec3 BH_Z = normalize(cross(BH_X, BH_Y));
    LocalToWorld = mat3(BH_X, BH_Y, BH_Z);
    return transpose(LocalToWorld);
}

// 相机远在包围球外时解析求射线(RaymarchingBoundary-1 球面)入射点并推进起点，跳过真空空步。
// 未命中球面 → bMarch=false 且 bWaitCalBack=true（直接按逃逸处理）。返回推进距离（供坐标时间 X.w）。
float AdvanceToMarchingBoundary(inout vec3 Origin, vec3 Dir, float Boundary, out bool bMarch, out bool bWaitCalBack)
{
    bMarch = true; bWaitCalBack = false;
    float r = Boundary - 1.0;
    float b = dot(Origin, Dir);
    float c = dot(Origin, Origin) - r * r;
    float delta = b * b - c;
    if (delta < 0.0) { bMarch = false; bWaitCalBack = true; return 0.0; }
    float tEnter = -b - sqrt(delta);
    if (tEnter > 0.0) { Origin += Dir * tEnter; return tEnter; }
    if (-b + sqrt(delta) <= 0.0) { bMarch = false; bWaitCalBack = true; return 0.0; }
    return 0.0; // 相机已在球内：不需推进
}

struct TraceResult {
    vec3  EscapeDir;      // 最终逸出方向 (World Space)
    float FreqShift;      // 频移 (E_emit / E_obs)
    float Status;         // 0=Stop, 1=Sky, 2=Antiverse,3=不透明体积,4=光来自上个宇宙（出白洞）,5=光来自上个宇宙的antiverse
    vec4  AccumColor;     // 体积光颜色 (吸积盘+喷流)
    float CurrentSign;    // 最终宇宙符号
};

#define SHADOW_SIZE_MULTIPLIER    0.995     // 阴影半径微调系数 (Multiplier for shadow radius)

// 阴影剔除：判定当前像素视线是否落入克尔/RN 阴影。
//   - 命中且无盘无喷流 → 填好 res 并返回 true（调用方立即 return res）。
//   - 命中但有盘/喷流 → 提升 TerminationR、置 bDeferredShadowCulling（延迟剔除），返回 false。
// 无论如何都会计算 ProgradePhotonRadius（供主循环光子壳剔除使用）。
bool ComputeShadowCulling(
    vec3 RayPosLocal, vec3 RayDir, vec4 X,
    float CameraStartR, float EventHorizonR,
    bool bIsNakedSingularity, bool bShouldContinueMarchRay,
    float CurrentUniverseSign,
    inout float TerminationR, inout bool bDeferredShadowCulling,
    out float ProgradePhotonRadius,
    inout vec4 Result,
    inout TraceResult res)
{
    //计算光子壳最窄处作为回落剔除判断
    float AbsSpin = abs(CONST_M * iSpin);
    float Q2 = iQ * iQ * CONST_M * CONST_M; // Q^2
    

    float AcosTerm = acos(clamp(-abs(iSpin), -1.0, 1.0));
    float PhCoefficient = 1.0 + cos(0.66666667 * AcosTerm);
    float r_guess = 2.0 * CONST_M * PhCoefficient; 
    float r = r_guess;
    float sign_a = 1.0; 
    
    for(int k=0; k<3; k++) {
        float Mr_Q2 = CONST_M * r - Q2;
        float sqrt_term = sqrt(max(0.0001, Mr_Q2)); 
        
        // 方程 f(r)
        float f = r*r - 3.0*CONST_M*r + 2.0*Q2 + sign_a * 2.0 * AbsSpin * sqrt_term;
        
        // 导数 f'(r)
        float df = 2.0*r - 3.0*CONST_M + sign_a * AbsSpin * CONST_M / sqrt_term;
        
        if(abs(df) < 0.00001) break;
    
        r = r - f / df;
    }
    
    ProgradePhotonRadius = r;


    // -------------------------------------------------------------------------
    // 阴影剔除逻辑
    // -------------------------------------------------------------------------

    //非裸奇点、在universe侧、距离足够远(>RN视界或KN逆行光子轨道)、当前还需要继续步进
    float AbsSpinA = abs(CONST_M * iSpin);
    bool bIsRot = AbsSpinA > 1e-5;
    
    // 判定是否需要剔除：
    // 非裸奇点 (视界存在),在正宇宙 (CurrentUniverseSign > 0),当前光线还需要继续步进 (bShouldContinueMarchRay)
    if (!bIsNakedSingularity && CurrentUniverseSign > 0.0 && bShouldContinueMarchRay && iGrid==0 && iWhitehole==0 &&(iObserverMode==0 || iObserverMode==1 ) && iEnableShadowCulling==1)
    {
        // 预计算剔除启动的阈值半径
        float CullingStartRadius;
        
        if (!bIsRot) {
            // 纯RN/史瓦西黑洞：允许进入光子球内部，直到非常接近视界
            CullingStartRadius = 1.005 * EventHorizonR;
        } else {
            // 计算逆行光子轨道半径 r_B (凸出侧)
            // 使用 SolveQuarticU 计算 r_B (对应参数 +1.0)
            float u_B_calc = SolveQuarticU(CONST_M, PhysicalQ, AbsSpinA, 1.0, true);
            float r_B_calc = (u_B_calc * u_B_calc + PhysicalQ * PhysicalQ) / CONST_M;

            CullingStartRadius = r_B_calc + 0.05;
        }
        if (CameraStartR > CullingStartRadius)
        {
            // 计算视线与黑洞中心的夹角
            vec3 ToCenterDir = -normalize(RayPosLocal); // 局部系下黑洞在原点
            float CosAlpha = dot(normalize(RayDir), ToCenterDir);
            float RayAngle = acos(clamp(CosAlpha, -1.0, 1.0)); // 当前像素视线与黑洞中心的夹角

            // 估算阴影的大致可能张角，仅在这个区域内进一步计算
            float SafetyFactor = 2.5 + 1.1 * abs(iSpin) - 0.5*iQ;
            float MaxShadowAngleEstimate = SafetyFactor * (2.0 * CONST_M) / max(1e-6, CameraStartR);
            if (RayAngle < MaxShadowAngleEstimate || CameraStartR < 3.0*EventHorizonR) // 大致朝向黑洞或在光子球内
            {
                float RayAngle = acos(CosAlpha); // 当前像素视线与黑洞中心的夹角
                bool bHitShadow = false; 
                
                if (!bIsRot)
                {
                    //球对称
                    float ShadowHalfAngle = GetShadowHalfAngleRN(CameraStartR, CONST_M, PhysicalQ, iObserverMode);
                    ShadowHalfAngle *= SHADOW_SIZE_MULTIPLIER;
                    
                    if (RayAngle < ShadowHalfAngle) bHitShadow = true;
                }
                else
                {
                    float M = CONST_M;
                    float Q = PhysicalQ;
                    float a = PhysicalSpinA; 
                    float a_abs = AbsSpinA; 
                    float Q2 = Q*Q;
                    float a2 = a_abs*a_abs;
                    float r = CameraStartR;
                    
                    //极轴视角
                    float P = a2 + 2.0*Q2 - 3.0*M*M;
                    float K = 2.0*Q2*M + 2.0*M*a2 - 2.0*M*M*M;
                    float x_pole = SolveCubicMaxReal(P, K);
                    float r_p = M + x_pole;
                    float b_pole = sqrt(max(0.0, (2.0*r_p*(r_p*r_p + a2))/(r_p - M))); // 碰撞参数
                    
                    float Delta_r = r*r - 2.0*M*r + a2 + Q2;
                    
                    float SinOF_Stat = b_pole * sqrt(max(0.0, Delta_r)) / (r*r + a2);
                    // 极轴视角在剔除区(r > r_B > r_ps) 总是锐角
                    float CosOF_Stat = sqrt(max(0.0, 1.0 - SinOF_Stat * SinOF_Stat));
                    
                    float AngleOF = GetDropFrameAngle(SinOF_Stat, CosOF_Stat, r, M, Q, a_abs, iObserverMode);
                    float LatFactor = abs(X.y) / length(X.xyz);

                    if (LatFactor > 0.99999)
                    {
                        float effectiveMult = SHADOW_SIZE_MULTIPLIER ;
                        if (RayAngle < AngleOF * effectiveMult) bHitShadow = true;
                    }
                    else
                    {
                        
                        // 赤道视角
                        // A点 (缺口/顺行): 对应方程减号项(-2a...), 且取较小根
                        float u_A = SolveQuarticU(M, Q, a_abs, -1.0, true); 
                        float r_A_rad = (u_A * u_A + Q2) / M;
                        
                        float u_B = SolveQuarticU(M, Q, a_abs, 1.0, true); 
                        float r_B_rad = (u_B * u_B + Q2) / M;
                        
                        float safe_a = max(1e-5, a_abs);
                        // Xi_A (缺口侧)
                        // Formula: xi = (r^2(3M-r) - a^2(M+r) - 2Q^2r) / (a(r-M))
                        float num_A = r_A_rad * r_A_rad * (3.0 * M - r_A_rad) - a2 * (M + r_A_rad) - 2.0 * Q2 * r_A_rad;
                        float xi_A = num_A / max(1e-9, safe_a * (r_A_rad - M));
                        // Xi_B (凸起侧)
                        float num_B = r_B_rad * r_B_rad * (3.0 * M - r_B_rad) - a2 * (M + r_B_rad) - 2.0 * Q2 * r_B_rad;
                        float xi_B = num_B / max(1e-9, safe_a * (r_B_rad - M));
                        
                        float Mr_Q2_Shadow = 2.0 * M * r - Q2;
                        float Sigma_Shadow = r * r; 
                        // 计算 BL 度规分量 (用于静态投影公式)
                        float g_tt_stat = -(1.0 - Mr_Q2_Shadow / Sigma_Shadow);
                        float gtphi_stat = -a_abs * Mr_Q2_Shadow / Sigma_Shadow; 
                        float D_cyl = gtphi_stat * gtphi_stat - g_tt_stat * (Sigma_Shadow + a2 + Mr_Q2_Shadow * a2 / Sigma_Shadow);
                        float InvSqrtD = 1.0 / sqrt(max(1e-9, D_cyl));
                        // 计算坐标系扭曲近似修正，Kerr-Schild 坐标系的径向与 Boyer-Lindquist 不同，存在 phi 方向的偏移。近似修正 a * r / Delta
                        float TwistCorrection = safe_a * r / max(1e-5, Delta_r);
                        
                        float SinOA_Stat = abs((xi_A + TwistCorrection) * g_tt_stat + gtphi_stat) * InvSqrtD;
                        float SinOB_Stat = abs((xi_B + TwistCorrection) * g_tt_stat + gtphi_stat) * InvSqrtD;
                        
                        // 计算 Cos (锐角)
                        float CosOA_Stat = sqrt(max(0.0, 1.0 - SinOA_Stat * SinOA_Stat));
                        float CosOB_Stat = sqrt(max(0.0, 1.0 - SinOB_Stat * SinOB_Stat));
                        
                        // 中心偏移 E
                        // 经测试，若使用近似公式 a*(rE+M)/(rE-M) ，则结果偏大 (偏向B点)，且a*越大、相机r越小，偏差越明显。此外，使用2.0*a将偏B，使用1.0*a将偏A。故取中间值 a(近时)到a*5/3(远时) 作为基准。
                        // 同时也叠加 TwistCorrection (因为坐标系扭曲是全局的)。
                        float xi_E_Corrected = (1.6666-2.0/r) * safe_a + TwistCorrection;
                        float SinOE_Stat = abs(xi_E_Corrected * g_tt_stat + gtphi_stat) * InvSqrtD;
                        float CosOE_Stat = sqrt(max(0.0, 1.0 - SinOE_Stat * SinOE_Stat));
                        
                        // 转换为落体视角角度
                        float AngleOA0 = GetDropFrameAngle(SinOA_Stat, CosOA_Stat, r, M, Q, a_abs, iObserverMode);
                        float AngleOB0 = GetDropFrameAngle(SinOB_Stat, CosOB_Stat, r, M, Q, a_abs, iObserverMode);
                        float AngleOE0 = GetDropFrameAngle(SinOE_Stat, CosOE_Stat, r, M, Q, a_abs, iObserverMode);
                        // 垂直半轴 EC 在数学上可证明和相同Q的RN黑洞完全一致
                        float AngleEC0 = GetShadowHalfAngleRN(r, M, Q, iObserverMode);
                        
                        // 混合
                        // 调试：如何选择混合函数。需要是一些凹函数。
                        float MixWA = clamp(tan(LatFactor*1.48)/10.98338,0.0,1.0);//指数函数在这里会前期太小、后期太大，所以用tan
                        float MixWB = pow(LatFactor, 2.5);//基本确定2.5
                        float MixWE = pow(LatFactor, 6.0);//基本确定6.0
                        float MixWCD = pow(LatFactor, 0.75);//基本确定0.75
                        
                        float AngleOA = mix(AngleOA0, AngleOF, MixWA);
                        float AngleOB = mix(AngleOB0, AngleOF, MixWB);
                        float AngleEC = mix(AngleEC0, AngleOF, MixWCD);
                        float AngleOE = mix(AngleOE0, 0.0,     MixWE);

                        float AberrationShift = 0.0;
                        if (iObserverMode == 1) {
                            // gtphi_stat是在赤道面上算出的，实际上切向速度随纬度变化(在两极处为0)
                            float SinTheta = sqrt(max(0.0, 1.0 - LatFactor * LatFactor));
                            float SinAberration = abs(gtphi_stat) * InvSqrtD * SinTheta; 
                            float CosAberration = sqrt(max(0.0, 1.0 - SinAberration * SinAberration));
                            // 算出横向光行差导致的视角偏移量
                            AberrationShift = 2.0*0.6666*a_abs*GetDropFrameAngle(SinAberration, CosAberration, r, M, Q, a_abs, iObserverMode); 
                        }
                        // 视平面判定
                        // 局部系，Y是自旋轴。ToCenterDir是视线反向
                        vec3 SpinAxis = vec3(0.0, 1.0, 0.0);
                        vec3 ScreenUp = normalize(SpinAxis - dot(SpinAxis, ToCenterDir) * ToCenterDir);
                        vec3 ScreenRight = cross(ToCenterDir, ScreenUp);
                        vec3 VecToPixel = normalize(RayDir - dot(RayDir, ToCenterDir) * ToCenterDir);
                        float ProjU = dot(VecToPixel, ScreenRight);
                        float ProjV = dot(VecToPixel, ScreenUp);
                        float x_ang = ProjU * RayAngle;
                        float y_ang = ProjV * RayAngle;
                        
                        // 手性：a>0 时，凸起(B)在U轴正向(Right)，缺口(A)在U轴负向(Left)，E 点向 B 侧偏移
                        float SignChirality = sign(a); 
                        if (abs(a) < 1e-9) SignChirality = 1.0;
                        // E 的位置 (在 U 轴上的坐标)
                        float CenterEx = SignChirality * (AngleOE + AberrationShift);
                        float dx = x_ang - CenterEx;
                        float dy = y_ang;
                        
                        float RadiusA_from_E = 0.99*(AngleOA + AngleOE);
                        float RadiusB_from_E = max(1e-5, AngleOB - AngleOE);
                        
                        float CurrentHRadius;
                        float CurrentVRadius = AngleEC;
                        
                        if (iDEBUG == 1)
                        {
                            vec2 currP = vec2(x_ang, y_ang);
                            float dotSize = 0.002; // 调试点大小（弧度）
                            
                            // O是白色
                            vec2 ptO = vec2(0.0, 0.0);
                            if (length(currP - ptO) < dotSize) {
                                Result += 0.3*vec4(1.0, 1.0, 1.0, 1.0);
                                res.Status = 3.0; return true;
                            }
                            
                            // C、D、E是蓝色
                            vec2 ptE = vec2(CenterEx, 0.0);
                            vec2 ptC = vec2(CenterEx,  AngleEC);
                            vec2 ptD = vec2(CenterEx, -AngleEC);
                            if (length(currP - ptE) < dotSize || length(currP - ptC) < dotSize || length(currP - ptD) < dotSize) {
                                Result += 0.3*vec4(0.0, 0.5, 1.0, 1.0);
                                res.Status = 3.0; return true;
                            }
                            
                            // A、B是红色
                            vec2 ptA = vec2(CenterEx - SignChirality * RadiusA_from_E, 0.0);
                            vec2 ptB = vec2(CenterEx + SignChirality * RadiusB_from_E, 0.0);
                            if (length(currP - ptA) < dotSize || length(currP - ptB) < dotSize) {
                                Result += 0.3*vec4(1.0, 0.0, 0.0, 1.0);
                                res.Status = 3.0; return true;
                            }
                        }
                        
                        // 判断是在 E 的 "A侧" 还是 "B侧"
                        if (dx * SignChirality > 0.0) {
                            CurrentHRadius = RadiusB_from_E; // B侧 (凸起)
                        } else {
                            CurrentHRadius = RadiusA_from_E; // A侧 (缺口)
                            // A侧修正系数
                            float a_star = a_abs / CONST_M; 
                            float f4 = clamp(1.0-((r-30.0)/(80.0-30.0)),0.0,1.0); // 相机距离较远时，避免拉伸
                            float f3 = clamp((a_star - 0.9) / 0.1, 0.0, 1.0); // a*不高时，D形不明显，边缘还是接近椭圆的。所以，修正仅在 a* > 0.9 时生效，1.0时达到最大
                            float f2 = pow(1.0 - LatFactor, 1.0); // 随相机纬度变化。在到达极轴时，应完全没有修正，变回圆形
                            float u = clamp(abs(dx) / RadiusA_from_E, 0.0, 1.0); // u=1表示在A点(边缘)，u=0表示在E点。
                            float f1 = 0.36 * pow(u, 3.5); // 使用 pow 确保靠近中心时修正迅速消失
                            // 缩放使得原本在椭圆外的点被包含进阴影，形成比半椭圆更丰满的"D"形
                            CurrentVRadius *= (1.0 + f1 * f2 * f3 * f4);
                            float f5 = (1.0-2.0*LatFactor)*(1.0-pow(abs(iQ),0.1));
                            CurrentHRadius *= 1.0+25.0*f4*f5*clamp(a_star - 0.98,0.0,0.02)*clamp(a_star - 0.98,0.0,0.02);
                        }
                        
                        float dist_sq = (dx*dx) / (CurrentHRadius*CurrentHRadius) + (dy*dy) / (CurrentVRadius*CurrentVRadius);
                        if (dist_sq < SHADOW_SIZE_MULTIPLIER * SHADOW_SIZE_MULTIPLIER) bHitShadow = true;
                    }
                }
                
                //执行剔除

                if (bHitShadow)
                {
                    bool bHasDisk = IsAccretionDiskVisible(iInterRadiusRs, iOuterRadiusRs, iThinRs, iHopper, iBrightmut, iDarkmut);
                    bool bHasJet  = IsJetVisible(iAccretionRate, iJetBrightmut);
                    
                    if (!bHasDisk && !bHasJet)
                    {
                        // 纯黑洞，无盘无喷流：立即返回黑色
                        if(iDEBUG==1)
                        {
                            res.AccumColor = vec4(0.0, 0.5, 0.0, 1.0); 
                            res.Status = 3.0; 
                        }else{
                            res.AccumColor = vec4(0.0, 0.0, 0.0, 1.0); 
                            res.Status = 3.0;
                        }
                        res.CurrentSign = CurrentUniverseSign;
                        res.EscapeDir = vec3(0.0);
                        res.FreqShift = 1.0;
                        return true; 
                    }
                    else
                    {
                        // 有盘或喷流，改终结半径，延迟剔除
                        float SafeCullRadius = max(iInterRadiusRs, 1.05 * EventHorizonR);
                        if (SafeCullRadius > TerminationR)
                        {
                            TerminationR = SafeCullRadius;
                            bDeferredShadowCulling = true; // 标记：这是因为剔除而提升的终结半径
                        }
                    }
                }
            }
        }
    }

    return false;
}

TraceResult TraceRay(vec2 FragUv)
{
    TraceResult res;
    res.EscapeDir = vec3(0.0);
    res.FreqShift = 0.0;
    res.Status    = 0.0; // Default: Stop
    res.AccumColor = vec4(0.0);

    // 盘环境光弥散项的全天空辐照：每像素一次 6 向 cubemap 采样，DiskColor 内零开销复用
    if (iDiskAmbient > 0.0) {
        gDiskAmbientSky = (AMBIENT_PHASE / 6.0) * (
            textureLod(iBackground0, vec3( 1.0, 0.0, 0.0), 0.0).rgb + textureLod(iBackground0, vec3(-1.0, 0.0, 0.0), 0.0).rgb +
            textureLod(iBackground0, vec3(0.0,  1.0, 0.0), 0.0).rgb + textureLod(iBackground0, vec3(0.0, -1.0, 0.0), 0.0).rgb +
            textureLod(iBackground0, vec3(0.0, 0.0,  1.0), 0.0).rgb + textureLod(iBackground0, vec3(0.0, 0.0, -1.0), 0.0).rgb);
    }

    bool bDeferredShadowCulling = false;

    // ---- 射线构造：屏幕像素 → 相机系视方向 →（下方）黑洞局部系原点/方向 ----
    FragUv.y = 1.0 - FragUv.y;                     // Vulkan 帧缓冲行序上→下，翻转为 GL 惯例下→上（全流程唯一一次 y 翻转）
    vec3 ViewDirLocal = BuildViewDirLocal(FragUv); // 相机系视方向（-Z 前，含 0.25 像素 TAA 抖动）

    // -------------------------------------------------------------------------
    // 物理常数与黑洞参数
    // -------------------------------------------------------------------------
    // （吸积盘物态参量 DiskArgument/PeakTemperature 已提取为 ComputeDiskArgument，
    //  唯一消费方是 DiskColor，此处不再重复计算）

    // 视界位置
    float HorizonDiscrim = 0.25 - PhysicalSpinA * PhysicalSpinA - PhysicalQ * PhysicalQ;
    float EventHorizonR = 0.5 + sqrt(max(0.0, HorizonDiscrim));
    float InnerHorizonR = 0.5 - sqrt(max(0.0, HorizonDiscrim));
    bool  bIsNakedSingularity = HorizonDiscrim < 0.0;

    // 包围球
    float RaymarchingBoundary = max(max(iOuterRadiusRs + 1.0, 501.0),iSpin*2.0);
    float BackgroundShiftMax = 2.0;
    float ShiftMax = 1.0; //吸积盘亮度最大值
    float CurrentUniverseSign = iUniverseSign;
    if (iBlackHoleMassSol<0.0)
    {
        CurrentUniverseSign=-CurrentUniverseSign;
    }

    // -------------------------------------------------------------------------
    // 相机系统与坐标变换（基底构造见 BuildWorldToLocal，跳空段见 AdvanceToMarchingBoundary）
    // -------------------------------------------------------------------------
    mat3 LocalToWorldRot;
    mat3 WorldToLocalRot = BuildWorldToLocal(LocalToWorldRot);

    vec3 RayPosLocal;                 // 光线起点：黑洞局部系（黑洞在原点，Y=自旋轴）
    vec3 RayDir;                      // 光线方向：黑洞局部系，单位向量
    if (iObserverMode == -1) {
        // 测地线观者：宿主已把位置/视锥方向放在正确的局域 KS 系，直接采用
        RayPosLocal = iBlackHoleRelativePosRs.xyz;
        RayDir      = normalize(ViewDirLocal);
    } else {
        // 静态/落体/三维速度观者：相机系数据 → 世界系（iInverseCamRot: 相机→世界）→ 黑洞局部系
        vec3 CamToWorldViewDir      = (iInverseCamRot * vec4(ViewDirLocal, 0.0)).xyz;
        vec3 CamPosWorld            = -(iInverseCamRot * vec4(iBlackHoleRelativePosRs.xyz, 0.0)).xyz; // 黑洞在原点：相机位 = -(相机→黑洞向量)
        RayPosLocal = WorldToLocalRot * CamPosWorld;
        RayDir      = normalize(WorldToLocalRot * CamToWorldViewDir);
    }

    vec4 Result = vec4(0.0);
    bool bShouldContinueMarchRay = true;
    bool bWaitCalBack = false;
    float DistanceToBlackHole = length(RayPosLocal);

    // 相机远在包围球外时：解析求射线-球面交点，直接推进到入射点，跳过真空段空步
    vec4 X = vec4(0.0);
    if (DistanceToBlackHole > RaymarchingBoundary)
    {
        X.w -= AdvanceToMarchingBoundary(RayPosLocal, RayDir, RaymarchingBoundary,
                                         bShouldContinueMarchRay, bWaitCalBack);
    }
    X.xyz = RayPosLocal;

    vec4 P_cov = vec4(0.0,0.0,0.0,-1.0);

    float E_conserved = 1.0;
    vec3 LastDir = RayDir;
    vec3 LastPos = RayPosLocal;
    float GravityFade = CubicInterpolate(max(min(1.0 - (length(RayPosLocal) - 100.0) / (RaymarchingBoundary - 100.0), 1.0), 0.0));

    if (iEnableHeatHaze == 1 &&  iInWhichUniverse==0 && CurrentUniverseSign>0.0)//热折射
    {
        // 1. 坐标与参数准备 (Rg 空间)
        vec3 pos_Rg_Start = X.xyz; 
        vec3 rayDirNorm = normalize(RayDir);

        float totalProbeDist = float(HAZE_PROBE_STEPS) * HAZE_STEP_SIZE;
        
        // [适配] 使用 iTime，取模防止噪声溢出
        float hazeTime = mod(iBlackHoleTime, 1000.0); 

        

        //几何剔除优化
        if (IsInHazeBoundingVolume(pos_Rg_Start, totalProbeDist, iOuterRadiusRs)) 
        {
            vec3 accumulatedForce = vec3(0.0);
            float totalWeight = 0.0;

            // 累积探测
            for (int i = 0; i < HAZE_PROBE_STEPS; i++)
            {
                float marchDist = float(i + 1) * HAZE_STEP_SIZE; 
                vec3 probePos_Rg = pos_Rg_Start + rayDirNorm * marchDist;

                float t = float(i+1) / float(HAZE_PROBE_STEPS);
                float weight = min(min(3.0*t, 1.0), 3.05 - 3.0*t);
                
                vec3 forceSample = GetHazeForce(probePos_Rg, hazeTime);
                
                accumulatedForce += forceSample * weight;
                totalWeight += weight;
            }

            vec3 avgHazeForce = accumulatedForce / max(0.001, totalWeight);

            // 偏转应用
            float forceMagSq = dot(avgHazeForce, avgHazeForce);
            if (forceMagSq > 1e-10)
            {
                vec3 forcePerp = avgHazeForce - dot(avgHazeForce, rayDirNorm) * rayDirNorm;
                // 计算总偏转量
                vec3 deflection = forcePerp * HAZE_STRENGTH * 25.0; 
                RayDir = normalize(RayDir + deflection * 0.1); 
                LastDir = RayDir;
            }
            if(iDEBUG==1)
            {
                if (length(avgHazeForce) > 1e-4) {
                    vec3 debugVec = normalize(avgHazeForce) * 0.5 + 0.5;
                    debugVec *= (0.5 + 10.0 * length(avgHazeForce)); 
                    Result += 0.1*vec4(debugVec, 1.0);
                }
            }
        }

    }
    
    bool isoutgoing = false; 
    if (iObserverMode == -1) {
        isoutgoing = (iCamDataCoordisOutgoing == 1);
    }

    // 偏振基底：相机屏幕 right/up 轴的 Walker-Penrose 常数 + 逐样本累积的 Stokes Q/U。
    // 必须在 GetInitialMomentum 之前声明——前者通过 out 参数写入 WP_CamX/WP_CamY。
    vec2 WP_CamX = vec2(0.0);
    vec2 WP_CamY = vec2(0.0);
    vec2 StokesQU = vec2(0.0);

    if (bShouldContinueMarchRay) {
       P_cov = GetInitialMomentum(RayDir, X, GravityFade, isoutgoing, WP_CamX, WP_CamY);
       
       // 如果被拦截（观者在该系下变成类空），且允许最大延拓，说明这是向外运动(如出白洞)的观者，
       // 需换到 Outgoing 系重算初始动量
       // 注意，此处有未定位的bug，导致角度变化
       if (P_cov == vec4(114514.0) && iWhitehole == 1) {
            isoutgoing = true;
            P_cov = GetInitialMomentum(RayDir, X, GravityFade, isoutgoing, WP_CamX, WP_CamY);
        }
     }
    if (P_cov == vec4(114514.0))
    {
        bShouldContinueMarchRay = false;
        bWaitCalBack = false;
        Result = vec4(0.0,0.0,0.0,1.0);
    }




    // -------------------------------------------------------------------------
    // 偏振基底（WP_CamX / WP_CamY / StokesQU）已在 GetInitialMomentum 调用之前声明并初始化：
    // 前两者由 GetInitialMomentum 通过 out 参数写入，StokesQU 由 DiskColor/JetColor 累积。
    // -------------------------------------------------------------------------

    // 调试视图 2：接管输出，把初始动量/观者标架的构造结果画成颜色后直接返回
    // （NPGS 同款位置：紧跟初始动量构造，早于一切盘/网格累加）
    if (iDEBUG == 2 && bShouldContinueMarchRay)
    {
        vec3 dbgColor = DebugInitialMomentum(
            P_cov, X, iObserverMode, iUniverseSign,
            PhysicalSpinA, PhysicalQ, GravityFade, isoutgoing, iCameraVelocity.xyz
        );
        res.AccumColor = vec4(dbgColor, 1.0);
        res.Status = 3.0; // 标记为不透明，直接绘制到屏幕
        return res;
    }

    E_conserved = -P_cov.w;
    // -------------------------------------------------------------------------
    // 初始合法性检查与终结半径
    // -------------------------------------------------------------------------
    float TerminationR = -1.0; 
    float CameraStartR = KerrSchildRadius(RayPosLocal, CurrentUniverseSign);
    
    if (CurrentUniverseSign > 0.0) 
    {
        // 静态观者能层合法性检查
        if (iObserverMode == 0) 
        {
            float CosThetaSq = (RayPosLocal.y * RayPosLocal.y) / (CameraStartR * CameraStartR + 1e-20);
            float SL_Discrim = 0.25 - PhysicalQ * PhysicalQ - PhysicalSpinA * PhysicalSpinA * CosThetaSq;
            
            if (SL_Discrim >= 0.0) {
                float SL_Outer = 0.5 + sqrt(SL_Discrim);
                float SL_Inner = 0.5 - sqrt(SL_Discrim); 
                
                if (CameraStartR < SL_Outer && CameraStartR > SL_Inner) {
                    bShouldContinueMarchRay = false; 
                    bWaitCalBack = false; 
                    Result = vec4(0.0, 0.0, 0.0, 1.0); 
                } 
            }
        }
        else
        {
        // 落体观者能层合法性检查 todo
        }
        // 确定光线追踪终止半径 (非裸奇点)
        if (!bIsNakedSingularity && CurrentUniverseSign > 0.0 && iWhitehole==0) 
        {
            if (CameraStartR > EventHorizonR) TerminationR = EventHorizonR; 
            else if (CameraStartR > InnerHorizonR) TerminationR = InnerHorizonR;
            else TerminationR = -1.0;
        }
    }
    


    float ProgradePhotonRadius;
    // 阴影剔除：命中且无盘无喷流 → ComputeShadowCulling 返回 true → 立即 return res
    if (ComputeShadowCulling(RayPosLocal, RayDir, X, CameraStartR, EventHorizonR,
                             bIsNakedSingularity, bShouldContinueMarchRay, CurrentUniverseSign,
                             TerminationR, bDeferredShadowCulling,
                             ProgradePhotonRadius, Result, res))
    {
        return res;
    }

    float MaxStep=150.0+300.0/(1.0+1000.0*(1.0-iSpin*iSpin-iQ*iQ)*(1.0-iSpin*iSpin-iQ*iQ));
    if(iWhitehole==1) MaxStep=1145;
    if(bIsNakedSingularity) MaxStep=450;//150.0+300.0/(1.0+10.0*(1.0-iSpin*iSpin-iQ*iQ)*(1.0-iSpin*iSpin-iQ*iQ));

    // -------------------------------------------------------------------------
    // 主循环
    // -------------------------------------------------------------------------
    int Count = 0;
    float lastR = 0.0;
    bool bIntoOutHorizon = false;
    bool bIntoInHorizon = false;
    bool bEscapeOutHorizon = false;
    bool bEscapeInHorizon = false;
    int universeoffset=0;
    // 注意：KerrSchildRadius 返回**带符号**半径（反宇宙侧 r_sign=-1 → 负值），
    // 故此处必须按半径**大小**比较；否则反宇宙下 `负 < InnerHorizonR` 恒成立，
    // universeoffset 会被误加，Status 抬到 ≥5 而被 main 的 `Status<2.5` 挡掉 → 整屏全黑。
    if(iWhitehole == 1 && !bIsNakedSingularity && abs(CameraStartR) < InnerHorizonR) universeoffset++;
    float LastDr = 0.0;           
    int RadialTurningCounts = 0;  
    float RayMarchPhase = RandomStep(FragUv, iTime); 
    float ThetaInShell=0;
    bool shiftinout=false;
    bool fromwhitehole=false;
    vec4 LastX_ingoing;
    vec4 X_ingoing = X;
    vec4 P_cov_ingoing = P_cov;
    vec4 LastP_cov_ingoing = P_cov_ingoing; // [新增] 用于保存上一步的局域四动量
    if(P_cov== vec4(0.0,0.0,0.0,-1.0))	{
        P_cov_ingoing.xyz = -RayDir;
    }
    else{
        if (isoutgoing) {
            transformKerrSchild_YSpin(X_ingoing, CurrentUniverseSign, P_cov_ingoing, isoutgoing);
        }
        LastX_ingoing = X_ingoing;
        LastP_cov_ingoing = P_cov_ingoing; // [新增] 初始化 LastP_cov
    }
    // ----------------------------------------
    KerrGeometry geo;
    ComputeGeometryScalars(X.xyz, GravityFade, CurrentUniverseSign, isoutgoing, geo);
    while (bShouldContinueMarchRay)
    {
        DistanceToBlackHole = length(X.xyz);
        if (DistanceToBlackHole > RaymarchingBoundary)
        { 
            bShouldContinueMarchRay = false; 
            bWaitCalBack = true; 
            break;  //离开足够远
        }
        
        if (CurrentUniverseSign > 0.0 && geo.r < TerminationR && !bIsNakedSingularity && TerminationR != -1.0 && iWhitehole==0) 
        { 
            bShouldContinueMarchRay = false;
            bWaitCalBack = false;
            if(iDEBUG==1) Result += vec4(0.0, 0.3, 0.3, 0.0); 
            break;  //视界判定情况1，直接进入视界判定区
        }

        if (Count >int(float( MaxStep)*iQuality*(1.0+0.3*iQuality))) 
        { 
            bShouldContinueMarchRay = false; 
            bWaitCalBack = false;
            if(bIsNakedSingularity&&RadialTurningCounts <= 2) bWaitCalBack = true;
            if(iDEBUG==1) Result += vec4(0.0, 0.3, 0.0, 0.0);
            break; //耗尽步数
        }

        State s0; s0.X = X; s0.P = P_cov;
        State k1 = GetDerivativesAnalytic(s0, GravityFade, isoutgoing, geo);

        float CurrentDr = dot(geo.grad_r, k1.X.xyz);
        shiftinout = false;

        if (Count > 0 && CurrentDr * LastDr < 0.0) RadialTurningCounts++;
        if (Count==0) lastR = geo.r;

        // 试探换系：若 ingoing/outgoing 中另一系的四动量明显更温和，则换系继续步进
        {
            vec4 P_contra = RaiseIndex(P_cov, geo);
            float current_Sum = dot(abs(P_contra), vec4(1.0));
            // 试探换系
            vec4 test_X = X;
            vec4 test_P = P_cov;
            transformKerrSchild_YSpin(test_X, CurrentUniverseSign, test_P, isoutgoing);
            
            KerrGeometry test_geo;
            ComputeGeometryScalars(test_X.xyz, GravityFade, CurrentUniverseSign, !isoutgoing, test_geo);
            
            vec4 test_P_contra = RaiseIndex(test_P, test_geo);
            float test_Sum = dot(abs(test_P_contra), vec4(1.0));
            
            if (current_Sum > 2.0 * test_Sum) 
            {
                X = test_X;
                P_cov = test_P;
                isoutgoing = !isoutgoing;
                geo = test_geo;
                s0.X = X; 
                s0.P = P_cov;
                k1 = GetDerivativesAnalytic(s0, GravityFade, isoutgoing, geo);
                CurrentDr = dot(geo.grad_r, k1.X.xyz);
                shiftinout = true;
            }
        }
        LastDr = CurrentDr;

        if(geo.r < InnerHorizonR && lastR > InnerHorizonR) bEscapeInHorizon = true;    //检测穿进(追踪方向)内视界   
        if(geo.r < EventHorizonR && lastR > EventHorizonR) bEscapeOutHorizon = true;   //检测穿进(追踪方向)外视界   

        if(iWhitehole == 1 && !bIsNakedSingularity && abs(geo.r) < InnerHorizonR && abs(lastR) > InnerHorizonR) universeoffset++;    

        int allow_uni=3;
        if(1==1) allow_uni=1;
        if (iWhitehole==0 && RadialTurningCounts > 2 ) 
        {
            bShouldContinueMarchRay = false; bWaitCalBack = false;
            if(iDEBUG==1) Result += vec4(0.3, 0.0, 0.3, 1.0); 
            break;//识别剔除束缚态光子轨道
        }else if((bIsNakedSingularity&&RadialTurningCounts > 4) || (!bIsNakedSingularity&&(universeoffset>allow_uni||RadialTurningCounts > 8)))
        {
            bShouldContinueMarchRay = false; bWaitCalBack = false;
            if(iDEBUG==1) Result += vec4(0.3, 0.0, 0.3, 1.0); 
            break;
        }

        

        if(iGrid==0 && iWhitehole==0)
        {
            if(geo.r > InnerHorizonR && lastR < InnerHorizonR) bIntoInHorizon = true;   //检测穿出(追踪方向)内视界    
            if(geo.r > EventHorizonR && lastR < EventHorizonR) bIntoOutHorizon = true;  //检测穿出(追踪方向)外视界    

            if (CurrentUniverseSign > 0.0 && !bIsNakedSingularity && iWhitehole==0)
            {
                float SafetyGap = 0.001;
                float ToHorizonGap = 0.2;
                float PhotonShellLimit = ProgradePhotonRadius - SafetyGap; 
                float preCeiling = min(CameraStartR - SafetyGap, TerminationR + ToHorizonGap);
                if(bIntoInHorizon)  { preCeiling = InnerHorizonR + ToHorizonGap; } //处理 射线从相机出发 -> 向外运动 -> 调头 -> 向内运动 -> 撞击内视界 的光
                if(bIntoOutHorizon) { preCeiling = EventHorizonR + ToHorizonGap; } //处理 射线从相机出发 -> 向外运动 -> 调头 -> 向内运动 -> 撞击外视界 的光
                
                float PruningCeiling = min(iInterRadiusRs, preCeiling);
                PruningCeiling = min(PruningCeiling, PhotonShellLimit); 
                if(iDensestarsurfaceR!=0.0) PruningCeiling = min(PruningCeiling, iDensestarsurfaceR); 
                
            
                if (geo.r < PruningCeiling)
                {
                    float DrDlambda = dot(geo.grad_r, k1.X.xyz);
                    if (DrDlambda > 1e-4) 
                    {
                        bShouldContinueMarchRay = false;
                        bWaitCalBack = false;
                        if(iDEBUG==1) Result += vec4(0.0, 0., 0.3, 0.0);
                        break;  //视界判定情况2，对凝结在视界前的光提前剔除
                    }
                }
            }
        }

        //对动量和位置及其导数做自适应步长。对电荷做自适应步长（Q贡献r^-2项
        float rho = length(X.xz);
        float DistRing = sqrt(X.y * X.y + pow(rho - abs(PhysicalSpinA), 2.0));
        float Vel_Mag = length(k1.X); 
        float Force_Mag = length(k1.P);
        float Mom_Mag = length(P_cov);
        
        float PotentialTerm = (PhysicalQ * PhysicalQ) / (geo.r2 + 0.01);
        float QDamping = 1.0 / (1.0 + 1.0 * PotentialTerm); 
        
        float ADamping =1.0;
        if(!bIsNakedSingularity) {ADamping = mix(max(abs(iSpin),0.1), 1.0, clamp((geo.r - InnerHorizonR) / (0.5 - InnerHorizonR), 0.0, 1.0));}
        float ErrorTolerance = 0.5 * ADamping*QDamping;
        float StepGeo =  DistRing / (Vel_Mag + 1e-9);
        float StepForce = Mom_Mag / (Force_Mag + 1e-15);
        
        float dLambda = ErrorTolerance*min(StepGeo, StepForce);
        dLambda = max(dLambda, 1e-7); 

        vec4 LastX = X;
        vec4 LastP_cov=P_cov;
        LastX_ingoing = X_ingoing;
        LastP_cov_ingoing = P_cov_ingoing; // [新增] 缓存步进前的协变四动量
        // --------------------------------------------------

        GravityFade = CubicInterpolate(max(min(1.0 - ( DistanceToBlackHole - 100.0) / (RaymarchingBoundary - 100.0), 1.0), 0.0));
        
        vec4 P_contra_step = RaiseIndex(P_cov, geo);
        float Pdangerous=10000.0*max(1.0,pow(0.5/iSpin,3.0));
        if (P_contra_step.w > Pdangerous*iQuality && !bIsNakedSingularity && CurrentUniverseSign > 0.0) 
        { 
            bShouldContinueMarchRay = false; 
            bWaitCalBack = false;
            if(iDEBUG==1) Result += vec4(0.3, 0.3, 0.2, 0.0);  
            break; //视界判定情况3，凝结在视界
        }

        StepGeodesicRK4_Optimized(X, P_cov, E_conserved, -dLambda/iQuality, GravityFade, CurrentUniverseSign, isoutgoing, geo, k1);
        float deltar = geo.r - lastR;


        // 大概在这里分界，上面的内容基本上对 inout 不敏感，比如半径。

        // 将步进结果转换为统一的 Ingoing 系供后续渲染

        //获取当前步在 Ingoing 系下的状态
        X_ingoing = X;
        P_cov_ingoing = P_cov;
        if (isoutgoing) {
            transformKerrSchild_YSpin(X_ingoing, CurrentUniverseSign, P_cov_ingoing, isoutgoing);
        }

        
        vec3 StepVec_ingoing = X_ingoing.xyz - LastX_ingoing.xyz; // [修改] 直接用 LastX_ingoing
        float ActualStepLength_ingoing = length(StepVec_ingoing);


        float drdl = deltar / max(ActualStepLength_ingoing, 1e-9);

        float rotfact = clamp(1.0 + iBoostRot * dot(-StepVec_ingoing, vec3(X_ingoing.z, 0.0, -X_ingoing.x)) / ActualStepLength_ingoing / length(X_ingoing.xz) * clamp(iSpin, -1.0, 1.0), 0.0, 2.0);
        if( geo.r < 1.6 + pow(abs(iSpin), 0.666666)){
            ThetaInShell += ActualStepLength_ingoing / (0.5*lastR + 0.5*geo.r) / (1.0 + 1000.0*drdl*drdl) * rotfact * clamp(11.0 - 10.0*(iSpin*iSpin + iQ*iQ), 0.0, 1.0);
        }

        lastR = geo.r;
        
        // 赤道穿越检测 + 宇宙符号翻转：改用 Phase 1.5 提取的共享函数（原先此处内联展开，
        // 与 CheckEquatorialCrossing、GetIntermediateSign 三处逻辑重复；现只留这一份调用）
        CurrentUniverseSign = CheckEquatorialCrossing(LastX, X, CurrentUniverseSign);
        ComputeGeometryScalars(X.xyz, GravityFade, CurrentUniverseSign, isoutgoing, geo);
        bool ShowInnerGrid=true;
        if (iWhitehole == 0 && !bIsNakedSingularity && //类似于视界判定情况1，直接进入视界判定区，这个在有网格也生效.这个判定和上面的直接进入判定以及更下面的不可逃逸剔除有重叠，但这个必须在最前面（），为了InnerGrid不漏光，因为ks系步长可以一次从外视界外进到内视界内，导致在外面看到内视界
            ( 
                (lastR > EventHorizonR && geo.r < EventHorizonR) ||                                                                   // 1. 上一步外视界外，这一步外视界内
                (lastR > InnerHorizonR && geo.r < InnerHorizonR) ||                                                                   // 2. 上一步内视界外，这一步内视界内
                (lastR > EventHorizonR && geo.r < InnerHorizonR) ||                                                                   // 3. 上一步外视界外，这一步内视界内
                (lastR < EventHorizonR && lastR > InnerHorizonR && geo.r < EventHorizonR && geo.r > InnerHorizonR && geo.r < lastR)   // 4. 两步都在视界间但是向内了
            )
        ){ 
            bShouldContinueMarchRay = false; 
            bWaitCalBack = false; 
            ShowInnerGrid=false;
        }
        if (CurrentUniverseSign > 0.0 && iBlackHoleMassSol > 0.0 &&   int(33+iInWhichUniverse-universeoffset)%3==0       )
        {
           if(IsAccretionDiskVisible(iInterRadiusRs, iOuterRadiusRs, iThinRs, iHopper, iBrightmut, iDarkmut))
           {
                Result = DiskColor(Result, X, LastX,  P_cov,LastP_cov, E_conserved,
                              isoutgoing, 
                              ThetaInShell,
                              RayMarchPhase,WP_CamX, WP_CamY, StokesQU 
                              );


            }
            // 喷流（Phase 4 接线，2026-09-19）：门控 IsJetVisible（吸积率 ≥1e-2 且 iJetBrightmut>0）。
            // 本移植版 JetColor 签名已与 DiskColor 同构（位置/动量插值 + 换系自理），
            // 不再依赖 NPGS 原版调用的 ingoing 系光线方向变量。默认 iJetBrightmut=0 保持关闭，
            // GUI Kerr Disk 面板 Jet 区（亮度/频移指数/饱和度/蓝移上限）开启与调参。
            if (IsJetVisible(iAccretionRate, iJetBrightmut))
            {
                Result = JetColor(Result, X, LastX, P_cov, LastP_cov, E_conserved,
                              isoutgoing,
                              RayMarchPhase, WP_CamX, WP_CamY, StokesQU);
            }
            // --- 贴图盘（NPGS 逐行移植）：iUseImageDisk != 0 时在赤道面铺 Disk/R.jpg。
            //     与 NPGS 一致地放在"宇宙选层"门控内、且先于致密星/网格 ---
            if (iUseImageDisk != 0) {
                Result = ImageDiskColor(Result, X, LastX, P_cov, LastP_cov,
                            PhysicalSpinA, PhysicalQ, isoutgoing,
                            CurrentUniverseSign, -dLambda/iQuality,
                            iInterRadiusRs, iOuterRadiusRs,
                            iRedShiftColorExponent, iRedShiftIntensityExponent);
            }
        // --- 落点白点（NPGS 逐行移植的调试可视化）：沿 Ingoing 主零矢量内落的白色
        //     光点，用于直观看清光线传播与坐标系切换。NPGS 原版此处是注释状态（默认不画），
        //     故本移植版默认关闭，由 Kerr Extras 面板「Falling dot」复选框开启。 ---
        if (iShowFallingDot != 0) {
            Result = DrawFallingWhiteDot(Result, X, LastX, P_cov, LastP_cov,
                        PhysicalSpinA,
                        PhysicalQ, isoutgoing,
                        CurrentUniverseSign, -dLambda/iQuality);
        }

        // --- 致密星表面（NPGS 逐行移植）：iDensestarsurfaceR != 0 时启用，
        //     与 NPGS 同样要求裸奇点或表面半径大于外视界（否则被视界吞没不可见） ---
        if (iDensestarsurfaceR != 0.0 && (bIsNakedSingularity || iDensestarsurfaceR > EventHorizonR)
            && int(33+iInWhichUniverse-universeoffset)%3==0)
        {
            Result = DensestarColor(Result, X, LastX, P_cov, LastP_cov,
                        PhysicalSpinA,
                        PhysicalQ, isoutgoing,
                        CurrentUniverseSign, -dLambda/iQuality);
        }

        // --- 时空网格（NPGS 逐行移植）：iGrid=1 → GridColor（真实坐标），
        //     iGrid=2 → GridColorSimple（简易版 + ShowInnerGrid 遮罩）。
        //     网格可视化最大延拓结构（内外视界/宇宙分区），仅在最大延拓或近距离观察时有意义 ---
        if (iGrid == 1)
        {
            Result = GridColor(Result, X, LastX,
                        P_cov, E_conserved,
                        PhysicalSpinA,
                        PhysicalQ, isoutgoing,
                        CurrentUniverseSign);
        }
        else if (iGrid == 2)
        {
            Result = GridColorSimple(Result, X, LastX, P_cov, LastP_cov,
                        PhysicalSpinA,
                        PhysicalQ, isoutgoing,
                        CurrentUniverseSign, -dLambda/iQuality, ShowInnerGrid);
        }
         }
        if (Result.a > 0.99) { bShouldContinueMarchRay = false; bWaitCalBack = false; break; }
        

        Count++;
    }

    if (iDEBUG == 3)
    {
        // 将步数归一化（0.0 到 1.0），MaxStep 是循环的最大限制
        float stepHeat = float(Count) / MaxStep;
        
        // 热力图颜色：蓝色(少) -> 红色(多)
        res.AccumColor = vec4(mix(vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 0.0), stepHeat), 1.0);
        res.Status = 3.0; // 强制标记为不透明，直接输出颜色
        return res;
    }
    // 结果打包
    res.CurrentSign = CurrentUniverseSign;
    res.AccumColor  = Result;
    


    float EVPA = 0.5 * atan(StokesQU.y, StokesQU.x); 
    float PolIntensity = length(StokesQU);

    if (iPolarization==1) {
        float hue = (EVPA + kPi/2.0) / kPi;
        // 色相映射显示偏振参数
        vec3 polColor = clamp(abs(fract(hue + vec3(3.0, 2.0, 1.0)/3.0) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
        res.AccumColor = vec4(polColor * PolIntensity, Result.a);

    } 
    else if (iPolarization==2) {
        // 马吕斯定律 (滤光片): I_out = 0.5 * (I_in + Q*cos(2θ) + U*sin(2θ))
        // 实际上这等价于计算光的主偏振方向和滤光片的偏振夹角 Δθ, 其透过率为 cos^2(Δθ)
        // 1. 获取像素内极化部分的光对应的占比（如果所有光都是完全偏振的，这里的除法则抵消）
        float Q = StokesQU.x;
        float U = StokesQU.y;
        
        float maxPolIntensity = max(PolIntensity, 1e-12);
        
        // 对于完全偏振光，滤光片将其强度在 [0, 1] 间变动
        float filterTransmission = 0.5 + 0.5 * (Q * cos(2.0 * iPolarizationAngle) + U * sin(2.0 * iPolarizationAngle)) / maxPolIntensity;
        
        // 保留原吸积盘上色，将偏振透射衰减系数相乘
        // 当光经过完美的偏振片时，如果相位正对则为亮度最高点，正交则变暗甚至接近黑。
        // 你可以通过降低 0.5 增加对暗部的限制或者乘某增亮系数平衡总体视觉亮度
        res.AccumColor.rgb *= filterTransmission;
    }





    //阴影剔除的 Debug 颜色
    if (bDeferredShadowCulling && !bIsNakedSingularity)
    {// 检查是否是因为撞到了我们设定的 TerminationR 而退出的
        float FinalR = length(X.xyz);
        // 在截断半径内，或不再继续步进）容差 +0.1防闪烁
        if (FinalR <= TerminationR + 0.1 || !bShouldContinueMarchRay)
        {
            if(iDEBUG==1) 
            {
                float RemainingAlpha = max(0.0, 1.0 - res.AccumColor.a);
                res.AccumColor.rgb += vec3(0.0, 0.5, 0.0) * RemainingAlpha;
                res.AccumColor.a = 1.0;
                res.Status = 3.0; 
            } else {
                res.AccumColor.a = 1.0;
                res.Status = 3.0;
            }
            return res;
        }
    }
    // 状态位定义:
    // 0.0 = Absorbed/Lost (视界/超时，且未被体积光完全遮挡)
    // 1.0 = Sky (Universe +1)
    // 2.0 = Antiverse (Universe -1)
    // 3.0 = Opaque (Result.a > 0.99，体积光完全遮挡，其边界与前三者交界不做检查)
    // 4.0 = last universe
    // 5.0 = last anti-universe

    //小数位.0 .2区分能量正负，用于上色平行宇宙
    if (Result.a > 0.99) {
        res.Status = 3.0; 
        res.EscapeDir = vec3(0.0); 
        res.FreqShift = 0.0;
    } 
    else if (bWaitCalBack) {
        KerrGeometry geo_sky;
        ComputeGeometryScalars(X_ingoing.xyz, GravityFade, CurrentUniverseSign, false, geo_sky); // 假定已经足够远，GravityFade取1即可，f会自然趋于0
        vec4 P_up_sky = RaiseIndex(P_cov_ingoing, geo_sky);
        
        // 天空盒方向使用Ingoing系局部动量方向
        vec3 skyDir = normalize(-P_up_sky.xyz);
        res.EscapeDir = (iObserverMode == -1) ? skyDir : (LocalToWorldRot * skyDir);
        
        // 检测是否为 NaN 或 Inf
        bool isInvalid = any(isnan(res.EscapeDir)) || any(isinf(res.EscapeDir));
        
        if (DistanceToBlackHole > RaymarchingBoundary) {
            vec3 skyDir2 = normalize(-P_cov_ingoing.xyz);
            res.EscapeDir = (iObserverMode == -1) ? skyDir2 : (LocalToWorldRot * skyDir2);
        }
        if (isInvalid) {
            vec3 skyDir3 = normalize(RayDir);
            res.EscapeDir = (iObserverMode == -1) ? skyDir3 : (LocalToWorldRot * skyDir3);
        }
        res.FreqShift = clamp(1.0 / max(1e-14, abs(E_conserved)), 1.0/iBackShiftMax, iBackShiftMax);
        if (iDEBUG == 4)
        {

            res.AccumColor = vec4(mix(vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 0.0), 0.5*(log(iBackShiftMax)+log(res.FreqShift))/(log(iBackShiftMax))), 1.0);

        
            res.Status = 3.0; // 强制标记为不透明，直接输出颜色
            return res;
        }
        if (CurrentUniverseSign  > 0.0) res.Status = 1.0; 
        else res.Status = 2.0; 
        
    if (iWhitehole == 1 && !bIsNakedSingularity && universeoffset>0)
    {
        if (res.Status == 1.0)
            res.Status = 1.0+3.0*float(universeoffset);
        else if (res.Status == 2.0)
            res.Status = 2.0+3.0*float(universeoffset);
    }
    } 
    else {
        res.Status = 0.0; 
        res.EscapeDir = vec3(0.0);
        res.FreqShift = 0.0;
    }
    float energyFlag = (E_conserved >= 0.0) ? 0.0 : 0.2; 
    res.Status       +=  energyFlag;    

    return res;
}



// =============================================================================
// main: TraceRay → 背景合成 → 色调映射 → TAA 混合
// =============================================================================

layout(location = 0) out vec4 FragColor;
// 仅 iPrepass==1（prepass 路径）写入：低分体积色第二附件；主/composite 路径不写（声明多输出、
// 单附件管线未消费的输出被 Vulkan 丢弃，与 NPGS 双附件 prepass 管线共用同一 shader module）
layout(location = 1) out vec4 FragColorPrepass;

// ============================== 低分 prepass/composite（NPGS 移植） ==============================
// iPrepass 三态：0=原路径逐像素完整追踪；1=半分辨率 prepass（输出扭曲场+体积色双附件）；
// 2=composite（边缘感知合成：边缘处全分辨率重算，平滑处低分场插值）。

// 边缘检测阈值（NPGS BlackHole_composite）
const float PREPASS_EDGE_NORMAL_THRESHOLD = 0.99; // 方向向量点积阈值
const float PREPASS_EDGE_STATUS_TOLERANCE = 0.1;  // 状态位必须一致

// 敏感边界判定：忽略涉及不透明(3.0)的边界，只关心状态位彼此不同的敏感对
bool IsSensitiveBoundary(float sA, float sB)
{
    if ((sA < 2.5 || sA > 3.5) || (sB < 2.5 || sB > 3.5)) return false;
    return abs(sA - sB) > PREPASS_EDGE_STATUS_TOLERANCE;
}

// 低分双纹理手动双线性插值：扭曲场/体积色线性混合，状态位不插值（取权重最大邻居）
void ManualBilinearSample(vec2 uv, out vec3 outDistortion, out vec4 outVolumetric, out float outNearestStatus)
{
    ivec2 texSize = textureSize(iPrepassDistortion, 0);
    vec2 pixelPos = uv * vec2(texSize) - 0.5;
    ivec2 basePos = ivec2(floor(pixelPos));
    vec2 f = fract(pixelPos); // 插值权重

    ivec2 p00 = clamp(basePos,                 ivec2(0), texSize - 1);
    ivec2 p10 = clamp(basePos + ivec2(1, 0),   ivec2(0), texSize - 1);
    ivec2 p01 = clamp(basePos + ivec2(0, 1),   ivec2(0), texSize - 1);
    ivec2 p11 = clamp(basePos + ivec2(1, 1),   ivec2(0), texSize - 1);

    vec4 d00 = texelFetch(iPrepassDistortion, p00, 0);
    vec4 d10 = texelFetch(iPrepassDistortion, p10, 0);
    vec4 d01 = texelFetch(iPrepassDistortion, p01, 0);
    vec4 d11 = texelFetch(iPrepassDistortion, p11, 0);

    vec4 v00 = texelFetch(iPrepassVolumetric, p00, 0);
    vec4 v10 = texelFetch(iPrepassVolumetric, p10, 0);
    vec4 v01 = texelFetch(iPrepassVolumetric, p01, 0);
    vec4 v11 = texelFetch(iPrepassVolumetric, p11, 0);

    outDistortion = mix(mix(d00.xyz, d10.xyz, f.x), mix(d01.xyz, d11.xyz, f.x), f.y);
    outVolumetric = mix(mix(v00, v10, f.x), mix(v01, v11, f.x), f.y);

    // 状态位不插值：取双线性权重最大的邻居
    float sBest = d00.w;
    float wMax = (1.0 - f.x) * (1.0 - f.y);
    if (f.x * (1.0 - f.y) > wMax) { wMax = f.x * (1.0 - f.y);      sBest = d10.w; }
    if ((1.0 - f.x) * f.y > wMax) { wMax = (1.0 - f.x) * f.y;      sBest = d01.w; }
    if (f.x * f.y > wMax)         {                                 sBest = d11.w; }
    outNearestStatus = sBest;
}

void main()
{
    // [调试] 直接输出SampleBackground
//    {
//        vec2 Uv = gl_FragCoord.xy / iResolution.xy;
//        vec3 Dir = normalize(BuildViewDirLocal(Uv + 0.5 * ScreenJitter(Uv, iResolution)));
//        vec4 tex = textureLod(iBackground0, Dir, 0.0);
//        FragColor = SampleBackground(Dir, 1.0, 1.0);
//        return;
//    }

    // gl_FragCoord 取 Vulkan 帧缓冲原生行序（上→下）；唯一一次 y 翻转发在 TraceRay 内部
    vec2 Uv = gl_FragCoord.xy / iResolution.xy;

    // ===== 半分辨率 prepass：完整追踪一次，输出扭曲场+体积色双附件后直接返回 =====
    if (iPrepass == 1) {
        TraceResult res = TraceRay(Uv);   // 无像素抖动：扭曲场是几何属性，抖动留给全分辨率像素
        FragColor        = vec4(res.EscapeDir * res.FreqShift, res.Status);
        FragColorPrepass = res.AccumColor;
        return;
    }

    vec4  FinalColor;   // 体积光（吸积盘等）累积色
    float Status;       // 0=吸收(纯黑) | 1=逃逸星空 | 2=反宇宙 | >=3 不透明体积光
    float Shift;
    vec3  BgDir;

    if (iPrepass == 2) {
        // ===== composite：十字邻域边缘检测 → 边缘重算 / 平滑插值 =====
        ivec2 PrepassSize = textureSize(iPrepassDistortion, 0);
        ivec2 sizeMax = PrepassSize - ivec2(1);
        ivec2 CenterCoord = clamp(ivec2(floor(Uv * vec2(PrepassSize))), ivec2(0), sizeMax);
        vec4 DataC = texelFetch(iPrepassDistortion, CenterCoord, 0);
        vec4 DataL = texelFetch(iPrepassDistortion, clamp(CenterCoord + ivec2(-1, 0), ivec2(0), sizeMax), 0);
        vec4 DataR = texelFetch(iPrepassDistortion, clamp(CenterCoord + ivec2( 1, 0), ivec2(0), sizeMax), 0);
        vec4 DataU = texelFetch(iPrepassDistortion, clamp(CenterCoord + ivec2( 0,-1), ivec2(0), sizeMax), 0);
        vec4 DataD = texelFetch(iPrepassDistortion, clamp(CenterCoord + ivec2( 0, 1), ivec2(0), sizeMax), 0);

        float FlagC = round(DataC.w);
        bool IsEdgeStatus = IsSensitiveBoundary(FlagC, round(DataL.w))
                         || IsSensitiveBoundary(FlagC, round(DataR.w))
                         || IsSensitiveBoundary(FlagC, round(DataU.w))
                         || IsSensitiveBoundary(FlagC, round(DataD.w));

        // 几何不连续：逸出方向场点积跌破阈值（不透明体内部不参与，其方向无意义）
        bool IsEdgeGeo = false;
        if (FlagC < 2.5 || FlagC > 3.5) {
            vec3 DirC = normalize(DataC.xyz + 1e-6);
            IsEdgeGeo = dot(normalize(DataL.xyz + 1e-6), DirC) < PREPASS_EDGE_NORMAL_THRESHOLD
                     || dot(normalize(DataR.xyz + 1e-6), DirC) < PREPASS_EDGE_NORMAL_THRESHOLD
                     || dot(normalize(DataU.xyz + 1e-6), DirC) < PREPASS_EDGE_NORMAL_THRESHOLD
                     || dot(normalize(DataD.xyz + 1e-6), DirC) < PREPASS_EDGE_NORMAL_THRESHOLD;
        }

        if (IsEdgeStatus || IsEdgeGeo) {
            // 边缘路径：全分辨率重算（与低分场同口径，不加像素抖动）
            TraceResult res = TraceRay(Uv);
            FinalColor = res.AccumColor;
            Status     = res.Status;
            Shift      = res.FreqShift;
            BgDir      = res.EscapeDir;
        } else {
            // 平滑路径：低分场双线性插值（状态位取最近邻）
            vec3 InterpDistortion;
            vec4 InterpVolumetric;
            float NearestStatus;
            ManualBilinearSample(Uv, InterpDistortion, InterpVolumetric, NearestStatus);
            FinalColor = InterpVolumetric;
            Status     = NearestStatus;
            Shift      = length(InterpDistortion);
            BgDir      = InterpDistortion / (Shift + 1e-9);
        }
    } else {
        // ===== 原路径：逐像素完整追踪 + 像素级 TAA 抖动（0.5 像素，与内部 0.25 像素子步叠加） =====
        TraceResult res = TraceRay(Uv + 0.5 * ScreenJitter(Uv, iResolution));
        FinalColor = res.AccumColor;
        Status     = res.Status;
        Shift      = res.FreqShift;
        BgDir      = res.EscapeDir;
    }

    // 背景采样门控与 NPGS BlackHole_composite L143 逐行对齐：除常规星空(Status 1/2)外，
    // Status>3.5（出白洞——来自上个宇宙的光）也要采背景，SampleBackground 内按 offset
    // 取上一层盒。普通版 BlackHole.frag 的 main 无此分支；缺失时白洞喉道区应见的
    // 「上个宇宙天空」整片不采样 → 中心天体不显示。
    if (FinalColor.a < 0.99 && ((Status > 0.5 && Status < 2.5) || Status > 3.5)) {
        vec4 Bg = SampleBackground(BgDir, Shift, Status);
        // 负能量状态位（小数 .2）：背景反相；非白洞模式直接置零（NPGS composite L146-155 同款）
        bool IsPositiveEnergy = abs(Status - round(Status)) < 0.1;
        if (!IsPositiveEnergy) {
            float cMax = max(max(Bg.r, Bg.g), Bg.b);
            float cMin = min(min(Bg.r, Bg.g), Bg.b);
            Bg.rgb = vec3(cMax + cMin) - Bg.rgb;
            if (iWhitehole == 0) Bg = vec4(0.0);
        }
        if (Status > 0.5 && Status < 2.5) {
            // 前向散射：被盘消光的那部分背景光（≈Bg·FinalColor.a，此时 alpha 仍为盘体积累积值）
            // 按 iDiskScatter 比例单次散射回视线（albedo ≤1 时能量守恒）。仅常规星空逃逸
            // 路径执行（NPGS composite 的白洞分支无此项）；吸收（Status=0）不经过此处保持纯黑
            FinalColor.rgb += Bg.rgb * iDiskScatter * FinalColor.a;
        }
        // 背景按体积光透过率逐通道叠加（蓝光被遮挡最快，红光最慢）
        FinalColor += 0.9999 * Bg * vec4(pow(1.0 - FinalColor.a, 1.0),
                                         pow(1.0 - FinalColor.a, 1.6),
                                         pow(1.0 - FinalColor.a, 2.5), 1.0);
    }

    FinalColor = ApplyToneMapping(FinalColor, Shift);

    // TAA 历史混合（Java 侧前几帧强制 iBlendWeight=1，全量覆盖未初始化历史）
    vec4 PrevColor = texelFetch(iHistoryTex, ivec2(gl_FragCoord.xy), 0);
    if (any(isnan(PrevColor)) || any(isinf(PrevColor))) {
        PrevColor = FinalColor; // 坏历史自愈：防止 NaN 以 0 权重仍自我延续
    }
    FragColor = (iBlendWeight >= 1.0) ? FinalColor
                                      : iBlendWeight * FinalColor + (1.0 - iBlendWeight) * PrevColor;
}
