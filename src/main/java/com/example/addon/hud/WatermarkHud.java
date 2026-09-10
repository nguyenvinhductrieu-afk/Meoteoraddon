package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;

public class WatermarkHud extends HudElement {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public static final HudElementInfo<WatermarkHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "catdz-watermark-glossy",
        "Watermark vẽ tay, hiệu ứng kính bóng (Glossy).",
        WatermarkHud::new
    );

    // --- SETTINGS ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStyle = settings.createGroup("Style & Colors");

    // 1. Kích thước
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Kích thước.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(3.0)
        .build()
    );

    // 2. Màu chữ
    private final Setting<SettingColor> mainColor = sgStyle.add(new ColorSetting.Builder()
        .name("brand-color")
        .description("Màu chữ CatDz.")
        .defaultValue(new SettingColor(255, 50, 50, 255)) // Đỏ tươi
        .build()
    );

    private final Setting<SettingColor> textColor = sgStyle.add(new ColorSetting.Builder()
        .name("info-color")
        .description("Màu thông số.")
        .defaultValue(new SettingColor(200, 200, 200, 255))
        .build()
    );

    // 3. Màu nền (Gradient dọc để tạo chiều sâu)
    private final Setting<SettingColor> bgTopColor = sgStyle.add(new ColorSetting.Builder()
        .name("bg-top-color")
        .description("Màu nền phía trên (nên để tối).")
        .defaultValue(new SettingColor(20, 20, 30, 200))
        .build()
    );

    private final Setting<SettingColor> bgBottomColor = sgStyle.add(new ColorSetting.Builder()
        .name("bg-bottom-color")
        .description("Màu nền phía dưới (nên để sáng hơn chút).")
        .defaultValue(new SettingColor(40, 40, 60, 220))
        .build()
    );

    // 4. Hiệu ứng bóng (Gloss)
    private final Setting<Boolean> glossy = sgStyle.add(new BoolSetting.Builder()
        .name("glossy-effect")
        .description("Bật hiệu ứng kính bóng loáng.")
        .defaultValue(true)
        .build()
    );

    // 5. Thanh màu RGB trên cùng
    private final Setting<Boolean> topBar = sgStyle.add(new BoolSetting.Builder()
        .name("rgb-bar")
        .description("Hiện thanh màu RGB trên cùng.")
        .defaultValue(true)
        .build()
    );

    public WatermarkHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null || mc.world == null) return;

        double s = scale.get();

        // --- BƯỚC 1: CHUẨN BỊ NỘI DUNG ---
        String name = "CatDz EzAddons";
        String userName = mc.getSession().getUsername();
        String ping = PlayerUtils.getPing() + "ms";
        String fps = MinecraftClient.getInstance().getCurrentFps() + " FPS";
        String server = mc.isInSingleplayer() ? "SinglePlayer" : (mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Unknown");
        String sep = " | ";

        // Tính toán kích thước
        double nameW = renderer.textWidth(name, true);
        double infoW = renderer.textWidth(sep + userName + sep + ping + sep + fps + sep + server, true);

        double paddingX = 6 * s;
        double paddingY = 6 * s;

        double totalWidth = nameW + infoW + (paddingX * 2);
        double totalHeight = renderer.textHeight(true) + (paddingY * 2);

        setSize(totalWidth * s, totalHeight * s);

        // --- BƯỚC 2: VẼ NỀN GRADIENT (Tạo độ sâu) ---
        // Vẽ hình chữ nhật với 4 màu ở 4 góc: 2 góc trên là màu Top, 2 góc dưới là màu Bottom
        renderer.quad(
            x, y, getWidth(), getHeight(),
            bgTopColor.get(), bgTopColor.get(),     // Góc trên trái, trên phải
            bgBottomColor.get(), bgBottomColor.get() // Góc dưới phải, dưới trái
        );

        // --- BƯỚC 3: VẼ ĐỘ BÓNG (GLOSS) ---
        if (glossy.get()) {
            // Vẽ một lớp phủ màu trắng mờ ở 50% phía trên của HUD
            // Tạo hiệu ứng ánh sáng phản chiếu
            renderer.quad(
                x, y, getWidth(), getHeight() / 2,
                new Color(255, 255, 255, 40), // Trắng mờ trên
                new Color(255, 255, 255, 40),
                new Color(255, 255, 255, 5),  // Nhạt dần xuống giữa
                new Color(255, 255, 255, 5)
            );
        }

        // --- BƯỚC 4: VẼ THANH RGB (Điểm nhấn) ---
        if (topBar.get()) {
            renderer.quad(
                x, y, getWidth(), 1.5 * s,
                new Color(255, 50, 50, 255),  // Đỏ
                new Color(50, 50, 255, 255),  // Xanh dương
                new Color(50, 50, 255, 255),
                new Color(255, 50, 50, 255)
            );
        }

        // --- BƯỚC 5: VẼ CHỮ ---
        // Căn giữa text theo chiều dọc
        double textY = y + (totalHeight / 2) - (renderer.textHeight(true) / 2);
        double currentX = x + paddingX;

        // Vẽ tên CatDz
        renderer.text(name, currentX, textY, mainColor.get(), true, s);
        currentX += (nameW * s);

        // Vẽ thông tin còn lại
        renderer.text(sep + userName + sep + ping + sep + fps + sep + server, currentX, textY, textColor.get(), true, s);
    }
}
