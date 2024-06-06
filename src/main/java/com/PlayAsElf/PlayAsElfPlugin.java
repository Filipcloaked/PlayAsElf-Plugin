package com.PlayAsElf;

import com.google.common.reflect.TypeToken;
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
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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

	private RuneLiteObject elfBodyRlObj;
	private ModelData elfHeadModel;

	private int[] modelIds;

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
			elfBodyRlObj = client.createRuneLiteObject();
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

			int[] equipIds = {
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



			CreatePlayerModelCopy(equipIds, true);

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
		if (elfBodyRlObj == null) return;

		LocalPoint localPoint = player.getLocalLocation();

		elfBodyRlObj.setLocation(localPoint, worldView.getPlane());
		elfBodyRlObj.setOrientation(player.getCurrentOrientation());

		if (!elfBodyRlObj.isActive())
			elfBodyRlObj.setActive(true);

		//play same anim as player
		int playerAnimation = player.getAnimation();
		int playerPose = player.getPoseAnimation();
		Animation animation = elfBodyRlObj.getAnimation();

		int elfheadAnim = -1;
		if (animation != null)
			elfheadAnim = animation.getId();

		if (playerAnimation == -1)
		{
			//need this to somehow be way faster and to match the speed at which the player anim goes
			if (elfheadAnim != playerPose)
				elfBodyRlObj.setAnimation(client.loadAnimation(playerPose));

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

	//todo need to get access to models using equip ids from player
	public void CreatePlayerModelCopy(int[] equipIds, boolean male)
	{
		Request itemRequest = new Request.Builder()
				.url("https://raw.githubusercontent.com/ScreteMonge/cache-converter/master/.venv/item_defs.json")
				.build();

		CountDownLatch countDownLatch = new CountDownLatch(1);
		Call itemCall = httpClient.newCall(itemRequest);

		itemCall.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to access URL: https://raw.githubusercontent.com/ScreteMonge/cache-converter/master/.venv/item_defs.json");
				countDownLatch.countDown();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				if (!response.isSuccessful() || response.body() == null)
					return;

				InputStreamReader reader = new InputStreamReader(response.body().byteStream());

				Type listType = new TypeToken<List<ItemData>>(){}.getType();
				List<ItemData> posts = gson.fromJson(reader, listType);

				// Setup up model IDs
				modelIds = new int[0];
				int offset = 0;

				// Search through all items to find equips
				for (int itemID : equipIds)
				{
					if (itemID >= 0)
					{
						for (ItemData itemData : posts)
						{
							if (itemData.getId() == itemID)
							{
								if (male)
								{
									modelIds = ArrayUtils.addAll(modelIds, itemData.getMaleModel0(), itemData.getMaleModel1(), itemData.getMaleModel2());
									offset = itemData.getMaleOffset();
								}
								else
								{
									modelIds = ArrayUtils.addAll(modelIds, itemData.getFemaleModel0(), itemData.getFemaleModel1(), itemData.getFemaleModel2());
									offset = itemData.getFemaleOffset();
								}
							}
						}
					}
				}

				CombineAndCreateElfBody();

				countDownLatch.countDown();
				response.body().close();
			}
		});

		try
		{
			countDownLatch.await();
		}
		catch (Exception e)
		{
			log.debug("CountDownLatch failed to wait at findModelsForPlayers");
		}

	}

	private void CombineAndCreateElfBody()
	{
		clientThread.invokeLater(() ->
		{
			ModelData[] loadedModels = new ModelData[0];

			for (int modelId : modelIds)
			{
				if (modelId >= 0)
				{
					log.info("LOADING MODEL: " + modelId);
					loadedModels = ArrayUtils.add(loadedModels, client.loadModelData(modelId));
				}

			}

			elfHeadModel = client.loadModelData(38049);
			elfHeadModel.translate(0, 3, -7);

			loadedModels = ArrayUtils.add(loadedModels, elfHeadModel);

			ModelData combinedElfBodyData = client.mergeModels(loadedModels);

			Model combinedElfBodyModel = combinedElfBodyData.light();

			elfBodyRlObj.setModel(combinedElfBodyModel);

			log.info("finished setting model");
		});

	}

	@Provides
	PlayAsElfConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayAsElfConfig.class);
	}

}
