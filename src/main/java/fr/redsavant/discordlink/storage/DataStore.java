package fr.redsavant.discordlink.storage;

import fr.redsavant.discordlink.DiscordLinkPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DataStore {

    private static final int SCHEMA_VERSION = 2;

    private final DiscordLinkPlugin plugin;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DiscordLink-Database");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<UUID, LinkedAccount> accountsByUuid = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidByDiscord = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSettings> playerSettings = new ConcurrentHashMap<>();
    private final Map<String, String> discordLocales = new ConcurrentHashMap<>();

    private Connection connection;

    public DataStore(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        File file = new File(plugin.getDataFolder(), plugin.getPluginConfig().databaseFile());
        file.getParentFile().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found", e);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS linked_accounts (
                        uuid TEXT PRIMARY KEY,
                        username TEXT NOT NULL,
                        discord_id TEXT NOT NULL UNIQUE,
                        linked_at BIGINT NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_settings (
                        uuid TEXT PRIMARY KEY,
                        locale TEXT,
                        bypass INTEGER NOT NULL DEFAULT 0
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_settings (
                        discord_id TEXT PRIMARY KEY,
                        locale TEXT
                    )""");
            statement.execute("CREATE TABLE IF NOT EXISTS schema_info (version INTEGER NOT NULL)");
        }
        migrate();
        loadAll();
    }

    private void migrate() throws SQLException {
        int version = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_info LIMIT 1")) {
            if (rs.next()) {
                version = rs.getInt(1);
            }
        }
        if (version >= SCHEMA_VERSION) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn("linked_accounts", "discord_name")) {
                statement.execute("ALTER TABLE linked_accounts ADD COLUMN discord_name TEXT");
            }
            int converted = statement.executeUpdate("UPDATE linked_accounts SET linked_at = linked_at * 1000 WHERE linked_at > 0 AND linked_at < 100000000000");
            if (converted > 0) {
                plugin.getDlogger().info("Migrated " + converted + " linked account(s) from DiscordLink v1.");
            }
            statement.execute("DELETE FROM schema_info");
            statement.execute("INSERT INTO schema_info (version) VALUES (" + SCHEMA_VERSION + ")");
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private void loadAll() throws SQLException {
        accountsByUuid.clear();
        uuidByDiscord.clear();
        playerSettings.clear();
        discordLocales.clear();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT uuid, username, discord_id, discord_name, linked_at FROM linked_accounts")) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    LinkedAccount account = new LinkedAccount(uuid, rs.getString("username"), rs.getString("discord_id"),
                            rs.getString("discord_name"), rs.getLong("linked_at"));
                    accountsByUuid.put(uuid, account);
                    uuidByDiscord.put(account.discordId(), uuid);
                }
            }
            try (ResultSet rs = statement.executeQuery("SELECT uuid, locale, bypass FROM player_settings")) {
                while (rs.next()) {
                    UUID uuid = parseUuid(rs.getString("uuid"));
                    if (uuid != null) {
                        playerSettings.put(uuid, new PlayerSettings(rs.getString("locale"), rs.getInt("bypass") == 1));
                    }
                }
            }
            try (ResultSet rs = statement.executeQuery("SELECT discord_id, locale FROM discord_settings")) {
                while (rs.next()) {
                    if (rs.getString("locale") != null) {
                        discordLocales.put(rs.getString("discord_id"), rs.getString("locale"));
                    }
                }
            }
        }
        plugin.getDlogger().info("Loaded " + accountsByUuid.size() + " linked account(s).");
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    public Collection<LinkedAccount> accounts() {
        return Collections.unmodifiableCollection(accountsByUuid.values());
    }

    public int linkedCount() {
        return accountsByUuid.size();
    }

    public Optional<LinkedAccount> byUuid(UUID uuid) {
        return Optional.ofNullable(accountsByUuid.get(uuid));
    }

    public Optional<LinkedAccount> byDiscord(String discordId) {
        UUID uuid = uuidByDiscord.get(discordId);
        return uuid == null ? Optional.empty() : byUuid(uuid);
    }

    public Optional<LinkedAccount> byName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return accountsByUuid.values().stream()
                .filter(account -> account.username().toLowerCase(Locale.ROOT).equals(lower))
                .findFirst();
    }

    public void saveLink(LinkedAccount account) {
        LinkedAccount previous = accountsByUuid.put(account.uuid(), account);
        if (previous != null && !previous.discordId().equals(account.discordId())) {
            uuidByDiscord.remove(previous.discordId());
        }
        uuidByDiscord.put(account.discordId(), account.uuid());
        write("INSERT OR REPLACE INTO linked_accounts (uuid, username, discord_id, discord_name, linked_at) VALUES (?, ?, ?, ?, ?)",
                account.uuid().toString(), account.username(), account.discordId(), account.discordName(), account.linkedAt());
    }

    public Optional<LinkedAccount> removeLink(UUID uuid) {
        LinkedAccount removed = accountsByUuid.remove(uuid);
        if (removed != null) {
            uuidByDiscord.remove(removed.discordId());
            write("DELETE FROM linked_accounts WHERE uuid = ?", uuid.toString());
        }
        return Optional.ofNullable(removed);
    }

    public void updateUsername(UUID uuid, String username) {
        LinkedAccount account = accountsByUuid.get(uuid);
        if (account == null || account.username().equals(username)) {
            return;
        }
        accountsByUuid.put(uuid, account.withUsername(username));
        write("UPDATE linked_accounts SET username = ? WHERE uuid = ?", username, uuid.toString());
    }

    public PlayerSettings settings(UUID uuid) {
        return playerSettings.getOrDefault(uuid, PlayerSettings.EMPTY);
    }

    public String playerLocale(UUID uuid) {
        return settings(uuid).locale();
    }

    public void setPlayerLocale(UUID uuid, String locale) {
        savePlayerSettings(uuid, settings(uuid).withLocale(locale));
    }

    public boolean bypass(UUID uuid) {
        return settings(uuid).bypass();
    }

    public void setBypass(UUID uuid, boolean bypass) {
        savePlayerSettings(uuid, settings(uuid).withBypass(bypass));
    }

    private void savePlayerSettings(UUID uuid, PlayerSettings settings) {
        playerSettings.put(uuid, settings);
        write("INSERT OR REPLACE INTO player_settings (uuid, locale, bypass) VALUES (?, ?, ?)",
                uuid.toString(), settings.locale(), settings.bypass() ? 1 : 0);
    }

    public Map<String, String> discordLocales() {
        return Collections.unmodifiableMap(discordLocales);
    }

    public void setDiscordLocale(String discordId, String locale) {
        if (locale == null) {
            discordLocales.remove(discordId);
            write("DELETE FROM discord_settings WHERE discord_id = ?", discordId);
            return;
        }
        discordLocales.put(discordId, locale);
        write("INSERT OR REPLACE INTO discord_settings (discord_id, locale) VALUES (?, ?)", discordId, locale);
    }

    private void write(String sql, Object... params) {
        if (writer.isShutdown()) {
            return;
        }
        writer.execute(() -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getDlogger().error("Database error: " + e.getMessage());
            }
        });
    }
}
