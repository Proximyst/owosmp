package dev.mardroemmar.owosmp.minecarts;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.jetbrains.annotations.NotNull;

/**
 * Proxy to let {@link AbstractMinecart} communicate with {@link ServerEntity}.
 *
 * @author aurieh
 */
public interface MinecartBehaviorProxy {
  @NotNull
  NewMinecartBehavior owosmp$syncProxy();
}
