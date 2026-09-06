package se.oscarwiklund.twlan2.backend.service.victory;

import se.oscarwiklund.twlan2.backend.domain.Resource;

// rosterSize: how many of the largest tribes (by total points) are locked in as combatants. selectAfterDays:
// real days before the roster is picked. prepDays: real days between roster lock and the war going live.
// bonusResource/bonusAmount: paid to every surviving winning member's home village warehouse when the war
// ends (last tribe standing).
public record WarParams(int rosterSize, int selectAfterDays, int prepDays, Resource bonusResource, double bonusAmount) {
    public static WarParams defaults() { return new WarParams(10, 90, 14, Resource.IRON, 5000); }
}
