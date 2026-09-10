package com.example.addon.hud;

import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class TargetHud extends HudElement {

    // 1. Enum quản lý Identifier (Namespace: template)
    public enum Icon {
        anh("anh.png"), so2("so2.png"), so3("so3.png"), so4("so4.png"), so5("so5.png");
        public final Identifier file;
        Icon(String fileName) { this.file = Identifier.of("template", "textures/" + fileName); }
    }

    public enum Nen {
        anh1("anh1.png"), nen2("nen2.png"), nen3("nen3.png"), nen4("nen4.png"), nen5("nen5.png");
        public final Identifier file;
        Nen(String fileName) { this.file = Identifier.of("template", "textures/" + fileName); }
    }

    public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(
        com.example.addon.AddonTemplate.HUD_GROUP,
        "target-hud-pez",
        "TargetHud - Không màu nền, ảnh sát nhau có vách ngăn.",
        TargetHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Settings chọn ảnh
    private final Setting<Icon> iconSet = sgGeneral.add(new EnumSetting.Builder<Icon>().name("Hình bên trái").defaultValue(Icon.anh).build());
    private final Setting<Nen> nenSet = sgGeneral.add(new EnumSetting.Builder<Nen>().name("Nền bên phải").defaultValue(Nen.anh1).build());

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder().name("Scale").defaultValue(1.0).min(0.5).sliderMax(3.0).build());

    public TargetHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        PlayerEntity target = getClosestPlayer(client);
        if (target == null) return;

        double s = scale.get();
        // Thiết lập kích thước tổng thể
        setSize(230 * s, 60 * s);

        // --- TÍNH TOÁN VỊ TRÍ SÁT NHAU ---
        double padding = 2 * s; // Khoảng cách nhỏ từ lề ngoài
        double imgSize = 56 * s; // Kích thước ảnh icon

        // 1. Vẽ ảnh ICON bên trái
        renderer.texture(iconSet.get().file, x + padding, y + padding, imgSize, imgSize, Color.WHITE);

        // 2. Vẽ ảnh NỀN bên phải (Bắt đầu ngay sau ảnh icon)
        double rX = x + padding + imgSize;
        double rW = getWidth() - imgSize - (padding * 2);
        double rH = imgSize; // Cao bằng ảnh bên trái để khớp nhau

        renderer.texture(nenSet.get().file, rX, y + padding, rW, rH, Color.WHITE);

        // 3. VẼ ĐƯỜNG KẺ GIỮA (Vách ngăn đen bóng)
        // Đường kẻ này nằm ngay điểm tiếp xúc của 2 ảnh
        renderer.quad(rX - (0.5 * s), y + padding, 1 * s, imgSize, new Color(0, 0, 0, 200));

        // --- VẼ CHỮ VÀ MÁU ĐÈ LÊN ẢNH NỀN ---
        float health = target.getHealth();
        float hpPercent = MathHelper.clamp(health / target.getMaxHealth(), 0, 1);

        // Tên mục tiêu (Dịch vào một chút từ vách ngăn)
        renderer.text(target.getName().getString(), rX + (8 * s), y + (8 * s), Color.WHITE, true, s * 1.1);

        // Thanh máu
        double barX = rX + (8 * s);
        double barY = y + (34 * s);
        double barWidthMax = rW - (16 * s);
        double barHeight = 14 * s;

        // Đế thanh máu đen đặc
        renderer.quad(barX, barY, barWidthMax, barHeight, new Color(10, 10, 10, 255));
        // Phần máu đỏ
        renderer.quad(barX, barY, barWidthMax * hpPercent, barHeight, new Color(220, 20, 20, 255));

        // Số máu
        String hpValue = String.format("%.1f", health);
        double hpW = renderer.textWidth(hpValue, true, s * 0.85);
        renderer.text(hpValue, barX + barWidthMax - hpW - (3 * s), barY + (2.5 * s), Color.WHITE, true, s * 0.85);
    }

    private PlayerEntity getClosestPlayer(MinecraftClient client) {
        PlayerEntity closest = null;
        double dist = 6.0;
        try {
            for (PlayerEntity player : client.world.getPlayers()) {
                if (player == client.player || !player.isAlive() || player.isRemoved()) continue;
                double d = client.player.distanceTo(player);
                if (d < dist) { closest = player; dist = d; }
            }
        } catch (Exception ignored) {}
        return closest;
    }
}
