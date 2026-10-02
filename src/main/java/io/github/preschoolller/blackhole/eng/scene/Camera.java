package io.github.preschoolller.blackhole.eng.scene;

import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import io.github.preschoolller.blackhole.eng.AppLog;

/**
 * 相机类 —— 支持轨道（Orbit）和自由视角（Free-Fly）两种模式，可随时无缝切换。
 * <p>
 * 轨道模式（Orbit）：
 * <ul>
 *   <li>相机始终朝向原点 (0,0,0)</li>
 *   <li>通过方位角（azimuth）、仰角（elevation）和距离（distance）控制位置</li>
 *   <li>使用球坐标系转换为笛卡尔坐标</li>
 * </ul>
 * <p>
 * 自由视角模式（Free-Fly）：
 * <ul>
 *   <li>姿态用四元数表示（相机局部系 → 世界系），鼠标/翻滚均为绕相机局部轴的增量旋转，
 *       全自由 6DOF：可俯仰越过头顶、翻滚始终绕视线轴，无万向锁、无俯仰钳制</li>
 *   <li>视图矩阵 V = conjugate(orientation)·T(-position)</li>
 *   <li>切换模式时从当前位姿换算参数，视角与位置均无跳变（翻滚归零）</li>
 * </ul>
 * <p>
 * 测地线模式（Geodesic）：
 * <ul>
 *   <li>位置由 {@link GeodesicIntegrator} 按史瓦西测地线驱动（运动归物理），
 *       姿态仍由鼠标自由控制（转头归玩家），视图矩阵与 Free-Fly 相同</li>
 * </ul>
 * <p>
 * 各模式共用 position；模式切换会改变视图矩阵，TAA 的相机变化检测会自动重置时域累积。
 */
public class Camera {

    /** 相机模式：轨道 / 自由视角 / 测地线 */
    public enum CameraMode { ORBIT, FREE_FLY, GEODESIC }

    /** 相机前方向向量（由视图矩阵计算得出） */
    private final Vector3f direction;
    /** 相机世界坐标位置 */
    private final Vector3f position;
    /** 相机右方向向量 */
    private final Vector3f right;
    /** 相机上方向向量 */
    private final Vector3f up;
    /** 视图矩阵（View Matrix） */
    private final Matrix4f viewMatrix;

    /** 当前模式（默认轨道） */
    private CameraMode mode = CameraMode.ORBIT;
    /** 轨道方位角（弧度）：绕 Y 轴的水平旋转角度 */
    private float orbitAzimuth;
    /** 轨道距离：相机到原点的距离 */
    private float orbitDistance;
    /** 轨道仰角（弧度）：相机在垂直方向的旋转角度，范围 [-89°, 89°] */
    private float orbitElevation;
    /** 自由视角姿态（相机局部系 → 世界系），四元数支持全自由 6DOF，无万向锁 */
    private final Quaternionf freeOrientation = new Quaternionf();
    /** 临时变量：视图矩阵所需的逆姿态（世界系 → 相机局部系） */
    private final Quaternionf freeInvOrientation = new Quaternionf();

    /**
     * 构造相机并设置默认轨道参数。
     * 默认位置：仰角 0.15 弧度（约 8.6°），距离 18 单位。
     */
    public Camera() {
        direction = new Vector3f();
        right = new Vector3f();
        up = new Vector3f();
        position = new Vector3f();
        viewMatrix = new Matrix4f();
        orbitAzimuth = 0.0f;
        orbitElevation = 0.15f;
        orbitDistance = 18.0f;
        updateOrbitPosition();
    }

    /**
     * 轨道旋转：调整方位角和仰角。
     * 仰角被钳制在 [-89°, 89°] 范围内，防止万向锁。
     *
     * @param azimuthDelta   方位角增量（弧度）
     * @param elevationDelta 仰角增量（弧度）
     */
    public void addOrbitRotation(float azimuthDelta, float elevationDelta) {
        orbitAzimuth += azimuthDelta;
        orbitElevation += elevationDelta;
        if (Math.abs(elevationDelta) >= 0.01) {
            AppLog.info("当前仰角： " + Math.toDegrees(orbitElevation));
        }
        orbitElevation = Math.max((float) Math.toRadians(-89.0), Math.min((float) Math.toRadians(89.0), orbitElevation));
        updateOrbitPosition();
    }

