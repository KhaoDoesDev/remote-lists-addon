package dev.khaodoes.remoteListsAddon.provider;

import dev.khaodoes.remoteListsAddon.model.PlayerList;

import java.util.Collection;

public interface PlayerListProvider {
    String getId();
    Collection<PlayerList> getLists();
    void refresh();

    default boolean addPlayer(String listId, String username) {
        return false;
    }
    default boolean removePlayer(String listId, String username) {
        return false;
    }
}
