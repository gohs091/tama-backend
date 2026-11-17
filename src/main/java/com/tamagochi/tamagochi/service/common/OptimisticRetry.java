package com.tamagochi.tamagochi.service.common;

import com.tamagochi.tamagochi.exception.CodedException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 한 트랜잭션으로 실행하고, 같은 유저의 다른 요청과 겹쳐 실패하면(@Version 충돌, 첫 행을 둘이 동시에 만듦) 처음부터 다시 한다.
 * 다시 할 때는 DB의 새 값을 읽으므로 열매·도시락이 두 번 빠지거나 두 번 늘지 않는다.
 */
@Component
public class OptimisticRetry {

    static final int ATTEMPTS = 5;

    private final TransactionTemplate tx;
    private final EntityManager entityManager;

    public OptimisticRetry(PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    public <T> T run(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> work.get());
            } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
                if (attempt >= ATTEMPTS) throw new CodedException("BUSY", "요청이 겹쳤어요. 잠시 후 다시 해 주세요.");
                entityManager.clear(); // 요청 내내 같은 영속성 컨텍스트를 쓰는 경우(open-in-view) 낡은 값을 다시 읽지 않게
            }
        }
    }
}
