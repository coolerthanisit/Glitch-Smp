package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

/**
 * Fishing enchantments.
 *
 * <p>Vanilla decides fishing loot through loot tables, which Paper does not
 * expose a direct hook to rewrite per-enchantment. Luck of the Sea is therefore
 * approximated here by awarding a bonus copy of the catch on a successful reel
 * in; Lure's wait-time reduction is client-side and has no reliable server
 * hook, so it is left to vanilla timing when applied to a fishing rod.
 */
public final class FishingListener implements Listener {

  @EventHandler
  public void onPlayerFish(PlayerFishEvent event) {
    if (event.getState() != PlayerFishEvent.State.REEL_IN_FISH) {
      return;
    }
    Player player = event.getPlayer();
    ItemStack rod = player.getInventory().getItemInMainHand();
    int luck = Enchantments.level(rod, org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
    if (luck <= 0) {
      return;
    }
    // Bonus treasure copy: ~level * 10% chance to duplicate the catch.
    if (Math.random() < luck * 0.10) {
      ItemStack caught = event.getItem(); // may be null in some versions
      if (caught == null || caught.getType().isAir()) {
        caught = new ItemStack(Material.COD);
      }
      player.getWorld().dropItemNaturally(player.getLocation(), caught.clone());
    }
  }
}
