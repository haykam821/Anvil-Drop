package io.github.haykam821.anvildrop;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.phase.AnvilDropWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.GameType;

public class Main implements ModInitializer {
	private static final String MOD_ID = "anvildrop";

	private static final Identifier ANVIL_DROP_ID = new Identifier(MOD_ID, "anvil_drop");
	public static final GameType<AnvilDropConfig> ANVIL_DROP_TYPE = GameType.register(ANVIL_DROP_ID, AnvilDropConfig.CODEC, AnvilDropWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}
}