package com.github.zeropointnothing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Commands {
    @FunctionalInterface
    public interface Command {
        int run(ServerCommandSource source, String[] args, CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException;
    }

    /**
     * Custom Exception class to ensure command errors are logged to console.
     * <p>
     * Because Brigadier automatically consumes all thrown exceptions, this exception will automatically print out
     * its error to the log, while giving a display message to the user via the provided context.
     */
    public static class CommandError extends RuntimeException {
        private static final String[] ERROR_MESSAGES = new String[] {
                "Whoops! IBG tripped over its own logic!",
                "Reality fractured. Please send cookies.",
                "The forbidden artifact has awoken.",
                "Command execution yeeted into the void.",
                "IBG encountered an existential crisis.",
                "Well, that wasn’t supposed to happen...",
                "Wait- What?",
                "The end is nigh!",
                "The sky is falling, the sky is falling!",
                "Hoo, boy! That right there's an error!",
                "Ruh roh! An error occurred!",
                "Uhh... What now?",
                "I... don't think that's right.",
                "Hold up! Wait a minute! Something ain't right!"
        };

        private static final Random RANDOM = new Random();

        public CommandError(String message, CommandContext<ServerCommandSource> context) {
            super(message);
            String flair = ERROR_MESSAGES[RANDOM.nextInt(ERROR_MESSAGES.length)];
            context.getSource().sendError(Text.literal("= [IBG ERROR] =\n'"+flair + "'\nInform your server admin!\n==="));

            ItemsBegone.LOGGER.error("[COMMAND EXECUTION FAILURE]");
            ItemsBegone.LOGGER.error("Context: {}", context.getInput());
            ItemsBegone.LOGGER.error("Exception: ", this);
        }
    }

    /**
     * 'Argument Builder' designed to assist with the creation of commands.
     * <p>
     * This function is internal, so you likely shouldn't be looking here.
     * @param arguments The arguments to string together
     * @param callback The final callback to attach to the argument chain
     * @return The created argument chain
     */
    private static ArgumentBuilder<ServerCommandSource, ?> chainArguments(
            List<ArgumentBuilder<ServerCommandSource, ?>> arguments,
            Command callback
    ) {
        // Attach 'executes' to the deepest argument
        ArgumentBuilder<ServerCommandSource, ?> tail = arguments.get(arguments.size() - 1);
        tail.executes(ctx -> callback.run(ctx.getSource(), new String[0], ctx));

        // Fold from bottom up
        for (int i = arguments.size() - 2; i >= 0; i--) {
            ArgumentBuilder<ServerCommandSource, ?> parent = arguments.get(i);
            parent.then(tail);
            tail = parent;
        }

        return tail;
    }

    /**
     * A 'command builder' wrapper designed to fix Brigadier's garbage syntax and make things a bit easier to read.
     * <p>
     * 'Sub' commands should be formatted like so "level1/level2/level3", with the command name (ex. cmd) being at
     * the end of that chain. ("/level1 level2 level3 cmd")
     * <p>
     * To have no 'sub' commands, just set the 'sub' argument to the name of the command.
     * @param dispatcher The CommandDispatcher to register this command to
     * @param name The name of the command
     * @param sub The 'sub' command path to attach this command to
     * @param permissionLevel The required permission level needed to execute this command
     * @param arguments Any arguments this command accepts
     * @param callback The callback to be run upon this commands execution
     */
    public static void build(
            CommandDispatcher<ServerCommandSource> dispatcher,
            String name,
            String sub,
            int permissionLevel,
            List<ArgumentBuilder<ServerCommandSource, ?>> arguments,
            Command callback
    ) {
        // Final executable node, otherwise known as the 'leaf'.
        LiteralArgumentBuilder<ServerCommandSource> leaf = CommandManager.literal(name);

        if (arguments.isEmpty()) {
            leaf.requires(src -> src.hasPermissionLevel(permissionLevel))
                    .executes(ctx -> callback.run(ctx.getSource(), new String[0], ctx));
        } else {
            leaf.requires(src -> src.hasPermissionLevel(permissionLevel))
                    .then(chainArguments(arguments, callback));
        }

        // Split subcommand chain
        List<String> subList = sub == null || sub.isBlank()
                ? List.of()
                : List.of(sub.split("/"));

        // Build subcommand chain in reverse (our branches)
        // ex. ibg -> debug -> testing -> hello
        LiteralArgumentBuilder<ServerCommandSource> finalBranch = leaf;

        if ((!Objects.equals(subList.get(0), name))) {
            for (int i = subList.size() - 1; i >= 0; i--) {
                LiteralArgumentBuilder<ServerCommandSource> branch = CommandManager.literal(subList.get(i));
                branch.then(finalBranch);
                finalBranch = branch;
            }
        }

        // supply the completed branch
        dispatcher.register(finalBranch);
    }


    /**
     * Basic suggestion builder that fetches all scoreboard teams, alongside the 'global' fake team.
      */
    public static CompletableFuture<Suggestions> teamSuggest(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        builder.suggest("global");
        MinecraftServer server = ctx.getSource().getServer();
        Collection<Team> teams = server.getScoreboard().getTeams();
        for (Team team : teams) {
            builder.suggest(team.getName());
        }
        return builder.buildFuture();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
            build(commandDispatcher, "hello", "ibg/debug/testing", 0, List.of(),
                    (source, args, ctx) -> {
                ctx.getSource().sendFeedback(() -> Text.literal("Hello, world! IBG is online!"),false);

                return 1;
            });

            build(commandDispatcher, "get", "ibg/blacklist", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest)
            ), ((source, args, ctx) -> {
                try {
                    String team_name = StringArgumentType.getString(ctx, "team");
                    Config.TeamConfig team;
                    try {
                        team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                    } catch (Config.NoSuchTeamException e) {
                        ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                        return 0;
                    }

                    ctx.getSource().sendFeedback(() -> Text.literal("// %s (%s):\n\nnamespaces:\n%s\nitems:\n%s".formatted(
                            team.name, team.enabled?"enabled":"disabled", team.namespace_blacklist.toString(), team.item_blacklist.toString())
                    ), false);
                    return 1;
                } catch (Exception e) {
                    throw new CommandError("%s: %s".formatted(e.getClass().getName(), e.getMessage()), ctx);
                }
            }));

            build(commandDispatcher, "add", "ibg/namespace", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest),
                    CommandManager.argument("namespace", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                // Suggest known namespaces
                                Registries.ITEM.stream()
                                        .map(item -> Registries.ITEM.getId(item).getNamespace())
                                        .distinct()
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
            ), ((source, args, ctx) -> {
                String team_name = StringArgumentType.getString(ctx, "team");
                Config.TeamConfig team;
                try {
                    team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                } catch (Config.NoSuchTeamException e) {
                    ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                    return 0;
                }

                String namespace = StringArgumentType.getString(ctx, "namespace");
                Set<String> validNamespaces = Registries.ITEM.stream()
                        .map(item -> Registries.ITEM.getId(item).getNamespace())
                        .collect(Collectors.toSet());
                if (!validNamespaces.contains(namespace)) {
                    ctx.getSource().sendError(Text.literal("No such namespace '%s'!".formatted(namespace)));
                    return 0;
                }
                if (ItemsBegone.isBlacklistedNamespace(namespace, team_name)) {
                    ctx.getSource().sendError(Text.literal(
                            "Team '%s' already has the namespace '%s' blacklisted!".formatted(team_name, namespace)
                    ));
                    return 0;
                } else {
                    team.namespace_blacklist.add(namespace);
                    ConfigLoader.saveConfig();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "Added the namespace '%s' to '%s''s blacklist!".formatted(namespace, team_name)
                    ), false);
                    return 1;
                }
            }));

            build(commandDispatcher, "del", "ibg/namespace", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest),
                    CommandManager.argument("namespace", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                // Suggest known namespace: electric boogaloo
                                Registries.ITEM.stream()
                                        .map(item -> Registries.ITEM.getId(item).getNamespace())
                                        .distinct()
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
            ), ((source, args, ctx) -> {
                String team_name = StringArgumentType.getString(ctx, "team");
                Config.TeamConfig team;
                try {
                    team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                } catch (Config.NoSuchTeamException e) {
                    ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                    return 0;
                }
                String namespace = StringArgumentType.getString(ctx, "namespace");
                Set<String> validNamespaces = Registries.ITEM.stream()
                        .map(item -> Registries.ITEM.getId(item).getNamespace())
                        .collect(Collectors.toSet());

                if (!validNamespaces.contains(namespace)) {
                    ctx.getSource().sendError(Text.literal("No such namespace '%s'".formatted(namespace)));
                    return 0;
                }
                if (!ItemsBegone.isBlacklistedNamespace(namespace, team_name)) {
                    ctx.getSource().sendError(Text.literal(
                            "Team '%s' already does not have the namespace '%s' blacklisted!".formatted(team_name, namespace)
                    ));
                    return 0;
                } else {
                    team.namespace_blacklist.remove(namespace);
                    ConfigLoader.saveConfig();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "Removed the namespace '%s' from '%s''s blacklisted!".formatted(namespace,team_name)
                    ), false);
                    return 1;
                }
            }));

            build(commandDispatcher, "add", "ibg/item", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest),
                    CommandManager.argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess))
            ), ((source, args, ctx) -> {
                String team_name = StringArgumentType.getString(ctx, "team");
                Config.TeamConfig team;
                try {
                    team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                } catch (Config.NoSuchTeamException e) {
                    ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                    return 0;
                }
                Item item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();
                String id = Registries.ITEM.getId(item).toString();
                if (ItemsBegone.isBlacklisted(item.getDefaultStack(), team_name)) {
                    ctx.getSource().sendError(Text.literal("Team '%s' already has the item '%s' blacklisted!".formatted(
                            team_name,
                            id
                    )));
                    return 0;
                } else {
                    team.item_blacklist.add(id);
                    ConfigLoader.saveConfig();
                    ctx.getSource().sendFeedback(() -> Text.literal("Added the item '%s' to '%s''s blacklist!".formatted(
                            id,
                            team_name
                    )),false);
                    return 1;
                }
            }));

            build(commandDispatcher, "del", "ibg/item", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest),
                    CommandManager.argument("item", ItemStackArgumentType.itemStack(commandRegistryAccess))
            ), ((source, args, ctx) -> {
                String team_name = StringArgumentType.getString(ctx, "team");
                Config.TeamConfig team;
                try {
                    team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                } catch (Config.NoSuchTeamException e) {
                    ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                    return 0;
                }
                Item item = ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem();
                String id = Registries.ITEM.getId(item).toString();

                if (!ItemsBegone.isBlacklisted(item.getDefaultStack(), team_name)) {
                    ctx.getSource().sendError(Text.literal("Team '%s' does not have the item '%s' blacklisted!".formatted(
                            team_name,
                            id
                    )));
                    return 0;
                } else {
                    team.item_blacklist.remove(id);
                    ConfigLoader.saveConfig();
                    ctx.getSource().sendFeedback(() -> Text.literal("Removed the item '%s' from '%s''s blacklist!".formatted(
                            id,
                            team_name
                    )),false);
                    return 1;
                }
            }));

            build(commandDispatcher, "set_enabled", "ibg/blacklist", 2, List.of(
                    CommandManager.argument("team", StringArgumentType.word()).suggests(Commands::teamSuggest),
                    CommandManager.argument("enabled", BoolArgumentType.bool())
            ), ((source, args, ctx) -> {
                String team_name = StringArgumentType.getString(ctx, "team");
                Config.TeamConfig team;
                try {
                    team = ConfigLoader.CONFIG.blacklist.getTeam(team_name);
                } catch (Config.NoSuchTeamException e) {
                    ctx.getSource().sendError(Text.literal("No such team '%s'!".formatted(team_name)));
                    return 0;
                }

                team.enabled = BoolArgumentType.getBool(ctx, "enabled");
                ConfigLoader.saveConfig();
                ctx.getSource().sendFeedback(() -> Text.literal(
                        "Switched team '%s' enabled state to: %s!".formatted(team_name, team.enabled)
                ), false);
                return 1;
            }));
        }));
    }

//        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("foo")
//                .executes(context -> {
//                    // For versions below 1.19, replace "Text.literal" with "new LiteralText".
//                    // For versions below 1.20, remode "() ->" directly.
//                    context.getSource().sendFeedback(() -> Text.literal("Called /foo with no arguments"), false);
//
//                    return 1;
//                })));
//    }
}
