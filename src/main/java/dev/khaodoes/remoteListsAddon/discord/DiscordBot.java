package dev.khaodoes.remoteListsAddon.discord;

import dev.khaodoes.remoteListsAddon.RemoteListsAddon;
import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
    private static final ExecutorService BOT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "discord-bot");
        t.setDaemon(true);
        return t;
    });

    private final String token;
    private final Map<String, String> listChoices;
    private volatile JDA jda;
    private volatile boolean running;

    public DiscordBot(String token, Map<String, String> listChoices) {
        this.token = token;
        this.listChoices = listChoices;
    }

    public synchronized void start() {
        if (running) return;

        BOT_EXECUTOR.submit(() -> {
            try {
                OptionData listOption = new OptionData(OptionType.STRING, "list", "List ID", true);
                OptionData playersListOption = new OptionData(OptionType.STRING, "list", "Filter by list", false);
                for (Map.Entry<String, String> entry : listChoices.entrySet()) {
                    listOption.addChoice(entry.getKey(), entry.getValue());
                    playersListOption.addChoice(entry.getKey(), entry.getValue());
                }

                JDA built = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                    // note: probably to be removed, or maybe make it a config option? idk, it's just a fun little touch
                    .setActivity(Activity.playing("with player lists"))
                    .addEventListeners(this)
                    .build().awaitReady();

                built.updateCommands().addCommands(
                    Commands.slash("lists", "Show all available player lists"),
                    Commands.slash("players", "Show all players")
                        .addOptions(playersListOption),
                    Commands.slash("check", "Check if a player is in any list")
                        .addOption(OptionType.STRING, "name", "The player's username", true, true),
                    Commands.slash("add", "Add a player to a list")
                        .addOptions(listOption)
                        .addOption(OptionType.STRING, "name", "The player's username", true, true),
                    Commands.slash("remove", "Remove a player from a list")
                        .addOptions(listOption)
                        .addOption(OptionType.STRING, "name", "The player's username", true, true)
                ).queue();

                jda = built;
                running = true;
                RemoteListsAddon.LOG.info("Discord bot started");
            } catch (Exception e) {
                RemoteListsAddon.LOG.error("Failed to start Discord bot, disabling", e);
                RemoteListsAddon.disableDiscordOnError();
            }
        });
    }

    public synchronized void stop() {
        running = false;
        if (jda != null) {
            jda.shutdown();
            jda = null;
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
            case "players" -> {
                String listId = e.getOption("list") != null ? e.getOption("list").getAsString() : null;
                reply(e, listId != null ? listPlayersMsg(listId) : playersMsg());
            }
            case "check" -> check(e);
            case "add" -> add(e);
            case "remove" -> remove(e);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent e) {
        if (!e.getFocusedOption().getName().equals("name")) return;

        List<String> names;
        if (e.getName().equals("remove")) {
            String listId = e.getOption("list").getAsString();
            PlayerList list = RemoteListsAddon.LIST_SERVICE.getList(listId);
            names = list != null ? list.entries().stream().map(PlayerEntry::username).toList() : List.of();
        } else {
            names = RemoteListsAddon.LIST_SERVICE.getAllPlayers().stream().map(PlayerEntry::username).toList();
        }
        e.replyChoiceStrings(names).queue();
    }

    private String listsMsg() {
        List<PlayerList> lists = RemoteListsAddon.LIST_SERVICE.getAllLists();
        if (lists.isEmpty()) return "No lists available.";
        return "**All Lists (" + lists.size() + "):**\n" + lists.stream()
            .map(l -> "- **" + l.displayName() + "** (`" + l.id() + "`) - " + l.entries().size() + " player(s)")
            .collect(Collectors.joining("\n"));
    }

    private String playersMsg() {
        List<PlayerEntry> players = RemoteListsAddon.LIST_SERVICE.getAllPlayers();
        if (players.isEmpty()) return "No players found across any list.";
        List<PlayerList> lists = RemoteListsAddon.LIST_SERVICE.getAllLists();

        StringBuilder sb = new StringBuilder("**All Players (" + players.size() + "):**\n");
        int shown = 0;
        for (PlayerEntry p : players) {
            if (shown >= 20) {
                sb.append("*...and ").append(players.size() - 20).append(" more*");
                break;
            }
            shown++;
            sb.append("- ").append(p.username());
            if (p.uuid() != null) sb.append(" (`").append(p.uuid()).append("`)");
            List<String> inLists = lists.stream()
                .filter(l -> l.entries().stream().anyMatch(e -> e.username().equalsIgnoreCase(p.username())))
                .map(PlayerList::displayName)
                .toList();
            if (!inLists.isEmpty()) sb.append(" — ").append(String.join(", ", inLists));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String listPlayersMsg(String listId) {
        PlayerList list = RemoteListsAddon.LIST_SERVICE.getList(listId);
        if (list == null) return "List `" + listId + "` doesn't exist.";
        List<PlayerEntry> entries = list.entries();
        if (entries.isEmpty()) return "No players in `" + listId + "`.";
        String header = "**Players in " + list.displayName() + " (" + entries.size() + "):**\n";
        String items = entries.stream().limit(20)
            .map(e -> "- " + e.username() + (e.uuid() != null ? " (`" + e.uuid() + "`)" : ""))
            .collect(Collectors.joining("\n"));
        return entries.size() > 20 ? header + items + "\n*...and " + (entries.size() - 20) + " more*" : header + items;
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
        String listId = e.getOption("list").getAsString();

        if (RemoteListsAddon.LIST_SERVICE.getList(listId) == null) {
            reply(e, "List `" + listId + "` doesn't exist.");
            return;
        }

        boolean ok = RemoteListsAddon.LIST_SERVICE.addPlayer(listId, name);
        reply(e, ok
            ? "Added **" + name + "** to `" + listId + "`."
            : "**" + name + "** is already in `" + listId + "`.");
    }

    private void remove(SlashCommandInteractionEvent e) {
        String name = e.getOption("name").getAsString();
        String listId = e.getOption("list").getAsString();

        if (RemoteListsAddon.LIST_SERVICE.getList(listId) == null) {
            reply(e, "List `" + listId + "` doesn't exist.");
            return;
        }

        boolean ok = RemoteListsAddon.LIST_SERVICE.removePlayer(listId, name);
        reply(e, ok
            ? "Removed **" + name + "** from `" + listId + "`."
            : "**" + name + "** is not in `" + listId + "`.");
    }

    private void reply(SlashCommandInteractionEvent e, String msg) {
        e.deferReply().queue();
        e.getHook().sendMessage(msg).queue();
    }
}
