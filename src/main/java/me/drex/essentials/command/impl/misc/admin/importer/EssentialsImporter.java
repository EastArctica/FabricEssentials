package me.drex.essentials.command.impl.misc.admin.importer;

import com.esotericsoftware.yamlbeans.YamlReader;
import me.drex.essentials.EssentialsMod;
import me.drex.essentials.storage.DataStorage;
import me.drex.essentials.storage.PlayerData;
import me.drex.essentials.storage.ServerData;
import me.drex.essentials.util.teleportation.Home;
import me.drex.essentials.util.teleportation.Location;
import me.drex.essentials.util.teleportation.Warp;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class EssentialsImporter implements DataImporter {

    public static final EssentialsImporter ESSENTIALS = new EssentialsImporter();

    @Override
    public String getImporterId() {
        return "essentialsx";
    }

    @Override
    public void importData(MinecraftServer server) {
        Path essentials = Path.of("plugins", "Essentials");
        importUserData(server, essentials.resolve("userdata"));
        importWarps(server, essentials.resolve("warps"));
        importSpawn(server, essentials.resolve("spawn.yml"));
    }

    private void importUserData(MinecraftServer server, Path userdata) {
        if (!Files.exists(userdata)) {
            EssentialsMod.LOGGER.error("User data directory ({}) doesn't exist", userdata);
            return;
        }
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        try (var files = Files.list(userdata)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith(".yml")) return;
                String uuid = fileName.substring(0, fileName.length() - 4);
                try {
                    PlayerData playerData = DataStorage.getOfflinePlayerData(server, UUID.fromString(uuid));
                    Map<String, Object> data = parseUserData(path);
                    if (data.isEmpty()) return;
                    Map<String, Home> homes = new HashMap<>();
                    parseHomes(server, data).forEach((homeName, location) -> homes.put(homeName, new Home(location)));
                    if (!homes.isEmpty()) {
                        playerData.homes.putAll(homes);
                        DataStorage.updateOfflinePlayerData(server, UUID.fromString(uuid), playerData);
                    }
                    success.incrementAndGet();
                } catch (IllegalArgumentException | IOException e) {
                    EssentialsMod.LOGGER.error("An error occurred while handling user file {}", path, e);
                    failed.incrementAndGet();
                }
            });
        } catch (IOException e) {
            EssentialsMod.LOGGER.error("An error occurred while importing user data", e);
            return;
        }
        EssentialsMod.LOGGER.info("User data imported, {} successful, {} failed!", success.get(), failed.get());
    }

    private void importWarps(MinecraftServer server, Path warpsDir) {
        if (!Files.exists(warpsDir)) {
            EssentialsMod.LOGGER.error("Warps directory ({}) doesn't exist", warpsDir);
            return;
        }
        Map<String, Warp> warps = new HashMap<>();
        try (var files = Files.list(warpsDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith(".yml")) return;
                String warpName = fileName.substring(0, fileName.length() - 4);
                try {
                    parseLocation(server, parseUserData(path)).ifPresent(location -> warps.put(warpName, new Warp(location)));
                } catch (IOException e) {
                    EssentialsMod.LOGGER.error("An error occurred while handling warp file {}", path, e);
                }
            });
        } catch (IOException e) {
            EssentialsMod.LOGGER.error("An error occurred while importing warps", e);
            return;
        }
        if (!warps.isEmpty()) {
            DataStorage.serverData().getWarps().putAll(warps);
            EssentialsMod.LOGGER.info("Warps data imported, imported {} warps!", warps.size());
        }
    }

    private void importSpawn(MinecraftServer server, Path spawnFile) {
        if (!Files.exists(spawnFile)) return;
        try {
            parseLocation(server, parseUserData(spawnFile)).ifPresent(location -> {
                ServerData serverData = DataStorage.serverData();
                if (serverData.getSpawn() == null) {
                    serverData.setSpawn(location);
                    EssentialsMod.LOGGER.info("Spawn data imported!");
                } else {
                    EssentialsMod.LOGGER.warn("Skipped spawn import, a spawn location is already set!");
                }
            });
        } catch (IOException e) {
            EssentialsMod.LOGGER.error("An error occurred while handling the spawn file {}", spawnFile, e);
        }
    }

    private Map<String, Location> parseHomes(MinecraftServer server, Map<String, Object> data) {
        Map<String, Location> homes = new HashMap<>();
        if (!(data.get("homes") instanceof Map<?, ?> homesData)) return homes;
        for (Map.Entry<?, ?> entry : homesData.entrySet()) {
            String homeName = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> homeData)) continue;
            parseLocation(server, homeData).ifPresent(location -> homes.put(homeName, location));
        }
        return homes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseUserData(Path path) throws IOException {
        Map<String, Object> data;
        try (YamlReader reader = new YamlReader(Files.newBufferedReader(path))) {
            data = (Map<String, Object>) reader.read();
        }
        return data == null ? Map.of() : data;
    }

    private Optional<Location> parseLocation(MinecraftServer server, Map<?, ?> data) {
        double x = getDouble(data, "x");
        double y = getDouble(data, "y");
        double z = getDouble(data, "z");
        float yaw = getFloat(data, "yaw");
        float pitch = getFloat(data, "pitch");
        Identifier dimension = worldToDimension(server, data.containsKey("world") ? String.valueOf(data.get("world")) : "world");
        return Optional.of(new Location(new Vec3(x, y, z), yaw, pitch, dimension));
    }

    private Identifier worldToDimension(MinecraftServer server, String name) {
        Identifier identifier = Identifier.tryParse(name);
        if (identifier != null) return identifier;
        String overworldName = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
        if (name.equals(overworldName)) return Identifier.withDefaultNamespace("overworld");
        if (name.equals(overworldName + "_nether")) return Identifier.withDefaultNamespace("the_nether");
        if (name.equals(overworldName + "_the_end")) return Identifier.withDefaultNamespace("the_end");
        EssentialsMod.LOGGER.warn("Unknown EssentialsX world '{}', assuming overworld", name);
        return Identifier.withDefaultNamespace("overworld");
    }

    private static double getDouble(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) return Double.parseDouble(string);
        return 0.0;
    }

    private static float getFloat(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.floatValue();
        if (value instanceof String string) return Float.parseFloat(string);
        return 0.0F;
    }

}