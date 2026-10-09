package dev.mardroemmar.owosmp.llm;

import dev.mardroemmar.owosmp.config.LlmConfig;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmHandler {
  private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(LlmHandler.class);
  private final @NotNull AtomicReference<@Nullable LlmSession> session =
      new AtomicReference<>(null);

  /** Replace the current LLM session with a new one. This closes the old session. */
  public void replaceSession(@NotNull LlmConfig newConfig) {
    var oldSession = this.session.getAndSet(new LlmSession(newConfig));
    if (oldSession != null) {
      oldSession.close();
    }
  }

  /** Clear the current LLM history. */
  public void clearHistory() {
    var session = this.session.get();
    if (session != null) {
      session.clearHistory();
    }
  }

  /** Update the active session's config without clearing its history. */
  public void updateConfig(@NotNull LlmConfig config) {
    var session = this.session.get();
    if (session != null) {
      session.updateConfig(config);
    }
  }

  public void close() {
    var oldSession = this.session.getAndSet(null);
    if (oldSession != null) {
      oldSession.close();
    }
  }

  public void register() {
    ServerPlayerEvents.JOIN.register(
        player -> {
          var session = this.session.get();
          if (session != null) {
            session.handlePlayerConnection(player.level(), player, true);
          }
        });
    ServerPlayerEvents.LEAVE.register(
        player -> {
          var session = this.session.get();
          if (session != null) {
            session.handlePlayerConnection(player.level(), player, false);
          }
        });
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, damageSource) -> {
          var session = this.session.get();
          if (session != null && entity instanceof ServerPlayer player) {
            session.handlePlayerDeath(
                player.level(), player, damageSource.getLocalizedDeathMessage(player).getString());
          }
        });
    ServerMessageEvents.CHAT_MESSAGE.register(
        (message, player, _) -> {
          var session = this.session.get();
          if (session != null) {
            var chatText = chatText(message);
            if (chatText != null && !chatText.isBlank()) {
              session.handleChatMessage(player.level(), player, chatText);
            }
          }
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(_ -> this.close());
  }

  /** Extract the raw chat content from a player. */
  private @Nullable String chatText(@NotNull PlayerChatMessage msg) {
    var unsignedContent = msg.unsignedContent() == null ? "" : msg.unsignedContent().getString();
    if (!unsignedContent.isBlank()) {
      return unsignedContent;
    }
    var decoratedContent = msg.decoratedContent().getString();
    if (!decoratedContent.isBlank()) {
      return decoratedContent;
    }
    return msg.signedContent().isBlank() ? null : msg.signedContent();
  }
}
