package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import com.glitchsmp.enchainsmp.mechanic.BlastCraterManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FireworkRocket;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which item launched each projectile so damage enchantments (Power,
 * Punch, Flame, Piercing, Channeling, Impaling) can be applied at impact time
 * even if the player switches items meanwhile.
 *
 * <p>Also implements the Crossbow Infinity fix: ammunition is regenerated in
 * the exact slot it was consumed from (arrows as arrows, fireworks as
 * fireworks, main hand or off hand), never duplicating or losing items.
 */
public final class ProjectileListener implements Listener {

  /** Projectile entity id -> item stack that launched it. */
  public static final Map<Integer, ItemStack> SOURCE = new ConcurrentHashMap<>();

  private final EnchainSMP plugin;
  private final BlastCraterManager craters;

  public ProjectileListener(EnchainSMP plugin) {
    this.plugin = plugin;
    this.craters = plugin.getBlastCraterManager();
  }

  /** CombatListener reads the source item that fired a projectile. */
  public static ItemStack getSource(Projectile projectile) {
    return SOURCE.get(projectile.getEntityId());
  }

  @EventHandler
  public void onProjectileLaunch(ProjectileLaunchEvent event) {
    recordSource(event.getProjectile(), event.getProjectile().getShooter());
    maybeRegenerateInfinityAmmo(event.getProjectile());
  }

  @EventHandler
  public void onEntityShootBow(EntityShootBowEvent event) {
    recordSource(event.getProjectile(), event.getEntity());
  }

  @EventHandler
  public void onProjectileHit(ProjectileHitEvent event) {
    Projectile projectile = event.getEntity();
    ItemStack source = SOURCE.get(projectile.getEntityId());
    if (source == null) {
      return;
    }
    int level = Enchantments.level(source, org.bukkit.enchantments.Enchantment.BLAST_PROTECTION);
    if (level > 0) {
      // Blast Protection craters only come from projectile impacts.
      int radius = Math.min(4, 2 + level);
      craters.createCrater(projectile.getLocation(), radius);
    }
  }

  private void recordSource(Projectile projectile, Entity shooter) {
    if (!(shooter instanceof Player player)) {
      return;
    }
    ItemStack weapon = weaponInHand(player);
    SOURCE.put(projectile.getEntityId(),
        weapon == null || weapon.getType().isAir() ? null : weapon.clone());
  }

  /** The actual shooting weapon (bow / crossbow / trident), preferring it over a plain item. */
  private static ItemStack weaponInHand(Player player) {
    ItemStack main = player.getInventory().getItemInMainHand();
    ItemStack off = player.getInventory().getItemInOffHand();
    ItemStack candidate = main == null || main.getType().isAir() ? off : main;
    if (candidate != null && !candidate.getType().isAir()
        && (candidate.getType() == Material.BOW
            || candidate.getType() == Material.CROSSBOW
            || candidate.getType() == Material.TRIDENT)) {
      return candidate;
    }
    return main == null || main.getType().isAir() ? off : main;
  }

  /* ---------------------------------------------------------------- crossbow infinity */

  private void maybeRegenerateInfinityAmmo(Projectile projectile) {
    Entity shooter = projectile.getShooter();
    if (!(shooter instanceof Player player)) {
      return;
    }
    if (!isCrossbowWithInfinity(player)) {
      return; // bows already keep their arrow via vanilla Infinity; only crossbows need regen.
    }
    Material ammo = projectile instanceof FireworkRocket ? Material.FIREWORK_ROCKET : Material.ARROW;
    new BukkitRunnable() {
      @Override
      public void run() {
        regenerateAmmo(player, ammo);
      }
    }.runTaskLater(plugin, 1L);
  }

  private boolean isCrossbowWithInfinity(Player player) {
    ItemStack main = player.getInventory().getItemInMainHand();
    ItemStack off = player.getInventory().getItemInOffHand();
    return (main != null && main.getType() == Material.CROSSBOW
        && Enchantments.has(main, org.bukkit.enchantments.Enchantment.INFINITY))
        || (off != null && off.getType() == Material.CROSSBOW
        && Enchantments.has(off, org.bukkit.enchantments.Enchantment.INFINITY));
  }

  /**
   * Regenerates one piece of {@code ammo} into the slot it was consumed from,
   * preserving type and slot, never duplicating or losing items.
   */
  private void regenerateAmmo(Player player, Material ammo) {
    PlayerInventory inv = player.getInventory();
    ItemStack main = inv.getItemInMainHand();
    ItemStack off = inv.getItemInOffHand();

    // Pick the ammunition slot: the hand not holding a crossbow, preferring a
    // slot that already holds this ammo type or is empty.
    boolean offhand;
    ItemStack target;
    ItemStack other = inv.getItemInMainHand();
    if (other != null && other.getType() == Material.CROSSBOW) {
      // Crossbow in main hand -> ammo slot is the off hand.
      offhand = true;
      target = off;
    } else if (off != null && off.getType() == Material.CROSSBOW) {
      // Crossbow in off hand -> ammo slot is the main hand.
      offhand = false;
      target = main;
    } else if (off == null || off.getType().isAir() || off.getType() == ammo) {
      offhand = true;
      target = off;
    } else {
      offhand = false;
      target = main;
    }

    ItemStack stack;
    if (target != null && !target.getType().isAir() && target.getType() == ammo
        && target.getAmount() < target.getMaxStackSize()) {
      stack = target.clone();
      stack.setAmount(target.getAmount() + 1);
    } else if (target != null && (target.getType().isAir() || target.getType() == ammo)) {
      stack = new ItemStack(ammo, 1);
    } else {
      stack = null;
    }

    if (stack != null) {
      if (offhand) {
        inv.setItemInOffHand(stack);
      } else {
        inv.setItemInMainHand(stack);
      }
      return;
    }

    // No usable hand slot: stack into any existing pile of the same type, any
    // empty inventory slot, or drop on the ground so the item is never lost.
    ItemStack addition = new ItemStack(ammo, 1);
    boolean dropped = false;
    for (ItemStack leftover : inv.addItem(addition).values()) {
      if (leftover != null && leftover.getAmount() > 0) {
        dropped = true;
      }
    }
    if (dropped) {
      Location loc = player.getLocation();
      loc.getWorld().dropItemNaturally(loc, addition);
    }
  }
}
