package vulkanb.eng.wnd;

import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

/**
 * 窗口管理类 —— 封装 GLFW 窗口创建和事件处理。
 * <p>
 * 职责：
 * <ul>
 *   <li>初始化 GLFW 并验证 Vulkan 支持</li>
 *   <li>创建全屏尺寸的窗口（使用主显示器分辨率）</li>
 *   <li>管理键盘和鼠标输入回调</li>
 *   <li>处理窗口大小变化事件</li>
 * </ul>
 * <p>
 * 注意：窗口创建时指定 {@code GLFW_NO_API}，不创建 OpenGL 上下文，
 * 因为本项目使用 Vulkan 进行渲染。
 */
public class Window {

    /** GLFW 窗口句柄（原生指针） */
    private final long handle;
    /** 键盘输入处理器 */
    private final KeyboardInput keyboardInput;
    /** 鼠标输入处理器 */
    private final MouseInput mouseInput;
    /** 窗口高度（可能随窗口大小变化而更新） */
    private int height;
    /** 窗口宽度 */
    private int width;
    /** 是否需要重建渲染管线（窗口大小变化时设置） */
    private boolean resizeNeeded = false;

    /**
     * 创建窗口。
     * <p>
     * 流程：GLFW 初始化 → Vulkan 支持检查 → 获取显示器尺寸 →
     * 配置窗口提示 → 创建窗口 → 注册输入回调 → 注册大小变化回调
     *
     * @param title 窗口标题
     * @throws RuntimeException 如果 GLFW 初始化失败、不支持 Vulkan 或窗口创建失败
     */
    public Window(String title) {
        // 初始化 GLFW
        if (!glfwInit()) {
            throw new RuntimeException("Unable to initialize GLFW");
        }

        // 检查 Vulkan 驱动支持
        if (!glfwVulkanSupported()) {
            throw new RuntimeException("Cannot find a compatible Vulkan installable client driver (ICD)");
        }

        // 初始窗口尺寸：eng.properties 的 window.width/height（jar 外覆盖文件可改）
        var engCfg = vulkanb.eng.EngCfg.getInstance();
        width = Math.max(320, engCfg.getWindowWidth());
        height = Math.max(240, engCfg.getWindowHeight());

        // 配置窗口提示：不创建 OpenGL 上下文（GLFW_NO_API），不最大化
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);

        // 创建窗口
        handle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (handle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // 注册键盘输入回调
        keyboardInput = new KeyboardInput(handle);

        // 注册窗口大小变化回调：更新 width/height，并标记需要重建渲染管线
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> {
            width = w;
            height = h;
            resizeNeeded = true;
        });

        // 注册鼠标输入回调
        mouseInput = new MouseInput(handle);
    }

    /** 释放 GLFW 资源：回调、窗口和 GLFW 本身 */
    public void cleanup() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    /** 获取原生窗口句柄 */
    public long getHandle() {
        return handle;
    }

    public int getHeight() {
        return height;
    }

    public KeyboardInput getKeyboardInput() {
        return keyboardInput;
    }

    public MouseInput getMouseInput() {
        return mouseInput;
    }

    public int getWidth() {
        return width;
    }

    /** 检查是否需要重建渲染管线（窗口大小变化时返回 true） */
    public boolean needsResize() {
        return resizeNeeded;
    }

    /** 重置重建标志（在 Engine 主循环中调用） */
    public void resetResizeFlag() {
        resizeNeeded = false;
    }

    /**
     * 轮询事件：更新键盘和鼠标状态。
     * 内部调用 glfwPollEvents() 触发所有注册的回调。
     */
    public void pollEvents() {
        keyboardInput.input();
        mouseInput.input();
    }

    /** 重置键盘和鼠标的单帧输入状态 */
    public void resetInput() {
        keyboardInput.resetInput();
        mouseInput.resetInput();
    }

    /** 隐藏并捕获光标：光标不可见，提供无限的虚拟移动（用于视角控制） */
    public void grabCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    /** 恢复显示光标 */
    public void releaseCursor() {
        glfwSetInputMode(handle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    /** 请求关闭窗口 */
    public void setShouldClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    /** 检查窗口是否应该关闭 */
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }
}
