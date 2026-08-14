package se.oscarwiklund.twlan2.backend.service;

import se.oscarwiklund.twlan2.backend.domain.BonusType;
import se.oscarwiklund.twlan2.backend.domain.MigrationMark;
import se.oscarwiklund.twlan2.backend.domain.World;
import se.oscarwiklund.twlan2.backend.repo.MigrationMarkRepository;
import se.oscarwiklund.twlan2.backend.repo.WorldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// One-time: the barbarian villages that existed before bonus villages get their bonus, like a freshly created world would have
// (BonusType.share: "normal" 8 %, "better" 15 %; worlds with "off" get none). Later changes of the world setting do not
// re-roll anything. Villages of players and NPCs are left alone.
// Plain SQL in one short batch, not entities in one big transaction: on the real save (about 1900 villages, NPC ticks running) the
// entity version held locks for over a minute and the game ticks timed out meanwhile.
@Component
public class BonusVillageMigration implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BonusVillageMigration.class);
    static final String MARK = "bonus-villages-1";

    private final WorldRepository worlds;
    private final MigrationMarkRepository marks;
    private final JdbcTemplate jdbc;

    public BonusVillageMigration(WorldRepository worlds, MigrationMarkRepository marks, JdbcTemplate jdbc) {
        this.worlds = worlds;
        this.marks = marks;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (marks.existsById(MARK)) return;
        Map<Long, World> byId = new HashMap<>();
        for (World w : worlds.findAll()) byId.put(w.getId(), w);
        Random rnd = new Random();
        List<Object[]> batch = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id, world_id FROM village WHERE owner_type = 'BARBARIAN' AND owner_id IS NULL AND bonus_code IS NULL")) {
            World w = byId.get(((Number) row.get("WORLD_ID")).longValue());
            Integer code = BonusType.roll(rnd, w);
            if (code != null) batch.add(new Object[]{code, ((Number) row.get("ID")).longValue()});
        }
        if (!batch.isEmpty()) jdbc.batchUpdate("UPDATE village SET bonus_code = ? WHERE id = ?", batch);
        marks.save(new MigrationMark(MARK));
        LOG.info("Bonus villages: {} barbarian villages got a bonus", batch.size());
    }
}
