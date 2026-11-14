import chat.client.ClientMessengerFrame;

import javax.swing.*;
import java.util.Random;

/**
 * Main entry point for RudimentaryChat Studio 2.0 Desktop Client.
 */
public class Cliente {

    public static void main(String[] args) {
        final String host = (args != null && args.length > 0) ? args[0] : "127.0.0.1";
        final int port = (args != null && args.length > 1) ? parsePort(args[1], 9999) : 9999;
        final String defaultNick = (args != null && args.length > 2)
                ? args[2]
                : "User" + (100 + new Random().nextInt(900));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ClientMessengerFrame frame = new ClientMessengerFrame(host, port, defaultNick);
                frame.setVisible(true);
            }
        });
    }

    private static int parsePort(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
