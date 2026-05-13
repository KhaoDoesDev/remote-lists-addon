package dev.khaodoes.remoteListsAddon.provider;

import dev.khaodoes.remoteListsAddon.mixin.meteor.FriendAccessor;
import dev.khaodoes.remoteListsAddon.model.PlayerEntry;
import dev.khaodoes.remoteListsAddon.model.PlayerList;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MeteorFriendListProvider implements PlayerListProvider {
    public static final String LIST_ID = "meteor_friends";
    public static final String LIST_DISPLAY_NAME = "Meteor Friends";

    private volatile List<PlayerList> cachedLists = List.of();

    @Override
    public String getId() {
        return "meteor";
    }

    @Override
    public Collection<PlayerList> getLists() {
        return cachedLists;
    }

    @Override
    public void refresh() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null) c.execute(this::refreshInternal);
    }

    private void refreshInternal() {
        Friends friends = Friends.get();
        if (friends == null) {
            cachedLists = List.of();
            return;
        }

        List<PlayerEntry> entries = new ArrayList<>();
        for (Friend f : friends) {
            FriendAccessor accessor = (FriendAccessor) f;
            entries.add(new PlayerEntry(accessor.id(), f.name));
        }
        cachedLists = List.of(new PlayerList(LIST_ID, LIST_DISPLAY_NAME, entries));
    }

    @Override
    public boolean addPlayer(String listId, String username) {
        if (!LIST_ID.equals(listId) || username == null || username.isBlank()) return false;
        Friend f = new Friend(username);
        boolean added = Friends.get().add(f);
        if (added) refresh();
        return added;
    }

    @Override
    public boolean removePlayer(String listId, String username) {
        if (!LIST_ID.equals(listId) || username == null || username.isBlank()) return false;
        Friend f = Friends.get().get(username);
        if (f == null) return false;
        boolean removed = Friends.get().remove(f);
        if (removed) refresh();
        return removed;
    }
}
