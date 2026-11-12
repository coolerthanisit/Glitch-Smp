# EnchainSMP

Paper 1.21.11 / Java 25 plugin that lets **all 43 vanilla enchantments work on any item**,
including normally incompatible combinations, with real gameplay effects (not visual-only).

## Build

Requires Maven and a JDK (Java 25). Builds against the Paper 1.21.11 API from the
PaperMC Maven repository (`io.papermc.paper:paper-api`).

```bash
cd enchainsmp
mvn clean package
target/enchainsmp-1.0.0.jar   # drop this into your Paper server's plugins/
```

> Note: this sandbox has no JDK and cannot reach Maven Central, so the project
> cannot be compiled here. The source targets the real Paper 1.21.11 API and is
> written to compile against `paper-api:1.21.11-R0.1-SNAPSHOT`. Adjust
> `paper.version` in `pom.xml` if your Paper build uses a different tag.

## What it implements

- **Anvil**: any enchantment on any item, incompatible combinations preserved,
  levels never summed (Efficiency IV + V = V), no enchantments silently dropped,
  item properties/NBT preserved.
- **Enchanting tables**: fully disabled; anvils remain usable.
- **Damage**: Sharpness, Smite, Bane of Arthropods (+Slowness IV), Fire Aspect,
  Flame, Knockback, Looting, Sweeping Edge, Impaling, Power, Punch, Channeling.
- **Mace smash** (Density / Breach / Wind Burst, independent): fall-distance
  damage, knockback, vanilla sounds/particles, ~0.5s downward-movement stop,
  fall-damage suppression, no downward glitching. Wind Burst launches the player
  upward and knocks back nearby entities for chained smashes; Breach penetrates
  armor.
- **Mace Boots**: Density/Breach/Wind Burst boots auto-smash entities you land on.
- **Riptide** (non-trident): charging period (never instant), charging pose via
  sound/particles, no swing/hit animation, launch with riptide sound/particles,
  strength scales with level, no water/rain required. Actual tridents keep
  vanilla water/rain behavior.
- **Lunge**: left-click (even air) launches horizontally; costs 1 hunger + 1
  durability per use; no cooldown.
- **Infinity**: crossbow ammo regenerated in the same slot/type (arrows as
  arrows, fireworks as fireworks, offhand returns to offhand); throwables,
  blocks, food/potion and Totems of Undying are not consumed; infinity totems
  return immediately on death.
- **Multishot**: a hit also chains to up to 2 nearby targets.
- **Piercing**: 100% shield bypass + projectiles pass through entities.
- **Efficiency**: extra projectiles (1 per level, V = 5), each from the player
  toward their current look, separate trajectory, staggered 0.5s; attack-speed
  modifier while held.
- **Blast Protection**: projectile impacts create large meteor-like craters
  (optimized, `applyPhysics=false`, bedrock spared).
- **Protection family**, Thorns, Feather Falling, Aqua Affinity, Silk Touch,
  Fortune, Respiration, Depth Strider, Frost Walker, Soul Speed, Swift Sneak,
  Luck of the Sea, Lure, Mending, Unbreaking, Curse of Binding, Curse of Vanishing.
- **Shield bypass** for melee and all supported projectiles.

## Files

- `pom.xml` — Maven build (Paper 1.21.11 API, Java 25).
- `plugin.yml` — plugin registration.
- `src/main/java/com/glitchsmp/enchainsmp/` — plugin source.
