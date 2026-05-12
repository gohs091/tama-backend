package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import com.tamagochi.tamagochi.domain.point.PointWallet;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PointBalanceResponse {
    private int balance;

    public static PointBalanceResponse from(PointWallet wallet) {
        return PointBalanceResponse.builder()
                .balance(wallet.getBalance())
                .build();
    }
}