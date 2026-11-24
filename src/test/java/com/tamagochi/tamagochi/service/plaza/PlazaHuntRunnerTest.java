package com.tamagochi.tamagochi.service.plaza;

import com.tamagochi.tamagochi.service.plaza.PlazaService.HuntResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlazaHuntRunnerTest {

    private final HuntService service = mock(HuntService.class);
    private final PlazaService plaza = new PlazaService(() -> 0L);
    private final PlazaHuntRunner runner = new PlazaHuntRunner(service, plaza);

    @Test
    void 지급이_터져도_다음_일을_한다() throws Exception {
        HuntResult ok = new HuntResult(true, null, 2, 1, 3, "BABY", false, null, 50, 50);
        when(service.hunt(anyLong(), anyString(), anyBoolean()))
                .thenThrow(new IllegalStateException("터짐"))
                .thenReturn(ok);
        AtomicReference<HuntResult> first = new AtomicReference<>();
        AtomicReference<HuntResult> second = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        runner.kill(1, "a", false, first::set);
        runner.kill(1, "a", false, r -> { second.set(r); done.countDown(); });
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(first.get()).isNull();
        assertThat(second.get()).isSameAs(ok);
        runner.stop();
    }

    @Test
    void 큐가_차면_거절_예외가_나고_광장은_멈추지_않는다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(service.hunt(anyLong(), anyString(), anyBoolean())).thenAnswer(inv -> { release.await(5, TimeUnit.SECONDS); return null; });
        PlazaHuntRunner small = new PlazaHuntRunner(service, plaza, PlazaHuntRunner.boundedExecutor("plaza-hunt-test", 1));
        small.kill(1, "a", false, r -> { });   // 실행 중
        small.kill(1, "a", false, r -> { });   // 큐 한 칸
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> small.kill(1, "a", false, r -> { }))
                .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        release.countDown();
        small.stop();
    }

    @Test
    void 일을_넣고_바로_돌아온다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(service.hunt(anyLong(), anyString(), anyBoolean())).thenAnswer(inv -> { release.await(5, TimeUnit.SECONDS); return null; });
        long start = System.nanoTime();
        runner.kill(1, "a", false, r -> { });
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(1000);
        release.countDown();
        runner.stop();
    }
}
