package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Set;

/**
 * AutoMine+ (Bản Rút Gọn) — Tự động đào và dùng /sellgui để bán item mục tiêu khi balo đầy.
 */
public class AutoMine extends Module {

    // =========================================================
    //  Settings
    // =========================================================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> mineBlock = sgGeneral.add(new ItemSetting.Builder()
        .name("mine-block")
        .description("Khối quặng cho Baritone #mine (luôn là ore block).")
        .defaultValue(Items.DIAMOND_ORE)
        .build());

    private final Setting<Boolean> autoDetectDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-detect-drop")
        .description("Bật để tự nhận diện: có Silk Touch -> bán quặng nguyên khối; không có -> bán khoáng sản.")
        .defaultValue(true)
        .build());

    private final Setting<Item> collectItem = sgGeneral.add(new ItemSetting.Builder()
        .name("collect-item")
        .description("Item muốn bán khi TẮT chế độ auto-detect.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> !autoDetectDrop.get())
        .build());

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Độ trễ (ticks) giữa các thao tác click slot bán đồ.")
        .defaultValue(5).min(1).sliderMax(20)
        .build());

    // =========================================================
    //  State Machine
    // =========================================================
    private enum State {
        IDLE,
        START_MINE,
        MINING,
        STOP_MINE,
        WAIT_STOP,
        OPEN_SELLGUI,
        SELLGUI_GUI
    }

    private State currentState = State.IDLE;
    private int timer = 0;

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "AutoMine+", "Tự động đào và bán vào /sellgui khi đầy balo.");
    }

    @Override
    public void onActivate() {
        currentState = State.IDLE;
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && mc.options.attackKey != null) mc.options.attackKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        switch (currentState) {
            case IDLE:
                currentState = State.START_MINE;
                break;

            case START_MINE:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("#mine " + Registries.ITEM.getId(mineBlock.get()).toString());
                timer = 60;
                currentState = State.MINING;
                break;

            case MINING:
                // Nếu không còn slot trống nào trong balo -> Dừng lại để bán
                if (isInventoryFull()) {
                    currentState = State.STOP_MINE;
                }
                break;

            case STOP_MINE:
                ChatUtils.sendPlayerMsg("#stop");
                timer = 40;
                currentState = State.WAIT_STOP;
                break;

            case WAIT_STOP:
                currentState = State.OPEN_SELLGUI;
                break;

            case OPEN_SELLGUI:
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                ChatUtils.sendPlayerMsg("/sellgui");
                timer = 40; // Chờ GUI /sellgui mở lên
                currentState = State.SELLGUI_GUI;
                break;

            case SELLGUI_GUI:
                if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) {
                    currentState = State.START_MINE;
                    break;
                }
                
                // Thực hiện shift-click item mục tiêu vào /sellgui
                if (!doSellTargetItems((net.minecraft.client.gui.screen.ingame.HandledScreen<?>) mc.currentScreen)) {
                    // Khi đã bán hết item, đóng GUI và tiếp tục đào
                    mc.player.closeHandledScreen();
                    timer = 15;
                    currentState = State.START_MINE;
                }
                break;
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================

    /** Thực hiện shift-click toàn bộ vật phẩm đang yêu cầu đào vào màn hình GUI */
    private boolean doSellTargetItems(net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
        net.minecraft.screen.ScreenHandler h = screen.getScreenHandler();
        int cSz = h.slots.size() - 36; // Lấy vị trí bắt đầu túi đồ của người chơi
        Item target = getEffectiveCollectItem();

        for (int i = cSz; i < h.slots.size(); i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (!st.isEmpty() && st.getItem() == target) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                timer = actionDelay.get();
                return true;
            }
        }
        return false;
    }

    /** Kiểm tra xem balo (cả hotbar và túi chính) đã full hay chưa */
    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Xác định item cần bán (hỗ trợ tự động nhận diện Silk Touch hoặc do cài đặt chỉ định) */
    private Item getEffectiveCollectItem() {
        if (!autoDetectDrop.get()) return collectItem.get();
        return hasSilkTouchInHotbar() ? mineBlock.get() : getOreDrop(mineBlock.get());
    }

    /** Kiểm tra Hotbar xem người chơi có đang cầm công cụ có Silk Touch không */
    private boolean hasSilkTouchInHotbar() {
        if (mc.player == null || mc.world == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            
            Set<RegistryEntry<Enchantment>> enchants = EnchantmentHelper.getEnchantments(s).getEnchantments();
            for (RegistryEntry<Enchantment> entry : enchants) {
                if (entry.getIdAsString().toLowerCase().contains("silk_touch")) return true;
            }
        }
        return false;
    }

    /** Trả về item rớt ra khi đập quặng nếu không có Silk Touch */
    private static Item getOreDrop(Item oreBlock) {
        if (oreBlock == Items.DIAMOND_ORE      || oreBlock == Items.DEEPSLATE_DIAMOND_ORE)  return Items.DIAMOND;
        if (oreBlock == Items.IRON_ORE         || oreBlock == Items.DEEPSLATE_IRON_ORE)     return Items.RAW_IRON;
        if (oreBlock == Items.GOLD_ORE         || oreBlock == Items.DEEPSLATE_GOLD_ORE
                                               || oreBlock == Items.NETHER_GOLD_ORE)        return Items.RAW_GOLD;
        if (oreBlock == Items.COPPER_ORE       || oreBlock == Items.DEEPSLATE_COPPER_ORE)   return Items.RAW_COPPER;
        if (oreBlock == Items.COAL_ORE         || oreBlock == Items.DEEPSLATE_COAL_ORE)     return Items.COAL;
        if (oreBlock == Items.EMERALD_ORE      || oreBlock == Items.DEEPSLATE_EMERALD_ORE)  return Items.EMERALD;
        if (oreBlock == Items.LAPIS_ORE        || oreBlock == Items.DEEPSLATE_LAPIS_ORE)    return Items.LAPIS_LAZULI;
        if (oreBlock == Items.REDSTONE_ORE     || oreBlock == Items.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (oreBlock == Items.NETHER_QUARTZ_ORE)                                            return Items.QUARTZ;
        if (oreBlock == Items.ANCIENT_DEBRIS)                                               return Items.NETHERITE_SCRAP;
        return oreBlock;
    }
}