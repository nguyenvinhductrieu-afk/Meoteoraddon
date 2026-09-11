package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public class AutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRepair = settings.createGroup("Auto Repair Settings");

    // General Settings
    private final Setting<Item> mineBlock = sgGeneral.add(new ItemSetting.Builder()
        .name("mine-block")
        .description("Khối quặng cho Baritone #mine (ví dụ: DIAMOND_ORE, IRON_ORE...).")
        .defaultValue(Items.DIAMOND_ORE)
        .build()
    );

    private final Setting<Boolean> autoDetectDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-detect-drop")
        .description("Tự động nhận biết Silk Touch trong hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Item> collectItem = sgGeneral.add(new ItemSetting.Builder()
        .name("collect-item")
        .description("Item cần lọc/chia kho khi tắt auto-detect.")
        .defaultValue(Items.DIAMOND)
        .visible(() -> !autoDetectDrop.get())
        .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Độ trễ (ticks) giữa các thao tác.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    // Repair Settings
    private final Setting<Integer> minDurability = sgRepair.add(new IntSetting.Builder()
        .name("min-durability")
        .description("Độ bền tối thiểu của dụng cụ trước khi dừng đào để dùng bình EXP.")
        .defaultValue(40)
        .min(5)
        .sliderMax(200)
        .build()
    );

    public enum State {
        IDLE,
        START_MINE,
        MINING,
        AUTO_REPAIR,
        OPEN_SELL_GUI,
        SELL_GUI,
        SPLIT_TARGET_SLOTS
    }

    private State currentState = State.IDLE;
    private int timer = 0;

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "AutoMine", "Tự đào quặng, bán rác qua /sellgui, chia 1 ore/ô balo để chống nhặt rác và tự sửa đồ.");
    }

    @Override
    public void onActivate() {
        currentState = State.START_MINE;
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            ChatUtils.sendPlayerMsg("#stop");
        }
        currentState = State.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        switch (currentState) {
            case START_MINE:
                ChatUtils.sendPlayerMsg("#mine " + mineBlock.get().toString().replace("minecraft:", ""));
                currentState = State.MINING;
                break;

            case MINING:
                // 1. Kiểm tra độ bền dụng cụ
                if (needsRepair()) {
                    ChatUtils.sendPlayerMsg("#stop");
                    currentState = State.AUTO_REPAIR;
                    timer = actionDelay.get();
                    break;
                }

                // 2. Khi full balo -> Bán hết item không phải item mục tiêu và chia ore vào ô trống
                if (isMainInventoryFull()) {
                    ChatUtils.sendPlayerMsg("#stop");
                    currentState = State.OPEN_SELL_GUI;
                    timer = actionDelay.get();
                    break;
                }
                break;

            case AUTO_REPAIR:
                if (isFullyRepaired()) {
                    currentState = State.START_MINE;
                    break;
                }

                FindItemResult expBottles = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE);
                if (expBottles.found()) {
                    InvUtils.swap(expBottles.slot(), true);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    timer = 2;
                } else {
                    currentState = State.START_MINE;
                }
                break;

            case OPEN_SELL_GUI:
                ChatUtils.sendPlayerMsg("/sellgui");
                currentState = State.SELL_GUI;
                timer = actionDelay.get();
                break;

            case SELL_GUI:
                if (mc.currentScreen instanceof HandledScreen) {
                    HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
                    boolean sold = doSellNonTargetItems(screen);
                    if (!sold) {
                        mc.player.closeHandledScreen();
                        currentState = State.SPLIT_TARGET_SLOTS;
                        timer = actionDelay.get();
                    } else {
                        timer = actionDelay.get();
                    }
                }
                break;

            case SPLIT_TARGET_SLOTS:
                boolean splitPerformed = fillOneEmptySlotWithTarget();
                if (splitPerformed) {
                    timer = 1;
                } else {
                    currentState = State.START_MINE;
                    timer = actionDelay.get();
                }
                break;

            default:
                break;
        }
    }

    private boolean doSellNonTargetItems(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        int playerInvStart = handler.slots.size() - 36;
        Item target = getEffectiveCollectItem();

        for (int i = playerInvStart; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            
            // Bán tất cả ngoại trừ: Item mục tiêu, Khối quặng mục tiêu, Trang bị/Cúp/Kiếm, Bình EXP
            boolean isTarget = (item == target || item == mineBlock.get());
            boolean isToolOrArmor = stack.isDamageable();
            boolean isExp = (item == Items.EXPERIENCE_BOTTLE);

            if (!isTarget && !isToolOrArmor && !isExp) {
                clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
                return true;
            }
        }
        return false;
    }

    private boolean fillOneEmptySlotWithTarget() {
        Item target = getEffectiveCollectItem();

        // Tìm 1 ô trống trong balo chính (slot 9 đến 35)
        int emptySlot = -1;
        for (int i = 9; i <= 35; i++) {
            if (mc.player.playerScreenHandler.getSlot(i).getStack().isEmpty()) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot == -1) return false; // Đã phủ kín 1 ore ở tất cả các ô!

        // Tìm 1 ô có chứa item mục tiêu với số lượng > 1 để thực hiện tách
        int sourceSlot = -1;
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if ((stack.getItem() == target || stack.getItem() == mineBlock.get()) && stack.getCount() > 1) {
                sourceSlot = i;
                break;
            }
        }

        if (sourceSlot == -1) {
            for (int i = 36; i <= 44; i++) {
                ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
                if ((stack.getItem() == target || stack.getItem() == mineBlock.get()) && stack.getCount() > 1) {
                    sourceSlot = i;
                    break;
                }
            }
        }

        if (sourceSlot == -1) return false;

        int syncId = mc.player.playerScreenHandler.syncId;

        // Tách 1 item sang ô trống
        clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP);  // Cầm stack
        clickSlot(syncId, emptySlot, 1, SlotActionType.PICKUP);   // Chuột phải thả 1 item
        clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP);  // Thả phần còn lại về vị trí cũ

        return true;
    }

    private void clickSlot(int syncId, int slotId, int button, SlotActionType actionType) {
        mc.interactionManager.clickSlot(syncId, slotId, button, actionType, mc.player);
    }

    private boolean isMainInventoryFull() {
        return mc.player.getInventory().main.stream().noneMatch(ItemStack::isEmpty);
    }

    private boolean needsRepair() {
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty() || !mainHand.isDamageable()) return false;
        int currentDurability = mainHand.getMaxDamage() - mainHand.getDamage();
        return currentDurability <= minDurability.get();
    }

    private boolean isFullyRepaired() {
        ItemStack mainHand = mc.player.getMainHandStack();
        if (mainHand.isEmpty() || !mainHand.isDamageable()) return true;
        return mainHand.getDamage() == 0;
    }

    private Item getEffectiveCollectItem() {
        if (autoDetectDrop.get()) {
            if (hasSilkTouchInHotbar()) {
                return mineBlock.get();
            } else {
                return getOreDrop(mineBlock.get());
            }
        }
        return collectItem.get();
    }

    private boolean hasSilkTouchInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.hasEnchantments()) {
                if (stack.getEnchantments().getEnchantments().stream()
                    .anyMatch(entry -> entry.getIdAsString().toLowerCase().contains("silk_touch"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Item getOreDrop(Item ore) {
        if (ore == Items.DIAMOND_ORE || ore == Items.DEEPSLATE_DIAMOND_ORE) return Items.DIAMOND;
        if (ore == Items.IRON_ORE || ore == Items.DEEPSLATE_IRON_ORE) return Items.RAW_IRON;
        if (ore == Items.GOLD_ORE || ore == Items.DEEPSLATE_GOLD_ORE || ore == Items.NETHER_GOLD_ORE) return Items.RAW_GOLD;
        if (ore == Items.COPPER_ORE || ore == Items.DEEPSLATE_COPPER_ORE) return Items.RAW_COPPER;
        if (ore == Items.COAL_ORE || ore == Items.DEEPSLATE_COAL_ORE) return Items.COAL;
        if (ore == Items.EMERALD_ORE || ore == Items.DEEPSLATE_EMERALD_ORE) return Items.EMERALD;
        if (ore == Items.LAPIS_ORE || ore == Items.DEEPSLATE_LAPIS_ORE) return Items.LAPIS_LAZULI;
        if (ore == Items.REDSTONE_ORE || ore == Items.DEEPSLATE_REDSTONE_ORE) return Items.REDSTONE;
        if (ore == Items.NETHER_QUARTZ_ORE) return Items.QUARTZ;
        if (ore == Items.ANCIENT_DEBRIS) return Items.NETHERITE_SCRAP;
        return ore;
    }
}
