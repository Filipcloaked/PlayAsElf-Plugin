package com.PlayAsElf;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;


@ConfigGroup("playaself")
public interface PlayAsElfConfig extends Config
{
	@ConfigItem(
		keyName = "greeting",
		name = "Welcome Greeting",
		description = "The message to show to the user when they login"
	)
	default String greeting()
	{
		return "Hello";
	}

	@ConfigItem(
			keyName = "selectedGender",
			name = "Select Gender",
			description = "Choose your gender"
	)
	default Gender setGender()
	{
		return Gender.MALE;
	}

	@ConfigItem(
			keyName = "headRenderPrio",
			name = "Head Render Prio",
			description = ""
	)
	default int setHeadRenderPrio()
	{
		return 10;
	}
}
