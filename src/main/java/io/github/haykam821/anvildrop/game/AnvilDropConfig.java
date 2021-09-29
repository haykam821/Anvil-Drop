package io.github.haykam821.anvildrop.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.anvildrop.game.map.AnvilDropMapConfig;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

public class AnvilDropConfig {
	public static final Codec<AnvilDropConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			AnvilDropMapConfig.CODEC.fieldOf("map").forGetter(AnvilDropConfig::getMapConfig),
			PlayerConfig.CODEC.fieldOf("players").forGetter(AnvilDropConfig::getPlayerConfig),
			Codec.INT.optionalFieldOf("delay", 20 * 2).forGetter(AnvilDropConfig::getDelay),
			Codec.DOUBLE.optionalFieldOf("chance", 0.4).forGetter(AnvilDropConfig::getChance),
			Codec.INT.optionalFieldOf("drop_height", 15).forGetter(AnvilDropConfig::getDropHeight),
			Codec.BOOL.optionalFieldOf("breaking", false).forGetter(AnvilDropConfig::isBreaking)
		).apply(instance, AnvilDropConfig::new);
	});

	private final AnvilDropMapConfig mapConfig;
	private final PlayerConfig playerConfig;
	private final int delay;
	private final double chance;
	private final int dropHeight;
	private final boolean breaking;

	public AnvilDropConfig(AnvilDropMapConfig mapConfig, PlayerConfig playerConfig, int delay, double chance, int dropHeight, boolean breaking) {
		this.mapConfig = mapConfig;
		this.playerConfig = playerConfig;
		this.delay = delay;
		this.chance = chance;
		this.dropHeight = dropHeight;
		this.breaking = breaking;
	}

	public AnvilDropMapConfig getMapConfig() {
		return this.mapConfig;
	}

	public PlayerConfig getPlayerConfig() {
		return this.playerConfig;
	}

	public int getDelay() {
		return this.delay;
	}

	public double getChance() {
		return this.chance;
	}

	public int getDropHeight() {
		return this.dropHeight;
	}

	public boolean isBreaking() {
		return this.breaking;
	}
}