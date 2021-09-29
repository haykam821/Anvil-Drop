package io.github.haykam821.anvildrop.game.map;

import java.util.Iterator;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public class AnvilDropMapBuilder {
	private static final BlockState FLOOR = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState FLOOR_OUTLINE = Blocks.NETHERITE_BLOCK.getDefaultState();
	private static final BlockState WALL = Blocks.END_STONE_BRICK_WALL.getDefaultState();
	private static final BlockState WALL_TOP = Blocks.BIRCH_SLAB.getDefaultState();
	private static final BlockState BARRIER = Blocks.BARRIER.getDefaultState();

	private final AnvilDropConfig config;

	public AnvilDropMapBuilder(AnvilDropConfig config) {
		this.config = config;
	}

	public AnvilDropMap create() {
		MapTemplate template = MapTemplate.createEmpty();
		AnvilDropMapConfig mapConfig = this.config.getMapConfig();

		BlockBounds bounds = BlockBounds.of(BlockPos.ORIGIN, new BlockPos(mapConfig.getX() + 1, 3, mapConfig.getZ() + 1));
		this.build(bounds, template, mapConfig);

		BlockBounds clearBounds = BlockBounds.of(new BlockPos(1, 1, 1), new BlockPos(mapConfig.getX(), 1, mapConfig.getZ()));
		BlockBounds dropBounds = clearBounds.offset(new BlockPos(0, this.config.getDropHeight(), 0));

		return new AnvilDropMap(template, config, bounds, clearBounds, dropBounds);
	}

	private BlockState getBlockState(BlockPos pos, BlockBounds bounds, AnvilDropMapConfig mapConfig) {
		int layer = pos.getY() - bounds.min().getY();
		boolean outline = pos.getX() == bounds.min().getX() || pos.getX() == bounds.max().getX() || pos.getZ() == bounds.min().getZ() || pos.getZ() == bounds.max().getZ();

		if (outline) {
			if (layer == 0) {
				return FLOOR_OUTLINE;
			} else if (layer == 1) {
				return WALL;
			} else if (layer == 2) {
				return WALL_TOP;
			} else if (layer == 3) {
				return BARRIER;
			}
		} else if (layer == 0) {
			return FLOOR;
		}

		return null;
	}

	public void build(BlockBounds bounds, MapTemplate template, AnvilDropMapConfig mapConfig) {
		Iterator<BlockPos> iterator = bounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();

			BlockState state = this.getBlockState(pos, bounds, mapConfig);
			if (state != null) {
				template.setBlockState(pos, state);
			}
		}
	}
}