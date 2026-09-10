import re

file_path = r"e:\addon meteor fram\meteor-addon1.21.8\src\main\java\com\example\addon\modules\VillagerRoller.java"

with open(file_path, "r", encoding="utf-8") as f:
    code = f.read()

code = code.replace(
    '.defaultValue(Keybind.none())\n        .build()\n    );',
    '.defaultValue(Keybind.none())\n        .build()\n    );\n\n    public final Setting<Keybind> addDumpChestKey = sgSetup.add(new KeybindSetting.Builder()\n        .name("add-dump-chest")\n        .description("Phím cài rương cất đồ (K).")\n        .defaultValue(Keybind.none())\n        .build()\n    );', 1
)

code = code.replace(
    'public final Setting<Integer> restockStacks2 =',
    'public final Setting<Integer> maxBooksToHold = sgInventory.add(new IntSetting.Builder()\n        .name("max-books-to-hold")\n        .description("Giới hạn sách trong túi trước khi đi cất.")\n        .defaultValue(5)\n        .min(1)\n        .sliderMax(36)\n        .build()\n    );\n\n    public final Setting<Integer> restockStacks2 ='
)

code = code.replace(
    'private final List<ChestPoint> chestPoints = new ArrayList<>();',
    'private final List<ChestPoint> chestPoints = new ArrayList<>();\n    private final List<ChestPoint> dumpPoints = new ArrayList<>();'
)

code = code.replace(
    'private int currentRestockChestIndex = 0;',
    'private int currentRestockChestIndex = 0;\n    private int currentDumpChestIndex = 0;\n    private int lecternCount = 0;\n    private boolean addDumpChestWasPressed = false;'
)

code = code.replace(
    'currentRestockChestIndex = 0;',
    'currentRestockChestIndex = 0;\n        currentDumpChestIndex = 0;\n        lecternCount = 0;'
)

code = code.replace(
    'return tag;\n    }',
    'NbtList dumps = new NbtList();\n        for (ChestPoint cp : dumpPoints) {\n            NbtCompound dpTag = new NbtCompound();\n            dpTag.putLong("standPos", cp.standPos.asLong());\n            dpTag.putLong("targetPos", cp.targetPos.asLong());\n            dumps.add(dpTag);\n        }\n        tag.put("dumpPoints", dumps);\n\n        return tag;\n    }'
)

code = code.replace(
    'return this;\n    }',
    'dumpPoints.clear();\n        if (tag.contains("dumpPoints")) {\n            NbtList dumps = (NbtList) tag.get("dumpPoints");\n            for (int i = 0; i < dumps.size(); i++) {\n                NbtCompound dpTag = (NbtCompound) dumps.get(i);\n                BlockPos standPos = BlockPos.fromLong(dpTag.getLong("standPos").orElse(0L));\n                BlockPos targetPos = BlockPos.fromLong(dpTag.getLong("targetPos").orElse(0L));\n                dumpPoints.add(new ChestPoint(standPos, targetPos));\n            }\n        }\n\n        return this;\n    }'
)

idle_logic = """                    if (countItemsInInventory(Items.ENCHANTED_BOOK) >= maxBooksToHold.get()) {
                        info("Túi đã đầy sách phù phép! Đi cất đồ.");
                        currentState = State.MOVING_TO_DUMP;
                        currentDumpChestIndex = 0;
                        if (!dumpPoints.isEmpty()) {
                            moveToPoint(dumpPoints.get(currentDumpChestIndex).standPos);
                        } else {
                            error("Chưa cài đặt rương cất đồ (Dump Chest)! Xin hãy dọn túi bằng tay.");
                            toggle();
                        }
                        return;
                    }
                    
                    if (needsRestock()) {"""
code = code.replace('if (needsRestock()) {', idle_logic, 1)

