package fr.redsavant.discordlink.runtime;

import fr.redsavant.discordlink.DiscordLinkPlugin;
import fr.redsavant.discordlink.logger.LogLevel;
import fr.redsavant.discordlink.logger.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NodeRuntimeManager {

    private static final String NODE_VERSION = "v20.11.1";

    private static final Logger dlogger = DiscordLinkPlugin.getInstance().getDlogger();

    public static File getOrDownloadNode(File dataFolder) throws IOException, InterruptedException {
        File runtimeDir = new File(dataFolder, "runtime");
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String nodeBinaryName = isWindows ? "node.exe" : "node";

        File targetNodeFile = new File(runtimeDir, "node/" + nodeBinaryName);
        if (targetNodeFile.exists() && targetNodeFile.canExecute()) {
            return targetNodeFile;
        }

        dlogger.log("Unable to find Node.js runtime, downloading...", LogLevel.INFO);
        if (!runtimeDir.exists()) {
            runtimeDir.mkdirs();
        }

        String osArch = System.getProperty("os.arch").toLowerCase();
        String platform;
        if (isWindows) {
            platform = "win";
        } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            platform = "darwin";
        } else {
            platform = "linux";
        }

        String arch;
        if (osArch.contains("arm") || osArch.contains("aarch64")) {
            arch = platform.equals("darwin") ? "arm64" : "x64";
        } else {
            arch = "x64";
        }

        String downloadUrl;
        if (platform.equals("win")) {
            downloadUrl = "https://nodejs.org/dist/" + NODE_VERSION + "/node-" + NODE_VERSION + "-win-" + arch + ".zip";
        } else if (platform.equals("darwin")) {
            downloadUrl = "https://nodejs.org/dist/" + NODE_VERSION + "/node-" + NODE_VERSION + "-darwin-" + arch + ".tar.gz";
        } else {
            downloadUrl = "https://nodejs.org/dist/" + NODE_VERSION + "/node-" + NODE_VERSION + "-linux-" + arch + ".tar.gz";
        }

        File archiveFile = new File(runtimeDir, platform.equals("win") ? "node.zip" : "node.tar.gz");

        downloadFile(downloadUrl, archiveFile);

        File extractDir = new File(runtimeDir, "extracted");
        if (!extractDir.exists()) {
            extractDir.mkdirs();
        }

        if (platform.equals("win")) {
            extractZip(archiveFile, extractDir);
        } else {
            extractTarGz(archiveFile, extractDir);
        }

        File[] subDirs = extractDir.listFiles(File::isDirectory);
        if (subDirs != null && subDirs.length > 0) {
            File extractedFolder = subDirs[0];
            File sourceBinary = platform.equals("win")
                    ? new File(extractedFolder, "node.exe")
                    : new File(extractedFolder, "bin/node");

            File finalNodeDir = new File(runtimeDir, "node");
            if (!finalNodeDir.exists()) {
                finalNodeDir.mkdirs();
            }

            Files.copy(sourceBinary.toPath(), targetNodeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            targetNodeFile.setExecutable(true);
        }

        archiveFile.delete();
        deleteDirectory(extractDir);

        dlogger.log("[DiscordLink] Sucessfully installed Node.js runtime : " + targetNodeFile.getAbsolutePath(), LogLevel.INFO);
        return targetNodeFile;
    }

    private static void downloadFile(String urlStr, File destination) throws IOException, InterruptedException {
        dlogger.log("[DiscordLink] Downloading from " + urlStr + " ...", LogLevel.INFO);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlStr)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream in = response.body(); FileOutputStream fos = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void extractZip(File zipFile, File targetDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void extractTarGz(File tarGzFile, File targetDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tarGzFile.getAbsolutePath(), "-C", targetDir.getAbsolutePath());
        Process process = pb.start();
        process.waitFor();
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}