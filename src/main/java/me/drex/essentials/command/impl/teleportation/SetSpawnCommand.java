package me.drex.essentials.command.impl.teleportation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.drex.essentials.command.Command;
import me.drex.essentials.command.CommandProperties;
import me.drex.essentials.storage.DataStorage;
import me.drex.essentials.util.teleportation.Location;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

import static me.drex.essentials.util.LocalizedMessage.localized;

public class SetSpawnCommand extends Command {

    public SetSpawnCommand() {
        super(CommandProperties.create("setspawn", 2));
    }

    @Override
    protected void registerArguments(LiteralArgumentBuilder<CommandSourceStack> literal, CommandBuildContext commandBuildContext) {
        literal.executes(this::setSpawn);
    }

    private int setSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        DataStorage.serverData().setSpawn(new Location(src));
        src.sendSuccess(() -> localized("fabric-essentials.commands.setspawn", src), false);
        return SUCCESS;
    }

}