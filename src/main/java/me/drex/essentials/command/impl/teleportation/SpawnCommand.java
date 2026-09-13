package me.drex.essentials.command.impl.teleportation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.drex.essentials.command.Command;
import me.drex.essentials.command.CommandProperties;
import me.drex.essentials.command.util.CommandUtil;
import me.drex.essentials.storage.DataStorage;
import me.drex.essentials.util.teleportation.Location;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import static me.drex.essentials.util.LocalizedMessage.localized;

public class SpawnCommand extends Command {

    public SpawnCommand() {
        super(CommandProperties.create("spawn", 0));
    }

    @Override
    protected void registerArguments(LiteralArgumentBuilder<CommandSourceStack> literal, CommandBuildContext commandBuildContext) {
        literal.executes(this::spawn);
    }

    private int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer serverPlayer = src.getPlayerOrException();
        final Location configuredSpawn = DataStorage.serverData().getSpawn();
        final boolean vanillaFallback = configuredSpawn == null;

        ServerLevel targetLevel;
        LevelData.RespawnData respawnData;
        float yaw;
        float pitch;
        int spawnX;
        int spawnZ;
        if (configuredSpawn != null) {
            targetLevel = configuredSpawn.getLevel(src.getServer());
            if (targetLevel == null) throw WORLD_UNKNOWN.create(src);
            respawnData = null;
            yaw = configuredSpawn.yaw();
            pitch = configuredSpawn.pitch();
            spawnX = (int) Math.floor(configuredSpawn.pos().x);
            spawnZ = (int) Math.floor(configuredSpawn.pos().z);
        } else {
            targetLevel = src.getServer().overworld();
            respawnData = targetLevel.getRespawnData();
            ServerLevel respawnLevel = targetLevel.getServer().getLevel(respawnData.globalPos().dimension());
            if (respawnLevel != null) targetLevel = respawnLevel;
            yaw = respawnData.yaw();
            pitch = respawnData.pitch();
            spawnX = respawnData.globalPos().pos().getX();
            spawnZ = respawnData.globalPos().pos().getZ();
        }

        final ServerLevel finalTargetLevel = targetLevel;
        final LevelData.RespawnData finalRespawnData = respawnData;
        final float finalYaw = yaw;
        final float finalPitch = pitch;
        final int finalSpawnX = spawnX;
        final int finalSpawnZ = spawnZ;

        CommandUtil.asyncTeleport(
            src,
            finalTargetLevel,
            vanillaFallback ? new ChunkPos(finalSpawnX >> 4, finalSpawnZ >> 4) : configuredSpawn.chunkPos(),
            config().teleportation.waitingPeriod
        ).whenCompleteAsync((chunkAccess, throwable) -> {
            if (chunkAccess == null) return;
            Location location;
            if (vanillaFallback) {
                int y = chunkAccess.getHeight(Heightmap.Types.MOTION_BLOCKING, finalSpawnX, finalSpawnZ) + 1;
                location = new Location(
                    Vec3.atBottomCenterOf(new BlockPos(finalSpawnX, y, finalSpawnZ)),
                    finalYaw,
                    finalPitch,
                    finalTargetLevel.dimension().identifier()
                );
            } else {
                location = configuredSpawn;
            }
            ctx.getSource().sendSuccess(() -> localized("fabric-essentials.commands.spawn", ctx.getSource()), false);
            location.teleport(serverPlayer);
        }, src.getServer());
        return SUCCESS;
    }

}