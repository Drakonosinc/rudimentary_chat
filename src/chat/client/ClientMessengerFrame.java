package chat.client;

import chat.protocol.ChatPacket;
import chat.protocol.PacketType;
import chat.protocol.UserInfo;
import chat.protocol.UserStatus;
import chat.ui.DarkTheme;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modern Dark Swing Chat Studio supporting multi-channel messaging, 1-to-1 private DMs,
 * end-to-end AES-GCM encryption, binary file sharing, slash commands, and history export.
 */
public class ClientMessengerFrame extends JFrame implements ChatClientConnection.ClientListener {
    private static final long serialVersionUID = 1L;

    private final ChatClientConnection connection;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField nickField;
    private final JComboBox<UserStatus> statusCombo;
    private final JCheckBox chkEncrypt;
    private final JPasswordField passphraseField;
    private final JButton connectBtn;

    private final DefaultListModel<String> channelListModel;
    private final JList<String> channelList;
    private final DefaultListModel<UserInfo> userListModel;
    private final JList<UserInfo> userList;

    private final JLabel activeTargetLabel;
    private final JButton clearDmBtn;
    private final JEditorPane chatPane;
    private final StringBuilder htmlTranscript = new StringBuilder();
    private final List<String> plainHistory = new ArrayList<String>();

    private final JTextField inputField;
    private String currentChannel = "#general";
    private String currentDmTarget = null;

