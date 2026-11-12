package com.starkinhon.battlegroundsmod.client.blocky;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starkinhon.battlegroundsmod.BattlegroundsMod;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoMob;
import com.starkinhon.battlegroundsmod.animal.naruto.NarutoSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// The Naruto creatures as the approved preview shows them: the box model and painting of each variant (normal, and
// low poly from 24 blocks off), moved by the preview's own animation baked frame by frame (BakedAnim, written by
// tools/mobs/naruto/export.js). Standing and walking loop and blend by how fast it walks; a blow plays its baked frames
// from the tick the server started it, blending in and out over a few frames. The body's offset and turn of the
// preview ('@') are applied around the same pivot the preview turns it on (1.5 blocks up), except what the server
// already does for real: the path a blow walks (NarutoTravel) and where a hidden body reappears (teleport frames).
public class NarutoMobRenderer extends MobRenderer<NarutoMob, BlockyModel<NarutoMob>> {
    private static final float LOD = 24;
    private static final float BLEND_IN = 2;
    private static final float BLEND_OUT = 4;

    private final NarutoSpec.Spec spec;
    private final List<BlockyModel<NarutoMob>> hi = new ArrayList<>();
    private final List<BlockyModel<NarutoMob>> lo = new ArrayList<>();
    private final List<ResourceLocation> texHi = new ArrayList<>();
    private final List<ResourceLocation> texLo = new ArrayList<>();
    private final String folder;
    private final java.util.function.BiFunction<String, String, float[][]> travel;
    // each variant's size (Cell Jr. 0.68, the dragon 1.4, the Saibamen's drawn size), applied about the feet
    private final List<Float> scaleHi = new ArrayList<>();
    private final List<Float> scaleLo = new ArrayList<>();
    private float scale = 1;
    private final BakedAnim animHi;
    private final BakedAnim animLo;
    private final float flyLift;

    // what this frame plays, worked out once in render() for the model and the body's turn
    private final float[] root = new float[BakedAnim.ROOT];
    private BakedAnim.Mode attack;
    private float attackFrame;
    private float attackW;
    private float walkW;
    private boolean low;
    private BakedAnim.Mode loopAnim;

    public NarutoMobRenderer(EntityRendererProvider.Context context, String id) {
        this(context, id, NarutoSpec.get(id), "naruto", com.starkinhon.battlegroundsmod.animal.naruto.NarutoTravel::of);
    }

