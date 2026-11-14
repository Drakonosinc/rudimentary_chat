package chat.protocol;

/**
 * Represents the availability state of a connected chat participant.
 */
public enum UserStatus {
    ONLINE("Online", "#10B981"),
    AWAY("Ausente", "#F59E0B"),
    BUSY("Ocupado", "#EF4444");

    private final String label;
    private final String hexColor;

    UserStatus(String label, String hexColor) {
        this.label = label;
        this.hexColor = hexColor;
    }

    public String getLabel() {
        return label;
    }

    public String getHexColor() {
        return hexColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
