package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Anvil interaction.
 *
 * <p>Vanilla anvils refuse incompatible enchantment combinations and cap how
 * enchantments combine. EnchainSMP overrides the result so that:
 * <ul>
 *   <li>Any enchantment can be applied to any item (compatibility ignored).</li>
 *   <li>Existing enchantments and item properties (name, lore, NBT, durability
 *       repair computed by the server) are preserved.</li>
 *   <li>Levels are never summed - the higher level wins (Efficiency IV + V =
 *       V, not VI).</li>
 *   <li>No enchantments are silently discarded.</li>
 * </ul>
 */
public final class AnvilListener implements Listener {

  private final EnchainSMP plugin;

  public AnvilListener(EnchainSMP plugin) {
    this.plugin = plugin;
  }

  @EventHandler
  public void onPrepareAnvil(PrepareAnvilEvent event) {
    AnvilInventory inventory = event.getInventory();
    ItemStack target = inventory.getItem(0);
    ItemStack sacrifice = inventory.getItem(1);

    // Nothing to enchant.
    if (target == null || target.getType().isAir()) {
      event.setResult(null);
      return;
    }

    ItemStack result = event.getResult();

    if (result == null) {
      // The server refuses this combination (e.g. incompatible enchantments,
      // or incompatible materials). We only force a result when the sacrifice
      // is an enchanted book, which is the standard way to apply enchantments.
      if (sacrifice == null || sacrifice.getType().isAir()
          || sacrifice.getType() != Material.ENCHANTED_BOOK) {
        event.setResult(null);
        return;
      }
      result = target.clone();
    }

    // Start from the server-computed result (preserves repair/durability and
    // any compatible enchantments it already calculated), then layer the
    // sacrifice's enchantments on top, forcing compatibility.
    ItemMeta meta = result.getItemMeta();
    if (meta == null) {
      meta = target.getItemMeta();
    }
    if (meta == null) {
      event.setResult(null);
      return;
    }

    int added = mergeSacrifice(meta, sacrifice);
    result.setItemMeta(meta);
    result.setAmount(1);

    event.setResult(result);

    // Keep the anvil affordable so "too expensive" never blocks a legitimate
    // combination. Cost scales lightly with the number of enchantments added.
    try {
      event.setRepairCost(Math.max(1, added));
    } catch (NoSuchMethodError ignored) {
    }
    try {
      event.setMaterialCost(0);
    } catch (NoSuchMethodError ignored) {
    }
  }

  /**
   * Copies enchantments from {@code sacrifice} into {@code meta}, keeping the
   * higher level for duplicates and ignoring compatibility restrictions.
   *
   * @return the number of enchantment entries applied.
   */
  private int mergeSacrifice(ItemMeta meta, ItemStack sacrifice) {
    if (sacrifice == null || sacrifice.getType().isAir()) {
      return 0;
    }
    int added = 0;
    if (sacrifice.getType() == Material.ENCHANTED_BOOK) {
      ItemMeta bookMeta = sacrifice.getItemMeta();
      if (bookMeta instanceof EnchantmentStorageMeta storage) {
        for (Map.Entry<Enchantment, Integer> entry : storage.getStoredEnchants().entrySet()) {
          Enchantment enchant = entry.getKey();
          int incoming = entry.getValue();
          int current = meta.getEnchants().getOrDefault(enchant, 0);
          if (incoming > current) {
            try {
              meta.addEnchant(enchant, incoming);
              added++;
            } catch (IllegalArgumentException ignored) {
            }
          }
        }
      }
    } else {
      for (Map.Entry<Enchantment, Integer> entry : sacrifice.getEnchantments().entrySet()) {
        Enchantment enchant = entry.getKey();
        int incoming = entry.getValue();
        int current = meta.getEnchants().getOrDefault(enchant, 0);
        if (incoming > current) {
          try {
            meta.addEnchant(enchant, incoming);
            added++;
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
    return added;
  }
}
