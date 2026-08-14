package se.oscarwiklund.twlan2.backend.web.dto;

import java.time.Instant;
import java.util.List;

public final class MarketDto {

    private MarketDto() {}

    public record Merchants(int total, int available, int capacity) {}

    // mine = made by the viewer (any of their villages).
    public record Offer(Long id, Long villageId, String villageName, int x, int y, String owner, boolean mine,
                        String sellResource, int sellAmount, String buyResource, int buyAmount, int remaining,
                        int maxDistance, double distance, double ratio, int merchantsNeeded, Instant expiresAt) {}

    // outgoing = the shipment left one of the viewer's villages; wood/clay/iron is what the merchants carry now.
    public record Shipment(Long id, boolean outgoing, boolean returning, Long originVillageId, String originName, int originX, int originY,
                           Long targetVillageId, String targetName, int targetX, int targetY,
                           int wood, int clay, int iron, int merchants, Instant departedAt, Instant arrivesAt) {}

    public record VillageMerchants(Long villageId, String name, int x, int y, int total, int available) {}

    public record State(Merchants merchants, List<Offer> offers, List<Offer> ownOffers, List<Shipment> shipments, List<VillageMerchants> villages) {}
}
