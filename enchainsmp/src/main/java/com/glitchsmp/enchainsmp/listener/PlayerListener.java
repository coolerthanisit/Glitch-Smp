package com.glitchsmp.enchainsmp.listener;

import com.glitchsmp.enchainsmp.EnchainSMP;
import com.glitchsmp.enchainsmp.api.Enchantments;
import com.glitchsmp.enchainsmp.api.CustomEnchantments;
import com.glitchsmp.enchainsmp.mechanic.RiptideManager;
import com.glitchsmp.enchainsmp.mechanic.LungeManager;
import com.glitchsmp.enchainsmp.mechanic.MaceSmashManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player-facing enchantment interactions: Riptide charging/launch, Lunge,
 * Infinity consumables/totems/blocks, Mace Boots smashes, and the movement
 * enchantments (Frost Walker, Soul Speed, Depth Strider, Swift Sneak,
 * Respiration).
 */
public final class PlayerListener implements Listener {

  private final EnchainSMP plugin;
  private final RiptideManager riptide;
  private final LungeManager lunge;
  private final MaceSmashManager mace;

  // Players who were airborne last tick (used to detect landings for Mace Boots).
  private final java.util.Set<UUID> wasAirborne = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  public PlayerListener(EnchainSMP plugin) {
    this.plugin = plugin;
    this.riptide = plugin.getRiptideManager();
    this.lunge = plugin.getLungeManager();
    this.mace = plugin.getMaceSmashManager();
  }

  /* ================================================================ right click */

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    Action action = event.getAction();

