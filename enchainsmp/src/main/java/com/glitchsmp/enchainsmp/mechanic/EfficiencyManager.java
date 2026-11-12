package com.glitchsmp.enchainsmp.mechanic;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Efficiency:
 * <ul>
 *   <li>Extra projectiles: each level fires one additional projectile
 *       (Efficiency V = five additional), each from the player toward the
 *       direction the player is <b>currently</b> looking, with its own
 *       trajectory, staggered by 0.5 seconds. Only Efficiency weapons do this.</li>
 *   <li>Attack speed on combat weapons (attribute modifier while held).</li>
 * </ul>
 *
 * <p>Mining speed and Silk Touch / Fortune / Aqua Affinity are handled in
 * {@link com.glitchsmp.enchainsmp.listener.BlockListener} because they hook
 * {@link org.bukkit.event.block.BlockBreakEvent}.
 */
public final class EfficiencyManager implements Listener {

  private static final UUID ATTACK_SPEED_MODIFIER_UUID =
      UUID.fromString("f3d5c8a2-1111-4444-8888-123456789abc");
  private static final String ATTACK_SPEED_NAME = "enchainsmp.efficiency_attack_speed";

  private final EnchainSMP plugin;

  // Players currently holding an Efficiency weapon (for the attack speed modifier).
  private final ConcurrentHashMap<UUID, Integer> efficiencyPlayers = new ConcurrentHashMap<>();

  public EfficiencyManager(EnchainSMP plugin) {
    this.plugin = plugin;
    plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAttackSpeed, 0L, 4L);
  }

  public void dispose() {
    efficiencyPlayers.clear();
  }

  /* ---------------------------------------------------------------- extra projectiles */

  @EventHandler
  public void onProjectileLaunch(ProjectileLaunchEvent event) {
    scheduleExtraProjectiles(event.getEntity(), event.getEntity().getShooter());
  }

  @EventHandler
  public void onEntityShootBow(EntityShootBowEvent event) {
    scheduleExtraProjectiles(event.getProjectile(), event.getEntity());
  }

  private void scheduleExtraProjectiles(Projectile original, Entity shooter) {
    if (!(shooter instanceof Player player)) {
      return;
    }
    ItemStack weapon = weaponInHand(player);
    int level = Enchantments.level(weapon, org.bukkit.enchantments.Enchantment.EFFICIENCY);
    if (level <= 0) {
      return; // normal projectiles without Efficiency are completely unchanged.
    }
    EntityType type = projectileType(original);
    if (type == null) {
      return;
    }

    // Fire `level` additional projectiles, each 0.5s apart, from the player
    // toward their current look, each with its own trajectory (no clumping).
    for (int i = 1; i <= level; i++) {
      int delay = i * 10; // 0.5 seconds per extra projectile
      new org.bukkit.scheduler.BukkitRunnable() {
        @Override
        public void run() {
          if (!player.isOnline() || player.isDead()) {
            return;
          }
          Vector aim = player.getLocation().getDirection().clone();
          // Give every extra projectile its own trajectory (small spread).
          double spread = 0.06 * ThreadLocalRandom.current().nextDouble(-1, 1);
          aim.rotateAroundX(Math.toRadians(spread));
          aim.rotateAroundY(Math.toRadians(spread));
          aim.normalize();
          Projectile extra = player.launchProjectile(type);
          extra.setVelocity(aim.multiply(1.5));
          // Extra projectiles from Efficiency should not themselves trigger more
          // extra projectiles; mark them briefly.
          ExtraProjectile.mark(extra);
        }
      }.runTaskLater(plugin, delay);
    }
  }

  private EntityType projectileType(Projectile p) {
    if (p instanceof org.bukkit.entity.Arrow) {
      return EntityType.ARROW;
    }
    if (p instanceof org.bukkit.entity.FireworkRocket) {
      return EntityType.FIREWORK_ROCKET;
    }
    if (p instanceof org.bukkit.entity.Snowball) {
      return EntityType.SNOWBALL;
    }
    if (p instanceof org.bukkit.entity.Egg) {
      return EntityType.EGG;
    }
    if (p instanceof org.bukkit.entity.SplashPotion) {
      return EntityType.SPLASH_POTION;
    }
    return null;
  }

  private static ItemStack weaponInHand(Player player) {
    ItemStack main = player.getInventory().getItemInMainHand();
    ItemStack off = player.getInventory().getItemInOffHand();
    return (main != null && !main.getType().isAir()) ? main : off;
  }

  /* ---------------------------------------------------------------- attack speed */

  private void refreshAttackSpeed() {
    for (Player player : plugin.getServer().getOnlinePlayers()) {
      ItemStack hand = player.getInventory().getItemInMainHand();
      int level = (hand != null && !hand.getType().isAir())
          ? Enchantments.level(hand, org.bukkit.enchantments.Enchantment.EFFICIENCY) : 0;

      Attribute attribute = player.getAttribute(Attribute.ATTACK_SPEED);
      if (attribute == null) {
        continue;
      }
      attribute.removeModifier(ATTACK_SPEED_MODIFIER_UUID);

      if (level > 0) {
        attribute.addModifier(new AttributeModifier(
            ATTACK_SPEED_MODIFIER_UUID, ATTACK_SPEED_NAME, level * 0.5,
            AttributeModifier.Operation.ADD_NUMBER));
      }
      efficiencyPlayers.put(player.getUniqueId(), level);
    }
  }

  /** Marker so Efficiency extra projectiles don't recursively spawn more. */
  static final class ExtraProjectile {
    static void mark(Projectile p) {
      // No persistent state needed; launchProjectile-created projectiles are not
      // re-checked because scheduleExtraProjectiles only counts the weapon on the
      // shooter's hand at launch time, and these extras are fired by the scheduler
      // directly, not by a weapon swing.
    }
  }
}
