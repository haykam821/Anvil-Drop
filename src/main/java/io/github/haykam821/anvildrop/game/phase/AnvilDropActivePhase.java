package io.github.haykam821.anvildrop.game.phase;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.map.AnvilDropMap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameActivity;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class AnvilDropActivePhase {
	private final ServerWorld world;
	private final GameSpace gameSpace;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;
	private final Set<PlayerRef> players;
	private boolean singleplayer;
	private boolean opened;
	private int ticksUntilSwitch;
	private int ticksUntilClose = -1;
	private int rounds = 0;
	private boolean anvilsDropping = false;

	public AnvilDropActivePhase(GameSpace gameSpace, ServerWorld world, AnvilDropMap map, AnvilDropConfig config, Set<PlayerRef> players) {
		this.world = world;
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.players = players;
		this.ticksUntilSwitch = this.config.getDelay();
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.INTERACTION);
		activity.deny(GameRuleType.PORTALS);
		activity.deny(GameRuleType.PVP);
	}

	public static void open(GameSpace gameSpace, ServerWorld world, AnvilDropMap map, AnvilDropConfig config) {
		Set<PlayerRef> players = gameSpace.getPlayers().stream().map(PlayerRef::of).collect(Collectors.toSet());
		AnvilDropActivePhase phase = new AnvilDropActivePhase(gameSpace, world, map, config, players);

		gameSpace.setActivity(activity -> {
			AnvilDropActivePhase.setRules(activity);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.OFFER, phase::offerPlayer);
			activity.listen(GamePlayerEvents.REMOVE, phase::removePlayer);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(PlayerDamageEvent.EVENT, phase::onPlayerDamage);
		});
	}

	private void enable() {
		this.opened = true;
		this.singleplayer = this.players.size() == 1;

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.world, player -> {
				this.updateRoundsExperienceLevel(player);
				player.changeGameMode(GameMode.ADVENTURE);
				AnvilDropActivePhase.spawn(this.world, this.map, player);
			});
		}
	}

	private void updateRoundsExperienceLevel(ServerPlayerEntity player) {
		player.setExperienceLevel(this.rounds + 1);
	}

	private void setRounds(int rounds) {
		this.rounds = rounds;
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			this.updateRoundsExperienceLevel(player);
		}
	}

	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		this.ticksUntilSwitch -= 1;
		if (this.ticksUntilSwitch < 0) {
			this.anvilsDropping = !this.anvilsDropping;
			this.ticksUntilSwitch = this.config.getDelay();

			if (this.anvilsDropping) {
				this.map.dropAnvils(this.world);
			} else {
				this.map.clearAnvils(this.world);
				this.setRounds(this.rounds + 1);
			}
		}

		// Eliminate players that are outside of the arena
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			playerRef.ifOnline(this.world, player -> {
				if (!this.map.getBox().contains(player.getPos())) {
					this.eliminate(player, player.getY() < this.map.getBox().minY ? ".hole_in_floor" : ".out_of_bounds", false);
					playerIterator.remove();
				}
			});
		}

		// Attempt to determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			
			Text endingMessage = this.getEndingMessage();
			for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
				player.sendMessage(endingMessage, false);
			}

			this.ticksUntilClose = this.config.getTicksUntilClose().get(this.world.getRandom());
		}
	}

	private Text getEndingMessage() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.world)) {
				PlayerEntity winner = winnerRef.getEntity(this.world);
				return Text.translatable("text.anvildrop.win", winner.getDisplayName(), this.rounds).formatted(Formatting.GOLD);
			}
		}
		return Text.translatable("text.anvildrop.no_winners", this.rounds).formatted(Formatting.GOLD);
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private void setSpectator(ServerPlayerEntity player) {
		player.changeGameMode(GameMode.SPECTATOR);
	}

	private PlayerOfferResult offerPlayer(PlayerOffer offer) {
		return offer.accept(this.world, AnvilDropActivePhase.getSpawnPos(this.map)).and(() -> {
			this.updateRoundsExperienceLevel(offer.player());
			this.setSpectator(offer.player());
		});
	}

	private void removePlayer(ServerPlayerEntity player) {
		this.eliminate(player, true);
	}

	private void eliminate(ServerPlayerEntity eliminatedPlayer, String suffix, boolean remove) {
		if (this.isGameEnding()) return;

		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) return;

		Text message = Text.translatable("text.anvildrop.eliminated" + suffix, eliminatedPlayer.getDisplayName()).formatted(Formatting.RED);
		for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
			player.sendMessage(message, false);
		}

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);
	}

	private void eliminate(ServerPlayerEntity eliminatedPlayer, boolean remove) {
		this.eliminate(eliminatedPlayer, "", remove);
	}

	private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		if (this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, true);
		} else {
			AnvilDropActivePhase.spawn(this.world, this.map, player);
		}
		return ActionResult.FAIL;
	}

	private static boolean isEliminatingDamageSource(DamageSource source) {
		return source.isFallingBlock();
	}

	private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		if (AnvilDropActivePhase.isEliminatingDamageSource(source) && this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, ".falling_anvil", true);
		}
		return ActionResult.SUCCESS;
	}

	public static void spawn(ServerWorld world, AnvilDropMap map, ServerPlayerEntity player) {
		Vec3d spawnPos = AnvilDropActivePhase.getSpawnPos(map);
		player.teleport(world, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 0, 0);
	}

	protected static Vec3d getSpawnPos(AnvilDropMap map) {
		Vec3d center = map.getPlatformBounds().center();
		return new Vec3d(center.getX(), map.getPlatformBounds().min().getY() + 1, center.getZ());
	}
}