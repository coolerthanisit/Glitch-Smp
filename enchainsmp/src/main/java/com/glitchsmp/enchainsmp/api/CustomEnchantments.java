package com.glitchsmp.enchainsmp.api;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.ItemStack;

/**
 * Registers enchantments that are not part of vanilla for this version.
 *
 * <p>Vanilla enchantments (Sharpness, Density, etc.) are already registered by
 * the server. Lunge is not a vanilla enchantment, so it is registered here
 * under the {@code enchainsmp:lunge} key so it can be stored on items and read
 * back by {@link Enchantments#level}.
 */
public final class CustomEnchantments {

  private CustomEnchantments() {
  }

  /** The custom Lunge enchantment instance (levels 1 only). */
  public static final Enchantment LUNGE = new LungeEnchantment();

  /** Register every custom enchantment with the server. */
  public static void register() {
    Enchantment.registerEnchantment(LUNGE);
  }

  /** Lunge: left-click anywhere (even air) to launch toward facing. */
  private static final class LungeEnchantment extends Enchantment {

    LungeEnchantment() {
      super(NamespacedKey.fromString("enchainsmp:lunge"));
    }

    @Override
    public boolean canEnchantItem(ItemStack item) {
      return true; // Lunge may go on any item.
    }

    @Override
    public boolean conflictsWith(Enchantment other) {
      return false; // Never conflicts; preserve incompatible combinations.
    }

    @Override
    public EnchantmentTarget getItemTarget() {
      return EnchantmentTarget.ALL;
    }

    @Override
    public int getStartLevel() {
      return 1;
    }

    @Override
    public int getMaxLevel() {
      return 1;
    }

  }
}
