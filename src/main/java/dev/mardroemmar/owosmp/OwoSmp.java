package dev.mardroemmar.owosmp;

import dev.mardroemmar.owosmp.config.ConfigHelper;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import dev.mardroemmar.owosmp.llm.AiCommands;
import dev.mardroemmar.owosmp.llm.LlmHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class OwoSmp implements ModInitializer {
  @Override
  public void onInitialize() {
    GameRules.register();

    ConfigHelper.getConfig(); // Ensure config is loaded on startup

    LlmHandler llmHandler = new LlmHandler();
    llmHandler.register();
    var llmConfig = ConfigHelper.getConfig().llm();
    if (llmConfig != null) {
      llmHandler.replaceSession(llmConfig);
    }
    AiCommands aiCommands = new AiCommands(llmHandler);
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, _, _) -> {
          aiCommands.register(dispatcher);
        });
  }
}
