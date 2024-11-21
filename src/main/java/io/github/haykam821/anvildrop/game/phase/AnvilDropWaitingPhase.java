package io.github.haykam821.anvildrop.game.phase;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.map.AnvilDropMap;
import io.github.haykam821.anvildrop.game.map.AnvilDropMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class AnvilDropWaitingPhase {
	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;

	public AnvilDropWaitingPhase(GameSpace gameSpace, ServerWorld world, AnvilDropMap map, AnvilDropConfig config) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<AnvilDropConfig> context) {
		AnvilDropMapBuilder mapBuilder = new AnvilDropMapBuilder(context.config());
		AnvilDropMap map = mapBuilder.create();

		RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
			.setGenerator(map.createGenerator(context.server()));

		return context.openWithWorld(worldConfig, (game, world) -> {
			AnvilDropWaitingPhase phase = new AnvilDropWaitingPhase(game.getGameSpace(), world, map, context.config());

			GameWaitingLobby.addTo(game, context.config().getPlayerConfig());
			AnvilDropActivePhase.setRules(game);

			// Listeners
			game.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			game.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			game.listen(GameActivityEvents.REQUEST_START, phase::requestStart);
		});
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, AnvilDropActivePhase.getSpawnPos(this.map)).thenRunForEach(player -> {
			player.changeGameMode(GameMode.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		AnvilDropActivePhase.open(this.gameSpace, this.world, this.map, this.config);
		return GameResult.ok();
	}

	private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		AnvilDropActivePhase.spawn(this.world, this.map, player);
		return EventResult.DENY;
	}
}