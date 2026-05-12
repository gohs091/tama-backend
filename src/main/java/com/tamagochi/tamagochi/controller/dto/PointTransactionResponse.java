package com.tamagochi.tamagochi.controller.dto;

import com.tamagochi.tamagochi.domain.point.PointTransaction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PointTransactionResponse {
    private Long id;
    private String type;
    private int amount;
    private String status;
    private LocalDateTime createdAt;

    public static PointTransactionResponse from(PointTransaction transaction) {
        return PointTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}