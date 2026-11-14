package chat.server;

import chat.protocol.ChatPacket;
import chat.protocol.PacketType;
import chat.protocol.UserInfo;
import chat.protocol.UserStatus;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded persistent TCP Chat Server engine supporting channels, direct messages,
 * binary file relay, online status synchronization, and administrative moderation.
 */
public class ChatServerEngine {

    public interface ServerListener {
        void onLogEvent(String timestamp, String category, String detail);

        void onRosterChanged(List<UserInfo> activeUsers);

        void onMetricsChanged(int connectedCount, int totalMessages, int totalFiles, int encryptedCount);
    }

    private final int port;
    private final ServerListener listener;
    private final Map<String, ClientSession> clients = new ConcurrentHashMap<String, ClientSession>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicInteger totalMessages = new AtomicInteger(0);
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger encryptedPackets = new AtomicInteger(0);

    private ServerSocket serverSocket;

    public ChatServerEngine(int port, ServerListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() throws IOException {
        if (running.get()) {
            return;
        }
        serverSocket = new ServerSocket(port);
        running.set(true);
        log("SISTEMA", "Servidor TCP escuchando conexiones persistentes en el puerto " + getBoundPort());

        pool.submit(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        });
    }

    public int getBoundPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : port;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        for (ClientSession session : clients.values()) {
            session.closeQuietly();
        }
        clients.clear();
        pool.shutdownNow();
        notifyRosterAndMetrics();
        log("SISTEMA", "Servidor detenido.");
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientSession session = new ClientSession(socket);
                pool.submit(session);
            } catch (SocketException se) {
                if (running.get()) {
                    log("ERROR", "Error de socket al aceptar cliente: " + se.getMessage());
                }
            } catch (IOException ioe) {
                if (running.get()) {
                    log("ERROR", "Fallo E/S aceptando conexión: " + ioe.getMessage());
                }
            }
        }
    }

    public void broadcastServerAnnouncement(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        ChatPacket announcement = ChatPacket.createAnnouncement(messageText.trim());
        totalMessages.incrementAndGet();
        broadcastToAll(announcement);
        log("ANUNCIO", "Broadcast global enviado: " + messageText.trim());
        notifyRosterAndMetrics();
    }

    public boolean kickUser(String nickname, String reason) {
        ClientSession target = clients.remove(nickname);
        if (target == null) {
            return false;
        }
        ChatPacket kickPacket = new ChatPacket();
        kickPacket.setType(PacketType.KICK);
        kickPacket.setSender("SERVIDOR");
        kickPacket.setContent(reason != null ? reason : "Desconectado por el administrador");
        target.sendPacket(kickPacket);
        target.closeQuietly();

        log("ADMIN", "Usuario expulsado: " + nickname);
        broadcastToAll(ChatPacket.createAnnouncement(nickname + " fue desconectado por el administrador."));
        broadcastRoster();
        notifyRosterAndMetrics();
        return true;
    }

    private void broadcastToAll(ChatPacket packet) {
        for (ClientSession session : clients.values()) {
            session.sendPacket(packet);
        }
    }

    private void broadcastRoster() {
        List<UserInfo> roster = getActiveRoster();
        ChatPacket sync = new ChatPacket();
        sync.setType(PacketType.ROSTER_SYNC);
        sync.setSender("SERVIDOR");
        sync.setRoster(roster);
        broadcastToAll(sync);
    }

    public List<UserInfo> getActiveRoster() {
        List<UserInfo> list = new ArrayList<UserInfo>();
        for (ClientSession s : clients.values()) {
            if (s.nickname != null) {
                list.add(new UserInfo(s.nickname, s.status, s.remoteAddress));
            }
        }
        return list;
    }

    private void notifyRosterAndMetrics() {
        if (listener != null) {
            listener.onRosterChanged(getActiveRoster());
            listener.onMetricsChanged(
                    clients.size(),
                    totalMessages.get(),
                    totalFiles.get(),
                    encryptedPackets.get()
            );
        }
    }

    private void log(String category, String detail) {
        if (listener != null) {
            ChatPacket dummy = new ChatPacket();
            listener.onLogEvent(dummy.getTimestamp(), category, detail);
        }
    }

    private final class ClientSession implements Runnable {
        private final Socket socket;
        private final String remoteAddress;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private volatile String nickname;
        private volatile UserStatus status = UserStatus.ONLINE;

        ClientSession(Socket socket) {
            this.socket = socket;
            this.remoteAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (running.get() && !socket.isClosed()) {
                    Object obj = in.readObject();
                    if (!(obj instanceof ChatPacket)) {
                        continue;
                    }
                    handlePacket((ChatPacket) obj);
                }
            } catch (EOFException | SocketException ignored) {
                // Client disconnected normally
            } catch (Exception e) {
                log("ERROR", "Sesión interrumpida (" + (nickname != null ? nickname : remoteAddress) + "): " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void handlePacket(ChatPacket packet) {
            if (packet.isEncrypted()) {
                encryptedPackets.incrementAndGet();
            }
            PacketType type = packet.getType();
            if (type == null) {
                return;
            }

            switch (type) {
                case CONNECT:
                    registerNickname(packet.getSender(), packet.getUserStatus());
                    break;

                case DISCONNECT:
                    closeQuietly();
                    break;

                case STATUS_UPDATE:
                    this.status = packet.getUserStatus() != null ? packet.getUserStatus() : UserStatus.ONLINE;
                    log("ESTADO", nickname + " cambió su estado a " + this.status.getLabel());
                    broadcastRoster();
                    notifyRosterAndMetrics();
                    break;

                case CHAT_MESSAGE:
                    totalMessages.incrementAndGet();
                    String preview = packet.isEncrypted()
                            ? "[Payload AES-GCM: " + abbreviate(packet.getContent(), 28) + "]"
                            : packet.getContent();
                    log("CANAL " + packet.getChannel(), nickname + ": " + preview);
                    broadcastToAll(packet);
                    notifyRosterAndMetrics();
                    break;

                case PRIVATE_MESSAGE:
                    totalMessages.incrementAndGet();
                    String target = packet.getTargetUser();
                    String dmPreview = packet.isEncrypted() ? "[DM Cifrado AES-GCM]" : packet.getContent();
                    log("PRIVADO", nickname + " -> " + target + ": " + dmPreview);
                    ClientSession targetSession = target != null ? clients.get(target) : null;
                    if (targetSession != null) {
                        targetSession.sendPacket(packet);
                    }
                    if (!nickname.equals(target)) {
                        this.sendPacket(packet);
                    }
                    notifyRosterAndMetrics();
                    break;

                case FILE_TRANSFER:
                    totalFiles.incrementAndGet();
                    totalMessages.incrementAndGet();
                    int size = packet.getFileData() != null ? packet.getFileData().length : 0;
                    log("ARCHIVO", nickname + " compartió '" + packet.getFileName() + "' (" + size + " B)");
                    if (packet.getTargetUser() != null && !packet.getTargetUser().trim().isEmpty()) {
                        ClientSession recipient = clients.get(packet.getTargetUser());
                        if (recipient != null) {
                            recipient.sendPacket(packet);
                        }
                        if (!nickname.equals(packet.getTargetUser())) {
                            this.sendPacket(packet);
                        }
                    } else {
                        broadcastToAll(packet);
                    }
                    notifyRosterAndMetrics();
                    break;

                default:
                    break;
            }
        }

        private void registerNickname(String requestedNick, UserStatus initialStatus) {
            String base = (requestedNick == null || requestedNick.trim().isEmpty())
                    ? "Usuario"
                    : requestedNick.trim();
            String assigned = base;
            int suffix = 2;
            while (clients.containsKey(assigned)) {
                assigned = base + "_" + suffix;
                suffix++;
            }
            this.nickname = assigned;
            this.status = initialStatus != null ? initialStatus : UserStatus.ONLINE;
            clients.put(assigned, this);

            log("CONEXIÓN", assigned + " conectado desde " + remoteAddress);
            broadcastRoster();
            broadcastToAll(ChatPacket.createAnnouncement("👋 " + assigned + " se ha unido al servidor."));
            notifyRosterAndMetrics();
        }

        synchronized void sendPacket(ChatPacket packet) {
            if (out == null || socket.isClosed()) {
                return;
            }
            try {
                out.reset();
                out.writeObject(packet);
                out.flush();
            } catch (IOException ignored) {
            }
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private void cleanup() {
            if (nickname != null && clients.remove(nickname, this)) {
                log("DESCONEXIÓN", nickname + " cerró sesión.");
                broadcastRoster();
                broadcastToAll(ChatPacket.createAnnouncement(nickname + " salió del chat."));
                notifyRosterAndMetrics();
            }
            closeQuietly();
        }

        private String abbreviate(String s, int max) {
            if (s == null) {
                return "";
            }
            return s.length() <= max ? s : s.substring(0, max) + "...";
        }
    }
}
