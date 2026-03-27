package me.gemini.aibuilder;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

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

        String prompt = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eПроектирую чертеж...");

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
            try {
                String response = askGemini(prompt);
                processBuild(player, response);
            } catch (Exception e) {
                player.sendMessage("§cОшибка: " + e.getMessage());
            }
        });
        return true;
    }

    private String askGemini(String userPrompt) throws Exception {
        String system = "You are a Minecraft builder. Generate a structure: " + userPrompt + 
            ". Return ONLY a JSON array like: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"STONE\"}]. " +
            "Max 150 blocks. Use 1.21.4 Material names.";

        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("text", system);
        parts.add(text);
        part.add("parts", parts);
        contents.add(part);
        root.add("contents", contents);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(root)))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void processBuild(Player player, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String raw = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

            String cleanJson = raw.substring(raw.indexOf("["), raw.lastIndexOf("]") + 1);
            JsonArray blocks = JsonParser.parseString(cleanJson).getAsJsonArray();
            Location loc = player.getLocation();

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
                for (JsonElement el : blocks) {
                    JsonObject b = el.getAsJsonObject();
                    Material m = Material.matchMaterial(b.get("type").getAsString().toUpperCase());
                    if (m == null) m = Material.STONE;
                    loc.clone().add(b.get("x").getAsInt(), b.get("y").getAsInt(), b.get("z").getAsInt()).getBlock().setType(m);
                }
                player.sendMessage("§aГотово!");
            });
        } catch (Exception e) {
            player.sendMessage("§cОшибка чертежа.");
        }
    }
}