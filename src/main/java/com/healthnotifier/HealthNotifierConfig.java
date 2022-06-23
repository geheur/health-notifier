package com.healthnotifier;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(HealthNotifierPlugin.CONFIG_GROUP)
public interface HealthNotifierConfig extends Config
{
	@ConfigItem(
			position = 1,
			keyName = "NPCName",
			name = "NPC names (leave blank for any npc)",
			description = "Name of the NPC you want to be notified about. Leave blank to have the plugin notify for all NPCs. You may add multiple names by separating them with commas or putting them on separate lines."
	)
	default String NPCName() { return ""; }

	@ConfigItem(
			position = 2,
			keyName = "specifiedHealth",
			name = "Health threshold",
			description = "Health threshold to send a notification at."
	)
	default int specifiedHealth() { return 0; }
}
