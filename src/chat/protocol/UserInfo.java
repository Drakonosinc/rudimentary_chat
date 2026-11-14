package chat.protocol;

import java.io.Serializable;

/**
 * Immutable snapshot of a connected participant broadcast during roster synchronization.
 */
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String nickname;
    private final UserStatus status;
    private final String remoteAddress;

    public UserInfo(String nickname, UserStatus status, String remoteAddress) {
        this.nickname = nickname;
        this.status = status != null ? status : UserStatus.ONLINE;
        this.remoteAddress = remoteAddress != null ? remoteAddress : "local";
    }

    public String getNickname() {
        return nickname;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String toString() {
        return nickname + " (" + status.getLabel() + ")";
    }
}
