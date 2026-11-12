package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import com.glitchsmp.enchainsmp.mechanic.MaceSmashManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Melee and projectile damage enchantments, shield bypass and Breach armor
 * penetration.
 *
 * <p>Every enchantment reads its level straight off the item that dealt the
 * damage, so it works on any item regardless of vanilla compatibility.
 */
public final class CombatListener implements Listener {

  private static final Random RANDOM = ThreadLocalRandom.current();

  private final EnchainSMP plugin;
  private final MaceSmashManager mace;

  public CombatListener(EnchainSMP plugin) {
    this.plugin = plugin;
    this.mace = plugin.getMaceSmashManager();
  }

  /* ================================================================ melee + projectile damage */

  @EventHandler
  public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
    Entity damager = event.getDamager();
    Entity victimEntity = event.getEntity();
    if (!(victimEntity instanceof LivingEntity victim)) {
      return;
    }

    // Resolve the item that dealt the damage.
    ItemStack source = resolveSourceItem(damager);
    if (source == null || source.getType().isAir()) {
      source = fallbackSource(damager);
    }
    if (source == null || source.getType().isAir()) {
      return;
    }

    boolean isPlayerAttack = damager instanceof Player;
    Player attacker = isPlayerAttack ? (Player) damager : null;

    // ---- Mace smash (held item): Density / Breach / Wind Burst.
    // If this is a falling-player smash, let MaceSmashManager augment damage in place.
    if (isPlayerAttack && !attacker.isOnGround() && mace.getFallDistance(attacker) > 0) {
      mace.tryHeldItemSmash(attacker, victim, event);
    }

    // ---- Melee enchantments (player swings an item).
    if (isPlayerAttack) {
      applyMeleeEnchantments(attacker, victim, event, source);
      return; // melee handled; projectile code below does not apply.
    }

