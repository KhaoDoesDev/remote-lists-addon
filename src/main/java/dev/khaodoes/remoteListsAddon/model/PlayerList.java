package dev.khaodoes.remoteListsAddon.model;

import java.util.List;

public record PlayerList(String id, String displayName, List<PlayerEntry> entries) {}
