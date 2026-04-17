package me.cookie.duel.duel.instance;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class WorldInstanceManager {

    private final Path worldContainer;

    public WorldInstanceManager() {
        this.worldContainer = Bukkit.getWorldContainer().toPath();
    }

    public Path copyTemplate(Path sourceDirectory, String instanceWorldName) throws IOException {
        Path targetDirectory = worldContainer.resolve(instanceWorldName);
        if (Files.exists(targetDirectory)) {
            throw new IOException("Instance world folder already exists: " + targetDirectory);
        }

        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDirectory.relativize(dir);
                Files.createDirectories(targetDirectory.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if ("uid.dat".equalsIgnoreCase(fileName) || "session.lock".equalsIgnoreCase(fileName)) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = sourceDirectory.relativize(file);
                Files.copy(file, targetDirectory.resolve(relative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });

        return targetDirectory;
    }

    public World loadWorld(String instanceWorldName) throws IOException {
        World world = Bukkit.createWorld(new WorldCreator(instanceWorldName));
        if (world == null) {
            throw new IOException("Could not load instance world '" + instanceWorldName + "'.");
        }
        return world;
    }

    public boolean unloadWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        return world == null || Bukkit.unloadWorld(world, false);
    }

    public boolean deleteWorldDirectory(String worldName) throws IOException {
        Path directory = worldContainer.resolve(worldName);
        if (!Files.exists(directory)) {
            return true;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        return !Files.exists(directory);
    }
}
