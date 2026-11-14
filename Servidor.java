import chat.server.ServerDashboardFrame;

import javax.swing.*;

/**
 * Main entry point for RudimentaryChat Studio 2.0 Central Server & Packet Inspector.
 */
public class Servidor {

    public static void main(String[] args) {
        final int port = parsePort(args, 9999);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ServerDashboardFrame frame = new ServerDashboardFrame(port);
                frame.setVisible(true);
            }
        });
    }

    private static int parsePort(String[] args, int fallback) {
        if (args != null && args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
