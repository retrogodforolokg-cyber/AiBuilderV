package me.gemini.aibuilder;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.regex.*;

public class GoAiCommand implements CommandExecutor {

    private final String API_KEY = "AIzaSyD4ZaA-ED5-NIftpWpDUNjQREwI1THzMgI";
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;
    private final Plugin plugin = Bukkit.getPluginManager().getPlugin("AiBuilder");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            player.sendMessage("§cИспользование: /goai <что построить>");
            return true;
        }

        String userRequest = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eСвязываюсь с архитектором...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Создаем запрос максимально просто
                String prompt = "Create a Minecraft schematic for: " + userRequest + 
                                ". Output ONLY a JSON array like: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"STONE\"}]. " +
                                "Use block names from Minecraft 1.21. No text before or after.";
                
                String jsonInput = "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt + "\"}]}]}";

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonInput))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String responseBody = response.body();

                Bukkit.getScheduler().runTask(plugin, () -> handleResponse(player, responseBody));

            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cОшибка связи: " + e.getMessage()));
            }
        });
        return true;
    }

    private void handleResponse(Player player, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            // Проверка на ошибки API
            if (json.has("error")) {
                player.sendMessage("§cAPI Error: " + json.getAsJsonObject("error").get("message").getAsString());
                return;
            }

            // Проверка на наличие контента
            if (!json.has("candidates")) {
                player.sendMessage("§cИИ не ответил. Попробуй другой запрос.");
                return;
            }

            String text = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

            // Ищем JSON-массив внутри любого текста
            Matcher matcher = Pattern.compile("\\[[\\s\\S]*\\]").matcher(text);
            if (!matcher.find()) {
                player.sendMessage("§cИИ прислал текст вместо схемы. Попробуй еще раз.");
                return;
            }

            JsonArray blocks = JsonParser.parseString(matcher.group()).getAsJsonArray();
            Location startLoc = player.getLocation();
            int builtCount = 0;

            for (JsonElement el : blocks) {
                try {
                    JsonObject b = el.getAsJsonObject();
                    Material mat = Material.matchMaterial(b.get("type").getAsString().toUpperCase());
                    if (mat == null) mat = Material.OAK_PLANKS;
                    
                    if (mat.isBlock()) {
                        startLoc.clone().add(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt())
                                .getBlock().setType(mat);
                        builtCount++;
                    }
                } catch (Exception ignored) {}
            }

            player.sendMessage("§a§l[ИИ] Готово! §fРазмещено блоков: §e" + builtCount);

        } catch (Exception e) {
            player.sendMessage("§cОшибка обработки: " + e.getMessage());
            // Вывод в лог для отладки
            Bukkit.getLogger().warning("[AiBuilder] Response was: " + body);
        }
    }
}
