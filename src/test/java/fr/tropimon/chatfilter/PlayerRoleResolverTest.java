package fr.tropimon.chatfilter;

import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRoleResolverTest {
    @Test
    void resolvesStaffRolesFromOfficialIconsOrLabels() {
        assertEquals(PlayerRole.ADMIN,
                PlayerRoleResolver.fromLabel("ꌂ ExampleAdmin", "ExampleAdmin"));
        assertEquals(PlayerRole.MODERATOR,
                PlayerRoleResolver.fromLabel("ꌃ ExampleMod", "ExampleMod"));
        assertEquals(PlayerRole.RESPONSABLE,
                PlayerRoleResolver.fromLabel("[Responsable] ExampleLead", "ExampleLead"));
        assertEquals(PlayerRole.GUIDE,
                PlayerRoleResolver.fromLabel("[Guide] ExampleGuide", "ExampleGuide"));
    }

    @Test
    void resolvesPlayerRanksWithoutTrustingWordsInsideTheUsername() {
        assertEquals(PlayerRole.CHAMPION,
                PlayerRoleResolver.fromLabel("섫 ExampleOne", "ExampleOne"));
        assertEquals(PlayerRole.SUPER,
                PlayerRoleResolver.fromLabel("ꑣ ExampleTwo", "ExampleTwo"));
        assertEquals(PlayerRole.HYPER,
                PlayerRoleResolver.fromLabel("ꑤ ExampleThree", "ExampleThree"));
        assertEquals(PlayerRole.MASTER,
                PlayerRoleResolver.fromLabel("ꑥ ExampleFour", "ExampleFour"));
        assertEquals(PlayerRole.UNRANKED,
                PlayerRoleResolver.fromLabel("MasterExample", "MasterExample"));
    }

    @Test
    void reusesTheExactServerColorOfThePlayerName() {
        Text display = Text.empty()
                .append(Text.literal("★ ").styled(style -> style.withColor(0xCC8800)))
                .append(Text.literal("ExamplePlayer").styled(style -> style.withColor(0x35C7F2)));

        assertEquals(0xFF35C7F2,
                PlayerRoleResolver.colorFromText(display, "ExamplePlayer", 0xFFFFFFFF));
        assertEquals(0xFFCC8800,
                PlayerRoleResolver.colorFromText(display, "MissingPlayer", 0xFFFFFFFF));
        assertEquals(0xFFFFFFFF,
                PlayerRoleResolver.colorFromText(Text.literal("plain"), "plain", 0xFFFFFFFF));
    }
}
