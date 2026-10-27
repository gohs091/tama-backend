package com.tamagochi.tamagochi.service.land;

import com.tamagochi.tamagochi.domain.land.Adventure;
import com.tamagochi.tamagochi.domain.land.LandInk;
import com.tamagochi.tamagochi.domain.land.LandProgress;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.tamagotchi.EvolutionStage;
import com.tamagochi.tamagochi.domain.tamagotchi.Tamagotchi;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.land.AdventureRepository;
import com.tamagochi.tamagochi.repository.land.LandInkRepository;
import com.tamagochi.tamagochi.repository.land.LandProgressRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import com.tamagochi.tamagochi.repository.tamagotchi.TamagotchiRepository;
import com.tamagochi.tamagochi.service.point.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdventureServiceTest {

    AdventureRepository adventureRepo = mock(AdventureRepository.class);
    LandProgressRepository progressRepo = mock(LandProgressRepository.class);
    LandInkRepository inkRepo = mock(LandInkRepository.class);
    PointWalletRepository walletRepo = mock(PointWalletRepository.class);
    PointService pointService = mock(PointService.class);
    TamagotchiRepository petRepo = mock(TamagotchiRepository.class);
    MutableClock clock = new MutableClock(Instant.parse("2026-10-27T01:00:00Z")); // KST 10:00
    AdventureService service = new AdventureService(adventureRepo, progressRepo, inkRepo, walletRepo, pointService, petRepo, clock);

    User user = mock(User.class);
    Tamagotchi pet = Tamagotchi.builder().evolutionStage(EvolutionStage.BABY).hunger(50).strength(50).build();
    PointWallet wallet = PointWallet.builder().balance(500).build();
    Adventure[] adventure = new Adventure[1];
    Map<String, LandProgress> progress = new HashMap<>();
    List<LandInk> inks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(1L);
        when(walletRepo.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(petRepo.findByUserId(1L)).thenReturn(Optional.of(pet));
        when(adventureRepo.findByUserId(1L)).thenAnswer(i -> Optional.ofNullable(adventure[0]));
        when(adventureRepo.save(any())).thenAnswer(i -> adventure[0] = i.getArgument(0));
        when(progressRepo.findByUserIdAndRegionId(eq(1L), anyString())).thenAnswer(i -> Optional.ofNullable(progress.get(i.<String>getArgument(1))));
        when(progressRepo.save(any())).thenAnswer(i -> { LandProgress p = i.getArgument(0); progress.put(p.getRegionId(), p); return p; });
        when(inkRepo.findByUserIdOrderByFoundAtAsc(anyLong())).thenAnswer(i -> List.copyOf(inks));
        when(inkRepo.save(any())).thenAnswer(i -> { inks.add(i.getArgument(0)); return i.getArgument(0); });
    }

    @Test
    void findsFirstInkAfterOneMinuteAndPetIsAway() {
        AdventureService.Status s = service.start(user, "nature-spirits");
        assertThat(s.running()).isTrue();
        assertThat(pet.isAdventuring()).isTrue();
        assertThat(s.found()).isEmpty();

        clock.advanceMinutes(1);
        s = service.status(user);
        assertThat(s.found()).extracting(f -> f.ink().id()).containsExactly("family.beast");
        assertThat(s.found().get(0).atSeconds()).isEqualTo(60);
        assertThat(inks).extracting(LandInk::getInkId).containsExactly("family.beast");
        // 하루 첫 모험: 1분에 +6 줍고 -2 썼다
        assertThat(s.pickedInk()).isEqualTo(6);
        assertThat(s.spentInk()).isEqualTo(2);
        assertThat(s.balance()).isEqualTo(504);
    }

    @Test
    void stopSettlesNetInkOnceAndSavesExploredMinutes() {
        service.start(user, "nature-spirits");
        clock.advanceMinutes(10);
        AdventureService.Status s = service.stop(user);

        // 줍기 18(보너스) + 7(4~10분) = 25, 소모 20 → +5
        verify(pointService).earnPoint(eq(user), eq(5), anyString());
        assertThat(s.running()).isFalse();
        assertThat(s.endedNow()).isTrue();
        assertThat(s.endReason()).isEqualTo("STOPPED");
        assertThat(progress.get("nature-spirits").getExploredMinutes()).isEqualTo(10);
        assertThat(pet.isAdventuring()).isFalse();

        service.stop(user);
        verify(pointService, times(1)).earnPoint(eq(user), anyInt(), anyString());
    }

    @Test
    void secondAdventureOfTheDayHasNoBonusAndUsesInk() {
        service.start(user, "nature-spirits");
        clock.advanceMinutes(5);
        service.stop(user);

        service.start(user, "nature-spirits");
        clock.advanceMinutes(10);
        service.stop(user);
        // 줍기 10, 소모 20 → -10
        verify(pointService).usePoint(eq(user), eq(10), anyString());
    }

    @Test
    void forgottenAdventureEndsAfterFourHoursOnNextCheck() {
        service.start(user, "deep-savers");
        clock.advanceMinutes(60 * 10);
        AdventureService.Status s = service.status(user);
        assertThat(s.running()).isFalse();
        assertThat(s.endReason()).isEqualTo("TIME_UP");
        assertThat(progress.get("deep-savers").getExploredMinutes()).isEqualTo(240);
        assertThat(s.foundCount()).isEqualTo(13);
    }

    @Test
    void cannotStartWhileBathingSleepingOrInEgg() {
        pet.setBathing(true);
        assertThatThrownBy(() -> service.start(user, "nature-spirits")).hasMessageContaining("잉크통");
        pet.setBathing(false);
        pet.sleep();
        assertThatThrownBy(() -> service.start(user, "nature-spirits")).hasMessageContaining("자고");
        assertThat(pet.isAdventuring()).isFalse();
    }

    @Test
    void cannotStartClosedRegionOrWithoutInk() {
        assertThatThrownBy(() -> service.start(user, "volcano")).hasMessageContaining("준비 중");
        wallet.use(480);
        assertThatThrownBy(() -> service.start(user, "nature-spirits")).hasMessageContaining("잉크가 부족");
    }

    @Test
    void cannotStartTwice() {
        service.start(user, "nature-spirits");
        assertThatThrownBy(() -> service.start(user, "deep-savers")).hasMessageContaining("이미");
        verify(pointService, never()).usePoint(any(), anyInt(), anyString());
    }

    static class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advanceMinutes(long m) { now = now.plusSeconds(m * 60); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
