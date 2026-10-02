package io.github.preschoolller.blackhole.eng.wnd;

import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 键盘输入处理器 —— 封装 GLFW 键盘回调。
 * <p>
 * 提供两种按键查询方式：
 * <ul>
 *   <li>{@link #keyPressed} —— 持续按键检测（只要按键未释放就返回 true）</li>
 *   <li>{@link #keySinglePress} —— 单帧按键检测（仅在按下当帧返回 true，需配合 resetInput）</li>
 * </ul>
 * <p>
 * 同时支持外部注册额外的键盘回调（如用于调试信息切换等）。
 */
public class KeyboardInput implements GLFWKeyCallbackI {

    /** 单帧按键状态表：keyCode → 是否按下（仅在按下帧有效） */
    private final Map<Integer, Boolean> singlePressKeyMap;
    /** GLFW 窗口句柄 */
    private final long windowHandle;
    /** 外部注册的额外键盘回调列表 */
    private List<GLFWKeyCallbackI> callbacks;

    /**
     * 构造键盘输入处理器并注册 GLFW 键盘回调。
     *
     * @param windowHandle GLFW 窗口句柄
     */
    public KeyboardInput(long windowHandle) {
        this.windowHandle = windowHandle;
        singlePressKeyMap = new HashMap<>();
        glfwSetKeyCallback(windowHandle, this);
        callbacks = new ArrayList<>();
    }

    /** 注册额外的键盘回调 */
    public void addKeyCallBack(GLFWKeyCallbackI callback) {
        callbacks.add(callback);
    }

    /**
     * 轮询事件：内部调用 glfwPollEvents() 触发所有注册的回调。
     * 调用此方法后，键盘回调会更新 singlePressKeyMap。
     */
    public void input() {
        glfwPollEvents();
    }

    /**
     * GLFW 键盘回调实现。
     * 记录按键状态到 singlePressKeyMap，并转发给所有已注册的外部回调。
     */
    @Override
    public void invoke(long handle, int keyCode, int scanCode, int action, int mods) {
        singlePressKeyMap.put(keyCode, action == GLFW_PRESS);
        int numCallBacks = callbacks.size();
        for (int i = 0; i < numCallBacks; i++) {
            callbacks.get(i).invoke(handle, keyCode, scanCode, action, mods);
        }
    }

    /**
     * 持续按键检测：只要按键处于按下状态就返回 true。
     * 适合移动等持续操作。
     *
     * @param keyCode GLFW 键码（如 GLFW_KEY_W）
     * @return 按键是否处于按下状态
     */
    public boolean keyPressed(int keyCode) {
        return glfwGetKey(windowHandle, keyCode) == GLFW_PRESS;
    }

    /**
     * 单帧按键检测：仅在按键按下的当帧返回 true。
     * 适合切换操作（如切换调试信息）。
     *
     * @param keyCode GLFW 键码
     * @return 本帧是否按下
     */
    public boolean keySinglePress(int keyCode) {
        Boolean value = singlePressKeyMap.get(keyCode);
        return value != null && value;
    }

    /**
     * 重置单帧按键状态表。
     * 在每帧输入处理完成后调用。
     */
    public void resetInput() {
        singlePressKeyMap.clear();
    }

    /** 注册字符输入回调（用于文本输入场景） */
    public void setCharCallBack(GLFWCharCallbackI charCallback) {
        glfwSetCharCallback(windowHandle, charCallback);
    }
}
