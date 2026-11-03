package com.tamagochi.tamagochi.config;

import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * 서버가 켜질 때 한 번: 끄적이를 한 유저가 여러 마리 가질 수 있게 DB를 맞춘다. 여러 번 돌아도 안전하다.
 * 1) 예전 끄적이 행에 공개 ID(public_id)를 채운다.
 * 2) (MySQL만) 1:1 관계 때 생긴 tamagotchi.user_id 유일 인덱스를 지운다. 다대일 인덱스(idx_tamagotchi_user)는 JPA가 만든다.
 *    ddl-auto: update는 인덱스를 지우지 않아서 여기서 직접 지운다. 실패해도 서버는 뜨고 로그만 남긴다
 *    (지금은 유저당 한 마리라 유일 인덱스가 남아 있어도 동작에는 문제가 없다).
 */
@Component
@org.springframework.core.annotation.Order(1)
@RequiredArgsConstructor
@Slf4j
public class PetIdentityMigration implements ApplicationRunner {

    private final TamagotchiRepository repository;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        backfillPublicIds();
        dropUniqueUserIndex();
    }

    void backfillPublicIds() {
        int total = 0;
        while (true) {
            Integer n = tx.execute(s -> {
                List<Tamagotchi> rows = repository.findTop500ByPublicIdIsNull();
                rows.forEach(Tamagotchi::assignPublicIdIfMissing);
                repository.saveAll(rows);
                return rows.size();
            });
            if (n == null || n == 0) break;
            total += n;
        }
        if (total > 0) log.info("끄적이 공개 ID 채움: {}마리", total);
    }

    void dropUniqueUserIndex() {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
        } catch (Exception e) {
            log.warn("DB 종류를 확인하지 못해 인덱스 정리를 건너뜀: {}", e.getMessage());
            return;
        }
        try {
            List<String> names = jdbc.queryForList(
                    "SELECT DISTINCT s.INDEX_NAME FROM information_schema.STATISTICS s " +
                    "WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'tamagotchi' AND s.NON_UNIQUE = 0 " +
                    "AND s.INDEX_NAME <> 'PRIMARY' AND s.COLUMN_NAME = 'user_id' " +
                    "AND (SELECT COUNT(*) FROM information_schema.STATISTICS x WHERE x.TABLE_SCHEMA = s.TABLE_SCHEMA " +
                    "     AND x.TABLE_NAME = s.TABLE_NAME AND x.INDEX_NAME = s.INDEX_NAME) = 1", String.class);
            for (String name : names) {
                if (!name.matches("[A-Za-z0-9_]+")) continue;
                jdbc.execute("ALTER TABLE tamagotchi DROP INDEX `" + name + "`");
                log.info("tamagotchi.user_id 유일 인덱스 지움: {} (한 유저가 여러 마리를 가질 수 있게)", name);
            }
        } catch (Exception e) {
            log.warn("tamagotchi.user_id 유일 인덱스를 지우지 못함 (지금 동작에는 문제없음): {}", e.getMessage());
        }
    }
}
