package com.PlayAsElf;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "PlayAsElf"
)
public class PlayAsElfPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PlayAsElfConfig config;

	@Inject
	private ClientThread clientThread;

	private RuneLiteObject elfHead;
	private Model elfHeadModel;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Elf started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Elf stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Example says " + config.greeting(), null);

			// todo Need a better spot for this
			elfHead = client.createRuneLiteObject();
			elfHeadModel = client.loadModel(38049);
			elfHeadModel.translate(0, 3, -7);
			elfHead.setModel(elfHeadModel);
		}
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		Player player = event.getPlayer();

		if (player != null && player == client.getLocalPlayer())
		{
			PlayerComposition playerComposition = player.getPlayerComposition();

			log.info("Player changed event!");

			for (int id : playerComposition.getEquipmentIds())
			{
				log.info("Equip ID: " + id);

			}

//			clientThread.invokeLater(() ->
//			{
//
//			});

        }
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.EQUIPMENT.getId())
		{
			log.info("Player changed gear");
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		Player player = client.getLocalPlayer();
		WorldView worldView = client.getTopLevelWorldView();

		if (player == null) return;
		if (elfHead == null) return;

		// need to add an offset here
		LocalPoint localPoint = player.getLocalLocation();

		elfHead.setLocation(localPoint, worldView.getPlane());
		elfHead.setOrientation(player.getCurrentOrientation());

		if (!elfHead.isActive())
			elfHead.setActive(true);

		//play same anim as player
		int playerAnimation = player.getAnimation();
		int playerPose = player.getPoseAnimation();
		Animation animation = elfHead.getAnimation();

		int elfheadAnim = -1;
		if (animation != null)
			elfheadAnim = animation.getId();

		if (playerAnimation == -1)
		{
			//need this to somehow be way faster and to match the speed at which the player anim goes
			if (elfheadAnim != playerPose)
				elfHead.setAnimation(client.loadAnimation(playerPose));

		}

	}

	//need on anim changed event
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{

		if (elfHead == null) return;

		if (event.getActor() instanceof Player)
		{
			Player player = (Player) event.getActor();
			if (player != client.getLocalPlayer()) return;

			int playerAnimation = player.getAnimation();
			Animation animation = elfHead.getAnimation();
			int elfHeadAnim = -1;

			if (animation != null) elfHeadAnim = animation.getId();

			if (elfHeadAnim != playerAnimation)
			{
				elfHead.setAnimation(client.loadAnimation(playerAnimation));
			}
		}

	}

	@Provides
	PlayAsElfConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayAsElfConfig.class);
	}
}