    public ClientMessengerFrame(String defaultHost, int defaultPort, String defaultNick) {
        this.connection = new ChatClientConnection(this);

        setTitle("RudimentaryChat Studio 2.0 — Cliente TCP Full-Duplex & Cifrado AES-GCM");
        setSize(980, 660);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(DarkTheme.BG_DARKEST);
        setLayout(new BorderLayout(10, 10));

        // 1. Top Connection & Security Bar
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        topPanel.setBackground(DarkTheme.BG_PANEL);
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row1.setOpaque(false);
        row1.add(createLabel("Servidor:"));
        hostField = DarkTheme.createTextField(defaultHost, 10);
        row1.add(hostField);

        row1.add(createLabel("Puerto:"));
        portField = DarkTheme.createTextField(String.valueOf(defaultPort), 5);
        row1.add(portField);

        row1.add(createLabel("Nickname:"));
        nickField = DarkTheme.createTextField(defaultNick, 10);
        row1.add(nickField);

        row1.add(createLabel("Estado:"));
        statusCombo = new JComboBox<UserStatus>(UserStatus.values());
        statusCombo.setBackground(DarkTheme.BG_INPUT);
        statusCombo.setForeground(DarkTheme.TEXT_PRIMARY);
        statusCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UserStatus selected = (UserStatus) statusCombo.getSelectedItem();
                if (selected != null && connection.isConnected()) {
                    connection.sendStatusUpdate(selected);
                }
            }
        });
        row1.add(statusCombo);

        connectBtn = DarkTheme.createButton("⚡ Conectar", DarkTheme.ACCENT_PRIMARY, Color.WHITE);
        connectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleConnection();
            }
        });
        row1.add(connectBtn);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row2.setOpaque(false);
        chkEncrypt = new JCheckBox("🔒 Cifrado Extremo a Extremo (AES-GCM)", true);
        chkEncrypt.setOpaque(false);
        chkEncrypt.setForeground(DarkTheme.ACCENT_CYAN);
        chkEncrypt.setFont(DarkTheme.FONT_BOLD);

        row2.add(chkEncrypt);
        row2.add(createLabel("Clave Secreta Compartida:"));
        passphraseField = new JPasswordField("secret-room-key", 14);
        passphraseField.setBackground(DarkTheme.BG_INPUT);
        passphraseField.setForeground(DarkTheme.TEXT_PRIMARY);
        passphraseField.setCaretColor(DarkTheme.TEXT_PRIMARY);
        passphraseField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DarkTheme.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        row2.add(passphraseField);

        JButton exportBtn = DarkTheme.createButton("💾 Exportar Chat", DarkTheme.BG_CARD, DarkTheme.TEXT_PRIMARY);
        exportBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportHistory();
            }
        });
        row2.add(exportBtn);

        topPanel.add(row1);
        topPanel.add(row2);
        add(topPanel, BorderLayout.NORTH);

        // 2. Left Sidebar: Channels + Connected Peers
        JPanel sidebar = new JPanel(new GridLayout(2, 1, 0, 10));
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(235, 400));
        sidebar.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        // Channels Box
        JPanel channelBox = new JPanel(new BorderLayout(0, 6));
        channelBox.setBackground(DarkTheme.BG_PANEL);
        channelBox.setBorder(DarkTheme.createCardBorder());
        JLabel chTitle = new JLabel("💬 SALAS PÚBLICAS");
        chTitle.setFont(DarkTheme.FONT_BOLD);
        chTitle.setForeground(DarkTheme.TEXT_MUTED);
        channelBox.add(chTitle, BorderLayout.NORTH);

        channelListModel = new DefaultListModel<String>();
        channelListModel.addElement("#general");
        channelListModel.addElement("#dev");
        channelListModel.addElement("#gaming");
        channelListModel.addElement("#offtopic");

        channelList = new JList<String>(channelListModel);
        channelList.setSelectedIndex(0);
        channelList.setBackground(DarkTheme.BG_INPUT);
        channelList.setForeground(DarkTheme.TEXT_PRIMARY);
        channelList.setSelectionBackground(DarkTheme.ACCENT_PRIMARY);
        channelList.setSelectionForeground(Color.WHITE);
        channelList.setFont(DarkTheme.FONT_BOLD);
        channelList.setFixedCellHeight(28);
        channelList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && channelList.getSelectedValue() != null) {
                    currentChannel = channelList.getSelectedValue();
                    currentDmTarget = null;
                    userList.clearSelection();
                    updateTargetBanner();
                }
            }
        });
        channelBox.add(new JScrollPane(channelList), BorderLayout.CENTER);
        sidebar.add(channelBox);

        // Online Users Box (Click for DM)
        JPanel usersBox = new JPanel(new BorderLayout(0, 6));
        usersBox.setBackground(DarkTheme.BG_PANEL);
        usersBox.setBorder(DarkTheme.createCardBorder());
        JLabel usrTitle = new JLabel("🟢 USUARIOS (Clic para DM)");
        usrTitle.setFont(DarkTheme.FONT_BOLD);
        usrTitle.setForeground(DarkTheme.TEXT_MUTED);
        usersBox.add(usrTitle, BorderLayout.NORTH);

        userListModel = new DefaultListModel<UserInfo>();
        userList = new JList<UserInfo>(userListModel);
        userList.setBackground(DarkTheme.BG_INPUT);
        userList.setForeground(DarkTheme.TEXT_PRIMARY);
        userList.setSelectionBackground(DarkTheme.ACCENT_CYAN);
        userList.setSelectionForeground(Color.BLACK);
        userList.setFont(DarkTheme.FONT_BODY);
        userList.setFixedCellHeight(28);
        userList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                    UserInfo selected = userList.getSelectedValue();
                    if (!selected.getNickname().equals(connection.getNickname())) {
                        currentDmTarget = selected.getNickname();
                        updateTargetBanner();
                    }
                }
            }
        });
        usersBox.add(new JScrollPane(userList), BorderLayout.CENTER);
        sidebar.add(usersBox);

        add(sidebar, BorderLayout.WEST);

        // 3. Center Chat Stream Panel
        JPanel chatContainer = new JPanel(new BorderLayout(0, 8));
        chatContainer.setBackground(DarkTheme.BG_PANEL);
        chatContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 12),
                DarkTheme.createCardBorder()
        ));

        JPanel bannerRow = new JPanel(new BorderLayout());
        bannerRow.setOpaque(false);
        activeTargetLabel = new JLabel("Hablando en sala pública: #general");
        activeTargetLabel.setFont(DarkTheme.FONT_TITLE);
        activeTargetLabel.setForeground(DarkTheme.TEXT_PRIMARY);

        clearDmBtn = DarkTheme.createButton("Volver a Sala Pública", DarkTheme.BG_CARD, DarkTheme.TEXT_PRIMARY);
        clearDmBtn.setVisible(false);
        clearDmBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentDmTarget = null;
                userList.clearSelection();
                updateTargetBanner();
            }
        });
        bannerRow.add(activeTargetLabel, BorderLayout.WEST);
        bannerRow.add(clearDmBtn, BorderLayout.EAST);
        chatContainer.add(bannerRow, BorderLayout.NORTH);

        chatPane = new JEditorPane();
        chatPane.setContentType("text/html");
        chatPane.setEditable(false);
        chatPane.setBackground(DarkTheme.BG_INPUT);
        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createLineBorder(DarkTheme.BORDER_SUBTLE));
        chatContainer.add(chatScroll, BorderLayout.CENTER);

        // 4. Bottom Message Composer & File Sharing Bar
        JPanel composerBar = new JPanel(new BorderLayout(8, 0));
        composerBar.setOpaque(false);

        JButton attachBtn = DarkTheme.createButton("📎 Archivo", DarkTheme.BG_CARD, DarkTheme.TEXT_PRIMARY);
        attachBtn.setToolTipText("Enviar un archivo o imagen por el socket");
        attachBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseAndSendFile();
            }
        });

        inputField = DarkTheme.createTextField("", 30);
        JButton sendBtn = DarkTheme.createButton("Enviar ➤", DarkTheme.ACCENT_PRIMARY, Color.WHITE);

        ActionListener sendAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendCurrentInput();
            }
        };
        inputField.addActionListener(sendAction);
        sendBtn.addActionListener(sendAction);

        composerBar.add(attachBtn, BorderLayout.WEST);
        composerBar.add(inputField, BorderLayout.CENTER);
        composerBar.add(sendBtn, BorderLayout.EAST);

        chatContainer.add(composerBar, BorderLayout.SOUTH);
        add(chatContainer, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                connection.disconnect();
            }
        });

        appendSystemHtml("Bienvenido a <b>RudimentaryChat Studio 2.0</b>. Haz clic en <b>⚡ Conectar</b> o usa <code>/help</code> para ver los comandos.");
        autoConnectIfAvailable();
    }

    private void autoConnectIfAvailable() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    syncCryptoSettings();
                    int port = Integer.parseInt(portField.getText().trim());
                    connection.connect(
                            hostField.getText().trim(),
                            port,
                            nickField.getText().trim(),
                            (UserStatus) statusCombo.getSelectedItem()
                    );
                } catch (Exception ignored) {
                    // Server may not be started yet; user can click Connect anytime
                }
            }
        });
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(DarkTheme.TEXT_MUTED);
        l.setFont(DarkTheme.FONT_BOLD);
        return l;
    }

    private void updateTargetBanner() {
        if (currentDmTarget != null) {
            activeTargetLabel.setText("🔒 Mensaje Directo Privado con @" + currentDmTarget);
            activeTargetLabel.setForeground(DarkTheme.ACCENT_CYAN);
            clearDmBtn.setVisible(true);
        } else {
            activeTargetLabel.setText("💬 Sala Pública: " + currentChannel);
            activeTargetLabel.setForeground(DarkTheme.TEXT_PRIMARY);
            clearDmBtn.setVisible(false);
        }
    }

    private void syncCryptoSettings() {
        connection.setEncryptionConfig(
                chkEncrypt.isSelected(),
                new String(passphraseField.getPassword())
        );
    }

    private void toggleConnection() {
        if (connection.isConnected()) {
            connection.disconnect();
        } else {
            try {
                syncCryptoSettings();
                int port = Integer.parseInt(portField.getText().trim());
                connection.connect(
                        hostField.getText().trim(),
                        port,
                        nickField.getText().trim(),
                        (UserStatus) statusCombo.getSelectedItem()
                );
            } catch (Exception ex) {
                appendSystemHtml("❌ No se pudo conectar al servidor: " + DarkTheme.escapeHtml(ex.getMessage()));
            }
        }
    }

    private void sendCurrentInput() {
        String raw = inputField.getText().trim();
        if (raw.isEmpty()) {
            return;
        }
        inputField.setText("");
        syncCryptoSettings();

        // Slash commands
        if (raw.startsWith("/")) {
            handleSlashCommand(raw);
            return;
        }

        if (!connection.isConnected()) {
            appendSystemHtml("⚠ Primero conéctate al servidor haciendo clic en <b>⚡ Conectar</b>.");
            return;
        }

        if (currentDmTarget != null) {
            connection.sendPrivateMessage(currentDmTarget, raw);
        } else {
            connection.sendChannelMessage(currentChannel, raw);
        }
    }

    private void handleSlashCommand(String cmdLine) {
        String[] parts = cmdLine.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        if ("/help".equals(cmd)) {
            appendSystemHtml(
                    "<b>Comandos disponibles:</b><br>"
                            + "• <code>/w &lt;usuario&gt; &lt;mensaje&gt;</code> — Enviar mensaje directo privado<br>"
                            + "• <code>/roll</code> — Lanzar un dado (1-100) en la sala actual<br>"
                            + "• <code>/clear</code> — Limpiar la pantalla de conversación<br>"
                            + "• <code>/help</code> — Mostrar esta ayuda"
            );
        } else if ("/clear".equals(cmd)) {
            htmlTranscript.setLength(0);
            refreshChatPane();
        } else if ("/roll".equals(cmd)) {
            int val = new Random().nextInt(100) + 1;
            connection.sendChannelMessage(currentChannel, "🎲 lanzó un dado y obtuvo: " + val + "/100");
        } else if (("/w".equals(cmd) || "/dm".equals(cmd)) && parts.length >= 3) {
            connection.sendPrivateMessage(parts[1], parts[2]);
        } else {
            appendSystemHtml("Comando no reconocido. Escribe <code>/help</code> para ver la lista.");
        }
    }

    private void chooseAndSendFile() {
        if (!connection.isConnected()) {
            appendSystemHtml("⚠ Debes estar conectado para compartir archivos.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo para enviar por el socket");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                syncCryptoSettings();
                connection.sendFile(currentChannel, currentDmTarget, file);
            } catch (Exception ex) {
                appendSystemHtml("❌ Error enviando archivo: " + DarkTheme.escapeHtml(ex.getMessage()));
            }
        }
    }

    private void exportHistory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar historial de chat");
        chooser.setSelectedFile(new File("chat_history_" + connection.getNickname() + ".txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), plainHistory, StandardCharsets.UTF_8);
                appendSystemHtml("💾 Historial exportado en: <code>"
                        + DarkTheme.escapeHtml(chooser.getSelectedFile().getAbsolutePath()) + "</code>");
            } catch (Exception ex) {
                appendSystemHtml("❌ No se pudo exportar el historial: " + DarkTheme.escapeHtml(ex.getMessage()));
            }
        }
    }

    private void appendSystemHtml(String htmlFragment) {
        htmlTranscript.append("<div style='margin:6px 0; padding:6px 10px; background:#172136; border-left:3px solid #6366F1; color:#94A3B8; font-size:11px;'>")
                .append(htmlFragment)
                .append("</div>");
        refreshChatPane();
    }

    private void refreshChatPane() {
        String doc = "<html><body style='font-family:Segoe UI, sans-serif; background-color:#0F1524; color:#F1F5F9; padding:8px;'>"
                + htmlTranscript.toString()
                + "</body></html>";
        chatPane.setText(doc);
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    @Override
    public void onConnectionStateChanged(final boolean isConn, final String statusText) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                hostField.setEnabled(!isConn);
                portField.setEnabled(!isConn);
                nickField.setEnabled(!isConn);
                if (isConn) {
                    connectBtn.setText("Desconectar");
                    connectBtn.setBackground(DarkTheme.ACCENT_DANGER);
                } else {
                    connectBtn.setText("⚡ Conectar");
                    connectBtn.setBackground(DarkTheme.ACCENT_PRIMARY);
                    userListModel.clear();
                }
                appendSystemHtml(DarkTheme.escapeHtml(statusText));
            }
        });
    }

    @Override
    public void onMessageReceived(final ChatPacket packet, final String displayContent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean isDm = packet.getType() == PacketType.PRIVATE_MESSAGE;
                String scopeBadge = isDm
                        ? "<span style='color:#06B6D4; font-weight:bold;'>[DM Privado]</span>"
                        : "<span style='color:#818CF8;'>[" + DarkTheme.escapeHtml(packet.getChannel()) + "]</span>";
                String lockBadge = packet.isEncrypted() ? " 🔒" : "";
                String senderColor = packet.getSender().equals(connection.getNickname()) ? "#34D399" : "#FBBF24";

                plainHistory.add(String.format("[%s] %s %s: %s",
                        packet.getTimestamp(),
                        isDm ? "[DM]" : packet.getChannel(),
                        packet.getSender(),
                        displayContent));

                htmlTranscript.append("<div style='margin:7px 0; padding:8px 12px; background:#1C2640; border-radius:6px;'>")
                        .append("<div style='font-size:10px; color:#94A3B8;'>")
                        .append(DarkTheme.escapeHtml(packet.getTimestamp())).append(" ")
                        .append(scopeBadge).append(lockBadge).append(" — ")
                        .append("<b style='color:").append(senderColor).append(";'>")
                        .append(DarkTheme.escapeHtml(packet.getSender())).append("</b></div>")
                        .append("<div style='margin-top:3px; font-size:12px; color:#F8FAFC;'>")
                        .append(DarkTheme.escapeHtml(displayContent))
                        .append("</div></div>");
                refreshChatPane();
            }
        });
    }

    @Override
    public void onFileReceived(final ChatPacket packet, final byte[] decodedData) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                int len = decodedData != null ? decodedData.length : 0;
                appendSystemHtml("📎 <b>" + DarkTheme.escapeHtml(packet.getSender())
                        + "</b> compartió el archivo <b>" + DarkTheme.escapeHtml(packet.getFileName())
                        + "</b> (" + len + " bytes)" + (packet.isEncrypted() ? " [Descifrado AES-GCM 🔒]" : ""));

                if (!packet.getSender().equals(connection.getNickname()) && decodedData != null) {
                    int choice = JOptionPane.showConfirmDialog(
                            ClientMessengerFrame.this,
                            packet.getSender() + " te envió el archivo '" + packet.getFileName() + "' (" + len + " bytes).\n¿Deseas guardarlo en tu equipo?",
                            "Archivo Recibido por Socket",
                            JOptionPane.YES_NO_OPTION
                    );
                    if (choice == JOptionPane.YES_OPTION) {
                        JFileChooser saver = new JFileChooser();
                        saver.setSelectedFile(new File(packet.getFileName()));
                        if (saver.showSaveDialog(ClientMessengerFrame.this) == JFileChooser.APPROVE_OPTION) {
                            try (FileOutputStream fos = new FileOutputStream(saver.getSelectedFile())) {
                                fos.write(decodedData);
                                appendSystemHtml("✓ Archivo guardado en: <code>"
                                        + DarkTheme.escapeHtml(saver.getSelectedFile().getAbsolutePath()) + "</code>");
                            } catch (Exception ex) {
                                appendSystemHtml("❌ Error guardando archivo: " + DarkTheme.escapeHtml(ex.getMessage()));
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onRosterUpdated(final List<UserInfo> roster) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                userListModel.clear();
                if (roster != null) {
                    for (UserInfo info : roster) {
                        userListModel.addElement(info);
                    }
                }
            }
        });
    }

    @Override
    public void onSystemNotice(final String timestamp, final String notice) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                plainHistory.add("[" + timestamp + "] [SISTEMA] " + notice);
                appendSystemHtml("<b>[" + DarkTheme.escapeHtml(timestamp) + "]</b> " + DarkTheme.escapeHtml(notice));
            }
        });
    }
}
