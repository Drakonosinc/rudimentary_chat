package chat.client;

import chat.protocol.ChatPacket;
import chat.protocol.PacketType;
import chat.protocol.UserInfo;
import chat.protocol.UserStatus;
import chat.security.CryptoEngine;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent full-duplex TCP client connection managing socket streams,
 * optional AES-GCM encryption, and asynchronous packet dispatch.
 */
public class ChatClientConnection {

    public interface ClientListener {
        void onConnectionStateChanged(boolean connected, String statusText);

        void onMessageReceived(ChatPacket packet, String displayContent);

        void onFileReceived(ChatPacket packet, byte[] decodedData);

        void onRosterUpdated(List<UserInfo> roster);

        void onSystemNotice(String timestamp, String notice);
    }

    private final ClientListener listener;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread readerThread;

    private volatile String nickname = "Usuario";
    private volatile boolean encryptionEnabled = true;
    private volatile String passphrase = "secret-room-key";

    public ChatClientConnection(ClientListener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port, String nick, UserStatus initialStatus) throws IOException {
        if (connected.get()) {
            disconnect();
        }
        this.nickname = (nick == null || nick.trim().isEmpty()) ? "Usuario" : nick.trim();
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
        this.connected.set(true);

        sendPacket(ChatPacket.createConnect(this.nickname, initialStatus));

        if (listener != null) {
            listener.onConnectionStateChanged(true, "Conectado a " + host + ":" + port + " como " + this.nickname);
        }

        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "ChatClient-Reader-" + this.nickname);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void disconnect() {
        if (!connected.getAndSet(false)) {
            return;
        }
        try {
            ChatPacket bye = new ChatPacket();
            bye.setType(PacketType.DISCONNECT);
            bye.setSender(nickname);
            sendPacket(bye);
        } catch (Exception ignored) {
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        if (listener != null) {
            listener.onConnectionStateChanged(false, "Desconectado");
        }
    }

    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed();
    }

    public String getNickname() {
        return nickname;
    }

    public void setEncryptionConfig(boolean enabled, String keyPassphrase) {
        this.encryptionEnabled = enabled;
        this.passphrase = keyPassphrase != null ? keyPassphrase : "";
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void sendChannelMessage(String channel, String plainText) {
        if (!isConnected() || plainText == null || plainText.trim().isEmpty()) {
            return;
        }
        String payload = encryptionEnabled
                ? CryptoEngine.encryptText(plainText, passphrase)
                : plainText;
        ChatPacket packet = ChatPacket.createChannelMessage(nickname, channel, payload, encryptionEnabled);
        sendPacket(packet);
    }

    public void sendPrivateMessage(String targetUser, String plainText) {
        if (!isConnected() || targetUser == null || plainText == null || plainText.trim().isEmpty()) {
            return;
        }
        String payload = encryptionEnabled
                ? CryptoEngine.encryptText(plainText, passphrase)
                : plainText;
        ChatPacket packet = ChatPacket.createPrivateMessage(nickname, targetUser, payload, encryptionEnabled);
        sendPacket(packet);
    }

    public void sendStatusUpdate(UserStatus newStatus) {
        if (!isConnected()) {
            return;
        }
        sendPacket(ChatPacket.createStatusUpdate(nickname, newStatus));
    }

    public void sendFile(String channel, String targetUser, File file) throws Exception {
        if (!isConnected() || file == null || !file.exists()) {
            return;
        }
        byte[] rawBytes = Files.readAllBytes(file.toPath());
        sendFileBytes(channel, targetUser, file.getName(), rawBytes);
    }

    public void sendFileBytes(String channel, String targetUser, String fileName, byte[] rawBytes) throws Exception {
        if (!isConnected() || rawBytes == null) {
            return;
        }
        byte[] wireBytes = encryptionEnabled
                ? CryptoEngine.encryptBytes(rawBytes, passphrase)
                : rawBytes;
        ChatPacket packet = ChatPacket.createFileTransfer(
                nickname,
                channel,
                targetUser,
                fileName,
                wireBytes,
                encryptionEnabled
        );
        sendPacket(packet);
    }

    public synchronized void sendPacket(ChatPacket packet) {
        if (!connected.get() || out == null) {
            return;
        }
        try {
            out.reset();
            out.writeObject(packet);
            out.flush();
        } catch (IOException e) {
            disconnect();
        }
    }

    private void readLoop() {
        try {
            while (connected.get() && !socket.isClosed()) {
                Object obj = in.readObject();
                if (!(obj instanceof ChatPacket)) {
                    continue;
                }
                dispatchIncoming((ChatPacket) obj);
            }
        } catch (EOFException | SocketException ignored) {
        } catch (Exception e) {
            if (listener != null && connected.get()) {
                listener.onSystemNotice("--:--:--", "Error de conexión: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void dispatchIncoming(ChatPacket packet) {
        if (listener == null || packet.getType() == null) {
            return;
        }
        switch (packet.getType()) {
            case ROSTER_SYNC:
                listener.onRosterUpdated(packet.getRoster());
                break;

            case SERVER_ANNOUNCEMENT:
                listener.onSystemNotice(packet.getTimestamp(), packet.getContent());
                break;

            case KICK:
                listener.onSystemNotice(packet.getTimestamp(), "⛔ Expulsado del servidor: " + packet.getContent());
                disconnect();
                break;

            case CHAT_MESSAGE:
            case PRIVATE_MESSAGE:
                String decoded = packet.isEncrypted()
                        ? CryptoEngine.decryptText(packet.getContent(), passphrase)
                        : packet.getContent();
                listener.onMessageReceived(packet, decoded);
                break;

            case FILE_TRANSFER:
                byte[] decodedBytes = packet.getFileData();
                if (packet.isEncrypted() && decodedBytes != null) {
                    try {
                        decodedBytes = CryptoEngine.decryptBytes(decodedBytes, passphrase);
                    } catch (Exception e) {
                        listener.onSystemNotice(packet.getTimestamp(), "🔒 No se pudo descifrar el archivo '" + packet.getFileName() + "' (clave AES distinta).");
                        return;
                    }
                }
                listener.onFileReceived(packet, decodedBytes);
                break;

            default:
                break;
        }
    }
}
