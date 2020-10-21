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
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.Game;
import xyz.nucleoid.plasmid.game.GameWorld;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.PlayerRef;

public class AnvilDropActivePhase {
	private final ServerWorld world;
	private final GameWorld gameWorld;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;
	private final Set<PlayerRef> players;
	private boolean singleplayer;
	private boolean opened;
	private int ticksUntilSwitch;
	private int rounds = 0;
	private boolean anvilsDropping = false;

	public AnvilDropActivePhase(GameWorld gameWorld, AnvilDropMap map, AnvilDropConfig config, Set<PlayerRef> players) {
		this.world = gameWorld.getWorld();
		this.gameWorld = gameWorld;
		this.map = map;
		this.config = config;
		this.players = players;
		this.ticksUntilSwitch = this.config.getDelay();
	}

	public static void setRules(Game game) {
		game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
		game.setRule(GameRule.CRAFTING, RuleResult.DENY);
		game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
		game.setRule(GameRule.HUNGER, RuleResult.DENY);
		game.setRule(GameRule.INTERACTION, RuleResult.DENY);
		game.setRule(GameRule.PORTALS, RuleResult.DENY);
		game.setRule(GameRule.PVP, RuleResult.DENY);
	}

	public static void open(GameWorld gameWorld, AnvilDropMap map, AnvilDropConfig config) {
		Set<PlayerRef> players = gameWorld.getPlayers().stream().map(PlayerRef::of).collect(Collectors.toSet());
		AnvilDropActivePhase phase = new AnvilDropActivePhase(gameWorld, map, config, players);

		gameWorld.openGame(game -> {
			AnvilDropActivePhase.setRules(game);

			// Listeners
			game.on(GameOpenListener.EVENT, phase::open);
			game.on(GameTickListener.EVENT, phase::tick);
			game.on(PlayerAddListener.EVENT, phase::addPlayer);
			game.on(PlayerDeathListener.EVENT, phase::onPlayerDeath);
			game.on(PlayerDamageListener.EVENT, phase::onPlayerDamage);
		});
	}

	private void open() {
		this.opened = true;
		this.singleplayer = this.players.size() == 1;

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.world, player -> {
				player.setGameMode(GameMode.ADVENTURE);
				AnvilDropActivePhase.spawn(this.world, this.map, player);
			});
		}
	}

	private void tick() {
		this.ticksUntilSwitch -= 1;
		if (this.ticksUntilSwitch < 0) {
			this.anvilsDropping = !this.anvilsDropping;
			this.ticksUntilSwitch = this.config.getDelay();

			if (this.anvilsDropping) {
				this.map.dropAnvils(this.world);
			} else {
				this.map.clearAnvils(this.world);
				this.rounds += 1;
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
			for (ServerPlayerEntity player : this.gameWorld.getPlayers()) {
				player.sendMessage(endingMessage, false);
			}

			this.gameWorld.close();
		}
	}

	private Text getEndingMessage() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.world)) {
				PlayerEntity winner = winnerRef.getEntity(this.world);
				return new TranslatableText("text.anvildrop.win", winner.getDisplayName(), this.rounds).formatted(Formatting.GOLD);
			}
		}
		return new TranslatableText("text.anvildrop.no_winners", this.rounds).formatted(Formatting.GOLD);
	}

	private void setSpectator(PlayerEntity player) {
		player.setGameMode(GameMode.SPECTATOR);
	}

	private void addPlayer(PlayerEntity player) {
		if (!this.players.contains(PlayerRef.of(player))) {
			this.setSpectator(player);
		} else if (this.opened) {
			this.eliminate(player, true);
		}
	}

	private void eliminate(PlayerEntity eliminatedPlayer, String suffix, boolean remove) {
		Text message = new TranslatableText("text.anvildrop.eliminated" + suffix, eliminatedPlayer.getDisplayName()).formatted(Formatting.RED);
		for (ServerPlayerEntity player : this.gameWorld.getPlayers()) {
			player.sendMessage(message, false);
		}

		if (remove) {
			this.players.remove(PlayerRef.of(eliminatedPlayer));
		}
		this.setSpectator(eliminatedPlayer);
	}

	private void eliminate(PlayerEntity eliminatedPlayer, boolean remove) {
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
		return source == DamageSource.ANVIL || source == DamageSource.FALLING_BLOCK;
	}

	private boolean onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		if (AnvilDropActivePhase.isEliminatingDamageSource(source) && this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, ".falling_anvil", true);
		}
		return true;
	}

	public static void spawn(ServerWorld world, AnvilDropMap map, ServerPlayerEntity player) {
		Vec3d center = map.getPlatformBounds().getCenter();
		player.teleport(world, center.getX(), map.getPlatformBounds().getMin().getY() + 1, center.getZ(), 0, 0);
	}
}