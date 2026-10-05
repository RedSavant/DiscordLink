package fr.redsavant.discordlink.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BotManager {

    private final File dataFolder;
    private final String discordToken;
    private final String guildId;
    private final boolean debug;

    private Process botProcess;
    private Thread readerThread;
    private Thread errorReaderThread;
    private volatile boolean running = false;

    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final Map<Long, CompletableFutureResponse> pendingRequests = new ConcurrentHashMap<>();

    private static final Logger dlogger = DiscordLinkPlugin.getInstance().getDlogger();

    public BotManager(File dataFolder, String discordToken, String guildId, boolean debug) {
        this.dataFolder = dataFolder;
        this.discordToken = discordToken;
        this.guildId = guildId;
        this.debug = debug;
    }

    public void start() {
        if (running) return;
        running = true;

        new Thread(() -> {
            while (running) {
                try {
                    dlogger.log("Enabling discord bot...", LogLevel.INFO);

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
                    env.put("DISCORD_TOKEN", discordToken);
                    if (guildId != null) {
                        env.put("DISCORD_GUILD_ID", guildId);
                    }

                    botProcess = pb.start();

                    startReaderThread();

                    int exitCode = botProcess.waitFor();
                    dlogger.log("Bot task exited : " + exitCode, LogLevel.INFO);

                } catch (Exception e) {
                    dlogger.log("Critical error: " + e.getMessage(), LogLevel.SEVERE);
                    e.printStackTrace();
                }

                if (running) {
                    dlogger.log("Restarting in 5 seconds...", LogLevel.INFO);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }, "DiscordLink-BotManager").start();
    }

    private void extractBotFiles(File targetDir) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("index.js")) {
            if (in != null) {
                File targetFile = new File(targetDir, "index.js");
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void startReaderThread() {
        readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(botProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleBotOutput(line);
                }
            } catch (IOException e) {
            }
        }, "DiscordLink-BotReader");
        readerThread.setDaemon(true);
        readerThread.start();

        errorReaderThread = new Thread(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(botProcess.getErrorStream()))) {
                String errLine;
                while ((errLine = errorReader.readLine()) != null) {
                    if (debug) {
                        dlogger.log("[Node Error] " + errLine, LogLevel.ERROR);
                    }
                }
            } catch (IOException e) {
            }
        }, "DiscordLink-BotErrorReader");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();
    }

    private void handleBotOutput(String line) {
        if (line.startsWith("{") && line.endsWith("}")) {
            JsonObject message = parseJsonObject(line);
            if (message != null && message.has("error")) {
                handleBotError(message);
            } else {
                parseIpcMessage(line);
            }
        } else if (debug) {
            dlogger.log("[Bot] " + line, LogLevel.INFO);
        }
    }

    private JsonObject parseJsonObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private void handleBotError(JsonObject message) {
        String error = message.has("error") && message.get("error").isJsonPrimitive()
                ? message.get("error").getAsString()
                : message.toString();
        dlogger.log(error, LogLevel.ERROR);

        if (message.has("fatal") && message.get("fatal").getAsBoolean()) {
            running = false;
        }
    }

    private void parseIpcMessage(String json) {
        if (json.contains("\"id\":")) {
            try {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendIpcRequest(String type, String jsonPayload, Consumer<String> callback) {
        if (botProcess == null || !botProcess.isAlive()) {
            dlogger.log("Unable to send IPC request, is the bot online ?", LogLevel.ERROR);
            return;
        }

        long id = requestIdCounter.incrementAndGet();
        String message = String.format("{\"id\":%d,\"type\":\"%s\"%s}\n", id, type, jsonPayload.isEmpty() ? "" : "," + jsonPayload.substring(1));

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(botProcess.getOutputStream()));
            writer.write(message);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void destroy() {
        running = false;
        if (botProcess != null && botProcess.isAlive()) {
            botProcess.destroy();
            try {
                botProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (botProcess.isAlive()) {
                    botProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                botProcess.destroyForcibly();
            }
        }
    }

    private static class CompletableFutureResponse {
    }
}