new_states = """            case MOVING_TO_DUMP:
                if (!dumpPoints.isEmpty() && isAtPoint(dumpPoints.get(currentDumpChestIndex).standPos)) {
                    lookAndInteractChest(dumpPoints.get(currentDumpChestIndex).targetPos);
                    currentState = State.DUMPING;
                    waitTicks = 10;
                }
                break;
                
            case DUMPING:
                doDumpLogic();
                break;
                
            case WAIT_FOR_LECTERN_DROP:
                if (countItemsInInventory(Items.LECTERN) > lecternCount) {
                    currentState = State.CHECK_VILLAGER;
                } else {
                    info("Bục văng quá xa hoặc vướng chân dân làng! Đang chạy đi nhặt...");
                    RollPoint rp = rollPoints.get(currentPointIndex);
                    ChatUtils.sendPlayerMsg("#goto " + rp.villagerPos.getX() + " " + rp.villagerPos.getY() + " " + rp.villagerPos.getZ());
                    waitTicks = 40;
                    currentState = State.MOVING_TO_RETRIEVE;
                }
                break;

            case MOVING_TO_RETRIEVE:
                RollPoint rpRetrieve = rollPoints.get(currentPointIndex);
                if (isAtPoint(rpRetrieve.villagerPos)) {
                    info("Đã tiếp cận bục, quay lại vị trí đứng...");
                    ChatUtils.sendPlayerMsg("#goto " + rpRetrieve.standPos.getX() + " " + rpRetrieve.standPos.getY() + " " + rpRetrieve.standPos.getZ());
                    waitTicks = 40;
                    currentState = State.MOVING_BACK_TO_STAND;
                }
                break;
                
            case MOVING_BACK_TO_STAND:
                RollPoint rpBack = rollPoints.get(currentPointIndex);
                if (isAtPoint(rpBack.standPos)) {
                    currentState = State.CHECK_VILLAGER;
                }
                break;
"""
code = code.replace('case RESTOCKING:\n                doRestockLogic();\n                break;', 'case RESTOCKING:\n                doRestockLogic();\n                break;\n\n' + new_states)

lock_trade_old = """        if (handler.getSlot(2).hasStack()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.QUICK_MOVE, mc.player);
            waitTicks = getDelay(); 
            
            info("Đã khóa nghề thành công! Chuyển sang trạm tiếp theo.");
            mc.player.closeHandledScreen();
            rollPoints.get(currentPointIndex).completed = true;
            lastRecipeIndex = -1;
            currentState = State.IDLE;
            return; 
        }"""
lock_trade_new = """        if (handler.getSlot(2).hasStack()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
            
            int emptySlot = -1;
            for (int i = 3; i < handler.slots.size(); i++) {
                if (!handler.getSlot(i).hasStack()) {
                    emptySlot = i;
                    break;
                }
            }
            
            if (emptySlot != -1) {
                mc.interactionManager.clickSlot(handler.syncId, emptySlot, 0, SlotActionType.PICKUP, mc.player);
            } else {
                mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.THROW, mc.player);
            }
            
            waitTicks = getDelay(); 
            
            info("Đã khóa nghề thành công! Chuyển sang trạm tiếp theo.");
            mc.player.closeHandledScreen();
            rollPoints.get(currentPointIndex).completed = true;
            lastRecipeIndex = -1;
            currentState = State.IDLE;
            return; 
        }"""
code = code.replace(lock_trade_old, lock_trade_new)

break_lectern_old = """        if (mc.world.getBlockState(rp.lecternPos).isAir()) {
            if (isBreaking) {
                mc.options.attackKey.setPressed(false);
                isBreaking = false;
            }
            waitTicks = 10;
            currentState = State.CHECK_VILLAGER;
            return;
        }

        if (!isBreaking) {"""
break_lectern_new = """        if (mc.world.getBlockState(rp.lecternPos).isAir()) {
            if (isBreaking) {
                mc.options.attackKey.setPressed(false);
                isBreaking = false;
            }
            waitTicks = 20;
            currentState = State.WAIT_FOR_LECTERN_DROP;
            return;
        }

        if (!isBreaking) {
            lecternCount = countItemsInInventory(Items.LECTERN);"""
