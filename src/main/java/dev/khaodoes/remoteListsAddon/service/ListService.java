package dev.khaodoes.remoteListsAddon.service;

import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;
import dev.khaodoes.remoteListsAddon.provider.PlayerListProvider;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListService {
    private final List<PlayerListProvider> providers = new CopyOnWriteArrayList<>();

    public void register(PlayerListProvider p) {
        providers.add(p);
    }

    public List<PlayerListProvider> getProviders() {
        return providers;
    }

    public void refreshAll() {
        providers.forEach(PlayerListProvider::refresh);
    }

    public List<PlayerList> getAllLists() {
        List<PlayerList> r = new ArrayList<>();
        providers.forEach(p -> r.addAll(p.getLists()));
        return r;
    }

    public PlayerList getList(String id) {
        for (PlayerListProvider p : providers)
            for (PlayerList l : p.getLists())
                if (l.id().equals(id)) return l;
        return null;
    }

    public List<PlayerEntry> getAllPlayers() {
        Set<PlayerEntry> seen = new LinkedHashSet<>();
        for (PlayerList l : getAllLists()) seen.addAll(l.entries());
        return new ArrayList<>(seen);
    }

    public boolean isPlayerPresent(String name) {
        for (PlayerEntry e : getAllPlayers())
            if (e.username().equalsIgnoreCase(name)) return true;
        return false;
    }

    public boolean addPlayer(String listId, String username) {
        for (PlayerListProvider p : providers) {
            if (!p.getId().equals(listId)) continue;
            if (p.addPlayer(username)) return true;
        }
        return false;
    }

    public boolean removePlayer(String listId, String username) {
        for (PlayerListProvider p : providers) {
            if (!p.getId().equals(listId)) continue;
            if (p.removePlayer(username)) return true;
        }
        return false;
    }
}
