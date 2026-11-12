package com.glitchsmp.enchainsmp.mechanic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Blast Protection craters: projectile impacts create large, meteor-like
 * craters without excessive lag.
 *
 * <p>Block changes use {@link Block#setType(Material, boolean)} with
 * {@code applyPhysics = false} so we do not trigger block-update cascades, and
 * air blocks are skipped entirely.
 */
public final class BlastCraterManager {

  /** Maximum crater radius, in blocks. */
  public static final int MAX_RADIUS = 10;

  /**
   * Carves a spherical crater of {@code radius} blocks at {@code center}.
   * Only non-air, non-bedrock blocks are replaced with air.
   */
  public void createCrater(Location center, int radius) {
    if (radius <= 0) {
      return;
    }
    radius = Math.min(radius, MAX_RADIUS);
    World world = center.getWorld();
    if (world == null) {
      return;
    }
    int cx = center.getBlockX();
    int cy = center.getBlockY();
    int cz = center.getBlockZ();
    double rSq = radius * radius;

    // Carve a sphere. applyPhysics=false avoids block-update lag spikes.
    for (int x = cx - radius; x <= cx + radius; x++) {
      int dx = x - cx;
      for (int z = cz - radius; z <= cz + radius; z++) {
        int dz = z - cz;
        for (int y = cy - radius; y <= cy + radius; y++) {
          int dy = y - cy;
          if (dx * dx + dy * dy + dz * dz > rSq) {
            continue;
          }
          Block block = world.getBlockAt(x, y, z);
          Material type = block.getType();
          if (type.isAir() || type == Material.BEDROCK) {
            continue;
          }
          block.setType(Material.AIR, false);
        }
      }
    }
  }
}
