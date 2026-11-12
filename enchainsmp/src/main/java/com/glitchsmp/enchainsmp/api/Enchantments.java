package com.glitchsmp.enchainsmp.api;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Helper for reading and applying enchantments on any item stack.
 *
 * <p>All operations deliberately ignore vanilla enchantment compatibility and
 * item restrictions: EnchainSMP's contract is that any enchantment can exist
 * on any item. Compatibility is only suppressed at the anvil level
 * ({@link com.glitchsmp.enchainsmp.listener.AnvilListener}); here we simply
 * read/write the {@code Map&lt;Enchantment,Integer&gt;} on the item meta.
 */
public final class Enchantments {

  private Enchantments() {
  }

  /** Returns the level of {@code enchant} on {@code item} (0 if absent). */
  public static int level(ItemStack item, Enchantment enchant) {
    if (item == null || enchant == null || item.getType().isAir()) {
      return 0;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return 0;
    }
    return meta.getEnchants().getOrDefault(enchant, 0);
  }

  public static boolean has(ItemStack item, Enchantment enchant) {
    return level(item, enchant) > 0;
  }

  /** Level of {@code enchant} stored in an enchantment book (0 if absent). */
  public static int storedLevel(ItemStack book, Enchantment enchant) {
    if (book == null || enchant == null || book.getType().isAir()) {
      return 0;
    }
    ItemMeta meta = book.getItemMeta();
    if (!(meta instanceof EnchantmentStorageMeta storage)) {
      return 0;
    }
    return storage.getStoredEnchants().getOrDefault(enchant, 0);
  }

  /**
   * Sets {@code enchant} to {@code level} on {@code item}, forcing the write
   * even for combinations vanilla would reject.
   *
   * @param level {@code 0} or negative removes the enchantment.
   */
  public static void set(ItemStack item, Enchantment enchant, int level) {
    if (item == null || enchant == null || item.getType().isAir()) {
      return;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return;
    }
    if (level <= 0) {
      meta.removeEnchant(enchant);
    } else {
      try {
        meta.addEnchant(enchant, level);
      } catch (IllegalArgumentException ignored) {
        // addEnchant can reject unknown level ranges; ignore and keep existing.
      }
    }
    item.setItemMeta(meta);
  }

  public static void remove(ItemStack item, Enchantment enchant) {
    set(item, enchant, 0);
  }

  /**
   * Merges enchantments from {@code source} into {@code into}, keeping the
   * higher level for duplicate enchantments (never summing levels). This is
   * what makes "Efficiency IV + Efficiency V = Efficiency V, not VI".
   */
  public static void merge(ItemStack into, ItemStack source) {
    if (into == null || source == null || into.getType().isAir() || source.getType().isAir()) {
      return;
    }
    ItemMeta meta = into.getItemMeta();
    if (meta == null) {
      return;
    }
    for (Map.Entry<Enchantment, Integer> entry : source.getEnchantments().entrySet()) {
      Enchantment enchant = entry.getKey();
      int incoming = entry.getValue();
      int current = meta.getEnchants().getOrDefault(enchant, 0);
      if (incoming > current) {
        try {
          meta.addEnchant(enchant, incoming);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    into.setItemMeta(meta);
  }

  /**
   * Merges stored enchantments from an enchantment book into {@code into},
   * keeping the higher level for duplicates.
   */
  public static void mergeStored(ItemStack into, ItemStack book) {
    if (into == null || book == null || into.getType().isAir() || book.getType().isAir()) {
      return;
    }
    ItemMeta meta = book.getItemMeta();
    if (!(meta instanceof EnchantmentStorageMeta storage)) {
      return;
    }
    ItemMeta target = into.getItemMeta();
    if (target == null) {
      return;
    }
    for (Map.Entry<Enchantment, Integer> entry : storage.getStoredEnchants().entrySet()) {
      Enchantment enchant = entry.getKey();
      int incoming = entry.getValue();
      int current = target.getEnchants().getOrDefault(enchant, 0);
      if (incoming > current) {
        try {
          target.addEnchant(enchant, incoming);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    into.setItemMeta(target);
  }
}
