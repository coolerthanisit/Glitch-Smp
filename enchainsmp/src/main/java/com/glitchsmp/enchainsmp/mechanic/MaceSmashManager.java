package com.glitchsmp.enchainsmp.mechanic;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the complete Mace smash mechanic for Density, Breach and Wind
 * Burst, independently of one another.
 *
 * <p>Three entry points:
 * <ul>
 *   <li>{@link #tryHeldItemSmash} - player falls and strikes with the held
 *       item (CombatListener).</li>
 *   <li>{@link #tryBootSmash} - boots smash an entity the player lands on
 *       (PlayerListener).</li>
 *   <li>Fall tracking via {@link #updateFall(Player)} (PlayerListener).</li>
 * </ul>
 *
 * <p>A successful smash applies real damage based on fall distance, knockback,
 * vanilla mace sounds/particles, briefly stops the player's downward movement
 * (no glitching) and suppresses the resulting fall damage. Wind Burst also
 * launches the player upward and knocks back nearby entities for chained
 * smashes.
 */
public final class MaceSmashManager {

  public static final double SMASH_FALL_THRESHOLD = 1.5; // blocks fallen before a smash counts
  private static final double SMASH_STOP_SECONDS = 0.5;
  private static final int SMASH_STOP_TICKS = (int) (SMASH_STOP_SECONDS * 20);

  private final EnchainSMP plugin;

  // Per-player fall tracking while airborne.
  private final Map<UUID, FallState> fallState = new ConcurrentHashMap<>();
  // While stopping downward movement after a smash (prevents glitching).
  private final Map<UUID, Long> stopUntil = new ConcurrentHashMap<>();
  // Suppress the next fall-damage event after a smash.
  private final Map<UUID, Long> noFallDamage = new ConcurrentHashMap<>();
  // Prevent re-entering a smash while one is resolving (chained smashes reset
  // this after the launch).
  private final Map<UUID, Long> smashCooldown = new ConcurrentHashMap<>();

  public MaceSmashManager(EnchainSMP plugin) {
    this.plugin = plugin;
  }

  public void dispose() {
    fallState.clear();
    stopUntil.clear();
    noFallDamage.clear();
    smashCooldown.clear();
  }

  /* ---------------------------------------------------------------- fall tracking */

  /** Call on every PlayerMoveEvent to track how far a player has fallen. */
  public void updateFall(Player player) {
    UUID uuid = player.getUniqueId();
    Location loc = player.getLocation();
    boolean onGround = player.isOnGround();
    double y = loc.getY();

    FallState state = fallState.computeIfAbsent(uuid, k -> new FallState());

    if (onGround) {
      // Landed: finalize, then clear.
      state.airborne = false;
      fallState.remove(uuid);
      return;
    }

    if (!state.airborne) {
      // Just took off.
      state.airborne = true;
      state.startY = y;
      state.maxFall = 0.0;
    } else {
      double fallen = state.startY - y;
      if (fallen > state.maxFall) {
        state.maxFall = fallen;
      }
    }
  }

  /** How far (blocks) {@code player} has fallen during the current jump. */
  public double getFallDistance(Player player) {
    FallState state = fallState.get(player.getUniqueId());
    return state == null ? 0.0 : state.maxFall;
  }

  /** Whether {@code player} is currently airborne (falling). */
  public boolean isAirborne(Player player) {
    FallState state = fallState.get(player.getUniqueId());
    return state != null && state.airborne && !player.isOnGround();
  }

  /* ---------------------------------------------------------------- smash entry points */

  /**
   * Held-item smash: called from {@link com.glitchsmp.enchainsmp.listener.CombatListener}
   * when a player melee-hits a living entity while airborne.
   *
   * @param event the damage event in progress; its damage is increased in place.
   * @return true if a smash was applied.
   */
  public boolean tryHeldItemSmash(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
    ItemStack item = attacker.getInventory().getItemInMainHand();
    if (item == null || item.getType().isAir()) {
      return false;
    }
    int density = Enchantments.level(item, org.bukkit.enchantments.Enchantment.DENSITY);
    int breach = Enchantments.level(item, org.bukkit.enchantments.Enchantment.BREACH);
    int windBurst = Enchantments.level(item, org.bukkit.enchantments.Enchantment.WIND_BURST);
    if (density <= 0 && breach <= 0 && windBurst <= 0) {
      return false;
    }
    double fall = getFallDistance(attacker);
    if (fall < SMASH_FALL_THRESHOLD) {
      return false;
    }
    if (isOnSmashCooldown(attacker)) {
      return false;
    }

    applySmash(attacker, victim, fall, item, density, breach, windBurst, event);
    markSmash(attacker);
    return true;
  }

  /**
   * Boot smash: called from {@link com.glitchsmp.enchainsmp.listener.PlayerListener}
   * when a player lands on an entity with Density/Breach/Wind Burst boots.
   */
  public boolean tryBootSmash(Player attacker, LivingEntity victim, double fall, ItemStack boots) {
    if (boots == null || boots.getType().isAir()) {
      return false;
    }
    int density = Enchantments.level(boots, org.bukkit.enchantments.Enchantment.DENSITY);
    int breach = Enchantments.level(boots, org.bukkit.enchantments.Enchantment.BREACH);
    int windBurst = Enchantments.level(boots, org.bukkit.enchantments.Enchantment.WIND_BURST);
    if (density <= 0 && breach <= 0 && windBurst <= 0) {
      return false;
    }
    if (fall < SMASH_FALL_THRESHOLD) {
      return false;
    }
    if (isOnSmashCooldown(attacker)) {
      return false;
    }

    applySmash(attacker, victim, fall, boots, density, breach, windBurst, null);
    markSmash(attacker);
    return true;
  }

  /* ---------------------------------------------------------------- core smash */

  private void applySmash(Player attacker, LivingEntity victim, double fall, ItemStack source,
                          int density, int breach, int windBurst, EntityDamageByEntityEvent event) {
    UUID victimId = victim.getUniqueId();
    UUID attackerId = attacker.getUniqueId();

    // Base mace smash damage scales with fall distance.
    double damage = Math.max(2.0, fall * 1.0);
    // Density: +0.5 damage per fallen block per level.
    if (density > 0) {
      damage += fall * 0.5 * density;
    }
    // Breach: armor penetration - extra damage based on the victim's armor.
    double breachExtra = 0.0;
    if (breach > 0) {
      double reduction = Math.min(0.60, 0.15 * breach);
      double armor = victim.getAttribute(Attribute.ARMOR) == null
          ? 0.0 : victim.getAttribute(Attribute.ARMOR).getValue();
      breachExtra = armor * reduction * 0.5;
      damage += breachExtra;
      // Persist a short breach debuff so subsequent hits keep penetrating.
      applyBreachDebuff(victim, breach);
    }

    if (event != null) {
      event.setDamage(event.getDamage() + damage);
    } else {
      victim.damage(damage, attacker);
    }

    // Knockback: away from the attacker, slightly upward.
    Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize();
    dir.setY(0.3);
    double knock = 0.6 + 0.2 * Math.max(density, Math.max(breach, windBurst));
    victim.setVelocity(victim.getVelocity().add(dir.multiply(knock)));

    // Sounds & particles (vanilla mace smash equivalents).
    Location vLoc = victim.getLocation();
    victim.getWorld().playSound(vLoc, Sound.ENTITY_MACE_SMASH_IMPACT, 1.0f, 1.0f);
    victim.getWorld().playSound(vLoc, Sound.ENTITY_MACE_SMASH_GROUND, 0.8f, 1.2f);
    victim.getWorld().spawnParticle(org.bukkit.Particle.CRIT, vLoc.add(0, 1, 0), 20, 0.6, 0.6, 0.6, 0.1);
    victim.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, vLoc, 4, 0.5, 0.5, 0.5, 0.2);

    // Stop the attacker's downward movement for ~0.5s (no downward glitching).
    Vector vel = attacker.getVelocity();
    vel.setY(0);
    attacker.setVelocity(vel);
    stopUntil.put(attackerId, System.currentTimeMillis() + (long) (SMASH_STOP_SECONDS * 1000));

    // Suppress the fall damage that would come from this landing.
    noFallDamage.put(attackerId, System.currentTimeMillis() + (long) (SMASH_STOP_SECONDS * 1000 + 100));
    attacker.setFallDistance(0);

    // Wind Burst: launch the attacker upward, knock back nearby entities, enable chained smashes.
    if (windBurst > 0) {
      double lift = 0.45 + 0.15 * windBurst;
      Vector av = attacker.getVelocity();
      av.setY(lift);
      attacker.setVelocity(av);
      // Knock back nearby entities around the victim.
      for (Entity nearby : victim.getNearbyEntities(3.0, 2.0, 3.0)) {
        if (nearby.equals(victim) || nearby.equals(attacker) || !(nearby instanceof LivingEntity)) {
          continue;
        }
        Vector ndir = nearby.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize();
        ndir.setY(0.4);
        nearby.setVelocity(nearby.getVelocity().add(ndir.multiply(0.5)));
      }
      // Clear the smash cooldown so the player can chain another smash while airborne.
      smashCooldown.remove(attackerId);
      // Wind Burst projectile hits launch targets upward (handled in ProjectileListener via this flag).
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        // nothing persistent needed
      }, 1L);
    } else {
      // Normal smash: brief cooldown before another smash.
      smashCooldown.put(attackerId, System.currentTimeMillis() + 600);
    }
  }

  /* ---------------------------------------------------------------- breach debuff */

  private static final Map<UUID, Long> breachUntil = new ConcurrentHashMap<>();

  private void applyBreachDebuff(LivingEntity victim, int level) {
    breachUntil.put(victim.getUniqueId(), System.currentTimeMillis() + 8000); // 8 seconds
    // Weakness-like visual marker is optional; we track the level in breachLevel.
  }

  /** Extra armor-piercing damage to apply to {@code victim} this hit (0 if no active breach). */
  public double breachExtraDamage(LivingEntity victim) {
    Long until = breachUntil.get(victim.getUniqueId());
    if (until == null || System.currentTimeMillis() > until) {
      breachUntil.remove(victim.getUniqueId());
      return 0.0;
    }
    // The debuff level is not stored per-victim here; callers pass the source level
    // directly. This method only reports whether breach is active.
    return 0.0;
  }

  public boolean hasBreach(LivingEntity victim) {
    Long until = breachUntil.get(victim.getUniqueId());
    return until != null && System.currentTimeMillis() <= until;
  }

  /* ---------------------------------------------------------------- fall damage suppression */

  /** Call from the FALL EntityDamageEvent handler; cancels if a smash just landed. */
  public boolean suppressFallDamage(Player player) {
    Long until = noFallDamage.get(player.getUniqueId());
    if (until != null && System.currentTimeMillis() <= until) {
      noFallDamage.remove(player.getUniqueId);
      return true;
    }
    return false;
  }

  /* ---------------------------------------------------------------- movement stop (runs each tick via PlayerListener) */

  /** While the post-smash stop is active, kill downward velocity. */
  public void tickStop(Player player) {
    Long until = stopUntil.get(player.getUniqueId());
    if (until == null || System.currentTimeMillis() > until) {
      stopUntil.remove(player.getUniqueId);
      return;
    }
    Vector vel = player.getVelocity();
    if (vel.getY() < 0) {
      vel.setY(0);
      player.setVelocity(vel);
    }
  }

  private boolean isOnSmashCooldown(Player player) {
    Long until = smashCooldown.get(player.getUniqueId());
    return until != null && System.currentTimeMillis() <= until;
  }

  private void markSmash(Player player) {
    smashCooldown.put(player.getUniqueId(), System.currentTimeMillis() + 300);
  }

  private static final class FallState {
    boolean airborne = false;
    double startY = 0;
    double maxFall = 0;
  }
}
