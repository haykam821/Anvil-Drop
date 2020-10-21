package io.github.haykam821.anvildrop.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class AnvilDropMapConfig {
	public static final Codec<AnvilDropMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Codec.INT.fieldOf("x").forGetter(AnvilDropMapConfig::getX),
			Codec.INT.fieldOf("z").forGetter(AnvilDropMapConfig::getZ),
			Codec.BOOL.optionalFieldOf("walls", true).forGetter(AnvilDropMapConfig::hasWalls)
		).apply(instance, AnvilDropMapConfig::new);
	});

	private final int x;
	private final int z;
	private final boolean walls;

	public AnvilDropMapConfig(int x, int z, boolean walls) {
		this.x = x;
		this.z = z;
		this.walls = walls;
	}

	public int getX() {
		return this.x;
	}

	public int getZ() {
		return this.z;
	}

	public boolean hasWalls() {
		return this.walls;
	}
}