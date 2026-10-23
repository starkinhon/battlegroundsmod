package com.starkinhon.battlegroundsmod.skill;

import java.util.function.DoubleFunction;

// A body animation: the pose at a time in ticks since it started, and when the body is back to normal.
// AnimationSpec is the usual wind-up + main pose; moves with more steps build their own (see AgarraoSpec).
public interface PoseTimeline {
    SkillSpec.Pose poseAt(double t);

    double animationEnd();

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
