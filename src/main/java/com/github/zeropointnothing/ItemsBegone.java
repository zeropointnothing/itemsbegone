package com.github.zeropointnothing;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ItemsBegone implements ModInitializer {
	public static final String MOD_ID = "items-begone";
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Boolean isBlacklisted(ItemStack stack, String team_name) {
		Identifier id = Registries.ITEM.getId(stack.getItem());

		return ConfigLoader.CONFIG.blacklist.getTeam(team_name).enabled
				&& (ConfigLoader.CONFIG.blacklist.getTeam(team_name).namespace_blacklist.contains(id.getNamespace())
				|| ConfigLoader.CONFIG.blacklist.getTeam(team_name).item_blacklist.contains(id.toString())
				|| ConfigLoader.CONFIG.blacklist.getTeam("global").namespace_blacklist.contains(id.getNamespace())
				|| ConfigLoader.CONFIG.blacklist.getTeam("global").item_blacklist.contains(id.toString()));
	}

	public static String getTeam(PlayerEntity player) {
		String team;
		try {
			team = Objects.requireNonNull(player.getScoreboardTeam()).getName();
		} catch (NullPointerException e) { // Player isn't on a team
			team = "global";
		}

		return team;
	}

	private static ActionResult checkActiveHand(PlayerEntity player, World world, Hand hand) {
		String team = getTeam(player);

		try {
			ItemStack holding = player.getStackInHand(hand);
			boolean blacklisted = isBlacklisted(holding, team);
			if (blacklisted) {
				LOGGER.info("Player '{}' attempted to use blacklisted item ({})!", player.getName(), holding);
				// While we're here, drop every item that isn't blacklisted.
				for (int i = 0; i < player.getInventory().size(); i++) {
					ItemStack stack = player.getInventory().getStack(i);
					if (isBlacklisted(stack, team)) {
						if (!ConfigLoader.CONFIG.delete_on_deny) {
							player.dropStack(stack.copy()); // Drop copy to avoid skipping next index
						}
						player.getInventory().setStack(i, ItemStack.EMPTY);
					}
				}

				return ActionResult.FAIL;
			} else {
				return ActionResult.PASS;
			}
		} catch (Config.NoSuchTeamException e) {
			LOGGER.warn("Unable to determine the blacklist! Fix your config! Original error: ", e);
			LOGGER.warn("Packet will be failed for safety...");
			return ActionResult.FAIL;
		} catch (RuntimeException e) {
			throw new RuntimeException("An error occurred while checking the blacklist! Your config is likely misconfigured! Original error: ", e);
		}
	}
	public static void checkInventory(PlayerEntity player) {
		String team = getTeam(player);
		ItemStack detected = null;

		for (int i=0; i<player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (isBlacklisted(stack, team)) {
				if (detected == null) {
					detected = stack;
				}
				if (!ConfigLoader.CONFIG.delete_on_deny) {
					player.dropStack(stack.copy());
				}
				player.getInventory().setStack(i, ItemStack.EMPTY);
			}
		}

		if (detected != null) {
			LOGGER.info("Player '{}' attempted to pick up blacklisted item ({})!", player.getName(), detected);
		}
	}

	private static TypedActionResult<ItemStack> typed_checkEventCallback(PlayerEntity player, World world, Hand hand) {
		ActionResult result = checkActiveHand(player, world, hand);
		if (result == ActionResult.PASS) {
			return TypedActionResult.pass(player.getStackInHand(hand));
		} else if (result == ActionResult.FAIL) {
			return TypedActionResult.fail(player.getStackInHand(hand));
		}
		throw new RuntimeException("Unhandled result for typed_checkEventCallback! Inform the developer of this problem!");
	}

	@Override
	public void onInitialize() {
		ConfigLoader.loadConfig();
		LOGGER.info("Hello Fabric world!");
		LOGGER.info("Initialized Config with team values set to:");
		for (Config.TeamConfig team : ConfigLoader.CONFIG.blacklist.teams) {
			LOGGER.info("// {} // ({})", team.name, team.enabled ? "enabled" : "disabled");
			LOGGER.info("// NAMESPACE: {}", team.namespace_blacklist);
			LOGGER.info("// ITEM: {}", team.item_blacklist);
		}

		UseBlockCallback.EVENT.register((a1,a2,a3,a4) -> checkActiveHand(a1,a2,a3));
		AttackBlockCallback.EVENT.register((a1,a2,a3,a4,a5) -> checkActiveHand(a1,a2,a3));
		UseEntityCallback.EVENT.register((a1,a2,a3,a4,a5) -> checkActiveHand(a1,a2,a3));
		AttackEntityCallback.EVENT.register((a1,a2,a3,a4,a5) -> checkActiveHand(a1,a2,a3));
		UseItemCallback.EVENT.register(ItemsBegone::typed_checkEventCallback);



//		ServerTickEvents.END_SERVER_TICK.register((server) -> {
//			if (++tickCounter % 40 != 0) return; // every ~2 seconds (20 ticks/sec)
//
//			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
////				Hand hand = player.getActiveHand();
////				World world = player.getWorld();
//
//				checkInventory(player);
//			}
//		});
	}
}
