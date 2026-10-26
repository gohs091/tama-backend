package com.tamagochi.tamagochi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 로컬 개발(localdev 프로필) 전용: Redis 없이 앱이 돌도록 메모리에서 흉내 낸다.
 * 서버가 Redis를 쓰는 곳(중복 요청 방지 키, 하루 횟수 카운터, 이미지 생성 한도)에 필요한 동작만 구현했다.
 * 운영·테스트 설정에서는 만들어지지 않는다.
 */
@Configuration
@Profile("localdev")
public class LocalDevRedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return new InMemoryStringRedisTemplate();
    }

    static class InMemoryStringRedisTemplate extends StringRedisTemplate {

        private record Entry(String value, long expiresAtMillis) {
            boolean expired(long now) {
                return expiresAtMillis > 0 && expiresAtMillis <= now;
            }
        }

        private final Map<String, Entry> store = new ConcurrentHashMap<>();
        private final ValueOperations<String, String> ops = createOps();

        @Override
        public void afterPropertiesSet() {
            // 연결 팩토리가 없어도 되도록 검증을 건너뛴다
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return ops;
        }

        @Override
        public Boolean delete(String key) {
            return store.remove(key) != null;
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            return expire(key, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public Boolean expire(String key, Duration timeout) {
            Entry entry = live(key);
            if (entry == null) return false;
            store.put(key, new Entry(entry.value(), System.currentTimeMillis() + timeout.toMillis()));
            return true;
        }

        private Entry live(String key) {
            Entry entry = store.get(key);
            if (entry == null) return null;
            if (entry.expired(System.currentTimeMillis())) {
                store.remove(key, entry);
                return null;
            }
            return entry;
        }

        @SuppressWarnings("unchecked")
        private ValueOperations<String, String> createOps() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(), new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setIfAbsent" -> setIfAbsent((String) args[0], (String) args[1], args);
                        case "increment" -> increment((String) args[0]);
                        case "get" -> {
                            Entry e = live((String) args[0]);
                            yield e == null ? null : e.value();
                        }
                        case "toString" -> "InMemoryValueOperations";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("로컬 개발용 메모리 Redis가 지원하지 않는 동작: " + method.getName());
                    });
        }

        private synchronized boolean setIfAbsent(String key, String value, Object[] args) {
            if (live(key) != null) return false;
            long ttlMillis = 0;
            if (args.length == 4) ttlMillis = ((TimeUnit) args[3]).toMillis((Long) args[2]);
            else if (args.length == 3) ttlMillis = ((Duration) args[2]).toMillis();
            store.put(key, new Entry(value, ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0));
            return true;
        }

        private synchronized Long increment(String key) {
            Entry entry = live(key);
            long next = (entry == null ? 0 : Long.parseLong(entry.value())) + 1;
            store.put(key, new Entry(String.valueOf(next), entry == null ? 0 : entry.expiresAtMillis()));
            return next;
        }
    }
}