code = code.replace(break_lectern_old, break_lectern_new)

dump_logic = """    private void doDumpLogic() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;
        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        
        int containerSize = handler.getInventory().size();
        boolean dumpedAnything = false;
        
        for (int i = containerSize; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).hasStack() && handler.getSlot(i).getStack().getItem() == Items.ENCHANTED_BOOK) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                dumpedAnything = true;
                waitTicks = getDelay();
                return; 
            }
        }
        
        if (!dumpedAnything) {
            if (countItemsInInventory(Items.ENCHANTED_BOOK) > 0) {
                mc.player.closeHandledScreen();
                currentDumpChestIndex++;
                if (currentDumpChestIndex < dumpPoints.size()) {
                    info("Rương cất đồ đã đầy! Chuyển sang rương tiếp theo...");
                    currentState = State.MOVING_TO_DUMP;
                    moveToPoint(dumpPoints.get(currentDumpChestIndex).standPos);
                } else {
                    error("Tất cả rương cất đồ đều đã ĐẦY! Hãy dọn bớt rương hoặc xả bằng tay.");
                    toggle();
                }
                return;
            }
            info("Đã cất hết sách! Quay lại làm việc.");
            mc.player.closeHandledScreen();
            currentState = State.IDLE;
        }
    }
"""
code = code.replace('private boolean isAtPoint(BlockPos pos)', dump_logic + '\n    private boolean isAtPoint(BlockPos pos)')

handle_setup_old = """        addRestockWasPressed = restockIsPressed;
        
        boolean clearAllIsPressed = clearAllKey.get().isPressed();"""
handle_setup_new = """        addRestockWasPressed = restockIsPressed;
        
        boolean dumpIsPressed = addDumpChestKey.get().isPressed();
        if (dumpIsPressed && !addDumpChestWasPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((net.minecraft.util.hit.BlockHitResult) mc.crosshairTarget).getBlockPos();
                BlockPos standPos = mc.player.getBlockPos();
                dumpPoints.add(new ChestPoint(standPos, targetPos));
                info("Đã lưu Rương cất sách (Dump Chest) số #" + dumpPoints.size());
            }
        }
        addDumpChestWasPressed = dumpIsPressed;
        
        boolean clearAllIsPressed = clearAllKey.get().isPressed();"""
code = code.replace(handle_setup_old, handle_setup_new)

code = code.replace(
    'rollPoints.clear();\n            chestPoints.clear();\n            info("Đã xóa sạch toàn bộ tọa độ Dân làng, Bục và Rương!");',
    'rollPoints.clear();\n            chestPoints.clear();\n            dumpPoints.clear();\n            info("Đã xóa sạch toàn bộ tọa độ Dân làng, Bục và Rương!");'
)

code = code.replace(
    'for (ChestPoint cp : chestPoints) {\n            event.renderer.box(new Box(cp.targetPos), Color.ORANGE, Color.ORANGE, ShapeMode.Lines, 0);\n        }',
    'for (ChestPoint cp : chestPoints) {\n            event.renderer.box(new Box(cp.targetPos), Color.ORANGE, Color.ORANGE, ShapeMode.Lines, 0);\n        }\n        for (ChestPoint cp : dumpPoints) {\n            event.renderer.box(new Box(cp.targetPos), Color.RED, Color.RED, ShapeMode.Lines, 0);\n        }'
)

code = code.replace(
    'BREAK_LECTERN, PLACE_LECTERN, MOVING_TO_RESTOCK, RESTOCKING',
    'BREAK_LECTERN, PLACE_LECTERN, MOVING_TO_RESTOCK, RESTOCKING, MOVING_TO_DUMP, DUMPING, WAIT_FOR_LECTERN_DROP, MOVING_TO_RETRIEVE, MOVING_BACK_TO_STAND'
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Patch applied successfully!")
