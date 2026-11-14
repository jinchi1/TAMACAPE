# 모비 피드백 표정

2026-11-13 내장 image_gen 편집 도구로 기본 mobi_sprite.png를 참조해 두 표정을 제작했습니다. 투명 PNG를 유지하고 384px로 최적화했습니다.

- app/src/main/res/drawable-nodpi/mobi_celebrate.png: 정답·완료 카드
- app/src/main/res/drawable-nodpi/mobi_thinking.png: 힌트·다시 생각하기 카드

공통 프롬프트: Preserve exact character identity, cream body proportions, mint headphones and head patch, gold star antenna, mint star belly with white exclamation mark, soft 3D materials. Whole seated body and antenna visible, centered with transparent margins. Single isolated sprite with genuinely transparent alpha background, no ground shadow, no text, no extra props.

축하 표정: Change only facial expression and arms: joyful eyes closed as upward smiling crescents, open delighted smile, both small arms lifted in celebration.
생각 표정: Change only facial expression and arms: gentle curious thinking expression, big warm brown eyes looking slightly upward, small friendly closed smile, one small hand at cheek. Encouraging, never sad or judging. No question marks.

모비·루루·보리 모두 기본·축하·생각 표정을 연결했습니다. 잠듦·놀람 등 추가 감정 세트는 포함하지 않습니다.

## 루루·보리 후속 제작
내장 image_gen 편집 도구, 각 기본 friend_lulu.png / friend_bori.png를 참조하고 투명 PNG 384px로 저장했습니다.
파일: app/src/main/res/drawable-nodpi/lulu_celebrate.png, lulu_thinking.png, bori_celebrate.png, bori_thinking.png.

프롬프트 공통: Preserve exact character identity, colors, cream face/muzzle and belly, mint star badge, paw colors, soft plush 3D materials and seated proportions. Whole ears/tuft and feet visible with transparent margins. Single isolated character, real alpha transparency, no ground shadow, glow, text or props.
루루 고유 조건: lavender rabbit, long ears, pink paws. 보리 고유 조건: golden yellow bear, round ears, mint leaf tuft, peach paws.
축하: Only change expression and arms: joyful closed crescent eyes, delighted open smile, both paws lifted celebrating.
생각: Only change expression and arms: warm brown eyes looking slightly upward curiously, gentle small closed smile, one paw on cheek thinking. Encouraging, never sad or judging. No question mark.
