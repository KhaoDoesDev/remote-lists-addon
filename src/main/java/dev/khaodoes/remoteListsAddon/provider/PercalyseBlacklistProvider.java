package dev.khaodoes.remoteListsAddon.provider;

import dev.khaodoes.remoteListsAddon.RemoteListsAddon;
import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Method;
import java.util.*;

public class PercalyseBlacklistProvider implements PlayerListProvider {
    public static final String LIST_ID = "percalypse_blacklist";
    public static final String LIST_DISPLAY_NAME = "Percalyse Blacklist";

    private volatile List<PlayerList> cachedLists = List.of();
    private final boolean available;
    private boolean enabled = true;

    private Method getInstance;
    private Method getNames;
    private Method addPlayerMethod;
    private Method removePlayerMethod;
    private Object instance;

    public PercalyseBlacklistProvider() {
        boolean found = false;
        try {
            Class<?> cls = Class.forName("com.eglijohn.percalypse.utils.data.Blacklist");
            getInstance = cls.getMethod("get");
            getNames = cls.getMethod("getNames");
            addPlayerMethod = cls.getMethod("add", String.class);
            removePlayerMethod = cls.getMethod("remove", String.class);
            instance = getInstance.invoke(null);
            found = true;
            RemoteListsAddon.LOG.info("Percalyse Blacklist provider available");
        } catch (Exception e) {
            RemoteListsAddon.LOG.info("Percalyse not found, blacklist provider disabled");
        }
        available = found;
    }

    public boolean isAvailable() { return available; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) cachedLists = List.of();
    }

    @Override
    public String getId() { return "percalypse"; }

    @Override
    public Collection<PlayerList> getLists() { return cachedLists; }

    @Override
    public void refresh() {
        if (!available || !enabled) { cachedLists = List.of(); return; }
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.isOnThread()) { refreshInternal(); }
        else if (c != null) { c.execute(this::refreshInternal); }
    }

    private void refreshInternal() {
        try {
            @SuppressWarnings("unchecked")
            Collection<String> names = (Collection<String>) getNames.invoke(instance);
            List<PlayerEntry> entries = new ArrayList<>();
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                entries.add(new PlayerEntry(UUID.nameUUIDFromBytes(name.getBytes()), name));
            }
            cachedLists = List.of(new PlayerList(LIST_ID, LIST_DISPLAY_NAME, entries));
        } catch (Exception e) {
            RemoteListsAddon.LOG.error("Failed to refresh Percalyse blacklist", e);
        }
    }

    @Override
    public boolean addPlayer(String username) {
        if (!available || !enabled) return false;
        if (username == null || username.isBlank()) return false;
        try {
            boolean result = (boolean) addPlayerMethod.invoke(instance, username);
            if (result) refresh();
            return result;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean removePlayer(String username) {
        if (!available || !enabled) return false;
        if (username == null || username.isBlank()) return false;
        try {
            boolean result = (boolean) removePlayerMethod.invoke(instance, username);
            if (result) refresh();
            return result;
        } catch (Exception e) { return false; }
    }
}
