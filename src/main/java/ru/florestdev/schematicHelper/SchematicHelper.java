package ru.florestdev.schematicHelper;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

    // ===================== COMMAND =====================

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
            pasteLayered(player, file, x, y, z);
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

                if (response.statusCode() != 200) {
                    Bukkit.getScheduler().runTask(this,
                            () -> handleHttpError(player, response.statusCode()));
                    return;
                }

                String fileName = new File(URI.create(url).getPath()).getName();
                if (!fileName.endsWith(".schem")) {
                    Bukkit.getScheduler().runTask(this,
                            () -> player.sendMessage(color(msg("invalid-format"))));
                    return;
                }

                File out = new File(getDataFolder(), "schematics/" + fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(response.body());
                }

                Bukkit.getScheduler().runTask(this,
                        () -> pasteLayered(player, out, x, y, z));

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this,
                        () -> player.sendMessage(color(msg("download-error"))));
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

    private void pasteLayered(Player player, File file, int x, int y, int z) {

        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            player.sendMessage(color(msg("invalid-format")));
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {
            clipboard = reader.read();
        } catch (IOException e) {
            player.sendMessage(color(msg("invalid-format")));
            return;
        }

        int minY = clipboard.getRegion().getMinimumPoint().y();
        int maxY = clipboard.getRegion().getMaximumPoint().y();
        int height = maxY - minY + 1;

        World world = BukkitAdapter.adapt(player.getWorld());

        sendBar(player, msg("paste-start"));

        new BukkitRunnable() {

            int currentLayer = 0;

            @Override
            public void run() {
                if (currentLayer >= height) {
                    sendBar(player, msg("done"));
                    cancel();
                    return;
                }

                try (EditSession session = WorldEdit.getInstance().newEditSession(world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);

                    // Вставка слоя через Paste
                    Operation op = holder.createPaste(session)
                            .to(BlockVector3.at(x, y, z))
                            .ignoreAirBlocks(true)
                            .build();

                    Operations.complete(op);
                } catch (Exception e) {
                    e.printStackTrace();
                    cancel();
                    return;
                }

                int percent = (int) ((currentLayer + 1) * 100.0 / height);
                sendBar(player, msg("progress").replace("%percent%", String.valueOf(percent)));

                currentLayer++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // ===================== UTILS =====================

    private void sendBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(text)));
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "Message not found");
    }

    private String color(String s) {
        return s.replace("&", "§");
    }
}
