package se.oscarwiklund.twlan2.backend.service.npc;

import se.oscarwiklund.twlan2.backend.domain.Account;
import se.oscarwiklund.twlan2.backend.domain.MigrationMark;
import se.oscarwiklund.twlan2.backend.domain.Village;
import se.oscarwiklund.twlan2.backend.repo.AccountRepository;
import se.oscarwiklund.twlan2.backend.repo.MigrationMarkRepository;
import se.oscarwiklund.twlan2.backend.repo.VillageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

// One-time: NPCs that were created with the old naming scheme ("Aldric_30": one of twenty first names plus a running number)
// get a name from NpcNames. Their default village names ("Aldric_30's village") and the player names written into
// old battle reports follow. NPCs with a name the admin chose (a custom prefix) are left alone.
@Component
public class NpcRenameMigration implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(NpcRenameMigration.class);
    static final String MARK = "npc-names-1";
    static final Pattern OLD = Pattern.compile("(Aldric|Bertram|Cedric|Dunstan|Eadric|Fulk|Godric|Hartwin|Ivo|Jorvik|Kendrick|Leofric|Merek|Norbert|Osric|Percival|Quentin|Rowan|Sigmund|Tancred)(_\\d+)?");

    private final AccountRepository accounts;
    private final VillageRepository villages;
    private final MigrationMarkRepository marks;
    private final JdbcTemplate jdbc;

    public NpcRenameMigration(AccountRepository accounts, VillageRepository villages, MigrationMarkRepository marks, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.villages = villages;
        this.marks = marks;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (marks.existsById(MARK)) return;
        Set<String> taken = new HashSet<>();
        for (Account a : accounts.findAll()) taken.add(a.getUsernameLower());
        Random rnd = new Random();
        int renamed = 0;
        for (Account a : accounts.findByNpc(true)) {
            String old = a.getUsername();
            if (!OLD.matcher(old).matches()) continue;
            String name = NpcNames.generate(rnd, taken::contains);
            taken.add(name.toLowerCase());
            a.setUsername(name);
            accounts.save(a);
            for (Village v : villages.findByOwner(a)) {
                if ((old + "'s village").equals(v.getName())) {
                    v.setName(name + "'s village");
                    villages.save(v);
                }
            }
            jdbc.update("UPDATE combat_report SET attacker_player = ? WHERE attacker_player = ?", name, old);
            jdbc.update("UPDATE combat_report SET defender_player = ? WHERE defender_player = ?", name, old);
            renamed++;
        }
        marks.save(new MigrationMark(MARK));
        if (renamed > 0) LOG.info("Gave {} NPCs new names", renamed);
    }
}