    // ---- Riptide charge (right-click, non-trident items only).
    if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
      if (item != null && Enchantments.level(item, org.bukkit.enchantments.Enchantment.RIPTIDE) > 0) {
        if (riptide.tryStartCharge(player, item)) {
          event.setCancelled(true); // don't place/use the item while charging
          return;
        }
      }
    }

    // ---- Lunge: left-click anywhere, even air.
    if ((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
        && item != null && lunge.tryLunge(player, item)) {
      event.setCancelled(true);
      return;
    }

    // ---- Infinity throwables: pearl / egg / snowball / splash potion never decrease.
    if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)
        && item != null && Enchantments.has(item, org.bukkit.enchantments.Enchantment.INFINITY)) {
      EntityType type = throwableType(item);
      if (type != null) {
        event.setCancelled(true);
        player.launchProjectile(type);
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 0.6f, 1.0f);
        return;
      }
    }
  }

  /* ================================================================ movement & boots */

  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    boolean onGround = player.isOnGround();
    boolean was = wasAirborne.contains(player.getUniqueId());

    // Track fall distance for mace smashes (held item and boots).
    double fallBefore = mace.getFallDistance(player);
    mace.updateFall(player);
    wasAirborne.remove(player.getUniqueId());
    if (!onGround) {
      wasAirborne.add(player.getUniqueId());
    }

    // Post-smash downward-movement stop (~0.5s).
    mace.tickStop(player);

    // ---- Mace Boots: land on an entity from a fall -> automatic smash.
    if (was && onGround && fallBefore > MaceSmashManager.SMASH_FALL_THRESHOLD) {
      LivingEntity target = entityAtFeet(player);
      if (target != null) {
        ItemStack boots = player.getInventory().getBoots();
        mace.tryBootSmash(player, target, fallBefore, boots);
      }
    }

    // ---- Frost Walker: freeze water near feet while walking on land.
    int frost = bootsLevel(player, org.bukkit.enchantments.Enchantment.FROST_WALKER);
    if (frost > 0 && !player.isInWater() && player.getLocation().getBlock().isSolid()) {
      freezeNearby(player, frost);
    }

    // ---- Soul Speed: speed on soul sand / soul soil.
    if (onSoulBlock(player)) {
      int ss = playerLevel(player, org.bukkit.enchantments.Enchantment.SOUL_SPEED);
      if (ss > 0) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, ss - 1, false, false));
      }
    }

    // ---- Depth Strider: speed while in water.
    if (player.isInWater() || player.isSwimming()) {
      int ds = playerLevel(player, org.bukkit.enchantments.Enchantment.DEPTH_STRIDER);
      if (ds > 0) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, ds - 1, false, false));
      }
    }

    // ---- Swift Sneak: faster sneaking.
    if (player.isSneaking() && !player.isSprinting()) {
      int ss2 = playerLevel(player, org.bukkit.enchantments.Enchantment.SWIFT_SNEAK);
      if (ss2 > 0) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, ss2 - 1, false, false));
      }
    }

    // ---- Respiration: refill air while underwater.
    int resp = maxArmorOrInventory(player, org.bukkit.enchantments.Enchantment.RESPIRATION);
    if (resp > 0 && player.isInWater() && player.getRemainingAir() < player.getMaxAir()) {
      player.setRemainingAir(player.getMaxAir());
    }
  }

  /** An entity standing at the player's feet (for Mace Boots). */
  private LivingEntity entityAtFeet(Player player) {
    LivingEntity best = null;
    double bestDist = Double.MAX_VALUE;
    for (Entity e : player.getNearbyEntities(0.9, 0.9, 0.9)) {
      if (e.equals(player) || !(e instanceof LivingEntity) || !e.isAlive()) {
        continue;
      }
      double d = e.getLocation().distanceSquared(player.getLocation());
      if (d < bestDist) {
        bestDist = d;
        best = (LivingEntity) e;
      }
    }
    return best;
  }

  private void freezeNearby(Player player, int level) {
    org.bukkit.Location center = player.getLocation().subtract(0, 0.5, 0);
    int radius = level;
    int count = 0;
    for (int x = -radius; x <= radius && count < 12; x++) {
      for (int z = -radius; z <= radius && count < 12; z++) {
        org.bukkit.block.Block b = center.getBlock().getRelative(x, -1, z);
        if (b.getType() == Material.WATER && b.isEmpty()) { // water source-ish
          b.setType(Material.ICE, false);
          count++;
        }
      }
    }
  }

  private boolean onSoulBlock(Player player) {
    org.bukkit.block.Block feet = player.getLocation().getBlock().getRelative(0, -1, 0);
    Material t = feet.getType();
    return t == Material.SOUL_SAND || t == Material.SOUL_SOIL;
  }

  private int bootsLevel(Player player, org.bukkit.enchantments.Enchantment e) {
    ItemStack boots = player.getInventory().getBoots();
    return Enchantments.level(boots, e);
  }

  private int maxArmorOrInventory(Player player, org.bukkit.enchantments.Enchantment e) {
    int best = 0;
    for (ItemStack piece : player.getInventory().getArmorContents()) {
      int l = Enchantments.level(piece, e);
      if (l > best) best = l;
    }
    return best;
  }

  private static EntityType throwableType(ItemStack item) {
    Material m = item.getType();
    if (m == Material.ENDER_PEARL) return EntityType.ENDER_PEARL;
    if (m == Material.EGG) return EntityType.EGG;
    if (m == Material.SNOWBALL) return EntityType.SNOWBALL;
    if (m == Material.SPLASH_POTION) return EntityType.SPLASH_POTION;
    if (m == Material.LINGERING_POTION) return EntityType.LINGERING_POTION;
    return null;
  }

  /* ================================================================ consumables & death */

  @EventHandler
  public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
    ItemStack item = event.getItem();
    if (item != null && Enchantments.has(item, org.bukkit.enchantments.Enchantment.INFINITY)) {
      // Infinity: used items never decrease in quantity. Preserve the item;
      // (re-applying the eaten/drunk effect would require item-specific food data.)
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();

    // Curse of Vanishing: cursed items disappear on death (are not dropped).
    if (event.getDrops() != null) {
      List<ItemStack> kept = new ArrayList<>();
      for (ItemStack drop : event.getDrops()) {
        if (drop != null && Enchantments.has(drop, org.bukkit.enchantments.Enchantment.CURSE_OF_VANISHING)) {
          continue; // vanish
        }
        kept.add(drop);
      }
      event.getDrops().clear();
      event.getDrops().addAll(kept);
    }

    // Infinity Totems of Undying: used totems immediately return (are not consumed).
    boolean hasInfinityTotem = false;
    for (ItemStack piece : player.getInventory().getArmorContents()) {
      if (piece != null && piece.getType() == Material.TOTEM_OF_UNDYING
          && Enchantments.has(piece, org.bukkit.enchantments.Enchantment.INFINITY)) {
        hasInfinityTotem = true;
      }
    }
    if (!hasInfinityTotem) {
      for (ItemStack stack : player.getInventory()) {
        if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING
            && Enchantments.has(stack, org.bukkit.enchantments.Enchantment.INFINITY)) {
          hasInfinityTotem = true;
          break;
        }
      }
    }
    if (hasInfinityTotem) {
      event.setDeathCancelled(true);
      player.setHealth(player.getMaxHealth());
      player.setFireTicks(0);
    }
  }

  // Curse of Binding: prevents normal equipment removal from armor slots.
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    ItemStack cur = event.getCurrentItem();
    int slot = event.getSlot();
    if (cur != null && slot >= 5 && slot <= 8
        && Enchantments.has(cur, org.bukkit.enchantments.Enchantment.CURSE_OF_BINDING)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player)) {
      return;
    }
    for (int slot : event.getInventorySlots()) {
      ItemStack cur = event.getInventory().getItem(slot);
      if (cur != null && slot >= 5 && slot <= 8
          && Enchantments.has(cur, org.bukkit.enchantments.Enchantment.CURSE_OF_BINDING)) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    riptide.abortCharge(player);
    wasAirborne.remove(player.getUniqueId());
  }
  /** Highest level of an enchantment anywhere on the player (armor + hands). */
  private int playerLevel(Player player, org.bukkit.enchantments.Enchantment e) {
    int best = 0;
    for (ItemStack piece : player.getInventory().getArmorContents()) {
      int l = Enchantments.level(piece, e);
      if (l > best) best = l;
    }
    ItemStack main = player.getInventory().getItemInMainHand();
    ItemStack off = player.getInventory().getItemInOffHand();
    best = Math.max(best, Enchantments.level(main, e));
    best = Math.max(best, Enchantments.level(off, e));
    return best;
  }

}
