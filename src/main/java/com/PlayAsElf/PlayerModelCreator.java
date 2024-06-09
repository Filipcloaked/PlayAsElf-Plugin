package com.PlayAsElf;

import javax.inject.Inject;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.client.callback.ClientThread;
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
public class PlayerModelCreator {

    private final Client client;
    private final ClientThread clientThread;
    private final PlayAsElfConfig config;
    private final OkHttpClient httpClient;
    private final Gson gson;

    private boolean headTranslated;
    private ArrayList<FetchedModelInfo> fetchedModelInfos;

    public PlayerModelCreator(Client client, ClientThread clientThread, PlayAsElfConfig config, OkHttpClient httpClient, Gson gson)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.httpClient = httpClient;
        this.gson = gson;
    }


    //todo need to get access to models using equip ids from player
    public void CreatePlayerModelCopy(int[] equipIds, int[] kitIds, boolean male, RuneLiteObject elfBodyRlObj)
    {
        // Setup up model data
        fetchedModelInfos = new ArrayList<>();

        // Fetch equipment/item models
        Request itemRequest = new Request.Builder()
                .url("https://raw.githubusercontent.com/ScreteMonge/cache-converter/master/.venv/item_defs.json")
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
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

                GetEquipmentModelIds(response, equipIds, male);

                countDownLatch.countDown();
                response.body().close();
            }
        });

        // For kit

        Request kitRequest = new Request.Builder()
                .url("https://raw.githubusercontent.com/ScreteMonge/cache-converter/master/.venv/kit.json")
                .build();
        Call kitCall = httpClient.newCall(kitRequest);
        kitCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.debug("Failed to access URL: https://raw.githubusercontent.com/ScreteMonge/cache-converter/master/.venv/kit.json");
                countDownLatch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                if (!response.isSuccessful() || response.body() == null)
                    return;

                GetKitModelIds(response, kitIds, male);

                //todo fix kit model colors

                countDownLatch.countDown();
                response.body().close();
            }
        });


        CombineAndCreateElfBody(elfBodyRlObj, fetchedModelInfos);

        try
        {
            countDownLatch.await();
        }
        catch (Exception e)
        {
            log.debug("CountDownLatch failed to wait at findModelsForPlayers");
        }
    }

    private void GetEquipmentModelIds(Response response, int[] equipIds, boolean male)
    {
        InputStreamReader reader = new InputStreamReader(response.body().byteStream());
        Type listType = new TypeToken<List<ItemData>>(){}.getType();
        List<ItemData> posts = gson.fromJson(reader, listType);

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

    }

    private void GetKitModelIds(Response response, int[] kitIds, boolean male)
    {
        InputStreamReader reader = new InputStreamReader(response.body().byteStream());
        Type listType = new TypeToken<List<KitData>>(){}.getType();
        List<KitData> posts = gson.fromJson(reader, listType);

        for (int kitId : kitIds)
        {
            if (kitId >= 0)
            {
                for (KitData kitData : posts)
                {
                    if (kitId == kitData.getId())
                    {
                        int[] modelIds = kitData.getModels();

                        short[] rf = new short[0];
                        short[] rt = new short[0];

                        if (kitData.getRecolorToReplace() != null)
                        {
                            int[] recolorToReplace = kitData.getRecolorToReplace();
                            int[] recolorToFind = kitData.getRecolorToFind();
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

    }

    private void CombineAndCreateElfBody(RuneLiteObject elfBodyRlObj, ArrayList<FetchedModelInfo> fetchedModelInfos)
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
}
