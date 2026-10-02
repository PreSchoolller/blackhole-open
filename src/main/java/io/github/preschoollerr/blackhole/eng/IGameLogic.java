package io.github.preschoollerr.blackhole.eng;

/**
 * 游戏逻辑接口 —— 引擎与具体应用之间的契约。
 * <p>
 * 引擎主循环在以下时机调用各方法：
 * <ul>
 *   <li>{@link #init}     —— 引擎初始化完成后调用一次</li>
 *   <li>{@link #input}    —— 每帧轮询事件后调用，处理用户输入</li>
 *   <li>{@link #update}   —— 按 UPS 频率调用，执行逻辑更新</li>
 *   <li>{@link #cleanup}  —— 窗口关闭后调用，释放资源</li>
 * </ul>
 */
public interface IGameLogic {

    /** 释放资源 */
    void cleanup();

    /**
     * 初始化（仅调用一次）。
     *
     * @param engCtx 引擎上下文
     */
    void init(EngCtx engCtx);

    /**
     * 处理输入（每帧调用）。
     *
     * @param engCtx          引擎上下文
     * @param diffTimeMillis  距上一帧的时间差（毫秒）
     */
    void input(EngCtx engCtx, long diffTimeMillis);

    /**
     * 逻辑更新（按 UPS 频率调用）。
     *
     * @param engCtx          引擎上下文
     * @param diffTimeMillis  距上一次更新的时间差（毫秒）
     */
    void update(EngCtx engCtx, long diffTimeMillis);
}
