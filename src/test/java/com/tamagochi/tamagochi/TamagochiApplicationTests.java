package com.tamagochi.tamagochi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 메모리 DB(H2)로 앱 전체를 띄워 빈 연결과 엔티티 매핑이 맞는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
class TamagochiApplicationTests {

	@Test
	void contextLoads() {
	}

}
