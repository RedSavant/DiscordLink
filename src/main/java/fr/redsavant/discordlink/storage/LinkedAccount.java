package fr.redsavant.discordlink.storage;

import java.util.UUID;

public record LinkedAccount(UUID uuid, String username, String discordId, String discordName, long linkedAt) {

    public LinkedAccount withUsername(String newUsername) {
        return new LinkedAccount(uuid, newUsername, discordId, discordName, linkedAt);
    }

    public String displayDiscordName() {
        return discordName == null || discordName.isBlank() ? discordId : discordName;
    }
}
