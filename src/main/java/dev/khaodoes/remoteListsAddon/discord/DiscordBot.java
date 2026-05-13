package dev.khaodoes.remoteListsAddon.discord;

import dev.khaodoes.remoteListsAddon.RemoteListsAddon;
import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
    private final String token;
    private JDA jda;
    private boolean running;

    public DiscordBot(String token) { this.token = token; }

    public synchronized void start() {
        if (running) return;
        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                // note: probably to be removed, or maybe make it a config option? idk, it's just a fun little touch
                .setActivity(Activity.playing("with player lists"))
                .addEventListeners(this)
                .build().awaitReady();
            jda.updateCommands().addCommands(
                Commands.slash("lists", "Show all available player lists"),
                Commands.slash("players", "Show all players across all lists"),
                Commands.slash("check", "Check if a player is in any list")
                    .addOption(OptionType.STRING, "name", "The player's username", true),
                Commands.slash("add", "Add a player to a list")
                    .addOption(OptionType.STRING, "name", "The player's username", true)
                    .addOption(OptionType.STRING, "list", "List ID (default: meteor_friends)", false),
                Commands.slash("remove", "Remove a player from a list")
                    .addOption(OptionType.STRING, "name", "The player's username", true)
                    .addOption(OptionType.STRING, "list", "List ID (default: meteor_friends)", false)
            ).queue();
            running = true;
            RemoteListsAddon.LOG.info("Discord bot started");
        } catch (Exception e) {
            RemoteListsAddon.LOG.error("Failed to start Discord bot", e);
        }
    }

    public synchronized void stop() {
        if (jda != null) {
            jda.shutdown();
            running = false;
            RemoteListsAddon.LOG.info("Discord bot stopped");
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void onReady(ReadyEvent e) {
        RemoteListsAddon.LOG.info("Discord bot logged in as {}", e.getJDA().getSelfUser().getEffectiveName());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e) {
        switch (e.getName()) {
            case "lists" -> reply(e, listsMsg());
            case "players" -> reply(e, playersMsg());
            case "check" -> check(e);
            case "add" -> add(e);
            case "remove" -> remove(e);
        }
    }

    private String listsMsg() {
        List<PlayerList> lists = RemoteListsAddon.LIST_SERVICE.getAllLists();
        if (lists.isEmpty()) return "No lists available.";
        return "**Available Lists:**\n" + lists.stream()
            .map(l -> "**" + l.displayName() + "** (`" + l.id() + "`) — " + l.entries().size() + " player(s)")
            .collect(Collectors.joining("\n"));
    }

    private String playersMsg() {
        List<PlayerEntry> players = RemoteListsAddon.LIST_SERVICE.getAllPlayers();
        if (players.isEmpty()) return "No players found across any list.";
        String header = "**All Players (" + players.size() + "):**\n";
        String items = players.stream().limit(100)
            .map(p -> "- " + p.username() + (p.uuid() != null ? " (`" + p.uuid() + "`)" : ""))
            .collect(Collectors.joining("\n"));
        return players.size() > 20 ? header + items + "\n*...and " + (players.size() - 20) + " more*" : header + items;
    }

    private void check(SlashCommandInteractionEvent e) {
        String name = e.getOption("name").getAsString();
        List<String> inLists = RemoteListsAddon.LIST_SERVICE.getAllLists().stream()
            .filter(l -> l.entries().stream().anyMatch(entry -> entry.username().equalsIgnoreCase(name)))
            .map(PlayerList::displayName).toList();
        reply(e, "**" + name + "** is " + (inLists.isEmpty() ? "not in any list." : "present in: " + String.join(", ", inLists)));
    }

    private void add(SlashCommandInteractionEvent e) {
        String name = e.getOption("name").getAsString();
        String listId = e.getOption("list") != null
            ? e.getOption("list").getAsString()
            : "meteor_friends";
        boolean ok = RemoteListsAddon.LIST_SERVICE.addPlayer(listId, name);
        reply(e, ok
            ? "Added **" + name + "** to `" + listId + "`."
            : "**" + name + "** is already in `" + listId + "` or that list doesn't exist.");
    }

    private void remove(SlashCommandInteractionEvent e) {
        String name = e.getOption("name").getAsString();
        String listId = e.getOption("list") != null
            ? e.getOption("list").getAsString()
            : "meteor_friends";
        boolean ok = RemoteListsAddon.LIST_SERVICE.removePlayer(listId, name);
        reply(e, ok
            ? "Removed **" + name + "** from `" + listId + "`."
            : "**" + name + "** is not in `" + listId + "` or that list doesn't exist.");
    }

    private void reply(SlashCommandInteractionEvent e, String msg) {
        e.deferReply().queue();
        e.getHook().sendMessage(msg).queue();
    }
}