    // folder: the models' and skins' folder (blocky/<folder>, textures/entity/<folder>); travel: the kind's blow paths
    public NarutoMobRenderer(EntityRendererProvider.Context context, String id, NarutoSpec.Spec spec, String folder,
            java.util.function.BiFunction<String, String, float[][]> travel) {
        super(context, null, spec.shadow);
        this.spec = spec;
        this.folder = folder;
        this.travel = travel;
        this.animHi = anim(context, id);
        this.animLo = anim(context, id + "_low");
        for (int v = 0; ; v++) {
            String name = id + "_" + v;
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "blocky/" + folder + "/" + name + ".json");
            if (context.getResourceManager().getResource(loc).isEmpty()) {
                break;
            }
            JsonObject jHi = json(context, "blocky/" + folder + "/" + name + ".json");
            JsonObject jLo = json(context, "blocky/" + folder + "/" + name + "_low.json");
            hi.add(new BlockyModel<>(jHi, (m, e, ls, amt, age, hy, hp) -> pose(m, animHi, age)));
            lo.add(new BlockyModel<>(jLo, (m, e, ls, amt, age, hy, hp) -> pose(m, animLo, age)));
            scaleHi.add(jHi.has("scale") ? jHi.get("scale").getAsFloat() : 1f);
            scaleLo.add(jLo.has("scale") ? jLo.get("scale").getAsFloat() : 1f);
            texHi.add(tex(name));
            texLo.add(tex(name + "_low"));
        }
        if (hi.isEmpty()) {
            throw new IllegalStateException("Souls: nenhum modelo de " + id);
        }
        this.model = hi.get(0);
        this.flyLift = spec.move == NarutoSpec.Move.FLYER ? animHi.minY("parado") : 0;
    }

    private ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, "textures/entity/" + folder + "/" + name + ".png");
    }

    private static JsonObject json(EntityRendererProvider.Context context, String path) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(BattlegroundsMod.MODID, path);
        try (Reader reader = context.getResourceManager().getResourceOrThrow(loc).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Souls: " + loc + " não abriu", e);
        }
    }

    private BakedAnim anim(EntityRendererProvider.Context context, String name) {
        return new BakedAnim(json(context, "blocky/" + folder + "/" + name + ".anim.json"));
    }

    private int index(NarutoMob e) {
        return Math.floorMod(e.variant(), hi.size());
    }

    // ---------- what plays now ----------

    private void work(NarutoMob e, float partial) {
        BakedAnim a = low ? animLo : animHi;
        float age = e.tickCount + partial;
        // walking: by how fast it goes (the flyers and the shark have no legs' swing to go by)
        float speed;
        if (spec.move == NarutoSpec.Move.GROUND) {
            speed = e.walkAnimation.speed(partial) * 1.6f;
        } else {
            double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
            speed = (float) Math.sqrt(dx * dx + dz * dz) * 6f;
        }
        walkW = Mth.clamp(speed, 0, 1);
        attack = null;
        attackW = 0;
        NarutoSpec.Attack at = spec.attack(e.attackKind);
        if (at != null && e.attackStart > -1000) {
            BakedAnim.Mode m = a.mode(at.mode());
            if (m != null) {
                float p = age - e.attackStart;
                int n = m.frames;
                if (p >= 0 && p < n + BLEND_OUT) {
                    attack = m;
                    attackFrame = Math.min(p, n - 1);
                    attackW = p < BLEND_IN ? p / BLEND_IN : p > n ? 1 - (p - n) / BLEND_OUT : 1;
                    attackW = attackW * attackW * (3 - 2 * attackW);
                }
            }
        }
        java.util.Arrays.fill(root, 0);
        float rest = 1 - attackW;
        float[] tmp = new float[BakedAnim.ROOT];
        String loop = e.loopMode();
        loopAnim = loop == null ? null : a.mode(loop);
        if (loopAnim != null) {
            BakedAnim.root(loopAnim, age, true, rest, tmp);
        } else {
            BakedAnim.root(a.mode("parado"), age, true, rest * (1 - walkW), tmp);
            BakedAnim.root(a.mode("andar"), age, true, rest * walkW, tmp);
        }
        // standing and walking: only the body's bob and lean; the path and turn are the entity's own
        tmp[0] = 0;
        tmp[2] = 0;
        tmp[4] = 0;
        tmp[6] = 0;
        add(root, tmp);
        if (attack != null) {
            java.util.Arrays.fill(tmp, 0);
            attackRoot(a, at.mode(), attack, attackFrame, tmp);
            for (int k = 0; k < BakedAnim.ROOT; k++) {
                root[k] += tmp[k] * (k == 6 ? 1 : attackW);
            }
        }
        root[1] -= flyLift;
    }

    private static void add(float[] into, float[] v) {
        for (int k = 0; k < into.length; k++) {
            into[k] += v[k];
        }
    }

    // The blow's body offset, less what the server does for real (see the class comment).
    private void attackRoot(BakedAnim a, String mode, BakedAnim.Mode m, float f, float[] out) {
        float[] now = new float[BakedAnim.ROOT];
        float[] first = new float[BakedAnim.ROOT];
        float[] last = new float[BakedAnim.ROOT];
        BakedAnim.root(m, f, false, 1, now);
        BakedAnim.root(m, 0, false, 1, first);
        BakedAnim.root(m, m.frames - 1, false, 1, last);
        int tp = spec.teleportFrame(mode);
        boolean travels = travel.apply(spec.id, mode) != null;
        boolean before = tp < 0 || f < tp;
        // x, z: walked by the server, or (before a teleport) the preview's own small moves from the start
        out[0] = travels || !before ? 0 : now[0] - first[0];
        out[2] = travels || !before ? 0 : now[2] - first[2];
        // the turn: after a teleport the entity faces the right way itself; a blow ending turned around (the
        // Gatsuga) leaves the turning to the creature
        boolean turnsAround = tp < 0 && Math.abs(last[4] - first[4]) > 1;
        out[4] = !before || turnsAround ? 0 : now[4] - first[4];
        out[1] = now[1];
        out[3] = now[3];
        out[5] = now[5];
        out[6] = now[6];
    }

    // the model's pose this frame (the parts; the body's offset is in setupRotations)
    private void pose(BlockyModel<NarutoMob> m, BakedAnim a, float age) {
        float rest = 1 - attackW;
        if (loopAnim != null) {
            BakedAnim.apply(m, a.mode(loopAnim.name), age, true, rest);
        } else {
            BakedAnim.apply(m, a.mode("parado"), age, true, rest * (1 - walkW));
            BakedAnim.apply(m, a.mode("andar"), age, true, rest * walkW);
        }
        if (attack != null) {
            BakedAnim.Mode mode = a.mode(attack.name);
            BakedAnim.apply(m, mode, attackFrame, false, attackW);
        }
    }

    @Override
    public void render(NarutoMob e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        low = e.position().distanceToSqr(cam) > LOD * LOD;
        int i = index(e);
        this.model = low ? lo.get(Math.min(i, lo.size() - 1)) : hi.get(i);
        scale = low ? scaleLo.get(Math.min(i, scaleLo.size() - 1)) : scaleHi.get(i);
        work(e, partial);
        if (root[6] > 0.5f) {
            // hidden: the log, the mist, the flock
            return;
        }
        super.render(e, yaw, partial, pose, buffers, light);
    }

    @Override
    protected void setupRotations(NarutoMob e, PoseStack pose, float bob, float bodyYaw, float partial, float scale) {
        super.setupRotations(e, pose, bob, bodyYaw, partial, scale);
        // the preview's body offset and turn, in its frame (y up, face to -z), about its pivot 1.5 blocks up
        pose.translate(root[0], root[1], root[2]);
        pose.translate(0, 1.5f * scale, 0);
        pose.mulPose(Axis.XP.rotation(root[3]));
        pose.mulPose(Axis.YP.rotation(root[4]));
        pose.mulPose(Axis.ZP.rotation(root[5]));
        pose.translate(0, -1.5f * scale, 0);
        if (scale != 1) {
            // about the feet: the model's origin stands 1.5 * scale up
            pose.scale(scale, scale, scale);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(NarutoMob e) {
        int i = index(e);
        return low ? texLo.get(Math.min(i, texLo.size() - 1)) : texHi.get(i);
    }
}
