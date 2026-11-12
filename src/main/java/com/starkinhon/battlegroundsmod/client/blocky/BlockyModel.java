package com.starkinhon.battlegroundsmod.client.blocky;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starkinhon.battlegroundsmod.animal.db.DbCreature;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

// A box model read from assets/battlegroundsmod/blocky/<id>.json (written by tools/mobs/blocky/export.js from the
// approved preview): the same parts, pivots, rotations, boxes and texture offsets, so the game shows exactly the preview.
// Space as vanilla's: y down, feet at 24, the face towards -Z, rotations Z then Y then X.
public class BlockyModel<T extends DbCreature> extends HierarchicalModel<T> {
    public interface Anim<T extends DbCreature> {
        void apply(BlockyModel<T> m, T entity, float limbSwing, float amount, float age, float headYaw, float headPitch);
    }

    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Anim<T> anim;
    public final float height;

    public BlockyModel(JsonObject json, Anim<T> anim) {
        MeshDefinition mesh = new MeshDefinition();
        JsonObject def = json.getAsJsonObject("root");
        add(mesh.getRoot(), def);
        this.root = LayerDefinition.create(mesh, json.get("w").getAsInt(), json.get("h").getAsInt()).bakeRoot();
        collect(root, def);
        this.anim = anim;
        this.height = json.has("height") ? json.get("height").getAsFloat() : 2f;
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    private static void add(PartDefinition parent, JsonObject d) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement e : d.getAsJsonArray("boxes")) {
            JsonArray b = e.getAsJsonArray();
            cubes.texOffs(b.get(6).getAsInt(), b.get(7).getAsInt()).addBox(f(b, 0), f(b, 1), f(b, 2), f(b, 3), f(b, 4), f(b, 5));
        }
        JsonArray p = d.getAsJsonArray("pivot"), r = d.getAsJsonArray("rot");
        PartDefinition part = parent.addOrReplaceChild(d.get("name").getAsString(), cubes,
                PartPose.offsetAndRotation(f(p, 0), f(p, 1), f(p, 2), f(r, 0), f(r, 1), f(r, 2)));
        for (JsonElement k : d.getAsJsonArray("kids")) {
            add(part, k.getAsJsonObject());
        }
    }

    private void collect(ModelPart parent, JsonObject d) {
        ModelPart part = parent.getChild(d.get("name").getAsString());
        parts.put(d.get("name").getAsString(), part);
        for (JsonElement k : d.getAsJsonArray("kids")) {
            collect(part, k.getAsJsonObject());
        }
    }

    @Override
    public ModelPart root() {
        return root;
    }

    public boolean has(String name) {
        return parts.containsKey(name);
    }

    public ModelPart part(String name) {
        return parts.get(name);
    }

    // adds to the rest rotation (radians), like the preview's r(...)
    public void rot(String name, float x, float y, float z) {
        ModelPart p = parts.get(name);
        if (p != null) {
            p.xRot += x;
            p.yRot += y;
            p.zRot += z;
        }
    }

    public void move(String name, float x, float y, float z) {
        ModelPart p = parts.get(name);
        if (p != null) {
            p.x += x;
            p.y += y;
            p.z += z;
        }
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        // back to the rest pose every frame, then the animation adds on top
        root.getAllParts().forEach(ModelPart::resetPose);
        anim.apply(this, entity, limbSwing, Math.min(1f, amount), age, headYaw, headPitch);
    }
}
