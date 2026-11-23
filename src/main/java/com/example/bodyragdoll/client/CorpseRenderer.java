package com.example.bodyragdoll.client;

import com.example.bodyragdoll.entity.CorpseEntity;
import com.example.bodyragdoll.physics.RagdollSim;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class CorpseRenderer extends EntityRenderer<CorpseEntity> {

    private static final class Cache {
        LivingEntity dummy;
        double scale = 1.0;
    }

    public CorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");
    }

    @Override
    public boolean shouldRender(CorpseEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return entity.sim != null && super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(CorpseEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int packedLight) {
        RagdollSim sim = e.sim;
        if (sim == null) return;
        Cache c = (Cache) e.renderCache;
        if (c == null) {
            c = createCache(e);
            e.renderCache = c;
        }
        if (c.dummy == null) return;

        double ox = e.getX(), oy = e.getY(), oz = e.getZ();
        Vec3 head = rel(sim, RagdollSim.HEAD, pt);
        Vec3 neck = rel(sim, RagdollSim.NECK, pt);
        Vec3 hip = rel(sim, RagdollSim.HIP, pt);
        Vec3 sl = rel(sim, RagdollSim.SL, pt);
        Vec3 sr = rel(sim, RagdollSim.SR, pt);
        Vec3 hl = rel(sim, RagdollSim.HL, pt);
        Vec3 hr = rel(sim, RagdollSim.HR, pt);
        Vec3 fl = rel(sim, RagdollSim.FL, pt);
        Vec3 fr = rel(sim, RagdollSim.FR, pt);

        int light = LevelRenderer.getLightColor(e.level(), BlockPos.containing(ox + hip.x, oy + hip.y + 0.3, oz + hip.z));

        EntityRenderDispatcher disp = Minecraft.getInstance().getEntityRenderDispatcher();
        Object renderer = disp.getRenderer(c.dummy);

        if (renderer instanceof LivingEntityRenderer<?, ?> ler && ler.getModel() instanceof HumanoidModel<?> hm) {
            ResourceLocation tex = ((LivingEntityRenderer) ler).getTextureLocation(c.dummy);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex));
            double s = c.scale;

            Vec3 leftHint = sl.subtract(sr);
            Vec3 leftN = leftHint.normalize();

            ModelPart jacket = null, sleeveL = null, sleeveR = null, pantsL = null, pantsR = null;
            if (hm instanceof PlayerModel<?> pm) {
                jacket = pm.jacket;
                sleeveL = pm.leftSleeve;
                sleeveR = pm.rightSleeve;
                pantsL = pm.leftPants;
                pantsR = pm.rightPants;
            }

            part(ps, vc, light, s, basis(neck, hip.subtract(neck), leftHint), hm.body, jacket);
            part(ps, vc, light, s, basis(neck, neck.subtract(head), leftHint), hm.head, hm.hat);
            part(ps, vc, light, s, basis(sl, hl.subtract(sl), leftHint), hm.leftArm, sleeveL);
            part(ps, vc, light, s, basis(sr, hr.subtract(sr), leftHint), hm.rightArm, sleeveR);
            Vec3 pl = hip.add(leftN.scale(0.125 * s));
            Vec3 pr = hip.subtract(leftN.scale(0.125 * s));
            part(ps, vc, light, s, basis(pl, fl.subtract(pl), leftHint), hm.leftLeg, pantsL);
            part(ps, vc, light, s, basis(pr, fr.subtract(pr), leftHint), hm.rightLeg, pantsR);
        } else {
            // не гуманоид: рисуем обычным рендерером в позе "лежит на боку", но с проскальзыванием от удара
            LivingEntity d = c.dummy;
            d.moveTo(ox, oy, oz, e.getYRot(), 0f);
            d.yBodyRot = d.yBodyRotO = d.yHeadRot = d.yHeadRotO = e.getYRot();
            d.deathTime = 20;
            d.hurtTime = 0;
            ps.pushPose();
            ps.translate(hip.x, 0, hip.z);
            disp.render(d, 0, 0, 0, e.getYRot(), pt, ps, buf, light);
            ps.popPose();
        }
    }

    private static Vec3 rel(RagdollSim sim, int i, float pt) {
        return new Vec3(sim.pos(i, 0, pt), sim.pos(i, 1, pt), sim.pos(i, 2, pt));
    }

    /** Базис модели: ey = "вниз" вдоль части, ex = "влево" сущности, ez = ex x ey. */
    private static Matrix4f basis(Vec3 origin, Vec3 down, Vec3 leftHint) {
        Vec3 ey = down.lengthSqr() < 1.0E-8 ? new Vec3(0, -1, 0) : down.normalize();
        Vec3 ex = leftHint.subtract(ey.scale(leftHint.dot(ey)));
        if (ex.lengthSqr() < 1.0E-8) {
            ex = Math.abs(ey.x) < 0.9 ? new Vec3(1, 0, 0).cross(ey) : new Vec3(0, 0, 1).cross(ey);
        }
        ex = ex.normalize();
        Vec3 ez = ex.cross(ey);
        return new Matrix4f(
                (float) ex.x, (float) ex.y, (float) ex.z, 0f,
                (float) ey.x, (float) ey.y, (float) ey.z, 0f,
                (float) ez.x, (float) ez.y, (float) ez.z, 0f,
                (float) origin.x, (float) origin.y, (float) origin.z, 1f);
    }

    private static void part(PoseStack ps, VertexConsumer vc, int light, double s, Matrix4f m, ModelPart... parts) {
        ps.pushPose();
        ps.mulPose(m);
        ps.scale((float) s, (float) s, (float) s);
        for (ModelPart mp : parts) {
            if (mp == null) continue;
            mp.visible = true;
            mp.setPos(0, 0, 0);
            mp.setRotation(0, 0, 0);
            mp.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
            mp.resetPose();
        }
        ps.popPose();
    }

    private Cache createCache(CorpseEntity e) {
        Cache c = new Cache();
        try {
            ClientLevel level = (ClientLevel) e.level();
            String id = e.getTypeId();
            if ("minecraft:player".equals(id)) {
                UUID uuid = e.getPlayerUuid().orElse(UUID.nameUUIDFromBytes(e.getPlayerName().getBytes()));
                c.dummy = new RemotePlayer(level, new GameProfile(uuid, e.getPlayerName()));
                c.scale = 0.9375;
            } else {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElse(null);
                if (type != null) {
                    Entity ent = type.create(level);
                    if (ent instanceof LivingEntity le) c.dummy = le;
                }
            }
        } catch (Throwable t) {
            c.dummy = null;
        }
        return c;
    }
}
