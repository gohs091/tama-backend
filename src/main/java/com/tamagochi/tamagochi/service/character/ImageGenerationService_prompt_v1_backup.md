# Prompt Version 1 — 수채화/RPG 풍 (롤백용)

## 적용 기간
초기 버전. 수채화 파스텔 톤의 chibi RPG 캐릭터 스타일.

## 문제점
- 랜덤 RPG 아키타입(드래곤, 기사, 마법사 등)이 유저 스케치와 무관한 캐릭터를 생성
- 수채화/페인팅 풍으로 다마고치 감성과 거리가 있음
- 유저 입장에서 "내가 그린 캐릭터"라는 연속성이 느껴지지 않음

## control_strength
- baby: 0.55
- child: 0.65
- adult: 0.70

## RPG_ARCHETYPES (랜덤 선택)
```
"cute chibi dragon with small wings and scales",
"cute chibi armored knight holding a tiny sword and shield",
"cute chibi holy paladin with glowing divine halo and armor",
"cute chibi dark knight with shadowy dark armor and red eyes",
"cute chibi wizard mage with a pointed hat and glowing staff",
"cute chibi rogue with a hood, tiny daggers, and sneaky expression",
```

## NEGATIVE_PROMPT
```
multiple characters, two characters, three characters, group, friends, companions,
surrounding characters, background characters, extra characters, other monsters, side characters,
pattern, tile, repeat, wallpaper, texture background, paper texture,
scenery, landscape, environment, backdrop, sky, grass, ocean, forest, city, nature,
shadow, ground, floor, detailed background, gradient background,
photorealistic, hyperrealistic, 3d render,
human anatomy, sharp metallic armor, dark, scary, ugly, blurry, high tech
```

## buildFactionPrompt 로직
```java
String archetype = RPG_ARCHETYPES[RANDOM.nextInt(RPG_ARCHETYPES.length)];
String stageModifier = switch (stage) {
    case "child" -> ", evolved form, more powerful and expressive, faction colors more prominent";
    case "adult"  -> ", ultimate fully evolved form, maximum power, spectacular faction-themed armor and features";
    default       -> "";
};
return archetype + ", " + factionPalette + ", " +
        "hand-drawn sketch style, soft pastel watercolor tones, gentle crayon pencil texture, " +
        "naive childlike art, ONLY ONE character, single character portrait, " +
        "large close-up centered, filling 80% of frame, " +
        "pure white background, flat white background, no background elements, no other characters" +
        stageModifier;
```

## getFactionPalette
```java
case "nature-spirits"     -> "soft sage green and warm earthy brown pastel palette";
case "deep-savers"        -> "cool sky blue and aqua pastel palette";
case "nightmare-soldiers" -> "muted lavender and soft violet pastel palette";
case "wind-guardians"     -> "light sky blue and pale mint pastel palette";
case "metal-empire"       -> "soft silver and warm beige pastel palette";
case "virus-busters"      -> "pastel pink and gentle white pearl palette";
case "dragons-roar"       -> "warm coral and soft peach pastel palette";
case "jungle-troopers"    -> "muted olive green and sandy tan pastel palette";
case "dark-area"          -> "soft indigo and dusty purple pastel palette";
case "trash"              -> "cheerful multi-color pastel rainbow palette";
default                   -> "soft warm cream and gentle pastel palette";
```

## 롤백 방법
위 상수/메서드 내용을 ImageGenerationService.java의 해당 위치에 복붙하면 됨.
