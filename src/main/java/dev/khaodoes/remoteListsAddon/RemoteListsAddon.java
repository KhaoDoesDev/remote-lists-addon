package dev.khaodoes.remoteListsAddon;

import com.mojang.logging.LogUtils;

import dev.khaodoes.remoteListsAddon.api.ApiServer;
import dev.khaodoes.remoteListsAddon.discord.DiscordBot;
import dev.khaodoes.remoteListsAddon.provider.MeteorFriendListProvider;
import dev.khaodoes.remoteListsAddon.service.ListService;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RemoteListsAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final ListService LIST_SERVICE = new ListService();

    private static ApiServer apiServer;
    private static DiscordBot discordBot;
    private static ScheduledExecutorService scheduler;

    private static SettingGroup sg;
    private static Setting<Boolean> apiEnabled;
    private static Setting<String> apiHost;
    private static Setting<Integer> apiPort;
    private static Setting<String> discordToken;
    private static Setting<Boolean> discordEnabled;

    @Override
    public void onInitialize() {
        LOG.info("Initializing Remote Lists Addon");

        LIST_SERVICE.register(new MeteorFriendListProvider());

        initConfig();
        startScheduler();

        if (mc != null) mc.execute(LIST_SERVICE::refreshAll);

        if (apiEnabled.get()) startApi();
        if (discordEnabled.get() && !discordToken.get().isBlank()) startDiscord();
    }

    private void initConfig() {
        sg = Config.get().settings.createGroup("Remote Lists");

        apiEnabled = sg.add(new BoolSetting.Builder()
            .name("api-enabled")
            .description("Enable the HTTP API server.")
            .defaultValue(false)
            .onChanged(v -> {
                if (v) startApi();
                else stopApi();
            })
            .build()
        );

        apiHost = sg.add(new StringSetting.Builder()
            .name("api-host")
            .description("Bind address for the HTTP API server.")
            .defaultValue("127.0.0.1")
            .filter((text, c) -> String.valueOf(c).matches("[a-zA-Z0-9.\\-_:]"))
            .onChanged(v -> {
                if (apiEnabled.get()) {
                    stopApi();
                    startApi();
                }
            })
            .build()
        );

        apiPort = sg.add(new IntSetting.Builder()
            .name("api-port")
            .description("Port for the HTTP API server.")
            .defaultValue(8080)
            .range(1024, 65535)
            .noSlider()
            .onChanged(v -> {
                if (apiEnabled.get()) {
                    stopApi();
                    startApi();
                }
            })
            .build()
        );

        discordEnabled = sg.add(new BoolSetting.Builder()
            .name("discord-enabled")
            .description("Enable the Discord bot integration.")
            .defaultValue(false)
            .onChanged(v -> {
                if (v) {
                    if (!discordToken.get().isBlank()) startDiscord();
                } else stopDiscord();
            })
            .build()
        );


        discordToken = sg.add(new StringSetting.Builder()
            .name("discord-token")
            .description("Discord bot token.")
            .defaultValue("")
            .onChanged(v -> {
                boolean was = isDiscordRunning();
                stopDiscord();
                if (was && !v.isBlank()) startDiscord();
            })
            .build()
        );
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "remote-lists-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(LIST_SERVICE::refreshAll, 5, 5, TimeUnit.SECONDS);
    }

    public static void startApi() {
        if (apiServer != null) stopApi();
        apiServer = new ApiServer(apiHost.get(), apiPort.get());

        try {
            apiServer.start();
        } catch (Exception e) {
            LOG.error("API server failed", e);
            apiServer = null;
        }
    }

    public static void stopApi() {
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    public static void startDiscord() {
        if (discordBot != null) stopDiscord();
        discordBot = new DiscordBot(discordToken.get());
        discordBot.start();
    }

    public static void stopDiscord() {
        if (discordBot != null) {
            discordBot.stop();
            discordBot = null;
        }
    }

    public static boolean isDiscordRunning() {
        return discordBot != null && discordBot.isRunning();
    }

    @Override
    public String getPackage() {
        return "dev.khaodoes.remoteListsAddon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("KhaoDoesDev", "remote-lists-addon");
    }
}
