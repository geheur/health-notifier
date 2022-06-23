package com.healthnotifier;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
		name = "Target Health Notifier",
		description = "Notifies you when the mob you are attacking is below certain health.",
		tags = {"target","notify","hp","dead","kill","enemy"}
)
public class HealthNotifierPlugin extends Plugin
{
	@Inject
	private NPCManager npcManager;

	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private HealthNotifierConfig config;

	public static final String CONFIG_GROUP = "healthnotifier";

	/**
	 * The local player's target, if it is tracked by the plugin (it is in npcNames or npcNames is empty). Otherwise null.
	 */
	private NPC currentNpc;
	private boolean hasNotified = false;
	private List<String> npcNames = new ArrayList<>();

	@Provides
	public HealthNotifierConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HealthNotifierConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		npcNames = Arrays.stream(config.NPCName().split("[,\n]"))
			.map(name -> name.toLowerCase().trim())
			.filter(name -> !name.isEmpty())
			.collect(Collectors.toList());
		hasNotified = false;
		// if we are logged in we should track the current target just in case the player is fighting it when they added it to the config.
		if (client.getLocalPlayer() != null)
		{
			trackTarget(client.getLocalPlayer().getInteracting());
		}
	}

	@Subscribe
	public void onClientTick(ClientTick e) {
		if (currentNpc == null || hasNotified) return;

		int healthThreshold = config.specifiedHealth();
		if (healthThreshold == 0) {
			if (currentNpc.getHealthRatio() == 0)
			{
				notifier.notify("Your target is dead.");
				hasNotified = true;
			}
			return;
		}

		int npcHealth = calculateHealth(currentNpc);
		if (npcHealth == -1) return;

		if (npcHealth <= healthThreshold)
		{
			notifier.notify("Your target is below " + healthThreshold + " health.");
			hasNotified = true;
		}
	}

	// Copied from slayer plugin.
	private int calculateHealth(NPC target)
	{
		// Based on OpponentInfoOverlay HP calculation
		if (target == null || target.getName() == null)
		{
			return -1;
		}

		final int healthScale = target.getHealthScale();
		final int healthRatio = target.getHealthRatio();
		final Integer maxHealth = npcManager.getHealth(target.getId());

		if (healthRatio < 0 || healthScale <= 0 || maxHealth == null)
		{
			return -1;
		}

		return (int)((maxHealth * healthRatio / healthScale) + 0.5f);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged e) {
		if (e.getSource() != client.getLocalPlayer()) return;

		trackTarget(e.getTarget());
	}

	private void trackTarget(Actor target)
	{
		if (target != currentNpc) {
			if (target instanceof NPC && (npcNames.isEmpty() || npcNames.contains(Text.standardize(target.getName())))) {
				currentNpc = (NPC) target;
			} else {
				currentNpc = null;
			}
			hasNotified = false;
		}
	}
}
