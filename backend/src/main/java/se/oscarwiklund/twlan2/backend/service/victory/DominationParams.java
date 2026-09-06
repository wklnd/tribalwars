package se.oscarwiklund.twlan2.backend.service.victory;

// thresholdPercent: 0-100, share of all player villages one tribe must hold. afterDays/holdDays: real
// calendar days, never scaled by world speed (a world lifecycle concern, not an in-game pacing one).
public record DominationParams(double thresholdPercent, int afterDays, int holdDays) {
    public static DominationParams defaults() { return new DominationParams(65, 180, 5); }
}
