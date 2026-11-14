# 스테이지 장면 아트 — 2026-11-13

내장 image_gen 도구로 생성했습니다. 외부 API 키나 Worker를 이미지 생성에 사용하지 않았습니다.
최종 파일은 app/src/main/res/drawable-nodpi/에 저장했으며 JPEG 1280px로 최적화했습니다(총 약 1.2MB).

| 파일 | 최종 프롬프트의 장면·제약 |
|---|---|
| stage_fog.jpg | TAMACAPE family car-safety adventure. Premium soft miniature 3D storybook render, mint teal car, deep lavender twilight, warm gold lamps. Winding forest road in gentle blue fog, small mint car far ahead, generous empty foreground, pine trees. No characters, text, logos or borders. |
| stage_town.jpg | Wide landscape premium soft miniature 3D storybook illustration. Lavender twilight town intersection, zebra crossing, parent and child holding hands, mint car stopped far behind crossing. All windows opaque dark violet, no visible occupants or animals. Brick corner and parked van. No words, logos, UI or borders. |
| stage_workshop.jpg | Cozy magical vehicle inspection workshop, parked unoccupied mint car with opaque windows and tires grounded, tire gauge, spare tire, closed toolbox and illustrated manual. Lavender shadows, golden lamps, rounded toy surfaces. No people, words, logos, exposed machinery or sparks. |
| stage_castle.jpg | Magical ivory castle with lavender roofs and gold stars, winding road, zebra crossing and sidewalk in foreground, mist, wet pavement after rain, warm lantern reflections. No vehicles, people, animals, words, logos, UI or border. |

도시 첫 생성본은 탑승자 안전 표현이 불명확하여 앱에 사용하지 않고 재생성했습니다.
장면은 이야기 배경이며 실제 교통 표지·차량 조작의 판정 자료가 아닙니다. 검수된 퀴즈 문구와 정답 로직은 유지합니다.
퀴즈와 스테이지 진입 카드에 동일 아트를 사용하며 FIT_CENTER/adjustViewBounds로 비율을 보존합니다.

추가 보완: 큰 글자 설정에서는 900dp 미만 너비를 한 열로 구성하고, 680dp 미만 화면에서 HUD를 세로 배치합니다.
