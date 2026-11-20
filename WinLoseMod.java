package ru.winlose;

import com.mojang.brigadier.arguments.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.Arrays;

public class WinLoseMod implements ClientModInitializer {
    static PlayerEntity target;
    static long lastActivity;
    static long previewUntil;

    static final String[] ANCHORS = {"TOP_LEFT","TOP_CENTER","TOP_RIGHT","MIDDLE_LEFT","CENTER","MIDDLE_RIGHT","BOTTOM_LEFT","BOTTOM_CENTER","BOTTOM_RIGHT"};

    @Override
    public void onInitializeClient() {
        // цель = игрок, которого мы ударили
        AttackEntityCallback.EVENT.register((p, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof PlayerEntity other && other != p) setTarget(other);
            return ActionResult.PASS;
        });

        // цель = ближайший игрок, когда нас ударили
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) { target = null; return; }
            Config c = Config.INSTANCE;
            if (mc.player.hurtTime > 0) {
                PlayerEntity nearest = null; double best = 36;
                for (PlayerEntity o : mc.world.getPlayers()) {
                    if (o == mc.player) continue;
                    double d = o.squaredDistanceTo(mc.player);
                    if (d < best) { best = d; nearest = o; }
                }
                if (nearest != null) setTarget(nearest);
            }
            if (target != null && (target.isRemoved() || !target.isAlive()
                    || target.distanceTo(mc.player) > c.maxDistance
                    || System.currentTimeMillis() - lastActivity > c.timeoutSeconds * 1000L)) target = null;
        });

        HudRenderCallback.EVENT.register(WinLoseMod::render);
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> registerCommands(d));
    }

    static void setTarget(PlayerEntity p) { target = p; lastActivity = System.currentTimeMillis(); }

    static void render(DrawContext ctx, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config c = Config.INSTANCE;
        if (!c.enabled || mc.player == null || mc.options.hudHidden) return;

        boolean preview = System.currentTimeMillis() < previewUntil;
        float mine = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float theirs; String name;
        if (preview) { theirs = 12f; name = "Enemy"; }
        else if (target != null) { theirs = target.getHealth() + target.getAbsorptionAmount(); name = target.getName().getString(); }
        else return;

        String status; int color;
        if (mine > theirs) { status = "WIN"; color = 0x55FF55; }
        else if (mine < theirs) { status = "LOSE"; color = 0xFF5555; }
        else { status = "DRAW"; color = 0xFFFF55; }

        String line2 = String.format("You %.1f  |  %s%.1f", mine, c.showNames ? name + " " : "", theirs);
        TextRenderer tr = mc.textRenderer;
        int w1 = tr.getWidth(status) * 2, w2 = tr.getWidth(line2);
        int w = Math.max(w1, w2), h = 18 + 12;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float bw = w * c.scale, bh = h * c.scale;
        String a = c.anchor.toUpperCase();
        float x = a.endsWith("LEFT") ? 0 : a.endsWith("RIGHT") ? sw - bw : (sw - bw) / 2f;
        float y = a.startsWith("TOP") ? 0 : a.startsWith("BOTTOM") ? sh - bh : (sh - bh) / 2f;
        x += c.offsetX; y += c.offsetY;

        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(c.scale, c.scale, 1f);
        m.push();
        m.translate((w - w1) / 2f, 0, 0);
        m.scale(2f, 2f, 1f);
        ctx.drawTextWithShadow(tr, status, 0, 0, color);
        m.pop();
        ctx.drawTextWithShadow(tr, line2, (w - w2) / 2, 20, 0xFFFFFF);
        m.pop();
    }

    static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> d) {
        var root = ClientCommandManager.literal("winlose");
        root.then(ClientCommandManager.literal("toggle").executes(x -> {
            Config c = Config.INSTANCE; c.enabled = !c.enabled; c.save();
            x.getSource().sendFeedback(Text.literal("WinLose: " + (c.enabled ? "вкл" : "выкл"))); return 1; }));
        root.then(ClientCommandManager.literal("test").executes(x -> {
            previewUntil = System.currentTimeMillis() + 15000;
            x.getSource().sendFeedback(Text.literal("Предпросмотр на 15 сек")); return 1; }));
        root.then(ClientCommandManager.literal("anchor").then(ClientCommandManager.argument("name", StringArgumentType.word())
            .suggests((ctx, b) -> { Arrays.stream(ANCHORS).forEach(b::suggest); return b.buildFuture(); })
            .executes(x -> {
                String n = StringArgumentType.getString(x, "name").toUpperCase();
                if (!Arrays.asList(ANCHORS).contains(n)) { x.getSource().sendFeedback(Text.literal("Варианты: " + String.join(", ", ANCHORS))); return 0; }
                Config.INSTANCE.anchor = n; Config.INSTANCE.save(); previewUntil = System.currentTimeMillis() + 8000; return 1; })));
        root.then(ClientCommandManager.literal("offset")
            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer(-4000, 4000))
            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer(-4000, 4000)).executes(x -> {
                Config.INSTANCE.offsetX = IntegerArgumentType.getInteger(x, "x");
                Config.INSTANCE.offsetY = IntegerArgumentType.getInteger(x, "y");
                Config.INSTANCE.save(); previewUntil = System.currentTimeMillis() + 8000; return 1; }))));
        root.then(ClientCommandManager.literal("scale")
            .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.5f, 5f)).executes(x -> {
                Config.INSTANCE.scale = FloatArgumentType.getFloat(x, "value");
                Config.INSTANCE.save(); previewUntil = System.currentTimeMillis() + 8000; return 1; })));
        root.then(ClientCommandManager.literal("names").executes(x -> {
            Config c = Config.INSTANCE; c.showNames = !c.showNames; c.save(); return 1; }));
        d.register(root);
    }
}
