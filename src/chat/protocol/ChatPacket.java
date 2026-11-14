package chat.protocol;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Wire protocol envelope transmitted between the central server and persistent clients.
 */
public class ChatPacket implements Serializable {
    private static final long serialVersionUID = 2L;

    private PacketType type;
    private String sender;
    private String targetUser;
    private String channel;
    private String content;
    private boolean encrypted;
    private String fileName;
    private byte[] fileData;
    private UserStatus userStatus;
    private String timestamp;
    private List<UserInfo> roster;

    public ChatPacket() {
        this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        this.channel = "#general";
        this.userStatus = UserStatus.ONLINE;
        this.roster = new ArrayList<UserInfo>();
    }

    public static ChatPacket createConnect(String sender, UserStatus status) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.CONNECT);
        p.setSender(sender);
        p.setUserStatus(status);
        return p;
    }

    public static ChatPacket createChannelMessage(String sender, String channel, String content, boolean encrypted) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.CHAT_MESSAGE);
        p.setSender(sender);
        p.setChannel(channel);
        p.setContent(content);
        p.setEncrypted(encrypted);
        return p;
    }

    public static ChatPacket createPrivateMessage(String sender, String targetUser, String content, boolean encrypted) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.PRIVATE_MESSAGE);
        p.setSender(sender);
        p.setTargetUser(targetUser);
        p.setContent(content);
        p.setEncrypted(encrypted);
        return p;
    }

    public static ChatPacket createFileTransfer(
            String sender,
            String channel,
            String targetUser,
            String fileName,
            byte[] fileData,
            boolean encrypted
    ) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.FILE_TRANSFER);
        p.setSender(sender);
        p.setChannel(channel);
        p.setTargetUser(targetUser);
        p.setFileName(fileName);
        p.setFileData(fileData);
        p.setEncrypted(encrypted);
        p.setContent("Archivo compartido: " + fileName + " (" + (fileData != null ? fileData.length : 0) + " bytes)");
        return p;
    }

    public static ChatPacket createStatusUpdate(String sender, UserStatus status) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.STATUS_UPDATE);
        p.setSender(sender);
        p.setUserStatus(status);
        return p;
    }

    public static ChatPacket createAnnouncement(String content) {
        ChatPacket p = new ChatPacket();
        p.setType(PacketType.SERVER_ANNOUNCEMENT);
        p.setSender("SERVIDOR");
        p.setContent(content);
        return p;
    }

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public List<UserInfo> getRoster() {
        return roster;
    }

    public void setRoster(List<UserInfo> roster) {
        this.roster = roster;
    }
}
