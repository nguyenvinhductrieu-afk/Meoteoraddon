package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ParticleHud extends HudElement {

    /* ================= HUD INFO ================= */
    public static final HudElementInfo<ParticleHud> INFO =
        new HudElementInfo<>(
            AddonTemplate.HUD_GROUP,
            "particle-hud",
            "Simple particle effect",
            ParticleHud::new
        );

    /* ================= PARTICLE ================= */
    private static class Particle {
        double x, y;
        double dx, dy;
        double size;
        int alpha = 255;
        Identifier texture;

        Particle(double x, double y, double dx, double dy, double size, Identifier texture) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.size = size;
            this.texture = texture;
        }

        void update() {
            x += dx;
            y += dy;
            alpha -= 3;
        }

        boolean dead(int screenHeight) {
            return alpha <= 0 || y > screenHeight + 20;
        }
    }

    /* ================= TEXTURES ================= */
    private static final Identifier[] TEXTURES = {
        Identifier.of("template", "textures/particles/circle.png"),
        Identifier.of("template", "textures/particles/dollar.png"),
        Identifier.of("template", "textures/particles/firefly.png"),
        Identifier.of("template", "textures/particles/snowflake.png"),
        Identifier.of("template", "textures/particles/star.png")
    };

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public ParticleHud() {
        super(INFO);
    }

    /* ================= RENDER ================= */
    @Override
    public void render(HudRenderer renderer) {
        int screenW = MeteorClient.mc.getWindow().getScaledWidth();
        int screenH = MeteorClient.mc.getWindow().getScaledHeight();

        spawn(screenW);

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();

            renderer.texture(
                p.texture,
                p.x,
                p.y,
                p.size,
                p.size,
                Color.WHITE.a(p.alpha)
            );

            if (p.dead(screenH)) it.remove();
        }
    }

    /* ================= SPAWN ================= */
    private void spawn(int screenW) {
        if (particles.size() >= 150) return;

        particles.add(new Particle(
            random.nextInt(screenW),
            -10,
            random.nextDouble() * 0.6 - 0.3,
            random.nextDouble() * 1.2 + 0.4,
            8,
            TEXTURES[random.nextInt(TEXTURES.length)]
        ));
    }
}
