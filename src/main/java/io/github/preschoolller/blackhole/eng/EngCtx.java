package io.github.preschoolller.blackhole.eng;

import io.github.preschoolller.blackhole.eng.scene.Scene;
import io.github.preschoolller.blackhole.eng.wnd.Window;

/**
 * 引擎上下文 —— 不可变记录类（record）。
 * <p>
 * 持有当前会话的核心对象引用：
 * <ul>
 *   <li>{@link Window} —— GLFW 窗口</li>
 *   <li>{@link Scene} —— 场景（包含相机和投影矩阵）</li>
 * </ul>
 * 在整个引擎生命周期中作为参数传递，避免全局状态。
 *
 * @param window 窗口实例
 * @param scene  场景实例
 */
public record EngCtx(Window window, Scene scene) {

    /** 释放窗口资源 */
    public void cleanup() {
        window.cleanup();
    }
}
