package io.github.preschoollerr.blackhole.eng.scene;

import org.joml.Matrix4f;

/**
 * 透视投影矩阵管理器。
 * <p>
 * 使用 JOML 的 {@link Matrix4f#perspective} 生成标准透视投影矩阵。
 * 当窗口大小变化时，调用 {@link #resize} 重新计算宽高比。
 * <p>
 * 投影矩阵在着色器中通过逆矩阵还原为视图空间坐标，
 * 用于构建从屏幕像素到世界空间的射线。
 */
public class Projection {

    /** 视场角（弧度） */
    private final float fov;
    /** 4×4 投影矩阵 */
    private final Matrix4f projectionMatrix;
    /** 远裁剪面距离 */
    private final float zFar;
    /** 近裁剪面距离 */
    private final float zNear;

    /**
     * 构造投影矩阵并计算初始宽高比。
     *
     * @param fov    视场角（弧度）
     * @param zNear  近裁剪面距离
     * @param zFar   远裁剪面距离
     * @param width  视口宽度（像素）
     * @param height 视口高度（像素）
     */
    public Projection(float fov, float zNear, float zFar, int width, int height) {
        this.fov = fov;
        this.zNear = zNear;
        this.zFar = zFar;
        projectionMatrix = new Matrix4f();
        resize(width, height);
    }

    public float getFov() {
        return fov;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public float getZFar() {
        return zFar;
    }

    public float getZNear() {
        return zNear;
    }

    /**
     * 重新计算投影矩阵（窗口大小变化时调用）。
     *
     * @param width  新视口宽度
     * @param height 新视口高度
     */
    public void resize(int width, int height) {
        projectionMatrix.identity();
        // true 参数表示 Vulkan 的深度范围 [0, 1]（而非 OpenGL 的 [-1, 1]）
        projectionMatrix.perspective(fov, (float) width / (float) height, zNear, zFar, true);
    }
}
