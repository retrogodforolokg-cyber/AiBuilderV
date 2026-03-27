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
    // ИСПОЛЬЗУЕМ LATEST ВЕРСИЮ МОДЕЛИ
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + API_KEY;
    private final Gson gson = new Gson();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            player.sendMessage("§cНапиши: /goai <что построить>");
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eПроектирую... Пожалуйста, подожди.");

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
            try {
                JsonObject textPart = new JsonObject();
                textPart.addProperty("text", "You are a Minecraft 1.21 builder. Generate JSON blocks for: " + prompt + ". ONLY JSON array: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"STONE\"}]. No chat.");

                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.add("parts", parts);
                JsonArray contentsArray = new JsonArray();
                contentsArray.add(content);
                JsonObject root = new JsonObject();
                root.add("contents", contentsArray);

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(root)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
                    try {
                        handleResponse(player, body);
                    } catch (Exception e) {
                        player.sendMessage("§cОшибка обработки. Ответ сервера: " + (body.length() > 50 ? body.substring(0, 50) : body));
                    }
                });
            } catch (Exception e) {
                player.sendMessage("§cОшибка связи: " + e.getMessage());
            }
        });
        return true;
    }

    private void handleResponse(Player player, String body) throws Exception {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (json.has("error")) {
            player.sendMessage("§cGoogle Error: " + json.getAsJsonObject("error").get("message").getAsString());
            return;
        }

        String rawText = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();

        Matcher m = Pattern.compile("\\[[\\s\\S]*\\]").matcher(rawText);
        if (m.find()) {
            JsonArray blocks = JsonParser.parseString(m.group()).getAsJsonArray();
            Location loc = player.getLocation();
            int count = 0;
            for (JsonElement el : blocks) {
                JsonObject b = el.getAsJsonObject();
                Material mat = Material.matchMaterial(b.get("type").getAsString().toUpperCase());
                if (mat == null) mat = Material.STONE;
                loc.clone().add(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt()).getBlock().setType(mat);
                count++;
            }
            player.sendMessage("§a§l[ИИ] Успех! §fБлоков: §e" + count);
        } else {
            player.sendMessage("§cИИ прислал текст вместо чертежа.");
        }
    }
}
