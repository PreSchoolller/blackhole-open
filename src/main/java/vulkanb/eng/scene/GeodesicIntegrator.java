package vulkanb.eng.scene;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 克尔时空测地线积分器 —— Kerr-Schild 笛卡尔坐标,几何单位 G = c = 1。
 * <p>
 * 长度单位为 Rs(史瓦西半径),故 M = Rs/2 = 0.5;时间单位为 Rs/c。
 * 度规(KS 解析式,视界处无坐标奇点,可平滑穿过外视界):
 * <pre>
 * f = (2Mr³ − Q²r²)/(r⁴ + a²y²);   l_μ = (1, l_x, l_y, l_z)   (自旋轴 = Y,与 kerr.frag 同式)
 * l_x = (rx − az)/(r²+a²),l_y = y/r,l_z = (rz + ax)/(r²+a²)
 * KS 参数 r 由 (x²+z²)/(r²+a²) + y²/r² = 1 定义(闭合式见 computeMetric)
 * g_μν = η_μν + f·l_μ·l_ν          (η = diag(+1,+1,+1,-1))
 * g^μν = η^μν - f·l^μ·l^ν          (l^t = -1,l^i = l_i)
 * </pre>
 * a = 0 时退化为史瓦西(r = |x|);自旋量由 {@link #setSpin} 按 GUI 的 a* 注入。
 * 状态:X = [x,y,z,t](位置,t 为坐标时),U = [Ux,Uy,Uz,Ut](四速度,约束 U·U = -1),
 * 外加**观测者四维标架** e1/e2/e3(上指标 4 矢量,与 U 构成 Lorentz 正交归一系)——
 * 三腿沿测地线平行输运 de/dτ = −Γ(U,e),为 Phase 5 观者模式 -1 提供相机姿态基;
 * 鼠标转头(头四元数)以线性组合 {@link #getFoldedTetrad} 折叠到输运后的标架上。
 * 积分:RK4(U+标架腿 16 维联立),dU/dτ = -Γ·(U,U),de/dτ = -Γ·(U,e);
 * 自适应步长 dtau_max = 0.05·max(r,1)^1.5,单帧子步上限 500;每步后由空间分量重解 U^t
 * 并对标架做 Gram-Schmidt 重正交(数值漂移清除)。
 * <p>
 * 参考:NPGS Application.cpp 的 GeodesicIntegrator 命名空间(Q 取 0;四维标架方案同源)。
 * 克氏符采用度规中心差分(方案 B),步长 1e-5,二阶精度,满足圆轨道验收要求。
 */
public class GeodesicIntegrator {

    /** 几何单位质量:M = Rs/2 = 0.5(Rs = 1) */
    private static final double M = 0.5;
    /** 物理自旋 a = a*·M(无量纲 a* 由 GUI 注入;0 = 史瓦西) */
    private double spinA = 0.0;
    /** 克氏符数值差分步长 */
    private static final double H = 1e-5;
    /** 单帧子步数上限(超过则等效时间变慢,防止视界附近发散) */
    private static final int MAX_SUBSTEPS = 500;
    /** a=0 时的奇点保护下限(真奇点 r=0;a≠0 时改用 minRadius() 的外视界保护) */
    private static final double MIN_RADIUS = 0.1;
    /** 奇点保护半径:低于此半径暂停推进。a=0 时为通用下限 0.1(真奇点 r=0);
     *  a≠0 时为外视界 r₊=M+√(M²−a²) 加 2% 余量——环奇区(r→0)梯度爆炸,
     *  数值上表现为轨迹弹射到 r~10¹⁵,而视界内积分本无视觉意义(渲染侧光线在
     *  EventHorizonR 全部终结、应用层坠落演出在 1.02Rs 即拉回) */
    private double minRadius() {
        if (spinA == 0.0) {
            return MIN_RADIUS;
        }
        return 0.5 + Math.sqrt(Math.max(0.0, 0.25 - spinA * spinA)) + 0.02;
    }

    /** 位置 X = [x,y,z,t](Rs 单位) */
    private final double[] posX = new double[4];
    /** 四速度 U = [Ux,Uy,Uz,Ut] */
    private final double[] velU = new double[4];
    /** 是否激活(未激活时 β=0、γ=1,着色器退化为静态观测者) */
    private boolean active;

    /** 时间流速:每真实秒推进的固有时 τ(可调,×1.25 步进) */
    private double timeScale = 1.0;
    /** 推力大小(W/S 沿视线加速,可调,[/] 步进) */
    private double thrust = 0.5;
    /** 初始速度(单位 c,进入测地模式前用 1/2 键调节) */
    private double v0 = 0.3;
    /** 圆轨道倾角(度,0=贴合吸积盘平面 XZ,90=过 ŷ 轴子午面[原固定行为];R 键初始化时生效) */
    private double orbitTilt = 90.0;

    /** 相机瞬时速度 β 向量(单位 c,静态观者系),供渲染侧相机多普勒使用 */
    private final Vector3f beta = new Vector3f();
    /** 相机瞬时洛伦兹因子 γ */
    private float gamma = 1.0f;

    // ---- Phase 5:观测者四维标架(平行输运)+ 头部旋转 ----
    /** 空间标架腿 e1/e2/e3(上指标 4 矢量;时间腿即 velU)。与 U 构成 Lorentz 正交归一系 */
    private final double[][] tetrad = new double[3][4];
    /** 鼠标头部旋转(测地模式下鼠标转它,不转相机;渲染侧折叠进标架与相机姿态推导) */
    private final Quaternionf headQuat = new Quaternionf();
    private final Matrix3f headMat = new Matrix3f();
    /** 标架是否已初始化(未初始化时重正交跳过,标架保持零) */
    private boolean tetradActive;

    // ---- 视界坠落演出状态机:触发 → 淡出到黑 → 重置到 5Rs 圆轨道 → 淡入 ----
    /** 0=未触发,1=淡出中,2=已传送、淡入中 */
    private int fallPhase;
    /** 淡出系数 0..1,经 push constant(iFade)控制画面淡黑 */
    private float horizonFade;

    // ---- RK4 / 度规暂存缓冲(积分器单线程使用,避免每步分配) ----
    private final double[][] gDown = new double[4][4];
    private final double[][] gUp = new double[4][4];
    private final double[][][] gammaSyms = new double[4][4][4];
    /** ∂_ρ g_μν:第一维为求导方向,必须开满 4 维——Γ 公式第三项 ∂_ρ 的 ρ 遍历 0..3(含时间);
     *  静态度规的时间导数恒为 0,时间切片保持零初始化即可 */
    private final double[][][] dG = new double[4][4][4];
    /** 联合状态向量 q = [U(4), e1(4), e2(4), e3(4)],RK4 的 16 维动力学部分 */
    private final double[] qNow = new double[16], qTmp = new double[16];
    private final double[][] kQ = new double[4][16];
    /** X 方程的四个阶段斜率(即各阶段的 U,注意与 dU/dτ 的 kQ 区分) */
    private final double[][] uStages = new double[4][4];
    /** 位置临时向量 */
    private final double[] xTmp = new double[4];

    /**
     * 用当前位置/视线方向/初速度初始化测地线状态。
     *
     * @param positionRs 相机位置(Rs 单位,与渲染世界单位一致)
     * @param dirUnit    初速度方向(单位向量,通常为当前视线方向)
     * @param v0c        初速度大小(单位 c)
     */
    public void initialize(Vector3f positionRs, Vector3f dirUnit, double v0c) {
        posX[0] = positionRs.x;
        posX[1] = positionRs.y;
        posX[2] = positionRs.z;
        posX[3] = 0.0;
        double v = Math.min(Math.max(v0c, 0.01), 0.99);
        velU[0] = dirUnit.x * v;
        velU[1] = dirUnit.y * v;
        velU[2] = dirUnit.z * v;
        velU[3] = 1.0;
        projectConstraint();
        active = true;
        updateBetaGamma();
    }

    /**
     * 初始化为当前位置的圆轨道(观赏吸积盘的最佳初条件)。
     * 坐标系严格圆轨道条件:坐标角速度 Ω = √(M/r³)(Schwarzschild 中开普勒第三律严格成立),
     * 空间坐标速度 u⃗ = Ω·U^t·(n̂×r⃗)。注意不能用静态观者局部速度 v=√(M/(r-Rs)) 直接当坐标速度
     * (两者差 ~9%,会得到明显椭圆轨道);前者仅作展示。
     * <p>
     * 轨道面法线 n̂ = e1·cos(tilt) + (e1×r̂)·sin(tilt),tilt 为轨道倾角(度,可调):
     * <ul>
     *   <li>tilt = 0:n̂ = e1 = ŷ⊥(ŷ 去掉 r̂ 分量)——过当前位置、最贴合吸积盘平面(XZ)的轨道</li>
     *   <li>tilt = 90:n̂ = e1×r̂ = ŷ×r̂ ——过 ŷ 轴的子午面(原固定行为)</li>
     * </ul>
     * U^t 由归一化二次式依赖 u⃗ → 不动点迭代(收敛极快)。
     *
     * @return 描述信息(r、倾角与局部速度 v),失败(半径过小)返回 null
     */
    public String initializeCircularOrbit() {
        double r = radius();
        double vLocal = Math.sqrt(M / (r - 2 * M));
        if (Double.isNaN(vLocal) || vLocal >= 0.999) {
            return null;   // r 过小(接近/低于光子球 1.5Rs),无 Timelike 圆轨道
        }
        double rx = posX[0], ry = posX[1], rz = posX[2];
        // e1 = ŷ⊥ 归一化;两极处(r̂ ∥ ŷ,hn→0)退化,回退 x̂(极点处 x̂ 本身 ⟂ r̂)
        double e1x, e1y, e1z;
        double hn = Math.sqrt(rx * rx + rz * rz);
        if (hn > 1e-9) {
            double s = r * hn;
            e1x = -rx * ry / s;
            e1y = (rx * rx + rz * rz) / s;
            e1z = -rz * ry / s;
        } else {
            e1x = 1.0;
            e1y = 0.0;
            e1z = 0.0;
        }
        double rux = rx / r, ruy = ry / r, ruz = rz / r;
        // m̂ = e1 × r̂(tilt=90 时即原 ĥ = ŷ×r̂,含极点回退语义)
        double mx = e1y * ruz - e1z * ruy;
        double my = e1z * rux - e1x * ruz;
        double mz = e1x * ruy - e1y * rux;
        double ci = Math.cos(Math.toRadians(orbitTilt));
        double si = Math.sin(Math.toRadians(orbitTilt));
        double nx = e1x * ci + mx * si;
        double ny = e1y * ci + my * si;
        double nz = e1z * ci + mz * si;
        // u⃗ = Ω·U^t·(n̂×r⃗):测地条件为角速度比,空间四速度需再乘 U^t;
        // U^t 又由归一化二次式依赖 u⃗ → 不动点迭代(收敛极快)。
        // Ω 取 Kerr 顺行开普勒角速度(BL 式,近似用于倾斜轨道):Ω = √M/(r^{3/2}+a√M),
        // a=0 退化为史瓦西开普勒第三律;顺行方向 = n̂×r⃗ 绕自旋轴 +Y 的右手法则
        double omega = Math.sqrt(M) / (Math.pow(r, 1.5) + spinA * Math.sqrt(M));
        double ut = 1.0;
        for (int i = 0; i < 8; i++) {
            velU[0] = ut * omega * (ny * rz - nz * ry);
            velU[1] = ut * omega * (nz * rx - nx * rz);
            velU[2] = ut * omega * (nx * ry - ny * rx);
            projectConstraint();
            ut = velU[3];
        }
        updateBetaGamma();
        return String.format("圆轨道:r = %.2f Rs,倾角 %.0f°,v(静态观者) = %.3f c",
                r, orbitTilt, beta.length());
    }

    /** 推进一个时间步(在 update() 中按 diffTimeMillis 调用) */
    public void step(long diffTimeMillis) {
        if (!active) {
            return;
        }
        double r = radius();
        if (r < minRadius()) {
            return;   // 奇点/视界保护:暂停推进
        }
        double totalDtau = (diffTimeMillis / 1000.0) * timeScale;
        double dtauMax = 0.05 * Math.pow(Math.max(r, 1.0), 1.5);
        int substeps = (int) Math.ceil(totalDtau / dtauMax);
        substeps = Math.min(Math.max(substeps, 1), MAX_SUBSTEPS);
        double h = totalDtau / substeps;
        for (int i = 0; i < substeps; i++) {
            rk4Step(h);
        }
        updateBetaGamma();
    }

    /** 沿给定世界方向施加推力(改变四速度空间分量后重新投影约束) */
    public void applyThrust(Vector3f dirUnit, long diffTimeMillis) {
        if (!active) {
            return;
        }
        double dtau = (diffTimeMillis / 1000.0) * timeScale;
        velU[0] += dirUnit.x * thrust * dtau;
        velU[1] += dirUnit.y * thrust * dtau;
        velU[2] += dirUnit.z * thrust * dtau;
        projectConstraint();
        updateBetaGamma();
    }

    /** 注入无量纲自旋 a*(GUI/Scene 每帧同步;0 = 史瓦西极限),内部换算为物理自旋 a·M */
    public void setSpin(double aStar) {
        this.spinA = Math.min(0.998, Math.max(0.0, aStar)) * M;
    }

    /** 退出测地模式:停用并恢复静态观测者(β=0、γ=1,着色器退化为原行为) */
    public void deactivate() {
        active = false;
        tetradActive = false;
        fallPhase = 0;
        horizonFade = 0.0f;
        beta.set(0.0f, 0.0f, 0.0f);
        gamma = 1.0f;
    }

    /** 是否处于视界坠落演出中(演出期间积分器冻结、画面由 iFade 接管) */
    public boolean isFalling() {
        return fallPhase != 0;
    }

    /** 淡出系数 0..1(0=正常画面,1=全黑) */
    public float getHorizonFade() {
        return horizonFade;
    }

    /** 触发视界坠落演出(由外部在 r < 1.02Rs 时调用) */
    public void beginHorizonFall() {
        if (fallPhase == 0) {
            fallPhase = 1;
        }
    }

    /**
     * 坠落演出推进:淡出到全黑(0.6s)→ 沿当前方向重置到 5Rs 并初始化为稳定圆轨道 → 淡入(0.6s)。
     * 演出期间不推进积分器(避免视界内的数值噪声画面)。
     */
    public void updateFall(long diffTimeMillis) {
        if (fallPhase == 0) {
            return;
        }
        float dt = diffTimeMillis / 1000.0f;
        if (fallPhase == 1) {
            horizonFade = Math.min(1.0f, horizonFade + dt / 0.6f);
            if (horizonFade >= 1.0f) {
                teleportToSafety();
                fallPhase = 2;
            }
        } else {
            horizonFade = Math.max(0.0f, horizonFade - dt / 0.6f);
            if (horizonFade <= 0.0f) {
                fallPhase = 0;
            }
        }
    }

    /** 演出中段:沿当前方向弹回 5Rs 并初始化为稳定圆轨道(r=5Rs > 3Rs,稳定区) */
    private void teleportToSafety() {
        double r = radius();
        double s = 5.0 / Math.max(r, 1e-6);
        posX[0] *= s;
        posX[1] *= s;
        posX[2] *= s;
        posX[3] = 0.0;
        initializeCircularOrbit();
    }

    /** 时间流速 ×factor(钳制 [0.1, 50]) */
    public void scaleTime(double factor) {
        timeScale = Math.min(50.0, Math.max(0.1, timeScale * factor));
    }

    /** 直接设置时间流速(GUI 滑条用,钳制同 scaleTime) */
    public void setTimeScale(double v) {
        timeScale = Math.min(50.0, Math.max(0.1, v));
    }

    /** 推力 ×factor(钳制 [0.05, 8]) */
    public void scaleThrust(double factor) {
        thrust = Math.min(8.0, Math.max(0.05, thrust * factor));
    }

    /** 直接设置推力(GUI 滑条用,钳制同 scaleThrust) */
    public void setThrust(double v) {
        thrust = Math.min(8.0, Math.max(0.05, v));
    }

    /** 初速度 ±d(钳制 [0.05, 0.99]) */
    public void adjustV0(double d) {
        v0 = Math.min(0.99, Math.max(0.05, v0 + d));
    }

    /** 直接设置初速度(GUI 滑条用,钳制同 adjustV0) */
    public void setV0(double v) {
        v0 = Math.min(0.99, Math.max(0.05, v));
    }

    /** 直接设置圆轨道倾角(GUI 滑条用,钳制 [0°,90°]) */
    public void setOrbitTilt(double deg) {
        orbitTilt = Math.min(90.0, Math.max(0.0, deg));
    }

    /** 圆轨道倾角(度) */
    public double getOrbitTilt() {
        return orbitTilt;
    }

    /** 当前半径(Rs 单位) */
    public double getRadius() {
        return radius();
    }

    /** 静态系能量 E = -U_t(测地线守恒量,调试用;测地模式下应恒定) */
    public double getEnergy() {
        computeMetric(posX, gDown, gUp);
        return -(gDown[3][3] * velU[3] + gDown[3][0] * velU[0] + gDown[3][1] * velU[1] + gDown[3][2] * velU[2]);
    }

    public double getTimeScale() {
        return timeScale;
    }

    public double getThrust() {
        return thrust;
    }

    public double getV0() {
        return v0;
    }

    public boolean isActive() {
        return active;
    }

    /** 相机瞬时速度 β 向量(单位 c;未激活时为 0) */
    public Vector3f getBeta() {
        return beta;
    }

    /** 相机瞬时洛伦兹因子 γ(未激活时为 1) */
    public float getGamma() {
        return gamma;
    }

    /**
     * KS 坐标速度 dx/dt(单位 c,ingoing 片;U^t=1 归一化前的空间比值)。
     * kerr.frag 观者模式 2 的约定输入:着色器以 (v,1) 归一化重构观者四速度,
     * 因此只有比值有意义,任何物理态都满足类时条件。未激活时为 0。
     */
    public Vector3f getCoordinateVelocity(Vector3f out) {
        if (!active) {
            return out.set(0.0f, 0.0f, 0.0f);
        }
        double ut = velU[3];
        double vx = velU[0] / ut, vy = velU[1] / ut, vz = velU[2] / ut;
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)
                || Math.abs(vx) > 1e3 || Math.abs(vy) > 1e3 || Math.abs(vz) > 1e3) {
            return out.set(0.0f, 0.0f, 0.0f);   // 视界附近数值异常时退化为静态观者
        }
        return out.set((float) vx, (float) vy, (float) vz);
    }

    /** 当前位置(Rs 单位) */
    public Vector3f getPosition(Vector3f out) {
        return out.set((float) posX[0], (float) posX[1], (float) posX[2]);
    }

    /** 当前 KS 半径(Rs 单位):a=0 时即 |x|;a≠0 时由 KS 椭球定义给出(视界/坠落判定均以此为准) */
    private double kerrRadius() {
        if (spinA == 0.0) {
            return Math.sqrt(posX[0] * posX[0] + posX[1] * posX[1] + posX[2] * posX[2]);
        }
        return ksRadius(posX[0], posX[1], posX[2]);
    }

    /** KS 参数半径的闭合解(与 kerr.frag KerrSchildRadius 同式,自旋轴 Y,Q=0) */
    private double ksRadius(double x, double y, double z) {
        double a2 = spinA * spinA;
        double b = x * x + z * z + y * y - a2;
        double det = Math.sqrt(b * b + 4.0 * a2 * y * y);
        double r2 = (b >= 0.0) ? 0.5 * (b + det) : (2.0 * a2 * y * y) / Math.max(1e-20, det - b);
        return Math.sqrt(Math.max(1e-12, r2));
    }

    private double radius() {
        return kerrRadius();
    }

    /** KS 度规(黑洞片):g_down = η + f·l⊗l,g_up = η − f·l^⊗l^(解析式,非数值求逆)。
     *  l_μ 时间分量为正:该符号下的切片允许下落轨迹平滑穿越视界
     *  (若取 l_i = -x/r 则为白洞出射片,下落粒子会被弹射到无穷远——已实测验证)。
     *  a≠0 时 f/l 用与 kerr.frag ComputeGeometryScalars 严格同式的 Kerr 形式(自旋轴 Y)。 */
    private void computeMetric(double[] x, double[][] gDn, double[][] gUp) {
        double r;
        double lx, ly, lz;
        double f;
        if (spinA == 0.0) {
            r = Math.sqrt(x[0] * x[0] + x[1] * x[1] + x[2] * x[2]);
            lx = x[0] / r;
            ly = x[1] / r;
            lz = x[2] / r;
            f = 2 * M / r;
        } else {
            r = ksRadius(x[0], x[1], x[2]);
            double r2 = r * r;
            double invR2A2 = 1.0 / (r2 + spinA * spinA);
            lx = (r * x[0] - spinA * x[2]) * invR2A2;
            ly = x[1] / r;
            lz = (r * x[2] + spinA * x[0]) * invR2A2;
            double y2 = x[1] * x[1];
            f = (2.0 * M * r2 * r) / Math.max(1e-20, r2 * r2 + spinA * spinA * y2);
        }
        double lt = 1.0;
        gDn[0][0] = 1 + f * lx * lx;
        gDn[0][1] = f * lx * ly;
        gDn[0][2] = f * lx * lz;
        gDn[0][3] = f * lt * lx;
        gDn[1][1] = 1 + f * ly * ly;
        gDn[1][2] = f * ly * lz;
        gDn[1][3] = f * lt * ly;
        gDn[2][2] = 1 + f * lz * lz;
        gDn[2][3] = f * lt * lz;
        gDn[3][3] = -1 + f * lt * lt;
        gDn[1][0] = gDn[0][1];
        gDn[2][0] = gDn[0][2];
        gDn[2][1] = gDn[1][2];
        gDn[3][0] = gDn[0][3];
        gDn[3][1] = gDn[1][3];
        gDn[3][2] = gDn[2][3];
        // l^t = -1,l^i = l_i → g^tt = -1-f,g^ti = +f·l_i,g^ij = δij - f·l_i·l_j
        double ltu = -1.0;
        gUp[0][0] = 1 - f * lx * lx;
        gUp[0][1] = -f * lx * ly;
        gUp[0][2] = -f * lx * lz;
        gUp[0][3] = -f * ltu * lx;
        gUp[1][1] = 1 - f * ly * ly;
        gUp[1][2] = -f * ly * lz;
        gUp[1][3] = -f * ltu * ly;
        gUp[2][2] = 1 - f * lz * lz;
        gUp[2][3] = -f * ltu * lz;
        gUp[3][3] = -1 - f * ltu * ltu;
        gUp[1][0] = gUp[0][1];
        gUp[2][0] = gUp[0][2];
        gUp[2][1] = gUp[1][2];
        gUp[3][0] = gUp[0][3];
        gUp[3][1] = gUp[1][3];
        gUp[3][2] = gUp[2][3];
    }

    /** 克氏符 Γ^μ_νσ = ½ g^μρ(∂_ν g_ρσ + ∂_σ g_ρν − ∂_ρ g_νσ),∂g 中心差分 */
    private void computeChristoffel(double[] x) {
        computeMetric(x, gDown, gUp);
        for (int i = 0; i < 3; i++) {
            double[] xp = x.clone();
            double[] xm = x.clone();
            xp[i] += H;
            xm[i] -= H;
            double[][] gp = new double[4][4];
            double[][] gm = new double[4][4];
            double[][] gupThrow = new double[4][4];
            computeMetric(xp, gp, gupThrow);
            computeMetric(xm, gm, gupThrow);
            for (int a = 0; a < 4; a++) {
                for (int b = 0; b < 4; b++) {
                    dG[i][a][b] = (gp[a][b] - gm[a][b]) / (2 * H);
                }
            }
        }
        for (int mu = 0; mu < 4; mu++) {
            for (int nu = 0; nu < 4; nu++) {
                for (int sig = 0; sig < 4; sig++) {
                    double sum = 0;
                    for (int rho = 0; rho < 4; rho++) {
                        sum += gUp[mu][rho] * (dG[nu][rho][sig] + dG[sig][rho][nu] - dG[rho][nu][sig]);
                    }
                    gammaSyms[mu][nu][sig] = 0.5 * sum;
                }
            }
        }
    }

    /**
     * 联合状态导数(16 维):dU/dτ = −Γ(U,U),de_a/dτ = −Γ(U,e_a)(平行输运方程;
     * 对时间腿 U 而言该式即测地线方程,故四条腿用同一个 Γ 一次性推进)。
     */
    private void derivQ(double[] x, double[] qv, double[] out) {
        computeChristoffel(x);
        for (int mu = 0; mu < 4; mu++) {
            double sum = 0;
            for (int nu = 0; nu < 4; nu++) {
                double unu = qv[nu];
                for (int sig = 0; sig < 4; sig++) {
                    sum += gammaSyms[mu][nu][sig] * unu * qv[sig];
                }
            }
            out[mu] = -sum;
        }
        for (int a = 0; a < 3; a++) {
            int off = 4 + 4 * a;
            for (int mu = 0; mu < 4; mu++) {
                double sum = 0;
                for (int nu = 0; nu < 4; nu++) {
                    double unu = qv[nu];
                    for (int sig = 0; sig < 4; sig++) {
                        sum += gammaSyms[mu][nu][sig] * unu * qv[off + sig];
                    }
                }
                out[off + mu] = -sum;
            }
        }
    }

    /** 单个 RK4 子步(X 与 q=[U,e1,e2,e3] 联立),随后做 U 约束投影与标架重正交。
     *  注意 X 方程的斜率是各阶段的 U 值(dX/dτ=U),与 dQ/dτ=derivQ 分开保存。 */
    private void rk4Step(double h) {
        System.arraycopy(velU, 0, qNow, 0, 4);
        for (int a = 0; a < 3; a++) {
            System.arraycopy(tetrad[a], 0, qNow, 4 + 4 * a, 4);
        }
        // ---- 阶段 1 ----
        derivQ(posX, qNow, kQ[0]);
        System.arraycopy(qNow, 0, uStages[0], 0, 4);
        for (int i = 0; i < 4; i++) {
            xTmp[i] = posX[i] + 0.5 * h * uStages[0][i];
        }
        for (int i = 0; i < 16; i++) {
            qTmp[i] = qNow[i] + 0.5 * h * kQ[0][i];
        }
        // ---- 阶段 2 ----
        derivQ(xTmp, qTmp, kQ[1]);
        System.arraycopy(qTmp, 0, uStages[1], 0, 4);
        for (int i = 0; i < 4; i++) {
            xTmp[i] = posX[i] + 0.5 * h * uStages[1][i];
        }
        for (int i = 0; i < 16; i++) {
            qTmp[i] = qNow[i] + 0.5 * h * kQ[1][i];
        }
        // ---- 阶段 3 ----
        derivQ(xTmp, qTmp, kQ[2]);
        System.arraycopy(qTmp, 0, uStages[2], 0, 4);
        for (int i = 0; i < 4; i++) {
            xTmp[i] = posX[i] + h * uStages[2][i];
        }
        for (int i = 0; i < 16; i++) {
            qTmp[i] = qNow[i] + h * kQ[2][i];
        }
        // ---- 阶段 4 ----
        derivQ(xTmp, qTmp, kQ[3]);
        System.arraycopy(qTmp, 0, uStages[3], 0, 4);
        // ---- 组合 ----
        for (int i = 0; i < 4; i++) {
            posX[i] += h / 6.0 * (uStages[0][i] + 2.0 * uStages[1][i]
                    + 2.0 * uStages[2][i] + uStages[3][i]);
        }
        for (int i = 0; i < 16; i++) {
            qNow[i] += h / 6.0 * (kQ[0][i] + 2.0 * kQ[1][i] + 2.0 * kQ[2][i] + kQ[3][i]);
        }
        System.arraycopy(qNow, 0, velU, 0, 4);
        for (int a = 0; a < 3; a++) {
            System.arraycopy(qNow, 4 + 4 * a, tetrad[a], 0, 4);
        }
        projectConstraint();
        renormalizeTetrad();
    }

    /**
     * 标架 Gram-Schmidt 重正交(每子步调用,清除平行输运的数值漂移):
     * 逐腿去除 U 分量与前腿投影,按 Lorentz 范数归一(g(e,e)>0,时间向 g(U,U)=-1)。
     */
    private void renormalizeTetrad() {
        if (!tetradActive) {
            return;
        }
        computeMetric(posX, gDown, gUp);
        for (int a = 0; a < 3; a++) {
            double[] leg = tetrad[a];
            // 去时间腿分量:g(U,U) = -1 → e' = e + g(e,U)·U
            double gEU = metricDot(leg, velU);
            for (int mu = 0; mu < 4; mu++) {
                leg[mu] += gEU * velU[mu];
            }
            // 去前腿投影
            for (int b = 0; b < a; b++) {
                double gEE = metricDot(leg, tetrad[b]);
                for (int mu = 0; mu < 4; mu++) {
                    leg[mu] -= gEE * tetrad[b][mu];
                }
            }
            double norm = Math.sqrt(Math.max(1e-300, metricDot(leg, leg)));
            for (int mu = 0; mu < 4; mu++) {
                leg[mu] /= norm;
            }
        }
    }

    /** 度规内积 g_μν a^μ b^ν(依赖 computeMetric 预先填充 gDown) */
    private double metricDot(double[] a, double[] b) {
        double s = 0;
        for (int mu = 0; mu < 4; mu++) {
            double t = 0;
            for (int nu = 0; nu < 4; nu++) {
                t += gDown[mu][nu] * b[nu];
            }
            s += a[mu] * t;
        }
        return s;
    }

    /**
     * 以相机世界轴(right/up/back)为基准初始化标架(Gram-Schmidt 度规正交化)。
     * G 进入测地模式时调用:初标架 = 当前相机轴 → 画面无跳变,此后标架随测地线输运。
     */
    public void initializeTetrad(Vector3f right, Vector3f up, Vector3f back) {
        tetradActive = true;
        computeMetric(posX, gDown, gUp);
        Vector3f[] fid = {right, up, back};
        for (int a = 0; a < 3; a++) {
            double[] leg = tetrad[a];
            leg[0] = fid[a].x;
            leg[1] = fid[a].y;
            leg[2] = fid[a].z;
            leg[3] = 0.0;
            // 去时间腿分量
            double gEU = metricDot(leg, velU);
            for (int mu = 0; mu < 4; mu++) {
                leg[mu] += gEU * velU[mu];
            }
            // 去前腿投影
            for (int b = 0; b < a; b++) {
                double gEE = metricDot(leg, tetrad[b]);
                for (int mu = 0; mu < 4; mu++) {
                    leg[mu] -= gEE * tetrad[b][mu];
                }
            }
            double norm = Math.sqrt(Math.max(1e-300, metricDot(leg, leg)));
            for (int mu = 0; mu < 4; mu++) {
                leg[mu] /= norm;
            }
        }
    }

    /** 注入鼠标头部旋转(测地模式每帧同步;着色器折叠与相机姿态推导共用) */
    public void setHeadRotation(Quaternionf q) {
        headQuat.set(q);
    }

    /**
     * 折叠头部旋转后的相机标架:E_i = Σ_j H[ij]·e_j(正交阵线性组合,保持 Lorentz 正交)。
     * H=I(刚进入)时 E_i 即输运标架本身。out[3][4] 上指标 4 矢量,供 KerrRender 打包
     * iU_up 之外的 ie1/2/3_up(kerr.frag 观者模式 -1 的 P = U + Σ v·E 构造)。
     */
    public void getFoldedTetrad(double[][] out) {
        headMat.rotation(headQuat);
        for (int i = 0; i < 3; i++) {
            for (int mu = 0; mu < 4; mu++) {
                double s = 0;
                for (int j = 0; j < 3; j++) {
                    // E_i = Σ_j t_j·H[j][i](M = T·H,头四元数=绕头部局部轴右乘,FPS 手感);
                    // joml Matrix3f.get(column,row) → 元素 H[j][i] 写作 get(i, j)
                    s += headMat.get(i, j) * tetrad[j][mu];
                }
                out[i][mu] = s;
            }
        }
    }

    /** 输运标架第 leg 腿的空间部分(世界 KS 坐标;Main 每帧推导相机姿态用) */
    public Vector3f getTetradSpatial(int leg, Vector3f out) {
        return out.set((float) tetrad[leg][0], (float) tetrad[leg][1], (float) tetrad[leg][2]);
    }

    /** 观者四速度 U^μ(上指标,KS 坐标系;KerrRender 打包 iU_up 用) */
    public void getFourVelocity(double[] out) {
        System.arraycopy(velU, 0, out, 0, 4);
    }

    /** 标架正交性误差:max|g(e_a,e_b)−δ_ab| 与 |g(e_a,U)|(GeoTest 数值验收用) */
    public double tetradOrthoError() {
        computeMetric(posX, gDown, gUp);
        double err = 0;
        for (int a = 0; a < 3; a++) {
            err = Math.max(err, Math.abs(metricDot(tetrad[a], velU)));
            for (int b = 0; b < 3; b++) {
                double target = (a == b) ? 1.0 : 0.0;
                err = Math.max(err, Math.abs(metricDot(tetrad[a], tetrad[b]) - target));
            }
        }
        return err;
    }

    /** 由空间分量重解 U^t(g_tt U^t² + B U^t + C = 0 取正根),投影 U·U = -1 */
    private void projectConstraint() {
        computeMetric(posX, gDown, gUp);
        double ux = velU[0], uy = velU[1], uz = velU[2];
        double a = gDown[3][3];
        if (a >= 0) {
            return;   // 视界内 g_tt > 0,静态观者不存在,保留原值
        }
        double b = 2.0 * (gDown[3][0] * ux + gDown[3][1] * uy + gDown[3][2] * uz);
        double c = gDown[0][0] * ux * ux + gDown[1][1] * uy * uy + gDown[2][2] * uz * uz
                + 2.0 * (gDown[0][1] * ux * uy + gDown[0][2] * ux * uz + gDown[1][2] * uy * uz)
                + 1.0;
        double disc = b * b - 4 * a * c;
        if (disc < 0) {
            return;
        }
        velU[3] = (-b - Math.sqrt(disc)) / (2 * a);
    }

    /** 计算相机瞬时速度:β 方向 = 坐标空间速度方向,大小 = √(1 + g_tt/U_t²);γ = 1/√(1-v²) */
    private void updateBetaGamma() {
        double ut = velU[3];
        double vx = velU[0] / ut, vy = velU[1] / ut, vz = velU[2] / ut;
        double norm = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double utCov = gDown[3][3] * ut + gDown[3][0] * velU[0] + gDown[3][1] * velU[1] + gDown[3][2] * velU[2];
        double v2 = 1.0 + gDown[3][3] / (utCov * utCov);
        if (norm < 1e-12 || v2 <= 0 || v2 >= 1) {
            beta.set(0.0f, 0.0f, 0.0f);
            gamma = 1.0f;
            return;
        }
        double v = Math.sqrt(v2);
        beta.set((float) (vx / norm * v), (float) (vy / norm * v), (float) (vz / norm * v));
        gamma = (float) (1.0 / Math.sqrt(1.0 - v * v));
    }
}
