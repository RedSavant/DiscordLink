package fr.redsavant.discordlink.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.bridge.BotBridge;
import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BotManager {

    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    private final DiscordLinkPlugin plugin;
    private final BotBridge bridge;
    private final File dataFolder;
    private final Logger dlogger;

    private Process botProcess;
    private BufferedWriter writer;
    private Thread managerThread;
    private volatile boolean running = false;

    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonObject>> pendingRequests = new ConcurrentHashMap<>();

    public BotManager(DiscordLinkPlugin plugin, BotBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.dataFolder = plugin.getDataFolder();
        this.dlogger = plugin.getDlogger();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isAlive() {
        return botProcess != null && botProcess.isAlive();
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        managerThread = new Thread(() -> {
            while (running) {
                try {
                    dlogger.info(plugin.getLangManager().console("bot-starting"));

                    File nodeExecutable = NodeRuntimeManager.getOrDownloadNode(dataFolder);

                    File botRuntimeDir = new File(dataFolder, "bot-runtime");
                    if (!botRuntimeDir.exists()) {
                        botRuntimeDir.mkdirs();
                    }

                    extractBotFiles(botRuntimeDir);

                    File scriptFile = new File(botRuntimeDir, "index.js");
                    if (!scriptFile.exists()) {
                        throw new FileNotFoundException("Unable to find index.js");
                    }

                    ProcessBuilder pb = new ProcessBuilder(nodeExecutable.getAbsolutePath(), scriptFile.getAbsolutePath());
                    pb.directory(botRuntimeDir);

                    Map<String, String> env = pb.environment();
                    env.put("DISCORD_TOKEN", plugin.getPluginConfig().token());
                    env.put("DISCORD_GUILD_ID", plugin.getPluginConfig().guildId());

                    Process process = pb.start();
                    synchronized (this) {
                        if (!running) {
                            process.destroyForcibly();
                            break;
                        }
                        botProcess = process;
                        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                    }

                    startReaderThreads(process);

                    int exitCode = process.waitFor();
                    dlogger.log(plugin.getLangManager().console("bot-exited", "code", exitCode), exitCode == 0 ? LogLevel.INFO : LogLevel.WARNING);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    dlogger.log("Critical error: " + e.getMessage(), LogLevel.SEVERE);
                    if (plugin.getPluginConfig().debug()) {
                        e.printStackTrace();
                    }
                } finally {
                    onDisconnect();
                }

                if (running) {
                    dlogger.info(plugin.getLangManager().console("bot-restarting"));
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "DiscordLink-BotManager");
        managerThread.setDaemon(true);
        managerThread.start();
    }

    private void onDisconnect() {
        synchronized (this) {
            writer = null;
        }
        pendingRequests.values().forEach(future -> future.completeExceptionally(new IOException("Bot disconnected")));
        pendingRequests.clear();
        bridge.onDisconnected();
    }

    private void extractBotFiles(File targetDir) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("index.js")) {
            if (in != null) {
                File targetFile = new File(targetDir, "index.js");
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void startReaderThreads(Process process) {
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleBotOutput(line);
                }
            } catch (IOException ignored) {
            }
        }, "DiscordLink-BotReader");
        readerThread.setDaemon(true);
        readerThread.start();

        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String errLine;
                while ((errLine = errorReader.readLine()) != null) {
                    if (plugin.getPluginConfig().debug()) {
                        dlogger.log("[Node] " + errLine, LogLevel.ERROR);
                    }
                }
            } catch (IOException ignored) {
            }
        }, "DiscordLink-BotErrorReader");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();
    }

    private void handleBotOutput(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("{")) {
            dlogger.debug("[Bot] " + line);
            return;
        }
        JsonObject message;
        try {
            message = JsonParser.parseString(trimmed).getAsJsonObject();
        } catch (Exception e) {
            dlogger.debug("[Bot] " + line);
            return;
        }
        String kind = message.has("kind") ? message.get("kind").getAsString() : "";
        switch (kind) {
            case "res" -> handleResponse(message);
            case "req" -> handleRequest(message);
            case "evt" -> bridge.handleEvent(message.get("type").getAsString(), data(message));
            case "log" -> handleLog(message);
            case "fatal" -> {
                dlogger.error(message.has("message") ? message.get("message").getAsString() : message.toString());
                running = false;
            }
            default -> dlogger.debug("[Bot] " + line);
        }
    }

    private JsonObject data(JsonObject message) {
        JsonElement data = message.get("data");
        return data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject();
    }

    private void handleResponse(JsonObject message) {
        long id = message.get("id").getAsLong();
        CompletableFuture<JsonObject> future = pendingRequests.remove(id);
        if (future == null) {
            return;
        }
        if (message.has("ok") && message.get("ok").getAsBoolean()) {
            future.complete(data(message));
        } else {
            String error = message.has("error") ? message.get("error").getAsString() : "Unknown error";
            future.completeExceptionally(new IllegalStateException(error));
        }
    }

    private void handleRequest(JsonObject message) {
        long id = message.get("id").getAsLong();
        String type = message.get("type").getAsString();
        bridge.handleRequest(type, data(message)).whenComplete((result, error) -> {
            JsonObject response = new JsonObject();
            response.addProperty("kind", "res");
            response.addProperty("id", id);
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                response.addProperty("ok", false);
                response.addProperty("error", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            } else {
                response.addProperty("ok", true);
                response.add("data", result == null ? new JsonObject() : result);
            }
            send(response);
        });
    }

    private void handleLog(JsonObject message) {
        String level = message.has("level") ? message.get("level").getAsString() : "info";
        String text = "[Bot] " + (message.has("message") ? message.get("message").getAsString() : "");
        switch (level) {
            case "debug" -> dlogger.debug(text);
            case "warn" -> dlogger.warn(text);
            case "error" -> dlogger.error(text);
            default -> dlogger.info(text);
        }
    }

    public CompletableFuture<JsonObject> request(String type, JsonObject data) {
        if (!isAlive()) {
            return CompletableFuture.failedFuture(new IOException("Bot is offline"));
        }
        long id = requestIdCounter.incrementAndGet();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        JsonObject message = new JsonObject();
        message.addProperty("kind", "req");
        message.addProperty("id", id);
        message.addProperty("type", type);
        message.add("data", data);
        if (!send(message)) {
            pendingRequests.remove(id);
            return CompletableFuture.failedFuture(new IOException("Unable to reach the bot"));
        }
        return future.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, error) -> pendingRequests.remove(id));
    }

    public synchronized boolean send(JsonObject message) {
        if (writer == null) {
            return false;
        }
        try {
            writer.write(message.toString());
            writer.write('\n');
            writer.flush();
            return true;
        } catch (IOException e) {
            dlogger.debug("Unable to write to bot process: " + e.getMessage());
            return false;
        }
    }

    public void destroy() {
        running = false;
        Process process = botProcess;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                process.waitFor(3, TimeUnit.SECONDS);
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        if (managerThread != null) {
            managerThread.interrupt();
        }
    }
}
