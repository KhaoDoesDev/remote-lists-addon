package dev.khaodoes.remoteListsAddon.mixin.meteor;

import meteordevelopment.meteorclient.systems.friends.Friend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(Friend.class)
public interface FriendAccessor {
    @Accessor("id")
    UUID id();
}