    /**
     * 缩放：调整轨道距离（相机到原点的距离）。
     * 距离被钳制在 [3, 50] 范围内，防止穿过黑洞或过远。
     *
     * @param delta 距离增量（负值拉近，正值拉远）
     */
    public void zoom(float delta) {
        orbitDistance += delta;
        orbitDistance = Math.max(3.0f, Math.min(50.0f, orbitDistance));
        updateOrbitPosition();
    }

    /**
     * 根据轨道参数（方位角、仰角、距离）计算相机位置。
     * 使用球坐标系到笛卡尔坐标的转换：
     * <pre>
     * x = distance * cos(elevation) * sin(azimuth)
     * y = distance * sin(elevation)
     * z = distance * cos(elevation) * cos(azimuth)
     * </pre>
     */
    private void updateOrbitPosition() {
        float x = orbitDistance * (float) Math.cos(orbitElevation) * (float) Math.sin(orbitAzimuth);
        float y = orbitDistance * (float) Math.sin(orbitElevation);
        float z = orbitDistance * (float) Math.cos(orbitElevation) * (float) Math.cos(orbitAzimuth);
        position.set(x, y, z);
        recalculate();
    }

    /**
     * 切换轨道/自由视角模式，切换瞬间视角与位置均无跳变：
     * <ul>
     *   <li>ORBIT → FREE_FLY：保持指向原点的视线方向，换算为 yaw/pitch</li>
     *   <li>FREE_FLY → ORBIT：从当前位置反推方位角/仰角/距离（距离钳制到 [3,50]，
     *       越界时沿同一视线方向吸附到边界，会有一次轻微推拉）</li>
     * </ul>
     *
     * @return 切换后的模式
     */
    public CameraMode toggleMode() {
        if (mode == CameraMode.GEODESIC) {
            return mode;   // 测地模式下 F 无效：积分器驱动位置时切 ORBIT/FREE_FLY 会状态错乱（由 G 管理进出）
        }
        if (mode == CameraMode.ORBIT) {
            mode = CameraMode.FREE_FLY;
            // 保持指向原点的视线方向、翻滚为零：由视线方向换算 yaw/pitch 再构造姿态四元数
            Vector3f dir = new Vector3f(position).negate().normalize();
            float pitch = (float) Math.asin(Math.clamp(dir.y, -1.0f, 1.0f));
            float yaw = (float) Math.atan2(dir.x, -dir.z);
            // 相机世界姿态 R = Ry(-yaw)·Rx(pitch)，其正向看方向恰为 dir
            // （用 setter + mul 显式控制乘法顺序，不依赖 rotate 系列的方向约定）
            freeOrientation.rotationY(-yaw).mul(new Quaternionf().rotationX(pitch));
            recalculate();
        } else {
            mode = CameraMode.ORBIT;
            // 从当前位置反推轨道参数（与 updateOrbitPosition 的球坐标公式互逆）
            float r = position.length();
            orbitDistance = r;
            orbitElevation = (float) Math.asin(Math.clamp(position.y / Math.max(r, 1e-6f), -1.0f, 1.0f));
            orbitAzimuth = (float) Math.atan2(position.x, position.z);
            orbitDistance = Math.max(3.0f, Math.min(50.0f, orbitDistance));
            updateOrbitPosition();
        }
        return mode;
    }

    /**
     * 自由视角旋转（鼠标拖拽），FPS 标准手感：拖动方向 = 视线转向（拖右看右、拖上抬头）。
     * 绕相机局部 Y 轴（偏航）和局部 X 轴（俯仰）做增量旋转。
     * 注意：必须用 rotateY/rotateX（局部轴、右乘），rotateLocalY/X 实际绕父级/世界轴
     * （俯视时水平拖动会变成绕视线轴原地旋转、Q/E 反而变成平移）。
     *
     * @param yawDelta   偏航增量（弧度）
     * @param pitchDelta 俯仰增量（弧度）
     */
    public void rotateFree(float yawDelta, float pitchDelta) {
        freeOrientation.rotateY(yawDelta).rotateX(-pitchDelta).normalize();
        recalculate();
    }