    // ---- Projectile damage.
    if (damager instanceof Projectile projectile) {
      applyProjectileEnchantments(projectile, victim, event, source);
    }
  }

  /* ================================================================ protection & thorns (final damage) */

  @EventHandler
  public void onEntityDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof LivingEntity living)) {
      return;
    }

    int level = maxArmorEnchant(living, org.bukkit.enchantments.Enchantment.PROTECTION);
    int fireLevel = maxArmorEnchant(living, org.bukkit.enchantments.Enchantment.FIRE_PROTECTION);
    int projLevel = maxArmorEnchant(living, org.bukkit.enchantments.Enchantment.PROJECTILE_PROTECTION);
    int featherLevel = maxArmorEnchant(living, org.bukkit.enchantments.Enchantment.FEATHER_FALLING);

    double damage = event.getDamage();
    switch (event.getCause()) {
      case FALL:
        if (featherLevel > 0) {
          damage *= (1.0 - 0.12 * featherLevel); // ~12% per level, like vanilla.
        }
        break;
      case FIRE_TICK:
      case LAVA:
      case FIRE:
      case HOT_FLOOR:
        if (fireLevel > 0) {
          damage *= (1.0 - 0.08 * fireLevel);
          // Reduce burning duration.
          int ticks = living.getRemainingFire();
          int reduce = ticks * fireLevel / 5;
          if (reduce > 0) {
            living.setRemainingFire(Math.max(0, ticks - reduce));
          }
        }
        if (level > 0) {
          damage *= (1.0 - 0.04 * level);
        }
        break;
      case PROJECTILE:
      case THROWN:
      case FALLING_BLOCK:
      case DRAGON_BREATH:
        if (projLevel > 0) {
          damage *= (1.0 - 0.08 * projLevel);
        }
        if (level > 0) {
          damage *= (1.0 - 0.04 * level);
        }
        break;
      default:
        if (level > 0) {
          damage *= (1.0 - 0.04 * level);
        }
        break;
    }

    // Clamp reductions so damage never goes negative or above original.
    if (damage < 0) {
      damage = 0;
    }
    event.setDamage(damage);
  }

  /* ================================================================ melee enchantments */

  private void applyMeleeEnchantments(Player attacker, LivingEntity victim,
                                      EntityDamageByEntityEvent event, ItemStack item) {
    double damage = event.getDamage();

    // Sharpness: +0.5 + 0.5*level (swords), axes get an extra 0.625*level.
    int sharp = Enchantments.level(item, org.bukkit.enchantments.Enchantment.SHARPNESS);
    if (sharp > 0) {
      double bonus = 0.5 + 0.5 * sharp;
      if (isAxe(item)) {
        bonus += 0.625 * sharp;
      }
      damage += bonus;
    }

    // Smite: extra vs undead.
    int smite = Enchantments.level(item, org.bukkit.enchantments.Enchantment.SMITE);
    if (smite > 0 && isUndead(victim)) {
      damage += 1.25 * smite;
    }

    // Bane of Arthropods: extra vs arthropods + Slowness IV.
    int bane = Enchantments.level(item, org.bukkit.enchantments.Enchantment.BANE_OF_ARTHROPODS);
    if (bane > 0 && isArthropod(victim)) {
      damage += 1.25 * bane;
      victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 4 * bane, 3, false, false));
    }

    // Fire Aspect: set target on fire (4 seconds per level).
    int fireAspect = Enchantments.level(item, org.bukkit.enchantments.Enchantment.FIRE_ASPECT);
    if (fireAspect > 0) {
      victim.setRemainingFire(Math.max(victim.getRemainingFire(), 80 * fireAspect));
    }

    // Impaling: extra vs all mobs (non-players).
    int impaling = Enchantments.level(item, org.bukkit.enchantments.Enchantment.IMPALING);
    if (impaling > 0 && !(victim instanceof Player)) {
      damage += 1.25 * impaling;
    }

    // Knockback: shove the victim away from the attacker.
    int knockback = Enchantments.level(item, org.bukkit.enchantments.Enchantment.KNOCKBACK);
    if (knockback > 0) {
      applyKnockback(attacker, victim, 0.15 * knockback);
    }

    // Sweeping Edge: bonus when sprinting (sweep proxy).
    int sweeping = Enchantments.level(item, org.bukkit.enchantments.Enchantment.SWEEPING_EDGE);
    if (sweeping > 0 && attacker.isSprinting()) {
      damage += 0.5 + 0.5 * sweeping;
    }

    // Thorns: damage the attacker back.
    int thorns = Enchantments.level(item, org.bukkit.enchantments.Enchantment.THORNS);
    if (thorns > 0 && attacker != null && attacker instanceof LivingEntity) {
      if (RANDOM.nextInt(100) < thorns * 15) { // ~15% per level
        double reflect = 1.0 + thorns;
        ((LivingEntity) attacker).damage(reflect, victim);
      }
    }

    event.setDamage(damage);
  }

  /* ================================================================ projectile enchantments */

  private void applyProjectileEnchantments(Projectile projectile, LivingEntity victim,
                                           EntityDamageByEntityEvent event, ItemStack source) {
    double damage = event.getDamage();

    // Power: extra projectile damage.
    int power = Enchantments.level(source, org.bukkit.enchantments.Enchantment.POWER);
    if (power > 0) {
      damage += 1.25 * power;
    }

    // Flame: set target on fire.
    int flame = Enchantments.level(source, org.bukkit.enchantments.Enchantment.FLAME);
    if (flame > 0) {
      victim.setRemainingFire(Math.max(victim.getRemainingFire(), 100 * flame));
    }

    // Punch: projectile knockback.
    int punch = Enchantments.level(source, org.bukkit.enchantments.Enchantment.PUNCH);
    if (punch > 0) {
      applyKnockback(projectile, victim, 0.2 * punch);
    }

    // Impaling on thrown tridents vs all mobs.
    int impaling = Enchantments.level(source, org.bukkit.enchantments.Enchantment.IMPALING);
    if (impaling > 0 && !(victim instanceof Player)) {
      damage += 1.25 * impaling;
    }

    // Channeling: lightning at the target (all weather).
    int channeling = Enchantments.level(source, org.bukkit.enchantments.Enchantment.CHANNELING);
    if (channeling > 0) {
      victim.getWorld().strikeLightning(victim.getLocation());
    }

    event.setDamage(damage);

    // Multishot (chain-hit): one hit also affects up to two nearby targets.
    int multishot = Enchantments.level(source, org.bukkit.enchantments.Enchantment.MULTISHOT);
    if (multishot > 0) {
      int hits = Math.min(2, multishot);
      int done = 0;
      for (Entity nearby : victim.getNearbyEntities(3.0, 2.0, 3.0)) {
        if (done >= hits) break;
        if (!(nearby instanceof LivingEntity target)) continue;
        if (target.equals(victim)) continue;
        if (!target.isAlive()) continue;
        target.damage(damage * 0.75, projectile.getShooter() instanceof LivingEntity shooter ? shooter : null);
        done++;
      }
    }

    // Shield bypass for projectiles (Piercing & general).
    if (Enchantments.level(source, org.bukkit.enchantments.Enchantment.PIERCING) > 0
        || plugin.getShieldBypassManager().shouldBypass(source)) {
      plugin.getShieldBypassManager().bypass(victim);
    }
  }

  /* ================================================================ helpers */

  /** Knock a victim away from the source. */
  private void applyKnockback(Entity sourceEntity, LivingEntity victim, double strength) {
    Vector dir = victim.getLocation().toVector().subtract(sourceEntity.getLocation().toVector());
    dir.setY(0);
    if (dir.lengthSquared() == 0) {
      dir = new Vector(1, 0, 0);
    } else {
      dir.normalize();
    }
    dir.setY(0.25);
    victim.setVelocity(victim.getVelocity().add(dir.multiply(strength)));
  }

  private ItemStack resolveSourceItem(Entity damager) {
    if (damager instanceof LivingEntity living) {
      if (living instanceof Player player) {
        return player.getInventory().getItemInMainHand();
      }
      return null;
    }
    return null; // projectiles use the snapshot passed by callers
  }

  private ItemStack fallbackSource(Entity damager) {
    if (damager instanceof Projectile projectile) {
      Object snapshot = ProjectileListener.getSource(projectile);
      if (snapshot instanceof ItemStack item) {
        return item;
      }
      if (projectile.getShooter() instanceof Player player) {
        return player.getInventory().getItemInMainHand();
      }
    }
    return null;
  }

  private int maxArmorEnchant(LivingEntity living, org.bukkit.enchantments.Enchantment enchant) {
    int best = 0;
    for (ItemStack piece : living.getEquipment().getArmorContents()) {
      int lvl = Enchantments.level(piece, enchant);
      if (lvl > best) {
        best = lvl;
      }
    }
    return best;
  }

  private boolean isAxe(ItemStack item) {
    return item.getType().name().endsWith("_AXE");
  }

  private boolean isUndead(LivingEntity entity) {
    EntityType t = entity.getType();
    return t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.DROWNED
        || t == EntityType.ZOMBIE_VILLAGER || t == EntityType.ZOMBIFIED_PIGLIN
        || t == EntityType.SKELETON || t == EntityType.STRAY || t == EntityType.WITHER
        || t == EntityType.WITHER_SKELETON || t == EntityType.PHANTOM || t == EntityType.ZOGLIN
        || t == EntityType.SKELETON_HORSE || t == EntityType.ZOMBIE_HORSE;
  }

  private boolean isArthropod(LivingEntity entity) {
    EntityType t = entity.getType();
    return t == EntityType.SPIDER || t == EntityType.CAVE_SPIDER || t == EntityType.SILVERFISH
        || t == EntityType.ENDERMITE || t == EntityType.BEE;
  }

  /** Extra armor-piercing damage from an active Breach debuff (0 if none). */
  public double breachExtraDamage(LivingEntity victim) {
    return mace.breachExtraDamage(victim);
  }
}
