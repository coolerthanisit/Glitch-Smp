package com.glitchsmp.enchainsmp.mechanic;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Lunge: left-click anywhere (even in air) to launch horizontally toward the
 * player's facing direction.
 *
 * <ul>
 *   <li>Costs exactly 1 hunger point and 1 durability per activation.</li>
 *   <li>No cooldown on normal items.</li>
 *   <li>Spears keep their normal Jab cooldown (EnchainSMP does not add spears,
 *       so this is a no-op here).</li>
 * </ul>
 */
public final class LungeManager {

  private final EnchainSMP plugin;

  public LungeManager(EnchainSMP plugin) {
    this.plugin = plugin;
  }

  public void dispose() {
  }

  /**
   * Call from PlayerListener on left-click. Returns true if a lunge was
   * performed (event should be cancelled so no normal attack happens).
   */
  public boolean tryLunge(Player player, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    if (Enchantments.level(item, com.glitchsmp.enchainsmp.api.CustomEnchantments.LUNGE) <= 0) {
      return false;
    }

    Vector dir = player.getLocation().getDirection();
    dir.setY(0);
    if (dir.lengthSquared() == 0) {
      dir = new Vector(1, 0, 0);
    } else {
      dir.normalize();
    }
    // Horizontal launch with a small upward nudge so the player doesn't stall.
    Vector velocity = dir.multiply(1.6);
    velocity.setY(0.25);
    player.setVelocity(velocity);

    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.2f);

    // Cost: exactly 1 hunger point and 1 durability.
    int food = player.getFoodLevel();
    if (food > 0) {
      player.setFoodLevel(food - 1);
    }
    if (item.getMaxDurability() > 0) {
      item.damage(1, player);
    }
    return true;
  }
}
