package dev.khaodoes.remoteListsAddon;

import com.mojang.logging.LogUtils;

import dev.khaodoes.remoteListsAddon.commands.CommandExample;
import dev.khaodoes.remoteListsAddon.hud.HudExample;
import dev.khaodoes.remoteListsAddon.modules.ModuleExample;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class RemoteListsAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Example");
    public static final HudGroup HUD_GROUP = new HudGroup("Example");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Remote Lists Addon");

        // Modules
        Modules.get().add(new ModuleExample());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
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
