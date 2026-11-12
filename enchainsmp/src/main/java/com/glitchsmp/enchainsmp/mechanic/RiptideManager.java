package com.glitchsmp.enchainsmp.mechanic;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riptide for non-trident items.
 *
 * <ul>
 *   <li>Right-click starts a charging period (never instant).</li>
 *   <li>The trident charging/throwing idle pose is approximated with charging
 *       sounds and particles (true pose animation requires ProtocolLib/NMS).</li>
 *   <li>No melee hit or attack-swing animation is played.</li>
 *   <li>After charging: riptide sound, particles and a launch in the look
 *       direction; strength grows with level.</li>
 *   <li>Non-trident items do NOT require water or rain.</li>
 *   <li>Actual tridents are left entirely to vanilla (which still requires
 *       water or rain), so their original charging/holding animation is kept.</li>
 * </ul>
 */
public final class RiptideManager {

  private static final float[] RIPIST_SOUNDS = {
      Sound.ITEM_TRIDENT_RIPTIDE_1.getVolume(), // placeholder index 0 unused
  };

  private final EnchainSMP plugin;

  // Players currently charging a riptide launch.
  private final Map<UUID, Charge> charging = new ConcurrentHashMap<>();

  public RiptideManager(EnchainSMP plugin) {
    this.plugin = plugin;
  }

  public void dispose() {
    charging.clear();
  }

  /** Call from PlayerListener on right-click with a riptide item. */
  public boolean tryStartCharge(Player player, ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    if (item.getType() == Material.TRIDENT) {
      // Actual tridents keep vanilla behaviour (water/rain required).
      return false;
    }
    int level = Enchantments.level(item, org.bukkit.enchantments.Enchantment.RIPTIDE);
    if (level <= 0) {
      return false;
    }
    if (charging.containsKey(player.getUniqueId())) {
      return true; // already charging
    }

    int chargeTicks = Math.max(10, 40 - 5 * level); // higher levels charge faster
    Charge charge = new Charge(player, item, level, chargeTicks);
    charging.put(player.getUniqueId(), charge);
    charge.start();

    // Charging feedback: sound + particles (pose approximation).
    player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.6f, 1.0f);
    player.sendActionBar(org.bukkit.ChatColor.AQUA + "Charging riptide...");
    return true;
  }

  /** Call to abort an in-progress charge (damage, item switch, etc.). */
  public void abortCharge(Player player) {
    Charge c = charging.remove(player.getUniqueId());
    if (c != null) {
      c.task.cancel();
    }
  }

  public boolean isCharging(Player player) {
    return charging.containsKey(player.getUniqueId());
  }

  private void launch(Player player, int level) {
    Location loc = player.getLocation();
    Vector dir = loc.getDirection();
    dir.setY(0);
    if (dir.lengthSquared() == 0) {
      dir = new Vector(1, 0, 0);
    } else {
      dir.normalize();
    }
    double strength = 0.8 + 0.35 * level;
    double lift = 0.4 + 0.15 * level;
    Vector velocity = dir.multiply(strength);
    velocity.setY(lift);
    player.setVelocity(velocity);

    Sound sound = switch (level) {
      case 2 -> Sound.ITEM_TRIDENT_RIPTIDE_2;
      case 3 -> Sound.ITEM_TRIDENT_RIPTIDE_3;
      default -> Sound.ITEM_TRIDENT_RIPTIDE_1;
    };
    loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
    loc.getWorld().spawnParticle(Particle.SPLASH, loc.add(0, 1, 0), 25, 0.6, 0.6, 0.6, 0.1);
    loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.4, 0.4, 0.4, 0.05);
  }

  private final class Charge {
    final Player player;
    final int level;
    final int ticks;
    int remaining;
    org.bukkit.scheduler.BukkitTask task;

    Charge(Player player, ItemStack item, int level, int ticks) {
      this.player = player;
      this.level = level;
      this.ticks = ticks;
      this.remaining = ticks;
    }

    void start() {
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
        if (!player.isOnline() || !charging.get(player.getUniqueId()).equals(this)) {
          cancel();
          return;
        }
        // Charging pose feedback each tick (no swing/hit animation).
        player.getWorld().spawnParticle(
            Particle.SPELL_WITCH, player.getEyeLocation(), 3, 0.3, 0.3, 0.3, 0.0);
        remaining--;
        if (remaining <= 0) {
          cancel();
          charging.remove(player.getUniqueId());
          launch(player, level);
        }
      }, 0L, 1L);
    }

    void cancel() {
      if (task != null) {
        task.cancel();
      }
    }
  }
}
