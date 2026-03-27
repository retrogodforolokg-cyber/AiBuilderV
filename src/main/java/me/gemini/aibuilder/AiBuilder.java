package me.gemini.aibuilder;

import org.bukkit.plugin.java.JavaPlugin;

public class AiBuilder extends JavaPlugin {
    @Override
    public void onEnable() {
        getCommand("goai").setExecutor(new GoAiCommand());
        getLogger().info("AiBuilder enabled!");
    }
}
