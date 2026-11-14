package chat.protocol;

/**
 * Defines all message and control frame types exchanged over the persistent TCP socket.
 */
public enum PacketType {
    CONNECT,
    DISCONNECT,
    CHAT_MESSAGE,
    PRIVATE_MESSAGE,
    FILE_TRANSFER,
    STATUS_UPDATE,
    ROSTER_SYNC,
    SERVER_ANNOUNCEMENT,
    KICK
}
