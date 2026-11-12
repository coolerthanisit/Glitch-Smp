package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerPickupItemExpEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Entity-related enchantments: Mending, Unbreaking, Looting.
 */
public final class EntityListener implements Listener {

  private static final ThreadLocalRandom R = ThreadLocalRandom.current();

  @EventHandler
  public void onEntityDeath(EntityDeathEvent event) {
    LivingEntity entity = event.getEntity();
    Player killer = entity.getKiller();
    if (killer == null) {
      return;
    }
    ItemStack weapon = killer.getInventory().getItemInMainHand();
    int looting = Enchantments.level(weapon, org.bukkit.enchantments.Enchantment.LOOTING);
    if (looting <= 0 || event.getDrops() == null) {
      return;
    }
    java.util.List<ItemStack> original = new java.util.ArrayList<>(event.getDrops());
    event.getDrops().clear();
    for (ItemStack drop : original) {
      if (drop == null || drop.getType().isAir()) {
        continue;
      }
      event.getDrops().add(drop);
      // Bonus copies: up to `looting` extra per drop stack.
      int bonus = R.nextInt(looting + 1);
      for (int i = 0; i < bonus; i++) {
        event.getDrops().add(drop.clone());
      }
    }
  }

  @EventHandler
  public void onPlayerItemDamage(PlayerItemDamageEvent event) {
    ItemStack item = event.getItem();
    int level = Enchantments.level(item, org.bukkit.enchantments.Enchantment.UNBREAKING);
    if (level > 0) {
      // Vanilla-ish: 100/(level+1) percent chance to consume no durability.
      if (R.nextInt(100) < 100 / (level + 1)) {
        event.setCancelled(true);
      }
    }
  }

  /**
   * Mending: redirect experience orbs to repair damaged Mending items instead
   * of adding experience to the player (~2 XP per durability point).
   */
  @EventHandler
  public void onPlayerPickupItemExp(PlayerPickupItemExpEvent event) {
    Player player = event.getPlayer();
    int xp = event.getExperience();
    if (xp <= 0) {
      return;
    }
    int repairPoints = xp / 2;
    if (repairPoints <= 0) {
      return;
    }
    event.setExperience(0); // XP is spent on repairs, not levels.

    PlayerInventory inv = player.getInventory();
    // Armor first, then hands, then inventory (vanilla picks a random mending item).
    java.util.List<ItemStack> candidates = new java.util.ArrayList<>();
    for (ItemStack piece : inv.getArmorContents()) {
      if (piece != null) candidates.add(piece);
    }
    ItemStack main = inv.getItemInMainHand();
    ItemStack off = inv.getItemInOffHand();
    if (main != null) candidates.add(main);
    if (off != null) candidates.add(off);
    for (ItemStack stack : inv.getContents()) {
      if (stack != null) candidates.add(stack);
    }

    for (ItemStack item : candidates) {
      if (repairPoints <= 0) break;
      if (item.getType().isAir()) continue;
      if (Enchantments.level(item, org.bukkit.enchantments.Enchantment.MENDING) <= 0) continue;
      int damage = item.getDamage();
      if (damage <= 0) continue;
      int fix = Math.min(repairPoints, damage);
      item.setDamage(damage - fix);
      repairPoints -= fix;
    }
  }
}
