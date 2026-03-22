package com.grelobites.romgenerator.handlers.dandanatormini.v10;

import com.grelobites.romgenerator.ApplicationContext;
import com.grelobites.romgenerator.Configuration;
import com.grelobites.romgenerator.handlers.dandanatormini.DandanatorMiniConstants;
import com.grelobites.romgenerator.handlers.dandanatormini.DandanatorMiniRamGameCompressor;
import com.grelobites.romgenerator.model.Game;
import com.grelobites.romgenerator.model.SnapshotGame;
import com.grelobites.romgenerator.util.OperationResult;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Headless ROM set handler for CLI (non-GUI) use.
 *
 * <p>This handler extends {@link DandanatorMiniV10RomSetHandler} and bypasses all
 * JavaFX GUI initialisation (menu-image creation, scene-graph bindings, etc.). It is
 * intended to be used from the command-line tool ({@code MainCLI}) and in automated
 * workflows where no display is available.</p>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * CliRomSetHandler handler = new CliRomSetHandler();
 * handler.bindGameList(games);
 * try (FileOutputStream fos = new FileOutputStream("output.rom")) {
 *     handler.exportRomSet(fos);
 * }
 * }</pre>
 */
public class CliRomSetHandler extends DandanatorMiniV10RomSetHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CliRomSetHandler.class);

    private static final DandanatorMiniRamGameCompressor CLI_COMPRESSOR =
            new DandanatorMiniRamGameCompressor();

    /**
     * Minimal application context used by the CLI handler.
     * Holds a game list and the EEPROM-loader-included flag without any JavaFX UI state.
     */
    private static class CliContext extends ApplicationContext {
        CliContext() {
            super();
        }
    }

    /**
     * Creates a new {@code CliRomSetHandler}.
     * The constructor is safe to call without a running JavaFX application because the
     * GUI-specific menu-image initialisation has been moved to {@link #bind(ApplicationContext)}.
     *
     * @throws IOException if the base handler cannot be initialised
     */
    public CliRomSetHandler() throws IOException {
        super();
    }

    /**
     * Populates this handler with the supplied game list and prepares it for ROM export.
     * This is the preferred entry-point for CLI callers; it avoids the need to create or
     * manage an {@link ApplicationContext} directly.
     *
     * <p>For each {@link SnapshotGame} in the list this method eagerly computes the
     * compressed slot data (equivalent to what the GUI does via {@code addGame()}) so
     * that {@link #exportRomSet(OutputStream)} can proceed without further setup.</p>
     *
     * @param games the games to include in the generated ROM set (order is preserved)
     * @throws IOException if a game's compressed data cannot be calculated
     */
    public void bindGameList(List<Game> games) throws IOException {
        LOGGER.debug("bindGameList called with {} games", games.size());
        CliContext ctx = new CliContext();
        for (Game game : games) {
            prepareGame(game);
            ctx.getGameList().add(game);
        }
        bindCliContext(ctx);
    }

    /**
     * Prepares a game for ROM generation, mirroring the GUI's {@code prepareAddedGame()}
     * logic that runs in the background when a game is added to the list in the GUI.
     *
     * <ul>
     *   <li>For {@link SnapshotGame}: eagerly computes compressed slot data so that
     *       {@code exportRomSet()} can use it without triggering a null-pointer on
     *       the first call to {@code recompressSlot()}.</li>
     *   <li>For all games: sets the ROM reference to the internal ROM if it is unset.</li>
     * </ul>
     *
     * @param game the game to prepare
     * @throws IOException if compression fails
     */
    private static void prepareGame(Game game) throws IOException {
        if (game instanceof SnapshotGame) {
            SnapshotGame snapshotGame = (SnapshotGame) game;
            if (snapshotGame.getRom() == null) {
                snapshotGame.setRom(DandanatorMiniConstants.INTERNAL_ROM_GAME);
            }
            // Eagerly compute compressed data so recompressSlot() never finds a null list.
            snapshotGame.getCompressedSize(CLI_COMPRESSOR);
        }
    }

    /**
     * Binds a pre-populated {@link ApplicationContext} to this handler in headless mode.
     * Unlike the GUI {@link #bind(ApplicationContext)}, this method skips all JavaFX
     * scene-graph operations (frame loading, menu-image creation, property listeners, etc.)
     * and only stores the context reference so that {@link #exportRomSet(OutputStream)} can
     * be called afterwards.
     *
     * @param applicationContext the context whose game list will be exported
     */
    public void bindCliContext(ApplicationContext applicationContext) {
        LOGGER.debug("CliRomSetHandler: binding context in headless mode with {} games",
                applicationContext.getGameList().size());
        this.applicationContext = applicationContext;
        Configuration.getInstance().setRamGameCompressor(CLI_COMPRESSOR);
    }

    /**
     * Full GUI bind — not supported in CLI mode.
     * This override performs a headless bind so that accidentally calling
     * {@code bind(ctx)} on a {@code CliRomSetHandler} still produces a usable state
     * rather than attempting to manipulate JavaFX scene-graph nodes.
     *
     * @param applicationContext the application context (GUI state is ignored)
     */
    @Override
    public void bind(ApplicationContext applicationContext) {
        LOGGER.warn("bind() called on CliRomSetHandler; using headless bind instead");
        bindCliContext(applicationContext);
    }

    /** No-op in CLI mode — there is no GUI menu preview to update. */
    @Override
    public void updateMenuPreview() {
        // Intentionally empty: no display available in CLI mode.
    }

    /** No-op in CLI mode — there is no JavaFX ApplicationContext to unbind from. */
    @Override
    public void unbind() {
        LOGGER.debug("CliRomSetHandler: unbind (no-op)");
        applicationContext = null;
    }

    /**
     * Not supported in CLI mode.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Future<OperationResult> addGame(Game game) {
        throw new UnsupportedOperationException(
                "addGame is not supported in CLI mode; populate the game list before calling bindGameList()");
    }

    /**
     * Not supported in CLI mode.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void importRomSet(InputStream is) {
        throw new UnsupportedOperationException("importRomSet is not supported in CLI mode");
    }

    /**
     * Not supported in CLI mode.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void mergeRomSet(InputStream is) {
        throw new UnsupportedOperationException("mergeRomSet is not supported in CLI mode");
    }

    /** {@return always {@code true} in CLI mode since there is no capacity constraint enforced here} */
    @Override
    public BooleanProperty generationAllowedProperty() {
        return new SimpleBooleanProperty(true);
    }
}
