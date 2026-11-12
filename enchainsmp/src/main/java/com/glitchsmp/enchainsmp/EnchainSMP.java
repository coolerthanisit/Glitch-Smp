package com.glitchsmp.enchainsmp;

import com.glitchsmp.enchainsmp.api.CustomEnchantments;
import com.glitchsmp.enchainsmp.listener.AnvilListener;
import com.glitchsmp.enchainsmp.listener.BlockListener;
import com.glitchsmp.enchainsmp.listener.CombatListener;
import com.glitchsmp.enchainsmp.listener.EnchantingTableListener;
import com.glitchsmp.enchainsmp.listener.EntityListener;
import com.glitchsmp.enchainsmp.listener.FishingListener;
import com.glitchsmp.enchainsmp.listener.PlayerListener;
import com.glitchsmp.enchainsmp.listener.ProjectileListener;
import com.glitchsmp.enchainsmp.mechanic.BlastCraterManager;
import com.glitchsmp.enchainsmp.mechanic.EfficiencyManager;
import com.glitchsmp.enchainsmp.mechanic.LungeManager;
import com.glitchsmp.enchainsmp.mechanic.MaceSmashManager;
import com.glitchsmp.enchainsmp.mechanic.RiptideManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for EnchainSMP.
 *
 * <p>Paper 1.21.11 / Java 25.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Vanilla enchantment compatibility is overridden at the anvil
 *       ({@link AnvilListener}) so any enchantment can be applied to any item
 *       and incompatible combinations are preserved.</li>
 *   <li>Enchanting tables are fully disabled ({@link EnchantingTableListener});
 *       anvils remain usable.</li>
 *   <li>Every enchantment reads its level straight off the item stack
 *       ({@link com.glitchsmp.enchainsmp.api.Enchantments}), so it works
 *       regardless of the item's natural enchantability.</li>
 *   <li>Mace smash, Riptide, Lunge and projectile extra-shots are handled by
 *       dedicated manager classes to keep the listeners thin.</li>
 * </ul>
 */
public final class EnchainSMP extends JavaPlugin {

  private static EnchainSMP instance;

  private MaceSmashManager maceSmashManager;
  private RiptideManager riptideManager;
  private LungeManager lungeManager;
  private EfficiencyManager efficiencyManager;
  private BlastCraterManager blastCraterManager;
  private ShieldBypassManager shieldBypassManager;

  @Override
  public void onEnable() {
    instance = this;
    saveDefaultConfig();

    // Register the non-vanilla enchantment (Lunge) so the anvil can apply it.
    CustomEnchantments.register();

    // Managers (own their own timers/tasks).
    maceSmashManager = new MaceSmashManager(this);
    riptideManager = new RiptideManager(this);
    lungeManager = new LungeManager(this);
    efficiencyManager = new EfficiencyManager(this);
    blastCraterManager = new BlastCraterManager(this);
    shieldBypassManager = new ShieldBypassManager(this);

    // Listeners.
    getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
    getServer().getPluginManager().registerEvents(new EnchantingTableListener(this), this);
    getServer().getPluginManager().registerEvents(new CombatListener(this), this);
    getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);
    getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    getServer().getPluginManager().registerEvents(new BlockListener(this), this);
    getServer().getPluginManager().registerEvents(new EntityListener(this), this);
    getServer().getPluginManager().registerEvents(new FishingListener(this), this);

    getLogger().info("EnchainSMP enabled - all vanilla enchantments work on any item.");
  }

  @Override
  public void onDisable() {
    if (maceSmashManager != null) maceSmashManager.dispose();
    if (riptideManager != null) riptideManager.dispose();
    if (lungeManager != null) lungeManager.dispose();
    if (efficiencyManager != null) efficiencyManager.dispose();
    if (blastCraterManager != null) blastCraterManager.dispose();
    if (shieldBypassManager != null) shieldBypassManager.dispose();
    instance = null;
    getLogger().info("EnchainSMP disabled.");
  }

  public static EnchainSMP getInstance() {
    return instance;
  }

  public MaceSmashManager getMaceSmashManager() {
    return maceSmashManager;
  }

  public RiptideManager getRiptideManager() {
    return riptideManager;
  }

  public LungeManager getLungeManager() {
    return lungeManager;
  }

  public EfficiencyManager getEfficiencyManager() {
    return efficiencyManager;
  }

  public BlastCraterManager getBlastCraterManager() {
    return blastCraterManager;
  }

  public ShieldBypassManager getShieldBypassManager() {
    return shieldBypassManager;
  }
}
