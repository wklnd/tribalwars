package com.twlan.backend.service;

import com.twlan.backend.domain.Account;
import com.twlan.backend.domain.FarmConfig;
import com.twlan.backend.domain.World;
import com.twlan.backend.repo.FarmConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

// The frontend owns the format of the stored config (opaque JSON); FarmConfig doesn't parse it.
@Service
public class FarmService {

    static final int MAX_LENGTH = 20000;

    private final FarmConfigRepository configs;

    public FarmService(FarmConfigRepository configs) {
        this.configs = configs;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(Account account, World world) {
        return configs.findByAccountIdAndWorldId(account.getId(), world.getId()).map(FarmConfig::getConfig);
    }

    @Transactional
    public void save(Account account, World world, String config) {
        String text = config == null ? "" : config.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) throw new IllegalArgumentException("The farm setup must be a JSON object.");
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("The farm setup is too large.");
        FarmConfig row = configs.findByAccountIdAndWorldId(account.getId(), world.getId()).orElseGet(() -> {
            FarmConfig n = new FarmConfig();
            n.setAccountId(account.getId());
            n.setWorldId(world.getId());
            return n;
        });
        row.setConfig(text);
        row.setUpdatedAt(Instant.now());
        configs.save(row);
    }

    @Transactional
    public void deleteWorldData(Long worldId) { configs.deleteByWorldId(worldId); }

    @Transactional
    public void deleteAccountData(Long accountId) { configs.deleteByAccountId(accountId); }
}
