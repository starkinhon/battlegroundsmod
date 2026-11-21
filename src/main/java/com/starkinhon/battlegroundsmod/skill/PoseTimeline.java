// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.skill;

import java.util.function.DoubleFunction;

// A body animation: the pose at a time in ticks since it started, and when the body is back to normal.
// AnimationSpec is the usual wind-up + main pose; moves with more steps build their own (see AgarraoSpec).
public interface PoseTimeline {
    SkillSpec.Pose poseAt(double t);

    double animationEnd();

    // When the body is in its strongest pose (held there in a clash de poderes, client/fx/ClashFx). The last pose before
    // it eases back to normal; a few ticks before the end unless the animation knows better.
    default double peak() {
        return Math.max(0, animationEnd() - 8);
    }

    static PoseTimeline of(double end, DoubleFunction<SkillSpec.Pose> pose) {
        return new PoseTimeline() {
            @Override
            public SkillSpec.Pose poseAt(double t) {
                return pose.apply(t);
            }

            @Override
            public double animationEnd() {
                return end;
            }
        };
    }
}
