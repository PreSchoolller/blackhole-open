import org.joml.Vector3f;
import io.github.preschoollerr.blackhole.eng.scene.GeodesicIntegrator;

/**
 * 测地线积分器无头回归测试（无 GUI，可直接 main 运行）。
 *
 * 场景 A：r=6Rs 圆轨道绕行 5 圈——验收半径漂移≈0（积分精度）；
 * 场景 B：瞄向黑洞中心 0.5c 俯冲——应平滑穿过视界并冻结于奇点保护半径。
 *
 * 运行方式：
 *   mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *   java -cp "target/test-classes;target/classes;$(cat target/cp.txt)" GeoTest
 */
public class GeoTest {
    public static void main(String[] args) {
        var g = new GeodesicIntegrator();

        // 场景 A：圆轨道稳定性
        g.initialize(new Vector3f(6, 0, 0), new Vector3f(0, 0, -1), 0.3);
        System.out.println("init: " + g.initializeCircularOrbit());
        double r0 = g.getRadius();
        long dt = 17;                                        // 模拟 ~144FPS 帧间隔
        int frames = (int) (131_000 / dt);                   // 5 圈 ≈ 131s（timeScale=5）
        double maxDrift = 0;
        for (int i = 0; i < frames; i++) {
            g.step(dt);
            double r = g.getRadius();
            if (!Double.isFinite(r)) {
                System.out.println("A: NaN/Inf at frame " + i);
                System.exit(1);
            }
            maxDrift = Math.max(maxDrift, Math.abs(r - r0));
        }
        System.out.printf("A 圆轨道 5 圈: r0=%.4f r_end=%.4f maxDrift=%.5f 能量=%.5f%n",
                r0, g.getRadius(), maxDrift, g.getEnergy());

        // 场景 B：视界穿越
        g.deactivate();
        g.initialize(new Vector3f(10, 0, 0), new Vector3f(-1, 0, 0), 0.5);
        for (int i = 0; i < 5000; i++) {
            g.step(dt);
            double r = g.getRadius();
            if (!Double.isFinite(r)) {
                System.out.println("B: NaN at frame " + i);
                System.exit(1);
            }
        }
        System.out.printf("B 俯冲: 最终 r=%.4f (应 <0.1 奇点保护)%n", g.getRadius());

        // 场景 C（Phase 3）：a*=0.9 赤道圆轨道稳定性（顺行,Ω 含自旋修正;倾角 0=赤道面,
        // Kerr 只绕自旋轴轴对称,极地轨道无圆测地线,必须用赤道面检验）
        g.deactivate();
        g.setSpin(0.9);
        g.setOrbitTilt(0.0);
        g.initialize(new Vector3f(6, 0, 0), new Vector3f(0, 0, -1), 0.3);
        System.out.println("C init: " + g.initializeCircularOrbit());
        double rC0 = g.getRadius();
        double eC0 = g.getEnergy();
        maxDrift = 0;
        for (int i = 0; i < frames; i++) {
            g.step(dt);
            double r = g.getRadius();
            if (!Double.isFinite(r)) {
                System.out.println("C: NaN/Inf at frame " + i);
                System.exit(1);
            }
            maxDrift = Math.max(maxDrift, Math.abs(r - rC0));
        }
        System.out.printf("C a*=0.9 圆轨道 5 圈: r0=%.4f r_end=%.4f maxDrift=%.5f 能量=%.5f(漂移 %.2e)%n",
                rC0, g.getRadius(), maxDrift, g.getEnergy(), Math.abs(g.getEnergy() - eC0));

        // 场景 D（Phase 3）：a*=0.9 赤道俯冲穿视界（外视界 r+ = 0.5+sqrt(0.25-0.2025) ≈ 0.845）
        g.deactivate();
        g.initialize(new Vector3f(10, 0, 0), new Vector3f(-1, 0, 0), 0.5);
        for (int i = 0; i < 5000; i++) {
            g.step(dt);
            double r = g.getRadius();
            if (!Double.isFinite(r)) {
                System.out.println("D: NaN at frame " + i);
                System.exit(1);
            }
        }
        System.out.printf("D a*=0.9 俯冲: 最终 r=%.4f (应 <0.1 奇点保护)%n", g.getRadius());

        // 场景 E（Phase 5）：a*=0.9 赤道圆轨道上的四维标架平行输运——
        // 验收:正交性误差 g(e_a,e_b)-δ_ab、g(e_a,U) 全程保持在积分精度量级
        g.deactivate();
        g.setSpin(0.9);
        g.setOrbitTilt(0.0);
        g.initialize(new Vector3f(6, 0, 0), new Vector3f(0, 0, -1), 0.3);
        g.initializeCircularOrbit();
        g.initializeTetrad(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));
        double maxOrthoErr = g.tetradOrthoError();
        for (int i = 0; i < frames; i++) {
            g.step(dt);
            if (!Double.isFinite(g.tetradOrthoError())) {
                System.out.println("E: NaN at frame " + i);
                System.exit(1);
            }
            maxOrthoErr = Math.max(maxOrthoErr, g.tetradOrthoError());
        }
        System.out.printf("E a*=0.9 标架输运 5 圈: 正交性最大误差=%.3e (应 ~1e-9 水平)%n", maxOrthoErr);
    }
}
