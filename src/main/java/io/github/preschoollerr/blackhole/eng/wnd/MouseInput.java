package io.github.preschoollerr.blackhole.eng.wnd;

import imgui.flag.ImGuiMouseButton;
import org.joml.Vector2f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 鼠标输入处理器 —— 追踪鼠标位置、移动增量和按钮状态。
 * <p>
 * 通过 GLFW 回调注册三个事件：
 * <ul>
 *   <li>光标位置回调 —— 更新当前鼠标位置</li>
 *   <li>光标进入/离开回调 —— 追踪鼠标是否在窗口内</li>
 *   <li>鼠标按钮回调 —— 追踪左键和右键的按下状态</li>
 * </ul>
 * <p>
 * 每帧调用 {@link #input()} 时计算鼠标移动增量（deltaPos），
 * 用于相机轨道旋转等操作。
 */
public class MouseInput {

    /** 当前鼠标位置（像素坐标） */
    private final Vector2f currentPos;
    /** 本帧鼠标移动增量（像素） */
    private final Vector2f deltaPos;
    /** 上一帧的鼠标位置 */
    private final Vector2f previousPos;
    /** 滚轮x */
    private float pendingScrollX;
    /** 滚轮y */
    private float pendingScrollY;
    /** 鼠标是否在窗口内 */
    private boolean inWindow;
    /** 左键是否按下 */
    private boolean leftButtonPressed;
    /** 右键是否按下 */
    private boolean rightButtonPressed;
    /** 右键单击事件（按下当帧有效，resetInput 后清除） */
    private boolean rightButtonSinglePress;
    /** 是否已有上一帧光标位置（光标捕获模式下虚拟坐标可为负，不能用 -1 作哨兵） */
    private boolean hasPreviousPos;
    /**
     * 本帧待喂给 ImGui 的鼠标按钮<b>边沿</b>事件（GLFW 回调写入，Panels.buildFrame 消费后清空）。
     * <p>
     * 轮询 leftButtonPressed 喂 ImGui 的隐患：glfwPollEvents 会把落在同一帧内的
     * press+release 一起处理完，轮询只读到最终的"未按下"，整次点击丢失。人手点击时长
     * （约 50~100ms）通常跨多帧故能侥幸生效，但低帧率下一帧即可吞掉一次点击
     * （测地近距掉到 10 FPS 时一帧 100ms），按事件喂则与帧长无关。
     */
    private static final int PENDING_MAX = 16;
    private final int[] pendingBtn = new int[PENDING_MAX];
    private final boolean[] pendingDown = new boolean[PENDING_MAX];
    private int pendingCount;

    /**
     * 构造鼠标输入处理器并注册 GLFW 回调。
     *
     * @param windowHandle GLFW 窗口句柄
     */
    public MouseInput(long windowHandle) {
        previousPos = new Vector2f(-1, -1);
        currentPos = new Vector2f();
        deltaPos = new Vector2f();
        leftButtonPressed = false;
        rightButtonPressed = false;
        inWindow = false;

        // 光标位置回调：实时更新当前鼠标位置
        glfwSetCursorPosCallback(windowHandle, (handle, xpos, ypos) -> {
            currentPos.x = (float) xpos;
            currentPos.y = (float) ypos;
        });

        // 光标进入/离开回调
        glfwSetCursorEnterCallback(windowHandle, (handle, entered) -> inWindow = entered);

        // 鼠标按钮回调：追踪左键和右键的按下状态，并排队边沿事件供 ImGui 消费
        glfwSetMouseButtonCallback(windowHandle, (handle, button, action, mode) -> {
            queueButton(button, action != GLFW_RELEASE);
            if (button == GLFW_MOUSE_BUTTON_1) {
                leftButtonPressed = action == GLFW_PRESS;
            }
            if (button == GLFW_MOUSE_BUTTON_2) {
                rightButtonPressed = action == GLFW_PRESS;
                if (action == GLFW_PRESS) {
                    rightButtonSinglePress = true;
                }
            }
        });

        glfwSetScrollCallback(windowHandle, (handle, offsetX, offsetY) -> {
            pendingScrollX += offsetX;
            pendingScrollY += offsetY;
        });
    }

    /** GLFW 按钮 → ImGui 按钮索引，其余返回 -1 */
    public static int imguiButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_1 -> ImGuiMouseButton.Left;
            case GLFW_MOUSE_BUTTON_2 -> ImGuiMouseButton.Right;
            case GLFW_MOUSE_BUTTON_3 -> ImGuiMouseButton.Middle;
            default -> -1;
        };
    }

    private void queueButton(int glfwButton, boolean down) {
        int imButton = imguiButton(glfwButton);
        if (imButton < 0 || pendingCount == PENDING_MAX) {
            return;
        }
        pendingBtn[pendingCount] = imButton;
        pendingDown[pendingCount] = down;
        pendingCount++;
    }

    /** 待喂 ImGui 的按钮事件条数 */
    public int pendingButtonCount() {
        return pendingCount;
    }

    /** 第 index 条待喂事件的 ImGui 按钮索引 */
    public int pendingButton(int index) {
        return pendingBtn[index];
    }

    /** 第 index 条待喂事件的按下状态 */
    public boolean pendingButtonDown(int index) {
        return pendingDown[index];
    }

    /** 清空待喂事件队列（buildFrame 消费后调用，避免隔帧重放） */
    public void clearPendingButtons() {
        pendingCount = 0;
    }

    public Vector2f getCurrentPos() {
        return currentPos;
    }

    public Vector2f getDeltaPos() {
        return deltaPos;
    }

    /**
     * 更新鼠标增量：计算当前帧与上一帧的位置差。
     * <p>
     * 在引擎主循环的 pollEvents() 之后调用。
     * 首帧（尚无上一帧位置）或鼠标不在窗口内时增量为零。
     * 注意：光标捕获（GLFW_CURSOR_DISABLED）下虚拟坐标可为负（向左/上越过窗口原点），
     * 不能用"坐标非负"来判断是否有上一帧数据。
     */
    public void input() {
        deltaPos.x = 0;
        deltaPos.y = 0;
        if (hasPreviousPos && inWindow) {
            deltaPos.x = currentPos.x - previousPos.x;
            deltaPos.y = currentPos.y - previousPos.y;
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
        hasPreviousPos = true;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }

    /** 右键单击检测：仅在按下当帧返回 true（适合切换类操作，如视角捕获） */
    public boolean isRightButtonSinglePress() {
        return rightButtonSinglePress;
    }

    public float consumeScrollX() {
        float v = pendingScrollX;
        pendingScrollX = 0.0f;
        return v;
    }

    public float consumeScrollY() {
        float v = pendingScrollY;
        pendingScrollY = 0.0f;
        return v;
    }

    /** 重置单帧鼠标状态。在每帧输入处理完成后调用。 */
    public void resetInput() {
        rightButtonSinglePress = false;
    }
}
