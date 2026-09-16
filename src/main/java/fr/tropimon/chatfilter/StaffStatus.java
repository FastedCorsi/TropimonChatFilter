package fr.tropimon.chatfilter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

/** Détecte le rôle staff depuis les informations déjà présentes dans la liste des joueurs. */
public final class StaffStatus {
    private static Object connection;
    private static boolean staff;
    private static boolean confirmedThisSession;
    private static long nextRefresh;

    private StaffStatus() {
    }

    public static boolean isStaff() {
        refresh();
        return staff;
    }

    public static void confirmFromStaffMessage() {
        // Initialise d'abord l'identité de connexion afin que la confirmation
        // reçue juste après un join ne soit pas effacée au rafraîchissement suivant.
        refresh();
        confirmedThisSession = true;
        staff = true;
        nextRefresh = System.currentTimeMillis() + 10_000L;
    }

    public static void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            connection = null;
            staff = false;
            confirmedThisSession = false;
            return;
        }
        Object currentConnection = client.getNetworkHandler();
        long now = System.currentTimeMillis();
        if (connection == currentConnection && now < nextRefresh) {
            return;
        }
        if (connection != currentConnection) {
            confirmedThisSession = false;
        }
        connection = currentConnection;
        nextRefresh = now + 2_000L;

        boolean detected = looksStaff(client.player.getDisplayName());
        detected |= TropimonTownProfile.isStaff(client.player.getUuid());
        if (client.player.getScoreboardTeam() != null) {
            detected |= looksStaff(client.player.getScoreboardTeam());
        }
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler()
                    .getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                detected |= looksStaff(entry.getDisplayName());
                detected |= looksStaff(entry.getScoreboardTeam());
            }
        }
        staff = confirmedThisSession || detected;
    }

    private static boolean looksStaff(Text text) {
        return text != null && GlobalMessageClassifier.looksLikeStaffIdentity(text.getString());
    }

    private static boolean looksStaff(Team team) {
        return team != null && (looksStaff(team.getPrefix()) || looksStaff(team.getSuffix())
                || GlobalMessageClassifier.looksLikeStaffIdentity(team.getName()));
    }
}
