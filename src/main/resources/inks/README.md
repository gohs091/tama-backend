# 잉크 데이터

서버가 켜질 때 `LandCatalog`가 이 폴더를 읽습니다. 잉크를 고치거나 추가할 때는 이 폴더만 바꾸면 됩니다.
빠진 칸이나 잘못된 값은 `InkDataFilesTest`가 잡아냅니다.

## 파일

| 파일 | 내용 |
|---|---|
| `families.json` | 계열(짐승·새·비늘 …). 계열 잉크 `family.<key>`는 여기서 만들어집니다 |
| `regions.json` | 구역 순서 (지도에 나오는 순서) |
| `<구역 id>.json` | 구역 정보, 탐험 순서(`explore`), 이 구역이 집인 잉크 |

- 잉크는 **집 구역 파일에 한 번만** 씁니다. 다른 구역의 탐험 목록에서는 id로 가리킵니다.
- `explore`는 끄적랜드 탐험으로 발견되는 순서입니다. 닫힌 구역(`"open": false`)은 비워 둡니다.
- 스파링 상대가 떨어뜨리는 잉크도 여기 있고, 어느 상대가 떨어뜨리는지는 `../spar/enemies.json`의 `drops`에 있습니다.

## 잉크 한 줄

```json
{"id":"megalodon","emoji":"🦈","name":"메갈로돈의 잉크","grade":"LEGEND","family":"fish",
 "prompt":"…전체 특징…","part":"a large dorsal fin and sharp teeth","body":"LEGLESS"}
```

| 칸 | 필수 | 쓰임 |
|---|---|---|
| `id`, `emoji`, `name` | 예 | `name`은 "○○의 잉크" |
| `grade` | 예 | `COMMON` · `RARE` · `LEGEND` (계열은 `families.json`) |
| `family` | 예 | `families.json`의 key |
| `prompt` | 예 | 진화 이미지에 넣는 영어 특징 (많이 바뀌는 단계) |
| `part` | 희귀·전설은 예 | 조금 바뀌는 단계에서 쓰는 한 부위. 없으면 `prompt` |
| `body` | 아니요 | 희귀·전설이 크게 바뀔 때 몸 형태: `HUMAN` 사람형 · `QUAD` 네발 · `DINO` 공룡 두발 · `TANK` 바퀴·궤도 · `LEGLESS` 다리 없음 · `FLY` 큰 날개 |
