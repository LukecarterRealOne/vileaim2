package com.vlineaim;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible vertical line = a vertical plane through the crosshair (shiftable by LINE_OFFSET).
 * Only entities within STRIP_HALF_WIDTH degrees of that line are targeted.
 * Only yaw (X axis) is changed; pitch is never touched.
 */
public class VLineAimClient implements ClientModInitializer {
    // ---- tweak these ----
    static float LINE_OFFSET = 0f;        // degrees left(-)/right(+) of crosshair
    static float STRIP_HALF_WIDTH = 15f;  // width the line "catches" targets (degrees)
    static float SMOOTHING = 0.35f;       // fraction of remaining angle closed per tick
    static float MAX_STEP = 20f;          // max degrees per tick
    static double RANGE = 6.0;            // blocks
    static boolean PLAYERS_ONLY = false;
    // ---------------------

    private static boolean enabled = false;
    private static KeyMapping toggleKey, leftKey, rightKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vlineaim", "main"));
        toggleKey = reg("key.vlineaim.toggle", InputConstants.KEY_R, cat);
        leftKey = reg("key.vlineaim.left", InputConstants.KEY_LEFT, cat);
        rightKey = reg("key.vlineaim.right", InputConstants.KEY_RIGHT, cat);
        ClientTickEvents.END_CLIENT_TICK.register(VLineAimClient::tick);
    }

    private static KeyMapping reg(String name, int key, KeyMapping.Category cat) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, key, cat));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        Player me = mc.player;

        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            me.displayClientMessage(Component.literal("VLine Aim: " + (enabled ? "ON" : "OFF")), true);
        }
        while (leftKey.consumeClick()) LINE_OFFSET = Mth.clamp(LINE_OFFSET - 5f, -90f, 90f);
        while (rightKey.consumeClick()) LINE_OFFSET = Mth.clamp(LINE_OFFSET + 5f, -90f, 90f);

        if (!enabled || mc.screen != null) return;

        Vec3 eye = me.getEyePosition();
        float lineYaw = me.getYRot() + LINE_OFFSET;

        float bestDelta = 0f, bestAbs = Float.MAX_VALUE;
        boolean found = false;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == me || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            if (PLAYERS_ONLY && !(e instanceof Player)) continue;
            if (me.distanceToSqr(e) > RANGE * RANGE || !me.hasLineOfSight(e)) continue;

            Vec3 c = e.getBoundingBox().getCenter();
            double dx = c.x - eye.x, dz = c.z - eye.z;
            float yawTo = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
            float delta = Mth.wrapDegrees(yawTo - lineYaw);

            float abs = Math.abs(delta);
            if (abs <= STRIP_HALF_WIDTH && abs < bestAbs) { bestAbs = abs; bestDelta = delta; found = true; }
        }

        if (!found) return;
        // Yaw only (X axis). Pitch untouched.
        me.setYRot(me.getYRot() + Mth.clamp(bestDelta * SMOOTHING, -MAX_STEP, MAX_STEP));
    }
}
