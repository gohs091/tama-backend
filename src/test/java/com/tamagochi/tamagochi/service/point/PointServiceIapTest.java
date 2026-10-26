package com.tamagochi.tamagochi.service.point;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import com.tamagochi.tamagochi.domain.user.User;
import com.tamagochi.tamagochi.repository.point.PointTransactionRepository;
import com.tamagochi.tamagochi.repository.point.PointWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class PointServiceIapTest {

    private PointWalletRepository walletRepository;
    private PointTransactionRepository transactionRepository;
    private IapOrderVerifier verifier;
    private PointService service;
    private PointWallet wallet;
    private User user;

    @BeforeEach
    void setUp() {
        walletRepository = mock(PointWalletRepository.class);
        transactionRepository = mock(PointTransactionRepository.class);
        verifier = mock(IapOrderVerifier.class);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any())).thenReturn(true);

        service = new PointService(walletRepository, transactionRepository, redis, verifier);

        user = User.builder().id(1L).tossUserId("toss_u1").nickname("u1").build();
        wallet = PointWallet.builder().user(user).balance(0).build();
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("결제가 확인된 주문은 SKU에 정해진 포인트를 지급한다")
    void grantsPointsForPaidOrder() {
        when(verifier.isPaid("order-1", "point_3000")).thenReturn(true);

        service.purchasePoint(user, "order-1", "point_3000");

        assertThat(wallet.getBalance()).isEqualTo(2200);
    }

    @Test
    @DisplayName("결제가 확인되지 않으면 지급하지 않는다")
    void rejectsUnpaidOrder() {
        when(verifier.isPaid(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "point_3000"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("등록되지 않은 SKU는 검증 전에 거절한다")
    void rejectsUnknownSku() {
        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "point_999999"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(verifier);
    }

    @Test
    @DisplayName("이미 지급된 주문을 다시 보내면 중복 지급 없이 기존 내역을 돌려준다")
    void duplicateOrderIsIdempotent() {
        PointTransaction existing = PointTransaction.builder().user(user).idempotencyKey("iap-order-1").amount(2200).build();
        when(transactionRepository.findByIdempotencyKey("iap-order-1")).thenReturn(Optional.of(existing));

        PointTransaction result = service.purchasePoint(user, "order-1", "point_3000");

        assertThat(result).isSameAs(existing);
        assertThat(wallet.getBalance()).isZero();
        verifyNoInteractions(verifier);
    }

    @Test
    @DisplayName("다른 유저가 이미 처리된 주문 번호를 보내면 거절한다")
    void rejectsOtherUsersOrder() {
        User other = User.builder().id(2L).tossUserId("toss_u2").nickname("u2").build();
        PointTransaction existing = PointTransaction.builder().user(other).idempotencyKey("iap-order-1").amount(2200).build();
        when(transactionRepository.findByIdempotencyKey("iap-order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "point_3000"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문 번호가 비어 있으면 거절한다")
    void rejectsBlankOrderId() {
        assertThatThrownBy(() -> service.purchasePoint(user, " ", "point_3000"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
