package com.grelobites.romgenerator;

/**
 * Bootstrap entry point for the Dandanator Mini ROM Generator fat JAR.
 *
 * <p>This class is intentionally thin. Its only purpose is to shield the JavaFX
 * dependency check that would otherwise fail on startup when JavaFX is bundled inside
 * the shaded JAR but the module-path is not explicitly configured.</p>
 *
 * <h2>Routing logic</h2>
 * <ul>
 *   <li>If the first argument is {@value MainCLI#CLI_FLAG}, the remaining arguments are
 *       forwarded to {@link MainCLI} and the graphical interface is <em>not</em>
 *       started. This is useful for headless environments and automated workflows.</li>
 *   <li>Otherwise the full JavaFX GUI is launched via {@link MainApp}.</li>
 * </ul>
 *
 * <h2>CLI quick-start</h2>
 * <pre>
 *   java -jar dandanator-mini-&lt;version&gt;.jar --cli -o output.rom game1.sna game2.mld
 *   java -jar dandanator-mini-&lt;version&gt;.jar --cli --help
 * </pre>
 *
 * @see MainCLI
 * @see MainApp
 */
public class MainShade {

    /**
     * Application entry point.
     *
     * @param args command-line arguments; if the first element is {@value MainCLI#CLI_FLAG}
     *             the remaining arguments are forwarded to {@link MainCLI#main(String[])},
     *             otherwise the full GUI is launched.
     */
    public static void main(String[] args) {
        if (args.length > 0 && MainCLI.CLI_FLAG.equals(args[0])) {
            // Strip the --cli flag and forward the rest to the CLI entry point.
            String[] cliArgs = new String[args.length - 1];
            System.arraycopy(args, 1, cliArgs, 0, cliArgs.length);
            MainCLI.main(cliArgs);
        } else {
            MainApp.main(args);
        }
    }
}
