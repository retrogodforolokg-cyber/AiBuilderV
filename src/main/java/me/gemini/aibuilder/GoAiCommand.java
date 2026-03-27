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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            player.sendMessage("§cНапиши: /goai <что построить>");
            return true;
        }

        String prompt = String.join(" ", args);
        player.sendMessage("§6[ИИ] §eПроектирую чертёж... Подожди 5-10 сек.");

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
            try {
                String response = askGemini(prompt);
                processBuild(player, response);
            } catch (Exception e) {
                player.sendMessage("§cОшибка сети: " + e.getMessage());
            }
        });
        return true;
    }

    private String askGemini(String userPrompt) throws Exception {
        String systemInstruction = "You are a Minecraft 1.21 Architect. Create: " + userPrompt + 
            ". Output ONLY a JSON array of blocks. No markdown, no intro. " +
            "Example: [{\"x\":0,\"y\":0,\"z\":0,\"type\":\"OAK_LOG\"}]. " +
            "Use ONLY official Bukkit Material names.";

        String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + systemInstruction + "\"}]}]}";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private void processBuild(Player player, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String rawText = json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();

            // Жесткая очистка: вырезаем всё, что не внутри [ ]
            Pattern pattern = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(rawText);
            
            if (!matcher.find()) {
                throw new Exception("ИИ не прислал массив блоков.");
            }
            
            JsonArray blocks = JsonParser.parseString(matcher.group()).getAsJsonArray();
            Location origin = player.getLocation();

            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> {
                int count = 0;
                for (JsonElement el : blocks) {
                    try {
                        JsonObject b = el.getAsJsonObject();
                        String materialName = b.get("type").getAsString().toUpperCase().replace(" ", "_");
                        Material m = Material.matchMaterial(materialName);
                        
                        if (m == null) m = Material.OAK_PLANKS; // Если ИИ ошибся, строим из досок
                        if (!m.isBlock()) continue;

                        origin.clone().add(
                            b.get("x").getAsInt(), 
                            b.get("y").getAsInt(), 
                            b.get("z").getAsInt()
                        ).getBlock().setType(m);
                        count++;
                    } catch (Exception ignore) {}
                }
                player.sendMessage("§a§l[ИИ] Готово! §fПостроено блоков: §e" + count);
            });
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("AiBuilder"), () -> 
                player.sendMessage("§cОшибка: " + e.getMessage())
            );
        }
    }
}
