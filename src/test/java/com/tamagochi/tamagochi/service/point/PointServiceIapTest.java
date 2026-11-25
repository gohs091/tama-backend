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
    @DisplayName("예전 앱(붉은 잉크를 모르는 앱)의 주문에는 그 앱이 보여 준 검은 잉크를 지급한다")
    void grantsPointsForPaidOrder() {
        when(verifier.isPaid("order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).thenReturn(true);

        service.purchasePoint(user, "order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", false);

        assertThat(wallet.getBalance()).isEqualTo(4500);
    }

    @Test
    @DisplayName("새 앱의 주문에는 붉은 잉크만 지급하고 검은 잉크는 그대로 둔다")
    void grantsRedInkForRedInkProduct() {
        when(verifier.isPaid("order-r", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).thenReturn(true);

        PointTransaction result = service.purchasePoint(user, "order-r", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", true);

        assertThat(wallet.redInkBalance()).isEqualTo(33);
        assertThat(wallet.getBalance()).isZero();
        assertThat(result.getAmount()).isEqualTo(33);
        assertThat(result.getCurrency()).isEqualTo(PointTransaction.RED_INK);
    }

    @Test
    @DisplayName("예전 앱의 주문 내역에는 재화 표시가 없고 붉은 잉크는 늘지 않는다")
    void blackInkPurchaseLeavesRedInkAlone() {
        when(verifier.isPaid("order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797")).thenReturn(true);

        PointTransaction result = service.purchasePoint(user, "order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", false);

        assertThat(result.getCurrency()).isNull();
        assertThat(wallet.redInkBalance()).isZero();
    }

    @Test
    @DisplayName("결제가 확인되지 않은 붉은 잉크 주문은 지급하지 않는다")
    void rejectsUnpaidRedInkOrder() {
        when(verifier.isPaid(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.purchasePoint(user, "order-r", "ait.0000032667.a43f662f.e3e8089345.3031847883", true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.redInkBalance()).isZero();
    }

    @Test
    @DisplayName("결제가 확인되지 않으면 지급하지 않는다")
    void rejectsUnpaidOrder() {
        when(verifier.isPaid(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("등록되지 않은 SKU는 검증 전에 거절한다")
    void rejectsUnknownSku() {
        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "point_999999", true))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(verifier);
    }

    @Test
    @DisplayName("이미 지급된 주문을 다시 보내면 중복 지급 없이 기존 내역을 돌려준다")
    void duplicateOrderIsIdempotent() {
        PointTransaction existing = PointTransaction.builder().user(user).idempotencyKey("iap-order-1").amount(4500).build();
        when(transactionRepository.findByIdempotencyKey("iap-order-1")).thenReturn(Optional.of(existing));

        PointTransaction result = service.purchasePoint(user, "order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", true);

        assertThat(result).isSameAs(existing);
        assertThat(wallet.getBalance()).isZero();
        verifyNoInteractions(verifier);
    }

    @Test
    @DisplayName("다른 유저가 이미 처리된 주문 번호를 보내면 거절한다")
    void rejectsOtherUsersOrder() {
        User other = User.builder().id(2L).tossUserId("toss_u2").nickname("u2").build();
        PointTransaction existing = PointTransaction.builder().user(other).idempotencyKey("iap-order-1").amount(4500).build();
        when(transactionRepository.findByIdempotencyKey("iap-order-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.purchasePoint(user, "order-1", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문 번호가 비어 있으면 거절한다")
    void rejectsBlankOrderId() {
        assertThatThrownBy(() -> service.purchasePoint(user, " ", "ait.0000032667.c26f773b.ab0e80b6a6.3031873797", true))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    @DisplayName("붉은 잉크를 쓰면 그만큼 줄고 내역이 남는다")
    void usesRedInk() {
        wallet.chargeRedInk(3);

        service.useRedInk(user, 1, "hatch-abc");

        assertThat(wallet.redInkBalance()).isEqualTo(2);
        verify(transactionRepository, times(1)).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("붉은 잉크가 모자라면 쓰지 못하고 내역도 남지 않는다")
    void rejectsUseWithoutRedInk() {
        assertThatThrownBy(() -> service.useRedInk(user, 1, "hatch-abc")).isInstanceOf(IllegalStateException.class);
        assertThat(wallet.redInkBalance()).isZero();
        verify(transactionRepository, never()).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("붉은 잉크로 검은 잉크를 바꾸면 붉은 잉크가 줄고 검은 잉크가 는다")
    void exchangesRedInkForInk() {
        wallet.chargeRedInk(33);

        PointWallet result = service.exchangeInk(user, "ink.4500", "req-1");

        assertThat(result.redInkBalance()).isEqualTo(3);
        assertThat(result.getBalance()).isEqualTo(4500);
        verify(transactionRepository, times(2)).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("붉은 잉크가 모자라면 바꾸지 않는다")
    void rejectsExchangeWithoutEnoughRedInk() {
        wallet.chargeRedInk(9);

        assertThatThrownBy(() -> service.exchangeInk(user, "ink.1500", "req-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(wallet.redInkBalance()).isEqualTo(9);
        assertThat(wallet.getBalance()).isZero();
        verify(transactionRepository, never()).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("같은 요청이 다시 오면 또 바꾸지 않는다")
    void duplicateExchangeIsIdempotent() {
        wallet.chargeRedInk(20);
        PointTransaction existing = PointTransaction.builder().user(user).idempotencyKey("exchange-req-1").amount(10).build();
        when(transactionRepository.findByIdempotencyKey("exchange-req-1")).thenReturn(Optional.of(existing));

        service.exchangeInk(user, "ink.1500", "req-1");

        assertThat(wallet.redInkBalance()).isEqualTo(20);
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("알 수 없는 묶음은 거절한다")
    void rejectsUnknownExchangePack() {
        wallet.chargeRedInk(100);

        assertThatThrownBy(() -> service.exchangeInk(user, "ink.999999", "req-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(wallet.redInkBalance()).isEqualTo(100);
    }
}
