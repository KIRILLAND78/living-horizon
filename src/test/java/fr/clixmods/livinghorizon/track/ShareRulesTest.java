package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.ServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the server's settings leave out of what one player is sent. */
class ShareRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final Map<String, String> TEAMS = Map.of("Alex", "red", "Steve", "red", "Notch", "blue");
    private static final ShareRules.Viewer ALEX = new ShareRules.Viewer("Alex", OVERWORLD, 0, 0, "red");

    private static final List<PositionsMessage.Player> PLAYERS = List.of(
            player("Alex", 0, OVERWORLD),
            player("Steve", 300, OVERWORLD),
            player("Notch", 3000, OVERWORLD),
            player("Herobrine", 10, NETHER));

    private static PositionsMessage.Player player(String name, double x, String dimension) {
        return new PositionsMessage.Player(name, x, 64, 0, 0, dimension, true, false, null);
    }

    private static List<String> names(List<PositionsMessage.Player> players) {
        return players.stream().map(PositionsMessage.Player::name).toList();
    }

    private static ShareRules rules(ServerConfig config) {
        return new ShareRules(config, TEAMS::get);
    }

    @Test
    void sharesEveryoneByDefault() {
        ShareRules rules = rules(new ServerConfig());
        assertTrue(rules.sameForAll());
        assertEquals(names(PLAYERS), names(rules.online(PLAYERS, ALEX)));
        assertEquals(names(PLAYERS), names(rules.online(PLAYERS, null)));
        assertEquals(names(PLAYERS), names(rules.offline(PLAYERS, ALEX)));
        assertTrue(rules.mobs(OVERWORLD));
    }

    @Test
    void keepsPlayersWithinTheRange() {
        ServerConfig config = new ServerConfig();
        config.playerRange = 500;
        ShareRules rules = rules(config);
        assertFalse(rules.sameForAll());
        // Another dimension is out of range too.
        assertEquals(List.of("Alex", "Steve"), names(rules.online(PLAYERS, ALEX)));
        assertEquals(List.of("Alex", "Steve"), names(rules.offline(PLAYERS, ALEX)));
    }

    @Test
    void keepsTeammatesOnly() {
        ServerConfig config = new ServerConfig();
        config.teamsOnly = true;
        ShareRules rules = rules(config);
        assertEquals(List.of("Alex", "Steve"), names(rules.online(PLAYERS, ALEX)));
        // Without a team, nobody but themselves.
        ShareRules.Viewer loner = new ShareRules.Viewer("Herobrine", NETHER, 10, 0, null);
        assertEquals(List.of("Herobrine"), names(rules.online(PLAYERS, loner)));
    }

    @Test
    void leavesOutHiddenNamesAndDimensions() {
        ServerConfig config = new ServerConfig();
        config.hiddenPlayers = List.of("notch");
        config.hiddenDimensions = List.of(NETHER);
        ShareRules rules = rules(config);
        assertTrue(rules.sameForAll());
        assertEquals(List.of("Alex", "Steve"), names(rules.online(PLAYERS, null)));
        assertEquals(List.of("Alex", "Steve"), names(rules.offline(PLAYERS, ALEX)));
        assertFalse(rules.mobs(NETHER));
        assertTrue(rules.mobs(OVERWORLD));
    }

    @Test
    void switchesEachKindOff() {
        ServerConfig config = new ServerConfig();
        config.sharePlayers = false;
        config.shareOfflinePlayers = false;
        config.shareMobs = false;
        ShareRules rules = rules(config);
        assertEquals(List.of("Alex"), names(rules.online(PLAYERS, ALEX)));
        assertEquals(List.of(), rules.online(PLAYERS, null));
        assertEquals(List.of(), rules.offline(PLAYERS, ALEX));
        assertFalse(rules.mobs(OVERWORLD));
    }
}
