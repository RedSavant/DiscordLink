package fr.redsavant.discordlink.storage;

public record PlayerSettings(String locale, boolean bypass) {

    public static final PlayerSettings EMPTY = new PlayerSettings(null, false);

    public PlayerSettings withLocale(String newLocale) {
        return new PlayerSettings(newLocale, bypass);
    }

    public PlayerSettings withBypass(boolean newBypass) {
        return new PlayerSettings(locale, newBypass);
    }
}
