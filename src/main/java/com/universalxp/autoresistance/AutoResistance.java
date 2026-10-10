package com.universalxp.autoresistance;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AutoResistance by Universal_XP_
 *
 * - A hit that would kill a player (fall damage, mobs, players, ...) does NOT kill them.
 *   They are left at 1 heart and get infinite Resistance 99 (full damage immunity).
 * - While at 1 heart (2 HP or less) the effect is kept on the player.
 * - Once they heal up to 2 hearts (4 HP or more) the effect is removed.
 * - Each player can switch the whole thing on/off with /autoresistance.
 *
 * The void and /kill still kill, otherwise a player falling into the void would be saved forever.
 */
public class AutoResistance implements ModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("AutoResistance");

	/** 1 heart = 2 HP. */
	private static final float ONE_HEART = 2.0f;

	/** 2 hearts = 4 HP. Effect is removed at or above this. */
	private static final float REMOVE_AT_OR_ABOVE = 4.0f;

	/** Effect amplifier. 99 = "Resistance 99" (level 100). Anything from 4 up is full immunity. */
	private static final int AMPLIFIER = 99;

	@Override
	public void onInitialize() {
		AutoResistanceConfig.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));

		// Death save: turn a lethal hit into "1 heart + Resistance 99"
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			if (!AutoResistanceConfig.isEnabled(player.getUuid())) {
				return true;
			}
			if (damageSource.isOf(DamageTypes.OUT_OF_WORLD) || damageSource.isOf(DamageTypes.GENERIC_KILL)) {
				return true; // let the void and /kill work normally
			}

			player.setHealth(ONE_HEART);
			applyEffect(player);
			return false; // cancel death
		});

		// Keep the effect while at 1 heart, remove it at 2 hearts
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tick(player);
			}
		});
	}

	private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("autoresistance")
				.executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					return setState(ctx.getSource(), player, !AutoResistanceConfig.isEnabled(player.getUuid()));
				})
				.then(CommandManager.literal("on").executes(ctx ->
						setState(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), true)))
				.then(CommandManager.literal("off").executes(ctx ->
						setState(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), false)))
				.then(CommandManager.literal("toggle").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					return setState(ctx.getSource(), player, !AutoResistanceConfig.isEnabled(player.getUuid()));
				}))
				.then(CommandManager.literal("status").executes(ctx -> {
					ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
					boolean on = AutoResistanceConfig.isEnabled(player.getUuid());
					ctx.getSource().sendFeedback(() -> statusText(on), false);
					return 1;
				})));
	}

	private static int setState(ServerCommandSource source, ServerPlayerEntity player, boolean enabled) {
		AutoResistanceConfig.setEnabled(player.getUuid(), enabled);
		if (!enabled && hasOurEffect(player)) {
			player.removeStatusEffect(StatusEffects.RESISTANCE);
		}
		source.sendFeedback(() -> statusText(enabled), false);
		return 1;
	}

	private static Text statusText(boolean enabled) {
		return Text.literal("AutoResistance: ").formatted(Formatting.GOLD)
				.append(Text.literal(enabled ? "ON" : "OFF").formatted(enabled ? Formatting.GREEN : Formatting.RED));
	}

	private static void tick(ServerPlayerEntity player) {
		if (!player.isAlive()) {
			return;
		}

		boolean hasOurEffect = hasOurEffect(player);

		if (!AutoResistanceConfig.isEnabled(player.getUuid())) {
			if (hasOurEffect) {
				player.removeStatusEffect(StatusEffects.RESISTANCE);
			}
			return;
		}

		float health = player.getHealth();

		if (health <= ONE_HEART) {
			if (!hasOurEffect) {
				applyEffect(player);
			}
		} else if (health >= REMOVE_AT_OR_ABOVE && hasOurEffect) {
			player.removeStatusEffect(StatusEffects.RESISTANCE);
		}
	}

	private static boolean hasOurEffect(ServerPlayerEntity player) {
		StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
		return current != null
				&& current.getDuration() == StatusEffectInstance.INFINITE
				&& current.getAmplifier() == AMPLIFIER;
	}

	private static void applyEffect(ServerPlayerEntity player) {
		// infinite duration, no particles, icon shown in the HUD
		player.addStatusEffect(new StatusEffectInstance(
				StatusEffects.RESISTANCE,
				StatusEffectInstance.INFINITE,
				AMPLIFIER,
				false,
				false,
				true
		));
	}
}
