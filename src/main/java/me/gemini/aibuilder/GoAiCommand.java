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
    // ИСПОЛЬЗУЕМ СТАБИЛЬНУЮ ВЕРСИЮ V1 И FLASH
    private final String API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + API_KEY;
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
                textPart.addProperty("text", "Generate Minecraft 1.21 blocks for: " + prompt + ". ONLY JSON array: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"STONE\"}]. No text.");

                JsonArray parts = new JsonArray();
                parts.add(textPart);
                JsonObject content = new JsonObject();
                content.add("parts", parts);
                JsonArray contents = new JsonArray();
                contents.add(content);
                JsonObject root = new JsonObject();
                root.add("contents", contents);

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
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        if (json.has("error")) {
                            player.sendMessage("§cОшибка API: " + json.getAsJsonObject("error").get("message").getAsString());
                            return;
                        }

                        String rawText = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                                .get("text").getAsString();

                        Matcher m = Pattern.compile("\\[[\\s\\S]*\\]").matcher(rawText);
                        if (!m.find()) throw new Exception("Массив не найден");

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

                    } catch (Exception e) {
                        player.sendMessage("§cОшибка обработки. Попробуй еще раз.");
                        System.out.println("DEBUG: " + body);
                    }
                });
            } catch (Exception e) {
                player.sendMessage("§cОшибка связи: " + e.getMessage());
            }
        });
        return true;
    }
}
