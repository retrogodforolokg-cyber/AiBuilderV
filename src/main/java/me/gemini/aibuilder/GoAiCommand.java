package me.gemini.aibuilder;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.regex.*;

public class GoAiCommand implements CommandExecutor {

    private final String API_KEY = "AIzaSyD4ZaA-ED5-NIftpWpDUNjQREwI1THzMgI";
    // ИСПРАВЛЕНО: v1beta и модель gemini-pro (флеш перестал отвечать)
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + API_KEY;
    private final Gson gson = new Gson();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            player.sendMessage("§cИспользование: /goai <что построить>");
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eЗапрос отправлен... Ждём ответ.");

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
            try {
                // Создаем запрос максимально просто через объекты
                JsonObject textPart = new JsonObject();
                // Уточняем, что ИИ не должен использовать JSON-маркеры в ответе
                textPart.addProperty("text", "You are a Minecraft 1.21 architecture assistant. Generate a building schematic for: " + prompt + ". Output ONLY a plain JSON array of blocks. Example: [{\\\"x\\\":0,\\\"y\\\":0,\\\"z\\\":0,\\\"type\\\":\\\"STONE\\\"}]. No explanations or markdown.");

                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                JsonObject root = new JsonObject();
                root.add("contents", contents);

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(25)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(root)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
                    try {
                        handleBuild(player, body);
                    } catch (Exception e) {
                        player.sendMessage("§cОшибка: " + e.getMessage());
                        System.out.println("DEBUG API RESPONSE: " + body); // Лог в консоль
                    }
                });

            } catch (Exception e) {
                player.sendMessage("§cОшибка связи: " + e.getMessage());
            }
        });
        return true;
    }

    private void handleBuild(Player player, String body) throws Exception {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        
        // Обработка ошибок от Google
        if (json.has("error")) {
            player.sendMessage("§cGoogle Error: " + json.getAsJsonObject("error").get("message").getAsString());
            return;
        }

        if (!json.has("candidates")) {
            player.sendMessage("§cИИ не ответил. Попробуй другой запрос.");
            return;
        }

        String rawText = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();

        // Умная очистка: ищем массив JSON
        Matcher m = Pattern.compile("\\[[\\s\\S]*\\]").matcher(rawText);
        if (!m.find()) {
            player.sendMessage("§cИИ не прислал чертеж. Попробуй еще раз.");
            return;
        }

        JsonArray blocks = JsonParser.parseString(m.group()).getAsJsonArray();
        Location playerLoc = player.getLocation();
        int built = 0;

        for (JsonElement el : blocks) {
            try {
                JsonObject b = el.getAsJsonObject();
                String materialName = b.get("type").getAsString().toUpperCase().replace(" ", "_");
                Material mat = Material.matchMaterial(materialName);
                if (mat == null) mat = Material.COBBLESTONE; // Камень, если ИИ ошибся в названии

                if (mat.isBlock()) {
                    playerLoc.clone().add(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt())
                            .getBlock().setType(mat);
                    built++;
                }
            } catch (Exception ignore) {}
        }
        player.sendMessage("§a§l[ИИ] Успех! §fПостроено блоков: §e" + built);
    }
}
