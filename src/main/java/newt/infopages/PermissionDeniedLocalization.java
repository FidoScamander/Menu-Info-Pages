package newt.infopages;

final class PermissionDeniedLocalization {
    private PermissionDeniedLocalization() {}
    static String text(Object player) { return Localization.permissionDenied(player); }
}
