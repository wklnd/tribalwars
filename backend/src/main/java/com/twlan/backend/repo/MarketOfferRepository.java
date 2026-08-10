package com.twlan.backend.repo;

import com.twlan.backend.domain.MarketOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MarketOfferRepository extends JpaRepository<MarketOffer, Long> {
    List<MarketOffer> findByWorldId(Long worldId);
    List<MarketOffer> findByVillageId(Long villageId);
    List<MarketOffer> findByExpiresAtBefore(Instant now);
    void deleteByVillageId(Long villageId);
}
