package com.glitchsmp.enchainsmp.mechanic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shield bypass: melee attacks and projectiles ignore shields so the hit
 * lands for full damage.
 */
public final class ShieldBypassManager {

  /** Make a shielded player stop blocking so the incoming hit lands fully. */
  public void bypass(LivingEntity victim) {
    if (victim instanceof Player player && player.isBlocking()) {
      player.setBlocking(false);
    }
  }

  public boolean shouldBypass(ItemStack source) {
    return source != null
        && !source.getType().isAir()
        && com.glitchsmp.enchainsmp.api.Enchantments.has(source, org.bukkit.enchantments.Enchantment.PIERCING);
  }

  public void dispose() {
    // nothing to clean up
  }
}
