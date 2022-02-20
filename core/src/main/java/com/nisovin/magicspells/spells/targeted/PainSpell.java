package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.compat.CompatBasics;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class PainSpell extends TargetedSpell implements TargetedEntitySpell, DamageSpell {

	private String spellDamageType;
	private DamageCause damageType;

	private double damage;
	private int damagePercent;

	private boolean ignoreArmor;
	private boolean checkPlugins;
	private boolean avoidDamageModification;
	private boolean tryAvoidingAntiCheatPlugins;
	
	public PainSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		spellDamageType = getConfigString("spell-damage-type", "");
		String damageTypeName = getConfigString("damage-type", "ENTITY_ATTACK");
		try {
			damageType = DamageCause.valueOf(damageTypeName.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			DebugHandler.debugBadEnumValue(DamageCause.class, damageTypeName);
			damageType = DamageCause.ENTITY_ATTACK;
		}

		damage = getConfigFloat("damage", 4);
		damagePercent = getConfigInt("damage-percent", 0);

		if (damagePercent < 0 || damagePercent > 100) {
			MagicSpells.error("HealSpell '" + internalName + "' uses damage-percent outside bounds 0-100.");
		}

		ignoreArmor = getConfigBoolean("ignore-armor", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		avoidDamageModification = getConfigBoolean("avoid-damage-modification", true);
		tryAvoidingAntiCheatPlugins = getConfigBoolean("try-avoiding-anticheat-plugins", false);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power);
			if (target == null) return noTarget(caster);

			boolean done;
			if (caster instanceof Player) done = CompatBasics.exemptAction(() -> causePain(caster, target.getTarget(), target.getPower()), (Player) caster, CompatBasics.activeExemptionAssistant.getPainExemptions());
			else done = causePain(caster, target.getTarget(), target.getPower());
			if (!done) return noTarget(caster);
			
			sendMessages(caster, target.getTarget(), args);
			return PostCastAction.NO_MESSAGES;
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target)) return false;
		return causePain(caster, target, power);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target)) return false;
		return causePain(null, target, power);
	}

	@Override
	public String getSpellDamageType() {
		return spellDamageType;
	}
	
	private boolean causePain(LivingEntity caster, LivingEntity target, float power) {
		if (target == null) return false;
		if (target.isDead()) return false;

		double localDamage;

		if (damagePercent == 0) localDamage = damage * power;
		else localDamage = Util.getMaxHealth(target) * (damagePercent / 100f);

		if (checkPlugins) {
			MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, damageType, localDamage, this);
			EventUtil.call(event);
			if (event.isCancelled()) return false;
			if (!avoidDamageModification) localDamage = event.getDamage();
			target.setLastDamageCause(event);
		}

		SpellApplyDamageEvent event = new SpellApplyDamageEvent(this, caster, target, localDamage, damageType, spellDamageType);
		EventUtil.call(event);
		localDamage = event.getFinalDamage();

		if (ignoreArmor) {
			double health = target.getHealth();
			if (health > Util.getMaxHealth(target)) health = Util.getMaxHealth(target);
			health = health - localDamage;
			if (health < 0) health = 0;
			if (health > Util.getMaxHealth(target)) health = Util.getMaxHealth(target);
			if (health == 0 && caster instanceof Player) target.setKiller((Player) caster);

			target.setHealth(health);
			playSpellEffects(caster, target);
			target.playEffect(EntityEffect.HURT);
			return true;
		}

		if (tryAvoidingAntiCheatPlugins) target.damage(localDamage);
		else target.damage(localDamage, caster);
		playSpellEffects(caster, target);
		return true;
	}

}
