package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.EntityEffect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.DamageSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellApplyDamageEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class DotSpell extends TargetedSpell implements TargetedEntitySpell, DamageSpell {

	private Map<UUID, Dot> activeDots;

	private int delay;
	private int interval;
	private int duration;

	private float damage;

	private boolean ignoreArmor;
	private boolean checkPlugins;
	private boolean avoidDamageModification;
	private boolean tryAvoidingAntiCheatPlugins;

	private String spellDamageType;
	private DamageCause damageType;

	public DotSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		delay = getConfigInt("delay", 1);
		interval = getConfigInt("interval", 20);
		duration = getConfigInt("duration", 200);

		damage = getConfigFloat("damage", 2);

		ignoreArmor = getConfigBoolean("ignore-armor", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		avoidDamageModification = getConfigBoolean("avoid-damage-modification", true);
		tryAvoidingAntiCheatPlugins = getConfigBoolean("try-avoiding-anticheat-plugins", false);

		spellDamageType = getConfigString("spell-damage-type", "");
		String damageTypeName = getConfigString("damage-type", "ENTITY_ATTACK");
		try {
			damageType = DamageCause.valueOf(damageTypeName.toUpperCase());
		}
		catch (IllegalArgumentException ignored) {
			DebugHandler.debugBadEnumValue(DamageCause.class, damageTypeName);
			damageType = DamageCause.ENTITY_ATTACK;
		}

		activeDots = new HashMap<>();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power);
			if (targetInfo == null) return noTarget(caster);
			applyDot(caster, targetInfo.getTarget(), targetInfo.getPower());
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		applyDot(caster, target, power);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		applyDot(null, target, power);
		return true;
	}

	@Override
	public String getSpellDamageType() {
		return spellDamageType;
	}

	public boolean isActive(LivingEntity entity) {
		return activeDots.containsKey(entity.getUniqueId());
	}

	public void cancelDot(LivingEntity entity) {
		if (!isActive(entity)) return;
		Dot dot = activeDots.get(entity.getUniqueId());
		dot.cancel();
	}
	
	private void applyDot(LivingEntity caster, LivingEntity target, float power) {
		Dot dot = activeDots.get(target.getUniqueId());
		if (dot != null) {
			dot.dur = 0;
			dot.power = power;
		} else {
			dot = new Dot(caster, target, power);
			activeDots.put(target.getUniqueId(), dot);
		}

		if (caster != null) playSpellEffects(caster, target);
		else playSpellEffects(EffectPosition.TARGET, target);
	}
	
	@EventHandler
	private void onDeath(PlayerDeathEvent event) {
		Dot dot = activeDots.get(event.getEntity().getUniqueId());
		if (dot != null) dot.cancel();
	}
	
	private class Dot implements Runnable {
		
		private LivingEntity caster;
		private LivingEntity target;
		private float power;

		private int taskId;
		private int dur = 0;

		private Dot(LivingEntity caster, LivingEntity target, float power) {
			this.caster = caster;
			this.target = target;
			this.power = power;
			taskId = MagicSpells.scheduleRepeatingTask(this, delay, interval);
		}
		
		@Override
		public void run() {
			dur += interval;
			if (dur > duration) {
				cancel();
				return;
			}

			if (target.isDead() || !target.isValid()) {
				cancel();
				return;
			}

			double localDamage = damage * power;

			if (checkPlugins) {
				MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, damageType, localDamage, DotSpell.this);
				EventUtil.call(event);
				if (event.isCancelled()) return;
				if (!avoidDamageModification) localDamage = event.getDamage();
				target.setLastDamageCause(event);
			}

			SpellApplyDamageEvent event = new SpellApplyDamageEvent(DotSpell.this, caster, target, localDamage, damageType, spellDamageType);
			EventUtil.call(event);
			localDamage = event.getFinalDamage();

			if (ignoreArmor) {
				double maxHealth = Util.getMaxHealth(target);
				double health = target.getHealth();

				if (health > maxHealth) health = maxHealth;
				health = health - localDamage;

				if (health < 0) {
					health = 0;
					if (caster instanceof Player) target.setKiller((Player) caster);
				}

				if (health > maxHealth) health = maxHealth;

				target.setHealth(health);
				target.playEffect(EntityEffect.HURT);
			} else {
				if (tryAvoidingAntiCheatPlugins) target.damage(localDamage);
				else target.damage(localDamage, caster);
			}

			playSpellEffects(EffectPosition.DELAYED, target);
			target.setNoDamageTicks(0);
		}

		private void cancel() {
			MagicSpells.cancelTask(taskId);
			activeDots.remove(target.getUniqueId());
		}

	}

}
