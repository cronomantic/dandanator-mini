package com.grelobites.romgenerator;

import com.grelobites.romgenerator.handlers.dandanatormini.DandanatorMiniConstants;
import com.grelobites.romgenerator.handlers.dandanatormini.v10.CliRomSetHandler;
import com.grelobites.romgenerator.model.Game;
import com.grelobites.romgenerator.util.gameloader.GameImageLoader;
import com.grelobites.romgenerator.util.gameloader.GameImageLoaderFactory;
import com.grelobites.romgenerator.util.gameloader.GameImageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for the Dandanator Mini ROM Generator.
 *
 * <p>This entry point allows you to generate a Dandanator Mini ROM set from one or more
 * supported game/snapshot files without launching the graphical application. It is useful
 * for scripting, CI/CD pipelines and automated batch workflows.</p>
 *
 * <h2>Synopsis</h2>
 * <pre>
 *   java -jar dandanator-mini-&lt;version&gt;.jar --cli -o &lt;output.rom&gt; game1.sna [game2.z80 ...]
 *   java -jar dandanator-mini-&lt;version&gt;.jar --cli --help
 * </pre>
 *
 * <h2>Supported input formats</h2>
 * <ul>
 *   <li>{@code .sna} — ZX Spectrum 48 K / 128 K snapshot</li>
 *   <li>{@code .z80} — Z80 emulator snapshot</li>
 *   <li>{@code .rom} — raw ROM / cartridge image</li>
 *   <li>{@code .tap} — ZX Spectrum tape image</li>
 *   <li>{@code .mld} — Multi-Load format (48 K / 128 K MLD games and DAN-SNAP)</li>
 *   <li>{@code .daad} / {@code .zip} — DAAD adventure-game archive</li>
 *   <li>{@code .dantap} — DAN-TAP format</li>
 * </ul>
 *
 * <h2>Exit codes</h2>
 * <ul>
 *   <li>0 — success</li>
 *   <li>1 — invalid arguments or usage error</li>
 *   <li>2 — I/O or generation error</li>
 * </ul>
 *
 * <h2>Options</h2>
 * <dl>
 *   <dt>{@code -o &lt;file&gt;}, {@code --output &lt;file&gt;}</dt>
 *   <dd>Path for the generated ROM set file (required).</dd>
 *   <dt>{@code -h}, {@code --help}</dt>
 *   <dd>Print usage information and exit.</dd>
 *   <dt>{@code -V}, {@code --version}</dt>
 *   <dd>Print version information and exit.</dd>
 * </dl>
 *
 * <h2>Examples</h2>
 * <pre>
 *   # Generate a ROM set from three snapshots
 *   java -jar dandanator-mini.jar --cli -o my-cartridge.rom game1.sna game2.z80 game3.mld
 *
 *   # Show supported formats and usage
 *   java -jar dandanator-mini.jar --cli --help
 * </pre>
 */
