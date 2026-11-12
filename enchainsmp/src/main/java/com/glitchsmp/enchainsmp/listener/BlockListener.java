package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Block-breaking and block-placing enchantments.
 *
 * <ul>
 *   <li>Silk Touch: mined blocks drop themselves.</li>
 *   <li>Fortune: increases applicable block drops.</li>
 *   <li>Efficiency: mining speed (approximated with haste).</li>
 *   <li>Aqua Affinity: removes the underwater mining penalty.</li>
 *   <li>Infinity on blocks: placing a block refills one of the same type so
 *       the quantity never decreases.</li>
 * </ul>
 */
public final class BlockListener implements Listener {

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    ItemStack item = player.getInventory().getItemInMainHand();
    Block block = event.getBlock();
    int silk = Enchantments.level(item, org.bukkit.enchantments.Enchantment.SILK_TOUCH);
    int fortune = Enchantments.level(item, org.bukkit.enchantments.Enchantment.FORTUNE);
    int efficiency = Enchantments.level(item, org.bukkit.enchantments.Enchantment.EFFICIENCY);
    int aqua = Enchantments.level(item, org.bukkit.enchantments.Enchantment.AQUA_AFFINITY);

    // Silk Touch takes precedence: the block drops itself.
    if (silk > 0) {
      List<ItemStack> drops = new ArrayList<>(event.getDrops());
      event.getDrops().clear();
      event.setExpToDrop(0);
      event.getDrops().add(new ItemStack(block.getType(), 1));
      // Mining speed for silk-touch tools still applies below.
    } else if (fortune > 0 && !event.getDrops().isEmpty()) {
      // Fortune: bonus copies of each drop.
      List<ItemStack> original = new ArrayList<>(event.getDrops());
      for (ItemStack drop : original) {
        if (drop == null || drop.getType().isAir()) continue;
        for (int i = 0; i < fortune; i++) {
          if (ThreadLocalRandom.current().nextInt(3) == 0) { // ~33% per level
            event.getDrops().add(drop.clone());
          }
        }
      }
    }

    // Efficiency: mining speed (approximated with a brief haste effect).
    if (efficiency > 0 && isBreakable(block)) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 20, efficiency - 1, false, false));
    }

    // Aqua Affinity: removes the underwater mining penalty.
    if (aqua > 0 && (player.isInWater() || block.getLocation().getBlock().isLiquid())) {
      player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 20, 0, false, false));
    }
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    ItemStack placed = event.getItemInHand();
    if (placed != null && Enchantments.has(placed, org.bukkit.enchantments.Enchantment.INFINITY)) {
      // Refill one block of the same type so the quantity never decreases.
      refillOne(player, placed.getType(), placed.getAmount() > 1 ? placed.getAmount() - 1 : 1);
    }
  }

  private boolean isBreakable(Block block) {
    Material type = block.getType();
    return !type.isAir() && !type.isOccluding() && type != Material.BEDROCK
        && type != Material.END_PORTAL_FRAME && type != Material.NETHER_PORTAL;
  }

  /**
   * Adds one block of {@code type} back into {@code player}'s inventory
   * (matching stack first, then empty slot, then on the ground).
   */
  private static void refillOne(Player player, Material type, int amount) {
    ItemStack addition = new ItemStack(type, Math.max(1, amount));
    boolean dropped = false;
    for (ItemStack leftover : player.getInventory().addItem(addition).values()) {
      if (leftover != null && leftover.getAmount() > 0) {
        dropped = true;
      }
    }
    if (dropped) {
      player.getWorld().dropItemNaturally(player.getLocation(), addition);
    }
  }
}
