package chat.server;

import chat.protocol.UserInfo;
import chat.ui.DarkTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;

/**
 * Modern Dark Swing Server Control Center providing live telemetry, packet inspection,
 * connected peer management, and administrative broadcast controls.
 */
public class ServerDashboardFrame extends JFrame implements ChatServerEngine.ServerListener {
    private static final long serialVersionUID = 1L;

    private ChatServerEngine engine;
    private final JTextField portField;
    private final JButton toggleServerBtn;
    private final JLabel lblConnected;
    private final JLabel lblMessages;
    private final JLabel lblFiles;
    private final JLabel lblEncrypted;
    private final DefaultTableModel rosterModel;
    private final JTable rosterTable;
    private final JTextArea logArea;
    private final JTextField broadcastField;

    public ServerDashboardFrame(int defaultPort) {
        setTitle("RudimentaryChat Studio 2.0 — Servidor Central & Monitor de Paquetes");
        setSize(900, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(DarkTheme.BG_DARKEST);
        setLayout(new BorderLayout(12, 12));

        // 1. Header & Port Control Bar
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setBackground(DarkTheme.BG_PANEL);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)
        ));

        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("⚡ Servidor Central TCP Full-Duplex");
        title.setFont(DarkTheme.FONT_TITLE);
        title.setForeground(DarkTheme.TEXT_PRIMARY);
        JLabel subtitle = new JLabel("Enrutamiento de salas, mensajes directos cifrados (AES-GCM) y transferencia de archivos");
        subtitle.setFont(DarkTheme.FONT_SUBTITLE);
        subtitle.setForeground(DarkTheme.TEXT_MUTED);
        titleBox.add(title);
        titleBox.add(subtitle);

        JPanel controlsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlsBox.setOpaque(false);
        JLabel portLabel = new JLabel("Puerto TCP:");
        portLabel.setForeground(DarkTheme.TEXT_MUTED);
        portLabel.setFont(DarkTheme.FONT_BOLD);
        portField = DarkTheme.createTextField(String.valueOf(defaultPort), 6);
        toggleServerBtn = DarkTheme.createButton("Detener Servidor", DarkTheme.ACCENT_DANGER, Color.WHITE);
        toggleServerBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleServer();
            }
        });
        controlsBox.add(portLabel);
        controlsBox.add(portField);
        controlsBox.add(toggleServerBtn);

        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(controlsBox, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Center Workspace: Telemetry + Split (Roster Table | Packet Inspector Log)
        JPanel centerContainer = new JPanel(new BorderLayout(12, 12));
        centerContainer.setOpaque(false);
        centerContainer.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        JPanel metricsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        metricsGrid.setOpaque(false);
        lblConnected = createMetricCard(metricsGrid, "USUARIOS ONLINE", "0", DarkTheme.ACCENT_SUCCESS);
        lblMessages = createMetricCard(metricsGrid, "MENSAJES ENRUTADOS", "0", DarkTheme.ACCENT_PRIMARY);
        lblFiles = createMetricCard(metricsGrid, "ARCHIVOS TRANSFERIDOS", "0", DarkTheme.ACCENT_CYAN);
        lblEncrypted = createMetricCard(metricsGrid, "PAQUETES AES-GCM", "0", new Color(245, 158, 11));
        centerContainer.add(metricsGrid, BorderLayout.NORTH);

        // Left: Active Clients Roster + Kick button
        JPanel rosterPanel = new JPanel(new BorderLayout(0, 8));
        rosterPanel.setBackground(DarkTheme.BG_PANEL);
        rosterPanel.setBorder(DarkTheme.createCardBorder());
        rosterPanel.setPreferredSize(new Dimension(320, 350));

        JLabel rosterTitle = new JLabel("👥 Clientes Conectados");
        rosterTitle.setFont(DarkTheme.FONT_BOLD);
        rosterTitle.setForeground(DarkTheme.TEXT_PRIMARY);
        rosterPanel.add(rosterTitle, BorderLayout.NORTH);

        rosterModel = new DefaultTableModel(new Object[]{"Nickname", "Estado", "Endpoint"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        rosterTable = new JTable(rosterModel);
        rosterTable.setBackground(DarkTheme.BG_INPUT);
        rosterTable.setForeground(DarkTheme.TEXT_PRIMARY);
        rosterTable.setGridColor(DarkTheme.BORDER_SUBTLE);
        rosterTable.setRowHeight(26);
        rosterTable.setFont(DarkTheme.FONT_BODY);
        rosterTable.getTableHeader().setBackground(DarkTheme.BG_CARD);
        rosterTable.getTableHeader().setForeground(DarkTheme.TEXT_MUTED);
        rosterTable.getTableHeader().setFont(DarkTheme.FONT_BOLD);

        JScrollPane tableScroll = new JScrollPane(rosterTable);
        tableScroll.getViewport().setBackground(DarkTheme.BG_INPUT);
        tableScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_SUBTLE));
        rosterPanel.add(tableScroll, BorderLayout.CENTER);

        JButton kickBtn = DarkTheme.createButton("⛔ Expulsar Usuario Seleccionado", DarkTheme.BG_CARD, DarkTheme.TEXT_PRIMARY);
        kickBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int row = rosterTable.getSelectedRow();
                if (row >= 0 && engine != null) {
                    String nick = String.valueOf(rosterModel.getValueAt(row, 0));
                    engine.kickUser(nick, "Sesión finalizada desde el panel del servidor.");
                }
            }
        });
        rosterPanel.add(kickBtn, BorderLayout.SOUTH);

        // Right: Live Packet Inspector & Audit Log
        JPanel logPanel = new JPanel(new BorderLayout(0, 8));
        logPanel.setBackground(DarkTheme.BG_PANEL);
        logPanel.setBorder(DarkTheme.createCardBorder());

        JLabel logTitle = new JLabel("📡 Inspector de Tráfico TCP & Auditoría en Vivo");
        logTitle.setFont(DarkTheme.FONT_BOLD);
        logTitle.setForeground(DarkTheme.TEXT_PRIMARY);
        logPanel.add(logTitle, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(DarkTheme.BG_INPUT);
        logArea.setForeground(new Color(167, 243, 208));
        logArea.setFont(DarkTheme.FONT_MONO);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_SUBTLE));
        logPanel.add(logScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rosterPanel, logPanel);
        splitPane.setDividerLocation(335);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        centerContainer.add(splitPane, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Bar: Global Server Announcement
        JPanel bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(DarkTheme.BG_PANEL);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JLabel announceLbl = new JLabel("📢 Anuncio Global:");
        announceLbl.setFont(DarkTheme.FONT_BOLD);
        announceLbl.setForeground(DarkTheme.TEXT_PRIMARY);
        broadcastField = DarkTheme.createTextField("", 30);
        JButton sendBroadcastBtn = DarkTheme.createButton("Enviar a Todos", DarkTheme.ACCENT_PRIMARY, Color.WHITE);

        ActionListener broadcastAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (engine != null && engine.isRunning()) {
                    String text = broadcastField.getText().trim();
                    if (!text.isEmpty()) {
                        engine.broadcastServerAnnouncement(text);
                        broadcastField.setText("");
                    }
                }
            }
        };
        broadcastField.addActionListener(broadcastAction);
        sendBroadcastBtn.addActionListener(broadcastAction);

        bottomBar.add(announceLbl, BorderLayout.WEST);
        bottomBar.add(broadcastField, BorderLayout.CENTER);
        bottomBar.add(sendBroadcastBtn, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (engine != null) {
                    engine.stop();
                }
            }
        });

        startEngine(defaultPort);
    }

    private JLabel createMetricCard(JPanel container, String label, String initialValue, Color valueColor) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
        card.setBackground(DarkTheme.BG_PANEL);
        card.setBorder(DarkTheme.createCardBorder());

        JLabel title = new JLabel(label);
        title.setFont(new Font("Segoe UI", Font.BOLD, 10));
        title.setForeground(DarkTheme.TEXT_MUTED);

        JLabel val = new JLabel(initialValue);
        val.setFont(new Font("Segoe UI", Font.BOLD, 20));
        val.setForeground(valueColor);

        card.add(title);
        card.add(val);
        container.add(card);
        return val;
    }

    private void startEngine(int port) {
        try {
            engine = new ChatServerEngine(port, this);
            engine.start();
            portField.setEnabled(false);
            toggleServerBtn.setText("Detener Servidor");
            toggleServerBtn.setBackground(DarkTheme.ACCENT_DANGER);
        } catch (IOException ex) {
            onLogEvent("--:--:--", "ERROR", "No se pudo abrir el puerto " + port + ": " + ex.getMessage());
            portField.setEnabled(true);
            toggleServerBtn.setText("Iniciar Servidor");
            toggleServerBtn.setBackground(DarkTheme.ACCENT_SUCCESS);
        }
    }

    private void toggleServer() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
            portField.setEnabled(true);
            toggleServerBtn.setText("Iniciar Servidor");
            toggleServerBtn.setBackground(DarkTheme.ACCENT_SUCCESS);
        } else {
            int port = 9999;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ignored) {
            }
            startEngine(port);
        }
    }

    @Override
    public void onLogEvent(final String timestamp, final String category, final String detail) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logArea.append(String.format("[%s] [%-10s] %s%n", timestamp, category, detail));
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    @Override
    public void onRosterChanged(final List<UserInfo> activeUsers) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                rosterModel.setRowCount(0);
                for (UserInfo u : activeUsers) {
                    rosterModel.addRow(new Object[]{
                            u.getNickname(),
                            u.getStatus().getLabel(),
                            u.getRemoteAddress()
                    });
                }
            }
        });
    }

    @Override
    public void onMetricsChanged(
            final int connectedCount,
            final int totalMessages,
            final int totalFiles,
            final int encryptedCount
    ) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                lblConnected.setText(String.valueOf(connectedCount));
                lblMessages.setText(String.valueOf(totalMessages));
                lblFiles.setText(String.valueOf(totalFiles));
                lblEncrypted.setText(String.valueOf(encryptedCount));
            }
        });
    }
}
