package io.github.haykam821.anvildrop.game.phase;

import java.util.concurrent.CompletableFuture;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.map.AnvilDropMap;
import io.github.haykam821.anvildrop.game.map.AnvilDropMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.world.bubble.BubbleWorldConfig;

public class AnvilDropWaitingPhase {
	private final GameWorld gameWorld;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;

	public AnvilDropWaitingPhase(GameWorld gameWorld, AnvilDropMap map, AnvilDropConfig config) {
		this.gameWorld = gameWorld;
		this.map = map;
		this.config = config;
	}

	public static CompletableFuture<GameWorld> open(GameOpenContext<AnvilDropConfig> context) {
		AnvilDropMapBuilder mapBuilder = new AnvilDropMapBuilder(context.getConfig());

		return mapBuilder.create().thenCompose(map -> {
			BubbleWorldConfig worldConfig = new BubbleWorldConfig()
				.setGenerator(map.createGenerator(context.getServer()))
				.setDefaultGameMode(GameMode.ADVENTURE);

			return context.openWorld(worldConfig).thenApply(gameWorld -> {
				AnvilDropWaitingPhase phase = new AnvilDropWaitingPhase(gameWorld, map, context.getConfig());

				return GameWaitingLobby.open(gameWorld, context.getConfig().getPlayerConfig(), game -> {
					AnvilDropActivePhase.setRules(game);

					// Listeners
					game.on(PlayerAddListener.EVENT, phase::addPlayer);
					game.on(PlayerDeathListener.EVENT, phase::onPlayerDeath);
					game.on(OfferPlayerListener.EVENT, phase::offerPlayer);
					game.on(RequestStartListener.EVENT, phase::requestStart);
				});
			});
		});
	}

	private boolean isFull() {
		return this.gameWorld.getPlayerCount() >= this.config.getPlayerConfig().getMaxPlayers();
	}

	private JoinResult offerPlayer(ServerPlayerEntity player) {
		return this.isFull() ? JoinResult.gameFull() : JoinResult.ok();
	}

	private StartResult requestStart() {
		PlayerConfig playerConfig = this.config.getPlayerConfig();
		if (this.gameWorld.getPlayerCount() < playerConfig.getMinPlayers()) {
			return StartResult.NOT_ENOUGH_PLAYERS;
		}

		AnvilDropActivePhase.open(this.gameWorld, this.map, this.config);
		return StartResult.OK;
	}

	private void addPlayer(ServerPlayerEntity player) {
		AnvilDropActivePhase.spawn(this.gameWorld.getWorld(), this.map, player);
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		AnvilDropActivePhase.spawn(this.gameWorld.getWorld(), this.map, player);
		return ActionResult.FAIL;
	}
}