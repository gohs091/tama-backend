package com.tamagochi.tamagochi.domain.spar;

import com.tamagochi.tamagochi.domain.land.LandCatalog;

/** 잉크 조각 하나 (이번 판에 떨어진 조각 또는 모으는 중인 조각). completed면 이번 조각으로 잉크가 완성돼 보관함에 들어갔다 */
public record SparDrop(String inkId, String emoji, String name, LandCatalog.Grade grade, int count, int need, boolean completed) {
}
