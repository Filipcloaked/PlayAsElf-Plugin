package com.PlayAsElf;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PlayAsElfTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PlayAsElfPlugin.class);
		RuneLite.main(args);
	}
}