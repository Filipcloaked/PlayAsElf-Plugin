package com.PlayAsElf;

import com.google.gson.Gson;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.*;
import java.util.Arrays;

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

	@Inject
	private Gson gson;

	@Inject
	OkHttpClient httpClient;

	private PlayerModelCreator playerModelCreator;

	private RuneLiteObject elfBodyRlObj;

	private int[] oldEquipIds;
	private int[] oldKitIds;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Elf started!");
		playerModelCreator = new PlayerModelCreator(client, clientThread, config, httpClient, gson);
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

			if (elfBodyRlObj != null)
			{
				if (!elfBodyRlObj.isActive())
				{
					elfBodyRlObj.setActive(false);
					elfBodyRlObj.setActive(true);
				}
			}
			else
			{
				elfBodyRlObj = client.createRuneLiteObject();
			}
		}

	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		Player player = event.getPlayer();

		if (player != null && player == client.getLocalPlayer())
		{
			log.info("Player changed event!");

			PlayerComposition playerComposition = player.getPlayerComposition();

			// Find all equipped items except for hair and jaw
			int capeID = playerComposition.getEquipmentId(KitType.CAPE);
			int amuletID = playerComposition.getEquipmentId(KitType.AMULET);
			int weaponID = playerComposition.getEquipmentId(KitType.WEAPON);
			int torsoID = playerComposition.getEquipmentId(KitType.TORSO);
			int shieldID = playerComposition.getEquipmentId(KitType.SHIELD);
			int armsID = playerComposition.getEquipmentId(KitType.ARMS);
			int legsID = playerComposition.getEquipmentId(KitType.LEGS);
			int handsID = playerComposition.getEquipmentId(KitType.HANDS);
			int bootsID = playerComposition.getEquipmentId(KitType.BOOTS);

			// Find all kit items except for hair and jaw
			int kitLegsID = playerComposition.getKitId(KitType.LEGS);
			int kitBootsID = playerComposition.getKitId(KitType.BOOTS);
			int kitHandsID = playerComposition.getKitId(KitType.HANDS);
			int kitArmsID = playerComposition.getKitId(KitType.ARMS);
			int kitTorsoID = playerComposition.getKitId(KitType.TORSO);

			int[] newEquipIds = {
				capeID,
				amuletID,
				weaponID,
				torsoID,
				shieldID,
				armsID,
				legsID,
				handsID,
				bootsID
			};

			int[] newKitIds = {
				kitLegsID,
				kitBootsID,
				kitHandsID,
				kitArmsID,
				kitTorsoID
			};

			if (!Arrays.equals(newEquipIds, oldEquipIds) || !Arrays.equals(newKitIds, oldKitIds))
			{
				//todo either remove male or get it from playercomp
				//todo make sure female kit models work
				playerModelCreator.CreatePlayerModelCopy(newEquipIds, newKitIds, true, elfBodyRlObj);
				oldEquipIds = newEquipIds;
				oldKitIds = newKitIds;
			}
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

		// On tick get player location and set player copy to that position
		Player player = client.getLocalPlayer();
		WorldView worldView = client.getTopLevelWorldView();

		if (player == null) return;
		if (elfBodyRlObj == null) return;

		LocalPoint localPoint = player.getLocalLocation();

		elfBodyRlObj.setLocation(localPoint, worldView.getPlane());
		elfBodyRlObj.setOrientation(player.getCurrentOrientation());

		// Make sure player copy is active
		if (!elfBodyRlObj.isActive())
			elfBodyRlObj.setActive(true);

		//play same anim as player
		int playerAnimation = player.getAnimation();
		int playerPose = player.getPoseAnimation();
		Animation animation = elfBodyRlObj.getAnimation();

		int elfBodyAnim = -1;

		if (animation != null)
			elfBodyAnim = animation.getId();


		// If we aren't playing an animation, copy the real player's current pose
		if (playerAnimation == -1)
		{
			if (elfBodyAnim != playerPose)
			{
				Animation poseAnim = client.loadAnimation(playerPose);
				elfBodyRlObj.setAnimation(poseAnim);
				elfBodyRlObj.setShouldLoop(true);
			}
		}

	}

	//When client player plays a new anim, make the elf version play the same anim
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (elfBodyRlObj == null) return;

		if (event.getActor() instanceof Player)
		{
			Player player = (Player) event.getActor();
			if (player != client.getLocalPlayer()) return;

			int playerAnimation = player.getAnimation();
			Animation animation = elfBodyRlObj.getAnimation();
			int elfHeadAnim = -1;

			if (animation != null) elfHeadAnim = animation.getId();

			if (elfHeadAnim != playerAnimation)
			{
				elfBodyRlObj.setAnimation(client.loadAnimation(playerAnimation));
			}
		}

	}


	@Provides
	PlayAsElfConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayAsElfConfig.class);
	}

}
