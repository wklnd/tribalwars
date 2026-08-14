package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.Village;
import org.springframework.stereotype.Component;

// A world's speed multiplies resource production and divides construction, recruitment and travel times
// (the original's world "speed"); 1 = normal.
@Component
public class GameSettings {

    // 1 for pre-world saves.
    public double speedOf(Village village) {
        if (village == null || village.getWorld() == null || village.getWorld().getSpeed() <= 0) {
            return 1.0;
        }
        return village.getWorld().getSpeed();
    }

    // World speed times the world's "unitsSpeed" setting.
    public double travelSpeedOf(Village village) {
        double unit = village == null || village.getWorld() == null ? 1.0 : WorldSettings.number(village.getWorld(), "unitsSpeed");
        return speedOf(village) * (unit > 0 ? unit : 1.0);
    }

    public String worldNameOf(Village village) {
        return village == null || village.getWorld() == null ? "Welt 1" : village.getWorld().getName();
    }

    // Floored at 1 second.
    public long scaleSeconds(Village village, double seconds) {
        return Math.max(1, Math.round(seconds / speedOf(village)));
    }
}
