package com.grelobites.romgenerator;

import com.grelobites.romgenerator.handlers.dandanatormini.v10.CliRomSetHandler;
import com.grelobites.romgenerator.model.Game;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the CLI entry point and headless ROM generation.
 *
 * <p>These tests intentionally avoid launching a JavaFX application and instead
 * exercise {@link MainCLI} and {@link CliRomSetHandler} in headless mode.</p>
 */
public class MainCLITest {

    private static final String SNA_RESOURCE = "/loader.48.sna";
    private static final int EXPECTED_ROM_SIZE = 524288; // 512 KiB

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // -----------------------------------------------------------------------
    // Argument parsing
    // -----------------------------------------------------------------------

    @Test
    public void parseHelp_short() {
        MainCLI.CliArgs args = MainCLI.parse(new String[]{"-h"});
        assertTrue(args.showHelp);
    }

    @Test
    public void parseHelp_long() {
        MainCLI.CliArgs args = MainCLI.parse(new String[]{"--help"});
        assertTrue(args.showHelp);
    }

    @Test
    public void parseVersion_short() {
        MainCLI.CliArgs args = MainCLI.parse(new String[]{"-V"});
        assertTrue(args.showVersion);
    }

    @Test
    public void parseVersion_long() {
        MainCLI.CliArgs args = MainCLI.parse(new String[]{"--version"});
        assertTrue(args.showVersion);
    }

    @Test
    public void parseOutputAndInputFiles() {
        MainCLI.CliArgs args = MainCLI.parse(
                new String[]{"-o", "out.rom", "game1.sna", "game2.z80"});
        assertNotNull(args.outputFile);
        assertEquals("out.rom", args.outputFile.getName());
        assertEquals(2, args.inputFiles.size());
        assertEquals("game1.sna", args.inputFiles.get(0).getName());
        assertEquals("game2.z80", args.inputFiles.get(1).getName());
    }

    @Test
    public void parseLongOutput() {
        MainCLI.CliArgs args = MainCLI.parse(new String[]{"--output", "cartridge.rom", "a.sna"});
        assertEquals("cartridge.rom", args.outputFile.getName());
        assertEquals(1, args.inputFiles.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseUnknownFlag_throws() {
        MainCLI.parse(new String[]{"--unknown"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseMissingOutputArg_throws() {
        MainCLI.parse(new String[]{"-o"});
    }

    // -----------------------------------------------------------------------
    // Extension helper
    // -----------------------------------------------------------------------

    @Test
    public void getExtension_normalFile() {
        assertEquals("sna", MainCLI.getExtension("game.sna"));
    }

    @Test
    public void getExtension_upperCase() {
        assertEquals("z80", MainCLI.getExtension("game.Z80"));
    }

    @Test
    public void getExtension_noExtension() {
        assertEquals("", MainCLI.getExtension("gamefile"));
    }

    @Test
    public void getExtension_dotInPath() {
        assertEquals("sna", MainCLI.getExtension("my.game.sna"));
    }

    // -----------------------------------------------------------------------
    // ROM generation (headless)
    // -----------------------------------------------------------------------

    @Test
    public void generateRomSet_fromSna_producesCorrectSize() throws IOException {
        File snaFile = new File(getClass().getResource(SNA_RESOURCE).getFile());
        File output  = tmp.newFile("test-output.rom");

        List<Game> games = MainCLI.loadGames(List.of(snaFile));
        assertEquals(1, games.size());

        MainCLI.generateRomSet(games, output);

        assertTrue("ROM set file must exist", output.exists());
        assertEquals("ROM set must be exactly 512 KiB", EXPECTED_ROM_SIZE, output.length());
    }

    @Test
    public void loadGames_setsNameFromFileName_whenNoEmbeddedName() throws IOException {
        File snaFile = new File(getClass().getResource(SNA_RESOURCE).getFile());
        List<Game> games = MainCLI.loadGames(List.of(snaFile));
        assertFalse("Game name must not be empty", games.get(0).getName().isEmpty());
    }

    @Test(expected = IOException.class)
    public void loadGames_unsupportedExtension_throws() throws IOException {
        File badFile = tmp.newFile("game.xyz");
        MainCLI.loadGames(List.of(badFile));
    }

    // -----------------------------------------------------------------------
    // CliRomSetHandler headless wiring
    // -----------------------------------------------------------------------

    @Test
    public void cliRomSetHandler_canBeInstantiatedWithoutJavaFX() throws IOException {
        // Merely instantiating the handler must not throw even without a JavaFX runtime.
        CliRomSetHandler handler = new CliRomSetHandler();
        assertNotNull(handler);
    }

    @Test
    public void cliRomSetHandler_generationAllowedAlwaysTrue() throws IOException {
        CliRomSetHandler handler = new CliRomSetHandler();
        assertTrue(handler.generationAllowedProperty().get());
    }

    @Test
    public void cliRomSetHandler_exportRomSet_producesCorrectSize() throws IOException {
        File snaFile = new File(getClass().getResource(SNA_RESOURCE).getFile());
        File output  = tmp.newFile("handler-test.rom");

        List<Game> games = MainCLI.loadGames(List.of(snaFile));

        CliRomSetHandler handler = new CliRomSetHandler();
        handler.bindGameList(games);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(output)) {
            handler.exportRomSet(fos);
        }

        assertEquals(EXPECTED_ROM_SIZE, output.length());
    }
}