public class MainCLI {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainCLI.class);

    /** The leading switch used by {@link MainShade} to route to CLI mode. */
    public static final String CLI_FLAG = "--cli";

    private static final String SHORT_OUTPUT = "-o";
    private static final String LONG_OUTPUT  = "--output";
    private static final String SHORT_HELP   = "-h";
    private static final String LONG_HELP    = "--help";
    private static final String SHORT_VER    = "-V";
    private static final String LONG_VER     = "--version";

    /**
     * Holds the parsed CLI arguments.
     */
    static class CliArgs {
        File outputFile;
        final List<File> inputFiles = new ArrayList<>();
        boolean showHelp;
        boolean showVersion;
    }

    /**
     * Main entry point for CLI mode.
     *
     * @param args command-line arguments (the leading {@code --cli} flag must already be stripped)
     */
    public static void main(String[] args) {
        CliArgs parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(System.err);
            System.exit(1);
            return;
        }

        if (parsed.showHelp) {
            printUsage(System.out);
            System.exit(0);
            return;
        }

        if (parsed.showVersion) {
            System.out.println("dandanator-mini " + Constants.currentVersion());
            System.exit(0);
            return;
        }

        if (parsed.outputFile == null) {
            System.err.println("Error: no output file specified (use -o <file>)");
            printUsage(System.err);
            System.exit(1);
            return;
        }

        if (parsed.inputFiles.isEmpty()) {
            System.err.println("Error: no input game files specified");
            printUsage(System.err);
            System.exit(1);
            return;
        }

        try {
            List<Game> games = loadGames(parsed.inputFiles);
            generateRomSet(games, parsed.outputFile);
            System.out.println("ROM set written to " + parsed.outputFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("ROM generation failed", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Parses the command-line arguments (without the leading {@code --cli} flag).
     *
     * @param args the arguments to parse
     * @return populated {@link CliArgs} structure
     * @throws IllegalArgumentException if any argument is invalid
     */
    static CliArgs parse(String[] args) {
        CliArgs result = new CliArgs();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            switch (arg) {
                case SHORT_HELP:
                case LONG_HELP:
                    result.showHelp = true;
                    return result;
                case SHORT_VER:
                case LONG_VER:
                    result.showVersion = true;
                    return result;
                case SHORT_OUTPUT:
                case LONG_OUTPUT:
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException(arg + " requires a file path argument");
                    }
                    result.outputFile = new File(args[++i]);
                    break;
                default:
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    result.inputFiles.add(new File(arg));
                    break;
            }
            i++;
        }
        return result;
    }

    /**
     * Loads each file in {@code inputFiles} into a {@link Game} using the appropriate
     * loader for its file extension.
     *
     * @param inputFiles the files to load
     * @return list of loaded games, in the same order as {@code inputFiles}
     * @throws IOException if a file cannot be read or its format is not supported
     */
    static List<Game> loadGames(List<File> inputFiles) throws IOException {
        List<Game> games = new ArrayList<>();
        for (File file : inputFiles) {
            String extension = getExtension(file.getName());
            GameImageType type = GameImageType.fromExtension(extension);
            if (type == null) {
                throw new IOException(
                        "Unsupported file extension '." + extension + "' for file: " + file
                        + ". Supported extensions: " + getSupportedExtensions());
            }
            LOGGER.info("Loading {} as {}", file, type);
            GameImageLoader loader = GameImageLoaderFactory.getLoader(type);
            try (FileInputStream fis = new FileInputStream(file)) {
                Game game = loader.load(fis);
                if (game.getName() == null || game.getName().isEmpty()) {
                    game.setName(stripExtension(file.getName()));
                }
                games.add(game);
                LOGGER.info("Loaded game: {} (type: {})", game.getName(), game.getType());
            }
        }
        return games;
    }

    /**
     * Generates a Dandanator Mini ROM set from the supplied game list and writes it to
     * {@code outputFile}.
     *
     * @param games      the games to include (order is preserved on the cartridge)
     * @param outputFile destination file for the ROM set
     * @throws IOException if the output file cannot be written or generation fails
     */
    static void generateRomSet(List<Game> games, File outputFile) throws IOException {
        LOGGER.info("Generating ROM set with {} game(s) to {}", games.size(), outputFile);
        CliRomSetHandler handler = new CliRomSetHandler();
        handler.bindGameList(games);
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            handler.exportRomSet(fos);
        }
        LOGGER.info("ROM set generation complete ({} bytes)", outputFile.length());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the lower-case file extension of {@code name} (without the leading dot). */
    static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
    }

    /** Returns {@code name} with its extension (and the preceding dot) removed. */
    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Returns a human-readable list of all supported file extensions. */
    private static String getSupportedExtensions() {
        StringBuilder sb = new StringBuilder();
        for (GameImageType type : GameImageType.values()) {
            // Reflectively get the supported extensions via the fromExtension probe
            // (simplest way without changing the enum API)
            String typeName = type.name().toLowerCase();
            switch (type) {
                case DAAD: sb.append(".daad, .zip"); break;
                default:   sb.append(".").append(typeName); break;
            }
            sb.append(", ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    private static void printUsage(PrintStream out) {
        String jar = "dandanator-mini-" + Constants.currentVersion() + ".jar";
        out.println("Usage:");
        out.println("  java -jar " + jar + " --cli -o <output.rom> <game1> [game2 ...]");
        out.println();
        out.println("Options:");
        out.println("  -o, --output <file>    Output ROM set file (required)");
        out.println("  -h, --help             Show this help message and exit");
        out.println("  -V, --version          Show version information and exit");
        out.println();
        out.println("Supported input formats:");
        out.println("  .sna       ZX Spectrum 48K/128K snapshot");
        out.println("  .z80       Z80 emulator snapshot");
        out.println("  .rom       Raw ROM/cartridge image");
        out.println("  .tap       ZX Spectrum tape image");
        out.println("  .mld       Multi-Load format (48K/128K MLD, DAN-SNAP)");
        out.println("  .daad/.zip DAAD adventure-game archive");
        out.println("  .dantap    DAN-TAP format");
        out.println();
        out.println("Examples:");
        out.println("  java -jar " + jar + " --cli -o cartridge.rom game1.sna game2.z80 game3.mld");
        out.println("  java -jar " + jar + " --cli --help");
        out.println();
        out.println("Notes:");
        out.println("  - Games are added to the ROM set in the order they are listed.");
        out.println("  - Maximum " + DandanatorMiniConstants.MAX_GAMES + " games per ROM set.");
        out.println("  - Game names are taken from the file name (without extension) when not");
        out.println("    embedded in the file itself.");
        out.println();
        out.println("To launch the graphical interface (default mode):");
        out.println("  java -jar " + jar);
    }
}
