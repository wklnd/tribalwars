package com.twlan.backend.service;

import com.twlan.backend.domain.UnitType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Spreads the losses of a defence over the troops that made it: the village's own garrison and each group of
// supporters lose the same share of every unit type (largest remainder: the whole losses are always handed out, and
// nobody loses more than they had).
public final class SupportSplit {
    private SupportSplit() {}

    // Returns, for every source (in the given order), the units it loses; losses above the total are capped.
    public static List<Map<UnitType, Integer>> split(Map<UnitType, Integer> losses, List<Map<UnitType, Integer>> sources) {
        List<Map<UnitType, Integer>> out = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) out.add(new EnumMap<>(UnitType.class));
        for (Map.Entry<UnitType, Integer> loss : losses.entrySet()) {
            UnitType type = loss.getKey();
            long total = 0;
            for (Map<UnitType, Integer> s : sources) total += Math.max(0, s.getOrDefault(type, 0));
            long lost = Math.min(loss.getValue() == null ? 0 : loss.getValue(), total);
            if (lost <= 0) continue;
            long[] share = new long[sources.size()];
            long[] fraction = new long[sources.size()];
            long given = 0;
            for (int i = 0; i < sources.size(); i++) {
                long have = Math.max(0, sources.get(i).getOrDefault(type, 0));
                share[i] = lost * have / total;
                fraction[i] = lost * have % total;
                given += share[i];
            }
            // the rest goes to the biggest remainders (bigger holders, then earlier sources, win ties)
            for (long rest = lost - given; rest > 0; rest--) {
                int best = -1;
                for (int i = 0; i < sources.size(); i++) {
                    long have = Math.max(0, sources.get(i).getOrDefault(type, 0));
                    if (share[i] >= have) continue;
                    if (best < 0 || fraction[i] > fraction[best]
                            || (fraction[i] == fraction[best] && have > Math.max(0, sources.get(best).getOrDefault(type, 0)))) best = i;
                }
                share[best]++;
                fraction[best] = -1; // one extra unit per source and round
            }
            for (int i = 0; i < sources.size(); i++) if (share[i] > 0) out.get(i).put(type, (int) share[i]);
        }
        return out;
    }
}
