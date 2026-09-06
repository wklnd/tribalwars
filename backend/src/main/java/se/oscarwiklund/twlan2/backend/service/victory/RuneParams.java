package se.oscarwiklund.twlan2.backend.service.victory;

// totalTarget: how many rune villages a tribe must hold at once. requireEveryPopulatedContinent: also
// require at least one in every continent that currently has a player village. holdDays: real calendar
// days the condition must hold continuously. spawnShare: chance a newly-generated barbarian village is
// rolled as a rune village instead (0-1) - only rolled at all while this is the world's active condition.
public record RuneParams(int totalTarget, boolean requireEveryPopulatedContinent, int holdDays, double spawnShare) {
    public static RuneParams defaults() { return new RuneParams(23, true, 7, 0.01); }
}
