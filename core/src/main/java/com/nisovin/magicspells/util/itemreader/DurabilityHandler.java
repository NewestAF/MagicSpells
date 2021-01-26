package com.nisovin.magicspells.util.itemreader;

import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.magicitems.MagicItemData;
import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.DURABILITY;

public class DurabilityHandler {

	private static final String CONFIG_NAME = DURABILITY.toString();

	public static void process(ConfigurationSection config, ItemMeta meta, MagicItemData data) {
		if (!(meta instanceof Damageable)) return;
		if (!config.isInt(CONFIG_NAME)) return;

		int durability = config.getInt(CONFIG_NAME);
		((Damageable) meta).setDamage(durability);
		data.setAttribute(DURABILITY, durability);
	}

	public static void processItemMeta(ItemMeta meta, MagicItemData data) {
		if (!(meta instanceof Damageable)) return;
		if (!data.hasAttribute(DURABILITY)) return;

		int durability = (int) data.getAttribute(DURABILITY);
		((Damageable) meta).setDamage(durability);
	}

	public static void processMagicItemData(ItemMeta meta, MagicItemData data) {
		if (!(meta instanceof Damageable)) return;

		int damage = ((Damageable) meta).getDamage();
		data.setAttribute(DURABILITY, damage);
	}

	public static int getDurability(ItemMeta meta) {
		if (!(meta instanceof Damageable)) return -1;

		return ((Damageable) meta).getDamage();
	}

}
