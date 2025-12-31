package ru.florestdev.schematicHelper;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.world.block.BlockTypes;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class SchematicHelper extends JavaPlugin {

    private HttpClient httpClient;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            getLogger().severe("WorldEdit not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        File schematics = new File(getDataFolder(), "schematics");
        if (!schematics.exists()) schematics.mkdirs();

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        getLogger().info("SchematicHelper enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(msg("only-player")));
            return true;
        }

        if (!player.hasPermission("schematichelper.paste")) {
            player.sendMessage(color(msg("no-permission")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(color(msg("usage")));
            return true;
        }

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        if (args.length >= 4) {
            try {
                x = Integer.parseInt(args[1]);
                y = Integer.parseInt(args[2]);
                z = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(color(msg("invalid-coords")));
                return true;
            }
        }

        String input = args[0];

        if (input.startsWith("http://") || input.startsWith("https://")) {
            downloadAndPaste(player, input, x, y, z);
        } else {
            File file = new File(getDataFolder(), "schematics/" + input + ".schem");
            if (!file.exists()) {
                player.sendMessage(color(msg("not-found")));
                return true;
            }
            pasteAsync(player, file, x, y, z);
        }

        return true;
    }

    // ===================== DOWNLOAD =====================

    private void downloadAndPaste(Player player, String url, int x, int y, int z) {

        sendBar(player, msg("download-start"));

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

                int status = response.statusCode();

                if (status != 200) {
                    handleHttpError(player, status);
                    return;
                }

                String fileName = new File(URI.create(url).getPath()).getName();
                if (!fileName.endsWith(".schem")) {
                    player.sendMessage(color(msg("invalid-format")));
                    return;
                }

                File outFile = new File(getDataFolder(), "schematics/" + fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(response.body());
                }

                Bukkit.getScheduler().runTask(this,
                        () -> pasteAsync(player, outFile, x, y, z));

            } catch (java.net.http.HttpTimeoutException e) {
                player.sendMessage(color(msg("timeout")));
            } catch (IOException e) {
                player.sendMessage(color(msg("network-error")));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                player.sendMessage(color(msg("download-error")));
            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage(color(msg("download-error")));
            }
        });
    }

    private void handleHttpError(Player player, int status) {
        String key = switch (status) {
            case 403 -> "http-403";
            case 404 -> "http-404";
            case 500, 502, 503 -> "http-5xx";
            default -> "http-other";
        };
        player.sendMessage(color(msg(key).replace("%code%", String.valueOf(status))));
    }

    // ===================== PASTE =====================

    private void pasteAsync(Player player, File file, int x, int y, int z) {

        sendBar(player, msg("paste-start"));

        new BukkitRunnable() {

            Clipboard clipboard;
            int currentY = 0;
            int maxY;

            @Override
            public void run() {
                try {
                    if (clipboard == null) {
                        ClipboardReader reader =
                                ClipboardFormats.findByFile(file)
                                        .getReader(new FileInputStream(file));
                        clipboard = reader.read();
                        maxY = clipboard.getRegion().getHeight();
                    }

                    if (currentY >= maxY) {
                        sendBar(player, msg("done"));
                        cancel();
                        return;
                    }

                    World world = BukkitAdapter.adapt(player.getWorld());

                    try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
                        ClipboardHolder holder = new ClipboardHolder(clipboard);
                        Operation op = holder.createPaste(session)
                                .to(BlockVector3.at(x, y + currentY, z))
                                .ignoreAirBlocks(true)
                                .build();
                        Operations.complete(op);
                    }

                    int percent = (int) ((currentY / (double) maxY) * 100);
                    sendBar(player, msg("progress")
                            .replace("%percent%", String.valueOf(percent)));

                    getLogger().info("Pasted layer Y=" + (y + currentY));
                    currentY++;

                } catch (Exception e) {
                    e.printStackTrace();
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // ===================== UTILS =====================

    private void sendBar(Player player, String text) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(color(text))
        );
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "Message not found");
    }

    private String color(String s) {
        return s.replace("&", "§");
    }
}