package io.github.preschoollerr.blackhole;

import org.joml.Vector3f;
import io.github.preschoollerr.blackhole.eng.AppLog;
import io.github.preschoollerr.blackhole.eng.scene.GeodesicIntegrator;

/**
 * [穿越模拟器] 离线驱动真实 GeodesicIntegrator，复现"中心俯冲到反宇宙"全过程。
 * 不依赖 Vulkan/窗口：java -cp target/blackhole-lwjgl-1.0-SNAPSHOT.jar io.github.preschoollerr.blackhole.TraversalSim
 * <p>
 * 场景：从 r=18 Rs（观赏轨道相机位置）以 v0 沿视线（可带横向偏移）初始化，可选
 * 朝心推力，积分至穿越视界/喉道或超时。逐步记录 r/ρ/y/符号/补丁，统计翻转、
 * 换系、发散恢复与"回弹"（y 逼近 0 又折返）事件。
 */
public class TraversalSim {

    public static void main(String[] args) {
        double aStar = arg(args, 0, 0.9);
        double v0 = arg(args, 1, 0.3);
        double aimOffZ = arg(args, 2, 0.0);   // 瞄准点横向偏移（Rs）：0 = 瞄准黑洞中心
        double thrustSec = arg(args, 3, 5.0); // 朝心推力持续秒数（0 = 无推力纯滑入）
        double yAim = arg(args, 4, 0.0);      // 瞄准点的 y（默认 0 = 瞄准赤道面中心）

        System.out.printf("== a*=%.3f v0=%.2f aimOff=%.2f thrust=%.1fs yAim=%.2f ==%n",
                aStar, v0, aimOffZ, thrustSec, yAim);
        AppLog.setVerbose(true);

        GeodesicIntegrator g = new GeodesicIntegrator();
        g.setSpin(aStar);
        g.setHorizonGuard(false); // 关坠落演出才可穿越（游戏内 Controls 复选框）
        g.setTimeScale(4.0);

        // 与游戏内一致：观赏相机位置 (0, 2.69, 17.80)（r=18Rs），视线指向黑洞中心+偏移
        int mode = args.length > 5 ? (int) Double.parseDouble(args[5]) : 0;
        Vector3f pos, dir;
        if (mode == 1) {
            // 自旋轴正上方垂直下潜：ρ≡0，L=0（预测：穿盘后被排斥核心弹回，二次穿盘翻回 +1）
            pos = new Vector3f(0f, 8f, 0f);
            dir = new Vector3f(0f, -1f, 0f);
        } else if (mode == 2) {
            // 平行于轴的下潜，ρ=aimOffZ（环内）：L≠0（预测：穿盘后被甩向 r<0 外侧，留在反宇宙）
            pos = new Vector3f(0f, 8f, (float) aimOffZ);
            dir = new Vector3f(0f, -1f, 0f);
        } else {
            pos = new Vector3f(0f, 2.69f, 17.80f);
            Vector3f aim = new Vector3f(0f, (float) yAim, (float) aimOffZ);
            dir = new Vector3f(aim).sub(pos).normalize();
        }
        g.initialize(pos, dir, v0);

        int frames = 20_000;           // 16ms/帧 → 最多 ~320s 游戏时间
        int thrustFrames = (int) (thrustSec * 62.5);
        double prevY = 2.69, prevRho = rho(g), minRhoAfterHorizon = 1e9;
        boolean crossedHorizon = false;
        int flips = 0, switches = 0;
        int lastFlipFrame = -10_000;

        for (int i = 0; i < frames; i++) {
            double signBefore = g.getUniverseSign();
            boolean patchBefore = g.isOutgoingPatch();
            Vector3f p = g.getPosition(new Vector3f());
            double rBefore = g.getRadius();

            if (i < thrustFrames) {
                // 朝心推力（模拟按住 W 对准中心飞）
                Vector3f toCenter = new Vector3f(-p.x, -p.y, -p.z).normalize();
                g.applyThrust(toCenter, 16);
            }
            g.step(16);

            double r = g.getRadius();
            double y = g.getPosition(new Vector3f()).y;
            double rho = rho(g);
            if (rBefore > horizon(aStar) && r <= horizon(aStar)) {
                crossedHorizon = true;
                System.out.printf("[%5d] 穿过外视界 r=%.3f%n", i, r);
            }
            if (crossedHorizon) {
                minRhoAfterHorizon = Math.min(minRhoAfterHorizon, rho);
            }
            if (g.getUniverseSign() != signBefore) {
                flips++;
                lastFlipFrame = i;
                System.out.printf("[%5d] 翻转 #%d → 符号 %.0f（r=%.3f ρ=%.3f y=%.3f）%n",
                        i, flips, g.getUniverseSign(), r, rho, y);
            }
            if (g.isOutgoingPatch() != patchBefore) {
                switches++;
            }
            // 回弹检测：y 未变号但 |y| 已小于 0.02 后又增大
            if (Math.abs(prevY) < 0.02 && Math.abs(y) > Math.abs(prevY) + 1e-4
                    && g.getUniverseSign() == 1.0 && flips == 0) {
                System.out.printf("[%5d] 疑似回弹：|y|=%.4f 折返（ρ=%.3f）%n", i, Math.abs(y), rho);
            }
            // 发散恢复检测：recoverFromBlowup 把位置精确重置到 (5,0,0)
            Vector3f pNow = g.getPosition(new Vector3f());
            if (Math.abs(pNow.x - 5.0) < 1e-9 && Math.abs(pNow.y) < 1e-9 && Math.abs(pNow.z) < 1e-9
                    && crossedHorizon) {
                System.out.printf("[%5d] 发散恢复踢出（重置 5Rs，符号复位 +1）%n", i);
                break;
            }
            if (i % 500 == 0 || (mode > 0 && r < 1.0 && i % 20 == 0)) {
                Vector3f u = g.getCoordinateVelocity(new Vector3f());
                Vector3f b = g.getBeta();
                System.out.printf("[%5d] r=%.4f ρ=%.3f y=%+.4f 符号=%.0f 补丁=%s |u|=%.3f β=(%.2f,%.2f,%.2f) γ=%.2f%n",
                        i, r, rho, y, g.getUniverseSign(), g.isOutgoingPatch() ? "out" : "in",
                        u.length(), b.x, b.y, b.z, g.getGamma());
            }
            // 已翻转后再飞 600 帧（~10s）即停；或已深入喉道（轴向下潜时放宽到 0.001 观察冻结）
            if ((flips > 0 && i - lastFlipFrame > 600) || r < (mode > 0 ? 0.001 : 0.02)) {
                break;
            }
            prevY = y;
            prevRho = rho;
        }
        Vector3f p = g.getPosition(new Vector3f());
        System.out.printf("== 结束: 位置(%.2f,%.2f,%.2f) r=%.3f ρ=%.3f 符号=%.0f 翻转=%d 换系=%d 过视界=%s 视界内minρ=%.3f |a|=%.3f ==%n",
                p.x, p.y, p.z, g.getRadius(), rho(g), g.getUniverseSign(), flips, switches,
                crossedHorizon, minRhoAfterHorizon, aStar * 0.5);
    }

    private static double rho(GeodesicIntegrator g) {
        Vector3f p = g.getPosition(new Vector3f());
        return Math.sqrt(p.x * p.x + p.z * p.z);
    }

    private static double horizon(double aStar) {
        double a = aStar * 0.5;
        return 0.5 + Math.sqrt(Math.max(0.0, 0.25 - a * a));
    }

    private static double arg(String[] args, int i, double dflt) {
        return args.length > i ? Double.parseDouble(args[i]) : dflt;
    }
}
