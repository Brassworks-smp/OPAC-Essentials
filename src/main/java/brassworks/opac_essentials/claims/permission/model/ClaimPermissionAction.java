package brassworks.opac_essentials.claims.permission.model;

public enum ClaimPermissionAction {
    INTERACT("interact"),
    BREAK("break"),
    PLACE("place"),
    ATTACK("attack"),
    THROWABLE("throwable");

    private final String commandName;

    ClaimPermissionAction(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }
}
