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

import java.util.List;

public class AutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter & Item Settings");
    private final SettingGroup sgTrash = settings.createGroup("Trash & Sell Settings");
    private final SettingGroup sgRepair = settings.createGroup("Auto Repair Settings");

    // --- General Settings ---
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
        .description("Item cần đếm/bán khi tắt auto-detect.")
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

    // --- Filter Settings ---
    private final Setting<List<Item>> keepItems = sgFilter.add(new ItemListSetting.Builder()
        .name("keep-items")
        .description("Danh sách item BẮT BUỘC GIỮ LẠI (không bao giờ bán hoặc vứt bỏ).")
        .defaultValue(
            Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE,
            Items.NETHERITE_SWORD, Items.DIAMOND_SWORD,
            Items.NETHERITE_AXE, Items.DIAMOND_AXE,
            Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
            Items.EXPERIENCE_BOTTLE, Items.SHULKER_BOX
        )
        .build()
    );

    private final Setting<Boolean> autoDropTrash = sgFilter.add(new BoolSetting.Builder()
        .name("auto-drop-trash")
        .description("Tự động vứt bỏ item rác ra đất ngay trong lúc đào (tránh nhanh đầy balo).")
        .defaultValue(false)
        .build()
    );

    // --- Trash & Sell Settings ---
    private final Setting<Boolean> sellTargetItem = sgTrash.add(new BoolSetting.Builder()
        .name("sell-target-item")
        .description("Bán cả vật phẩm mục tiêu (kim cương/quặng) khi mở GUI bán đồ.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> trashItems = sgTrash.add(new ItemListSetting.Builder()
        .name("trash-items")
        .description("Danh sách item rác sẽ bị bán vào /sellgui hoặc tự động vứt.")
        .defaultValue(
            Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.STONE,
            Items.NETHERRACK, Items.DIORITE, Items.ANDESITE, Items.GRANITE,
            Items.COBBLED_DEEPSLATE, Items.DEEPSLATE, Items.ROTTEN_FLESH, Items.BONE,
            Items.ARROW, Items.GUNPOWDER, Items.STRING, Items.SPIDER_EYE, Items.BLAZE_ROD, Items.ENDER_PEARL
        )
        .build()
    );

    // --- Repair Settings ---
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
        SELL_GUI
    }

    private State currentState = State.IDLE;
    private int timer = 0;

    public AutoMine() {
        super(AddonTemplate.CATEGORY, "AutoMine", "Đào quặng tự động, tự lọc item, vứt rác, tự sửa đồ và bán item.");
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
                // 1. Lọc và tự động vứt rác ra đất nếu bật autoDropTrash
                if (autoDropTrash.get()) {
                    if (dropTrashFromInventory()) {
                        timer = 2;
                        break;
                    }
                }

                // 2. Kiểm tra độ bền dụng cụ
                if (needsRepair()) {
                    ChatUtils.sendPlayerMsg("#stop");
                    currentState = State.AUTO_REPAIR;
                    timer = actionDelay.get();
                    break;
                }

                // 3. Kiểm tra khi full balo -> Đi bán đồ
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
                    boolean sold = doSellItemsFromPlayerInv(screen);
                    if (!sold) {
                        mc.player.closeHandledScreen();
                        currentState = State.START_MINE;
                        timer = actionDelay.get();
                    } else {
                        timer = actionDelay.get();
                    }
                }
                break;

            default:
                break;
        }
    }

    // Lọc và vứt rác khỏi inventory khi đang đào
    private boolean dropTrashFromInventory() {
        for (int i = 0; i < mc.player.getInventory().main.size(); i++) {
            ItemStack stack = mc.player.getInventory().main.get(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (isTrashItem(item) && !isKeepItem(item)) {
                InvUtils.drop(i);
                return true; // Vứt từng ô một để tránh bị kick hoặc kẹt
            }
        }
        return false;
    }

    // Bán item trong GUI /sellgui dựa trên bộ lọc
    private boolean doSellItemsFromPlayerInv(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        int playerInvStart = handler.slots.size() - 36;
        Item target = getEffectiveCollectItem();

        for (int i = playerInvStart; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Nếu nằm trong danh sách GIỮ LẠI -> Bỏ qua ngay
            if (isKeepItem(item)) continue;

            boolean isTrash = isTrashItem(item);
            boolean isTarget = sellTargetItem.get() && (item == target || item == mineBlock.get());

            if (isTrash || isTarget) {
                clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE);
                return true;
            }
        }
        return false;
    }

    // Logic kiểm tra bộ lọc
    private boolean isKeepItem(Item item) {
        return keepItems.get().contains(item);
    }

    private boolean isTrashItem(Item item) {
        return trashItems.get().contains(item);
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
