package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class KeyBindsHud extends HudElement {

    public static final HudElementInfo<KeyBindsHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP,
        "key-binds-list",
        "Hiển thị danh sách các phím tắt (Keybinds).",
        KeyBindsHud::new
    );

    // --- SETTINGS ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("only-enabled")
        .description("Chỉ hiển thị các module đang BẬT.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> onColor = sgGeneral.add(new ColorSetting.Builder()
        .name("on-color")
        .description("Màu tên khi module đang BẬT.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );

    private final Setting<SettingColor> offColor = sgGeneral.add(new ColorSetting.Builder()
        .name("off-color")
        .description("Màu tên khi module đang TẮT.")
        .defaultValue(new SettingColor(180, 180, 180, 255))
        .build()
    );

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color")
        .description("Màu chữ tiêu đề KeyBinds.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Kích thước chữ.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(3.0)
        .build()
    );

    public KeyBindsHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double s = scale.get();
        double padding = 2 * s;

        // 1. Lọc danh sách các Module có Bind
        List<Module> bindedModules = new ArrayList<>();
        for (Module module : Modules.get().getAll()) {
            // FIX: Xóa .get(), gọi trực tiếp .getValue()
            if (module.keybind.getValue() == -1) continue;

            // Logic lọc Only Enabled
            if (onlyEnabled.get() && !module.isActive()) continue;

            bindedModules.add(module);
        }

        // Nếu không có gì để hiện thì chỉ hiện tiêu đề
        if (bindedModules.isEmpty()) {
            setSize(renderer.textWidth("KeyBinds", true) * s + padding * 4, renderer.textHeight(true) * s + padding * 2);
            renderer.text("KeyBinds", x + padding * 2, y + padding, titleColor.get(), true, s);
            return;
        }

        // 2. Tính toán kích thước
        double titleW = renderer.textWidth("KeyBinds", true) * s;
        double maxNameW = 0;
        double maxBindW = 0;

        for (Module m : bindedModules) {
            double nw = renderer.textWidth(m.title, true) * s;
            // FIX: Xóa .get()
            double bw = renderer.textWidth(getShortKeyName(m.keybind), true) * s;
            if (nw > maxNameW) maxNameW = nw;
            if (bw > maxBindW) maxBindW = bw;
        }

        double contentWidth = maxNameW + (15 * s) + maxBindW;
        double totalWidth = Math.max(titleW + (20 * s), contentWidth) + (padding * 2);

        double lineHeight = renderer.textHeight(true) * s + (2 * s);
        double headerHeight = lineHeight + (4 * s);
        double totalHeight = headerHeight + (bindedModules.size() * lineHeight) + padding;

        setSize(totalWidth, totalHeight);

        // 3. VẼ GIAO DIỆN

        // Header
        double titleX = x + (totalWidth - titleW) / 2;
        renderer.text("KeyBinds", titleX, y + padding, titleColor.get(), true, s);

        // Line
        double lineY = y + headerHeight - (2 * s);
        renderer.quad(x + (5*s), lineY, totalWidth - (10*s), 1 * s,
            new Color(0, 0, 0, 0), titleColor.get(), titleColor.get(), new Color(0, 0, 0, 0));

        // List
        double currentY = y + headerHeight;
        double separatorX = x + (padding * 2) + maxNameW + (5 * s);

        for (Module m : bindedModules) {
            Color c = m.isActive() ? onColor.get() : offColor.get();
            // FIX: Xóa .get()
            String keyName = getShortKeyName(m.keybind);

            renderer.text(m.title, x + (5 * s), currentY, c, true, s);
            renderer.quad(separatorX, currentY - (1*s), 1 * s, lineHeight - (2*s), new Color(255, 255, 255, 50));

            double keyW = renderer.textWidth(keyName, true) * s;
            double rightSpaceX = separatorX + (2 * s);
            double remainingWidth = totalWidth - (rightSpaceX - x);
            double keyX = rightSpaceX + (remainingWidth - keyW) / 2 - (3*s);

            renderer.text(keyName, keyX, currentY, c, true, s);

            currentY += lineHeight;
        }
    }

    // --- HÀM XỬ LÝ TÊN PHÍM ---
    private String getShortKeyName(Keybind keybind) {
        // Lấy value trực tiếp
        int key = keybind.getValue();
        if (key == -1) return "None";

        String name = getGlfwKeyName(key);
        if (name == null) return "Unknown";

        switch (name) {
            case "LEFT_CONTROL": return "LCtrl";
            case "RIGHT_CONTROL": return "RCtrl";
            case "LEFT_SHIFT": return "LShift";
            case "RIGHT_SHIFT": return "RShift";
            case "LEFT_ALT": return "LAlt";
            case "RIGHT_ALT": return "RAlt";
            case "DELETE": return "Del";
            case "ENTER": return "Ent";
            case "ESCAPE": return "Esc";
            case "BACKSPACE": return "Back";
            default: return name;
        }
    }

    private String getGlfwKeyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase();

        switch (key) {
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LEFT_CONTROL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RIGHT_CONTROL";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LEFT_SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RIGHT_SHIFT";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LEFT_ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RIGHT_ALT";
            case GLFW.GLFW_KEY_DELETE: return "DELETE";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_BACKSPACE: return "BACKSPACE";
            case GLFW.GLFW_KEY_ESCAPE: return "ESCAPE";
            // Thêm phím chuột
            case GLFW.GLFW_MOUSE_BUTTON_LEFT: return "M1";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT: return "M2";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE: return "M3";
        }

        return "KEY" + key;
    }
}
