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
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
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
		description = "Notifies you when the mob you are attacking is below a certain health.",
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
	private int healthThreshold = 0;

	@Provides
	public HealthNotifierConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HealthNotifierConfig.class);
	}

	@Override
	public void startUp() {
		loadConfigValues();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP)) return;

		loadConfigValues();
	}

	private void loadConfigValues()
	{
		npcNames = Arrays.stream(config.NPCName().split("[,\n]"))
			.map(name -> name.toLowerCase().trim())
			.filter(name -> !name.isEmpty())
			.collect(Collectors.toList());
		// we don't know if the current target is tracked by the new npc names so reset it.
		currentNpc = null;
		if (client.getLocalPlayer() != null)
		{
			trackTarget(client.getLocalPlayer().getInteracting());
		}

		if (healthThreshold != config.specifiedHealth()) {
			healthThreshold = config.specifiedHealth();
			hasNotified = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick e) {
		if (currentNpc == null || hasNotified) return;

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
	public void onActorDeath(ActorDeath e) {
		// This event is more reliable than checking getHealthRatio in GameTick. getHealthRatio does not work when entity hider hide dead npcs is on and it is unreliable when the game is running at low fps (yes, really).
		if (e.getActor() == currentNpc && !hasNotified) {
			notifier.notify("Your target is dead.");
			hasNotified = true;
		}
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
