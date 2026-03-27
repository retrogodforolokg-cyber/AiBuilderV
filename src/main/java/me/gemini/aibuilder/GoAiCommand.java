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
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;
    private final Gson gson = new Gson();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            player.sendMessage("§cНапиши: /goai <что построить>");
            return true;
        }

        String userPrompt = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eПроектирую... Пожалуйста, подожди.");

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
            try {
                // ПРАВИЛЬНОЕ формирование JSON через объекты (исключает ошибки кавычек)
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", "Generate Minecraft 1.21 build schema for: " + userPrompt + 
                    ". Output ONLY JSON array. Example: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"STONE\"}]");

                JsonArray partsArray = new JsonArray();
                partsArray.add(textPart);

                JsonObject contentObject = new JsonObject();
                contentObject.add("parts", partsArray);

                JsonArray contentsArray = new JsonArray();
                contentsArray.add(contentObject);

                JsonObject root = new JsonObject();
                root.add("contents", contentsArray);

                String jsonPayload = gson.toJson(root);

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
                    try {
                        parseAndBuild(player, body);
                    } catch (Exception e) {
                        player.sendMessage("§cОшибка обработки: " + e.getMessage());
                        Bukkit.getLogger().severe(body); // Лог в консоль для нас
                    }
                });

            } catch (Exception e) {
                player.sendMessage("§cОшибка связи: " + e.getMessage());
            }
        });
        return true;
    }

    private void parseAndBuild(Player player, String body) throws Exception {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        
        if (json.has("error")) {
            player.sendMessage("§cОшибка API: " + json.getAsJsonObject("error").get("message").getAsString());
            return;
        }

        String rawText = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();

        Matcher m = Pattern.compile("\\[[\\s\\S]*\\]").matcher(rawText);
        if (!m.find()) {
            player.sendMessage("§cИИ не прислал чертеж. Попробуй еще раз.");
            return;
        }

        JsonArray blocks = JsonParser.parseString(m.group()).getAsJsonArray();
        Location loc = player.getLocation();
        int count = 0;

        for (JsonElement el : blocks) {
            JsonObject b = el.getAsJsonObject();
            String type = b.get("type").getAsString().toUpperCase().replace(" ", "_");
            Material mat = Material.matchMaterial(type);
            if (mat == null) mat = Material.OAK_PLANKS;

            if (mat.isBlock()) {
                loc.clone().add(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt())
                   .getBlock().setType(mat);
                count++;
            }
        }
        player.sendMessage("§a§l[ИИ] Готово! §fБлоков: §e" + count);
    }
}
