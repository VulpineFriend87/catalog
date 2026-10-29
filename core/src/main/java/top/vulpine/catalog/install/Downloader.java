package top.vulpine.catalog.install;

import top.vulpine.catalog.Errors;
import top.vulpine.catalog.hash.Hashing;
import top.vulpine.catalog.jar.JarInspector;
import top.vulpine.catalog.jar.model.PluginDescriptor;
import top.vulpine.catalog.modrinth.ModrinthClient;
import top.vulpine.catalog.modrinth.model.ModrinthVersion;
import top.vulpine.catalog.modrinth.model.VersionFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Downloads a version's jar into staging and verifies it.
 *
 * <p>A caller moves it into place.</p>
 *
 * <p>The hash and size must match the file Modrinth lists. The bytecode version must run on
 * this JVM.</p>
 */
public final class Downloader {

    /** Class file major version 52 is Java 8. Every release since adds one. */
    private static final int JAVA_8_BYTECODE = 52;

    private final ModrinthClient modrinth;
    private final Path staging;

    /**
     * @param modrinth the client to download through
     * @param staging  a directory Catalog owns and empties on startup
     */
    public Downloader(ModrinthClient modrinth, Path staging) {
        this.modrinth = modrinth;
        this.staging = staging;
    }

    /**
     * Downloads the plugin jar of a version and verifies it.
     *
     * <p>Blocks, so it must be called off the main thread.</p>
     *
     * @param version     the version to fetch
     * @param javaFeature the Java feature release this server runs on
     * @return the verified staged file
     * @throws InstallException if the file is missing, corrupt, or too new for this JVM
     */
    public Path fetch(ModrinthVersion version, int javaFeature) {

        VersionFile file = version.primaryFile();

        if (file == null || file.url() == null) {
            throw new InstallException("Modrinth has no downloadable file for version "
                    + version.versionNumber() + ".");
        }

        // The name becomes a path in the update folder and in the plugins folder.
        if (!isPlainJarName(file.filename())) {
            throw new InstallException("The file Modrinth lists for " + version.versionNumber()
                    + " is not a plain jar name. Not downloaded.");
        }

        if (file.sha512() == null || file.sha512().isBlank()) {
            throw new InstallException("Modrinth lists no hash for " + version.versionNumber()
                    + ". Not downloaded.");
        }

        Path target = staging.resolve(file.filename());

        try {
            modrinth.download(file.url(), target).join();
        } catch (Exception e) {
            throw new InstallException("Download failed: " + Errors.rootMessage(e), e);
        }

        try {
            verify(target, file);
            verifyRunnable(target, javaFeature);
        } catch (InstallException e) {
            discard(target);
            throw e;
        }

        return target;
    }

    /**
     * A file name with no folder that is not hidden and ends in {@code .jar}.
     */
    static boolean isPlainJarName(String name) {

        if (name == null || name.isBlank() || name.startsWith(".")
                || name.contains("/") || name.contains("\\") || name.contains(":")) {
            return false;
        }

        try {
            if (!name.equals(Path.of(name).getFileName().toString())) {
                return false;
            }
        } catch (InvalidPathException e) {
            return false;
        }

        return name.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    /**
     * Empties the staging directory.
     *
     * <p>Anything left there is from an interrupted download.</p>
     */
    public void clean() {

        if (!Files.isDirectory(staging)) {
            return;
        }

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(staging)) {

            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }

        } catch (IOException ignored) {}
    }

    private static void verify(Path file, VersionFile expected) {

        long size;
        String hash;

        try {
            size = Files.size(file);
            hash = Hashing.sha512(file);
        } catch (IOException e) {
            throw new InstallException("Could not read the downloaded file: " + e.getMessage(), e);
        }

        if (expected.size() > 0 && size != expected.size()) {
            throw new InstallException("The download is " + size + " bytes, Modrinth says it should be "
                    + expected.size() + ". Discarded.");
        }

        if (!expected.sha512().equalsIgnoreCase(hash)) {
            throw new InstallException("The download does not match the hash Modrinth published. Discarded.");
        }
    }

    /**
     * Refuses a jar compiled for a newer Java than this server runs.
     */
    private static void verifyRunnable(Path file, int javaFeature) {

        PluginDescriptor descriptor = JarInspector.inspect(file);
        int major = descriptor.bytecodeMajor();

        // Zero means nothing readable was found
        if (major <= 0) {
            return;
        }

        int required = major - JAVA_8_BYTECODE + 8;

        if (required > javaFeature) {
            throw new InstallException("This build needs Java " + required
                    + " and the server runs Java " + javaFeature + ". Discarded.");
        }
    }

    private static void discard(Path file) {

        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Staging is emptied on the next startup anyway.
        }
    }

}
