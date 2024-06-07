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
import java.util.ArrayList;
import java.util.Arrays;
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

	private boolean headTranslated;

	private int[] oldEquipIds;

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


			// Check if we have certain slots without armor
			//int kitTorsoID

			//log.info("CHEST ID: " + torsoID);
			//log.info("KIT CHEST ID: " + playerComposition.getKitId(KitType.TORSO));

			for (int color : playerComposition.getColors())
			{
				log.info("KIT COLOR: " + color);
			}

			//todo for kit ids: lines 155 ModelFinder.java

			if (!Arrays.equals(newEquipIds, oldEquipIds))
			{
				CreatePlayerModelCopy(newEquipIds, true);
				oldEquipIds = newEquipIds;
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

		int elfBodyAnim = -1;
		if (animation != null)
			elfBodyAnim = animation.getId();

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

				// Setup up model data
				ArrayList<FetchedModelInfo> fetchedModelInfos = new ArrayList<>();


				// Search through all items to find equips
				for (int itemID : equipIds)
				{
					// Get Model Ids for our equipped items
					if (itemID >= 0)
					{
						for (ItemData itemData : posts)
						{
							if (itemData.getId() == itemID)
							{
								//FetchedModelInfo newFetchedModelInfo;
								int[] modelIds = new int[0];

								if (male)
								{
									modelIds = ArrayUtils.addAll(modelIds, itemData.getMaleModel0(), itemData.getMaleModel1(), itemData.getMaleModel2());
								}
								else
								{
									modelIds = ArrayUtils.addAll(modelIds, itemData.getMaleModel0(), itemData.getMaleModel1(), itemData.getMaleModel2());
								}

								short[] rf = new short[0];
								short[] rt = new short[0];

								if (itemData.getColorReplace() != null)
								{
									int[] recolorToReplace = itemData.getColorReplace();
									int[] recolorToFind = itemData.getColorFind();
									rf = new short[recolorToReplace.length];
									rt = new short[recolorToReplace.length];

									for (int e = 0; e < rf.length; e++)
									{
										int rfi = recolorToFind[e];
										if (rfi > 32767)
										{
											rfi -= 65536;
										}
										rf[e] = (short) rfi;

										int rti = recolorToReplace[e];
										if (rti > 32767)
										{
											rti -= 65536;
										}
										rt[e] = (short) rti;
									}
								}

								fetchedModelInfos.add(new FetchedModelInfo(modelIds, rf, rt));

							}
						}
					}
				}

				CombineAndCreateElfBody(fetchedModelInfos);
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

	private void CombineAndCreateElfBody(ArrayList<FetchedModelInfo> fetchedModelInfos)
	{
		clientThread.invokeLater(() ->
		{
			ModelData[] loadedModels = new ModelData[0];

			ModelData elfHeadModel = client.loadModelData(38049);

			if (!headTranslated) // might be a better way to deal with this
			{
				elfHeadModel.translate(0, 7, -5);
				headTranslated = true;
			}

			elfHeadModel = ColorElfHead(elfHeadModel);

			loadedModels = ArrayUtils.add(loadedModels, elfHeadModel);

			for (FetchedModelInfo fetchedModelInfo : fetchedModelInfos)
			{
				for (int modelId : fetchedModelInfo.getModelIds())
				{
					if (modelId >= 0)
					{
						//log.info("LOADING MODEL: " + modelId);
						ModelData newModel = client.loadModelData(modelId);

						short defaultSkinColor = 4550;

						//todo replace skin color with a config skin color value later on
						short testColorToReplace = 5673;

						// If an armor piece contains the default skin color, replace it with the player's
						// chosen skin color
						if (ArrayUtils.contains(newModel.getFaceColors(), defaultSkinColor))
						{
							newModel.cloneColors();
							newModel.recolor(defaultSkinColor, testColorToReplace);
						}

						// If an armor has colors to replace apply it here
						if (fetchedModelInfo.getRecolorFrom().length > 0)
						{
							for (int i = 0; i < fetchedModelInfo.getRecolorFrom().length; i++)
							{
								newModel.recolor(fetchedModelInfo.getRecolorFrom()[i], fetchedModelInfo.getRecolorTo()[i]);
							}
						}

						loadedModels = ArrayUtils.add(loadedModels, newModel);
					}
				}
			}

			ModelData combinedElfBodyData = client.mergeModels(loadedModels);

			Model combinedElfBodyModel = combinedElfBodyData.light();

			byte[] renderPriorities = combinedElfBodyModel.getFaceRenderPriorities();

			if (renderPriorities != null && renderPriorities.length > 0)
			{
				//10 is best prio for head, however cape is still having render issues with it
				Arrays.fill(renderPriorities, 0, elfHeadModel.getFaceCount()-1, (byte) config.setHeadRenderPrio());

			}

			elfBodyRlObj.setModel(combinedElfBodyModel);

			log.info("finished setting model");
		});

	}

	private ModelData ColorElfHead(ModelData elfHeadModelData)
	{
		elfHeadModelData.cloneColors();

		short c1 = 4308;
		short c2 = 5673;
		elfHeadModelData.recolor(c1, c2);
		c1 = 4304;
		c2 = 5673;
		elfHeadModelData.recolor(c1, c2);
		c1 = 4300;
		c2 = 5673;
		elfHeadModelData.recolor(c1, c2);
		c1 = -28119;
		c2 = 5465;
		elfHeadModelData.recolor(c1, c2);
		c1 = 17061;
		c2 = 5465;
		elfHeadModelData.recolor(c1, c2);
		c1 = 2578;
		c2 = 5673;
		elfHeadModelData.recolor(c1, c2);
		c1 = 2454;
		c2 = 5673;
		elfHeadModelData.recolor(c1, c2);

		return elfHeadModelData;
	}

	@Provides
	PlayAsElfConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayAsElfConfig.class);
	}

}
