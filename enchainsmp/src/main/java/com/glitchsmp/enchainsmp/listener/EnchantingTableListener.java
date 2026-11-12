package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.EnchainSMP;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

/**
 * Completely disables enchanting tables. {@link EnchantItemEvent} is cancelled
 * so no item can ever be enchanted through a table. Anvils
 * ({@link AnvilListener}) remain fully usable.
 */
public final class EnchantingTableListener implements Listener {

  private final EnchainSMP plugin;

  public EnchantingTableListener(EnchainSMP plugin) {
    this.plugin = plugin;
  }

  @EventHandler(ignoreCancelled = true)
  public void onEnchantItem(EnchantItemEvent event) {
    // Cancel every enchanting-table enchantment attempt.
    event.setCancelled(true);
  }
}
