package io.github.haykam821.anvildrop.game.map;

import java.util.Iterator;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.game.world.generator.TemplateChunkGenerator;

public class AnvilDropMap {
	private static final BlockState CLEAR_STATE = Blocks.AIR.getDefaultState();
	private static final BlockState ANVIL_STATE = Blocks.ANVIL.getDefaultState();
	private static final BlockState ALTERNATE_ANVIL_STATE = Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.EAST);

	private final MapTemplate template;
	private final AnvilDropConfig config;
	private final BlockBounds platformBounds;
	private final Box box;
	private final BlockBounds clearBounds;
	private final BlockBounds dropBounds;

	public AnvilDropMap(MapTemplate template, AnvilDropConfig config, BlockBounds platformBounds, BlockBounds clearBounds, BlockBounds dropBounds) {
		this.template = template;
		this.config = config;

		this.platformBounds = platformBounds;
		this.box = this.platformBounds.asBox().expand(-1, -0.5, -1);

		this.clearBounds = clearBounds;
		this.dropBounds = dropBounds;
	}

	public BlockBounds getPlatformBounds() {
		return this.platformBounds;
	}

	public Box getBox() {
		return this.box;
	}

	public void clearAnvils(ServerWorld world) {
		Iterator<BlockPos> iterator = this.clearBounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();
			if (this.config.isBreaking() && !world.isAir(pos)) {
				world.breakBlock(pos.withY(0), false);
			}
			world.setBlockState(pos, CLEAR_STATE);
		}
	}

	public void dropAnvils(ServerWorld world) {
		Iterator<BlockPos> iterator = this.dropBounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();
			if (world.getRandom().nextDouble() < this.config.getChance()) {
				BlockState state = world.getRandom().nextBoolean() ? ANVIL_STATE : ALTERNATE_ANVIL_STATE;
				world.setBlockState(pos, state, 0);
			}
		}
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}