    /**
     * 自由视角翻滚：绕视线轴（相机局部 Z）旋转，正 = 向左滚。
     *
     * @param rollDelta 翻滚增量（弧度）
     */
    public void addRoll(float rollDelta) {
        freeOrientation.rotateZ(rollDelta).normalize();
        recalculate();
    }

    public CameraMode getMode() {
        return mode;
    }

    /**
     * 直接设置模式（供测地模式进出使用，不做位置/姿态换算）。
     *
     * @param m 目标模式
     */
    public void setModeRaw(CameraMode m) {
        mode = m;
        recalculate();
    }

    /**
     * 直接设置位置（测地积分器每帧回写用），随后重算视图矩阵。
     *
     * @param p 世界坐标位置（Rs 单位）
     */
    public void setPosition(Vector3f p) {
        position.set(p);
        recalculate();
    }

    /**
     * 直接设置自由视角姿态四元数（Phase 5 测地模式每帧回写用：由输运标架+头部旋转
     * 推导出的世界姿态），随后重算视图矩阵。仅对 FREE_FLY/GEODESIC 的 freeOrientation 生效。
     *
     * @param q 世界姿态（单位四元数）
     */
    public void setOrientationRaw(Quaternionf q) {
        freeOrientation.set(q).normalize();
        recalculate();
    }

    /**
     * 取自由视角姿态四元数（副本）。
     *
     * @return 当前姿态（新分配对象）
     */
    public Quaternionf getOrientation() {
        return new Quaternionf(freeOrientation);
    }

    /**
     * 获取当前视线方向（单位向量）。
     *
     * @return 新分配的方向向量
     */
    public Vector3f getViewDirection() {
        Vector3f dir = new Vector3f();
        viewMatrix.positiveZ(dir).negate();
        return dir;
    }

    /** 向前移动：沿视图矩阵 Z 轴负方向（即相机朝向方向） */
    public void moveForward(float inc) {
        viewMatrix.positiveZ(direction).negate().mul(inc);
        position.add(direction);
        orbitDistance = position.length();
        recalculate();
    }

    /** 向后移动：沿视图矩阵 Z 轴正方向 */
    public void moveBackwards(float inc) {
        viewMatrix.positiveZ(direction).negate().mul(inc);
        position.sub(direction);
        orbitDistance = position.length();
        recalculate();
    }

    /** 向左移动：沿视图矩阵 X 轴负方向 */
    public void moveLeft(float inc) {
        viewMatrix.positiveX(right).mul(inc);
        position.sub(right);
        orbitDistance = position.length();
        recalculate();
    }

    /** 向右移动：沿视图矩阵 X 轴正方向 */
    public void moveRight(float inc) {
        viewMatrix.positiveX(right).mul(inc);
        position.add(right);
        orbitDistance = position.length();
        recalculate();
    }

    /** 向上移动：沿视图矩阵 Y 轴正方向 */
    public void moveUp(float inc) {
        viewMatrix.positiveY(up).mul(inc);
        position.add(up);
        orbitDistance = position.length();
        recalculate();
    }

    /** 向下移动：沿视图矩阵 Y 轴负方向 */
    public void moveDown(float inc) {
        viewMatrix.positiveY(up).mul(inc);
        position.sub(up);
        orbitDistance = position.length();
        recalculate();
    }

    /**
     * 重新计算视图矩阵。
     * 轨道模式：lookAt 原点 (0,0,0)，上方向 (0,1,0)，确保相机始终朝向黑洞。
     * 自由视角/测地线：V = conjugate(orientation)·T(-position)，姿态四元数即相机世界朝向。
     */
    private void recalculate() {
        if (mode == CameraMode.FREE_FLY || mode == CameraMode.GEODESIC) {
            freeInvOrientation.set(freeOrientation).conjugate();
            viewMatrix.rotation(freeInvOrientation).translate(-position.x, -position.y, -position.z);
        } else {
            viewMatrix.identity();
            viewMatrix.lookAt(position, new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        }
    }

    public Vector3f getPosition() {
        return position;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    public float getOrbitAzimuth() {
        return orbitAzimuth;
    }

    public float getOrbitDistance() {
        return orbitDistance;
    }

    public float getOrbitElevation() {
        return orbitElevation;
    }
}
