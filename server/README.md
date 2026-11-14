# TAMACAPE 가족 AI 중계

Cloudflare Worker에서 OpenAI Responses API를 호출합니다. 기존 `pleos-kids-ai` Worker에 배포하고 AI 대화·동화의 실제 호출과 앱 동화 표시를 확인했습니다.

엔드포인트: https://pleos-kids-ai.jinchi1.workers.dev/v1/family
모델: `gpt-5.6-luna`, reasoning effort `none`. 신규 Worker 생성이나 유료 플랜 전환은 하지 않았습니다.

## 구성

- 앱 → HTTPS POST /v1/family → 인증/입력 검증/요청 제한 → 입력 검사 → 생성 → 출력 검사 → 앱.
- kind=coach: 현재 퀴즈에 대한 부모·아이의 생각을 받아 짧은 설명과 후속 질문.
- kind=story: 해당 스테이지의 검수된 안전 약속과 선택 캐릭터로 짧은 모험 동화.
- 정답·보상은 앱 콘텐츠가 판정합니다. AI 응답은 진행을 변경하지 않습니다.
- 서버의 catalog.mjs가 안전 약속의 기준입니다. 클라이언트는 안전 규칙이나 모델·시스템 프롬프트를 덮어쓸 수 없습니다.
- 원음 미전송. 앱은 대화·접속 코드를 디스크에 저장하지 않습니다. 앱 재생성 시 연결 설정은 다시 필요합니다.
- Responses에 store:false를 지정합니다. 이것이 API 제공자의 모든 보관 정책을 해제하는 의미는 아닙니다.
- 서버는 요청 본문/응답/비밀키를 로그로 출력하지 않으며 Workers 관측 로그는 기본 비활성입니다.

## 준비 및 배포

필요: Cloudflare 계정, OpenAI API 사용 가능한 프로젝트/키, 해당 프로젝트에서 사용 가능한 텍스트 모델 ID.
키는 채팅이나 APK, Git에 넣지 않습니다.

Node.js LTS와 npm이 설치된 개발 환경에서 이 폴더를 작업 디렉터리로 사용합니다.

    npm test
    npx wrangler login
    npx wrangler secret put OPENAI_API_KEY
    npx wrangler secret put APP_ACCESS_TOKEN
    npx wrangler secret put ACCESS_EXPIRES_AT
    npx wrangler deploy

각 secret 명령의 입력 프롬프트에 값을 입력합니다.
- OPENAI_MODEL: wrangler.toml의 일반 변수. API 키는 기존 Cloudflare Secret만 참조합니다.
- APP_ACCESS_TOKEN: API 키와 별개로 생성한 32~200자 무작위 시연 접속 코드.
- ACCESS_EXPIRES_AT: 가까운 미래의 ISO 8601 UTC 만료 시각. 만료 시 401.
- OPENAI_API_KEY: 서버에만 두는 API 키.

배포 후 앱 설정 → 부모님 AI 연결 설정:
1. https://배포주소/v1/family
2. APP_ACCESS_TOKEN에 지정한 접속 코드
3. 전송 안내를 읽고 적용
4. 퀴즈 해설에서 'AI 친구와 더 이야기하기', 완료 화면에서 '우리 가족 모험 동화'

설정 적용만으로 서버 연결 성공이라고 표시하지 않습니다. 실제 요청의 인증·응답 여부를 화면에서 확인합니다.

## 시연 범위와 공개 운영 전 작업

현재 인증은 만료되는 공유 시연 코드입니다. 배포 주소만 아는 비인증 호출은 거부하지만 공개 서비스용 사용자 인증을 대신하지 않습니다.
공개 서비스에는 보호자별 로그인·짧은 수명 토큰 발급·사용자별 할당량을 추가해야 합니다.
위치별 제한에 더해 동일 Worker의 SQLite Durable Object가 전체 합산 최근 60초 6회, UTC 하루 100회를 강제합니다. 실패한 생성도 예약 횟수에 포함됩니다. 호출 수 제한이며 달러 단위 지출 상한은 아닙니다. 무료 플랜 호환 구성이며 OpenAI API 호출은 별도 과금됩니다.
아동 대상 개인정보 고지·보호자 동의 흐름과 AI 응답 품질은 출시 전 별도 검증이 필요합니다.
모델에 따라 응답 지연 또는 출력 토큰 한도에 걸릴 수 있습니다. 서버는 incomplete 응답을 표시하지 않고 실패로 돌려줍니다.
부모가 앱을 벗어나면 요청은 취소하지만 이미 제공자에 전달된 호출의 과금까지 취소된다고 보장하지 않습니다.

## 오류

401 접속 코드 오류/만료, 400 잘못된 문맥, 415 잘못된 형식, 422 가족 주제로 다시 입력,
429 잠시 후 재시도 또는 당일 사용 한도, 503 연결·설정·제공자 오류. 원문 오류는 앱에 노출하지 않습니다.
클라이언트 30초 제한, 서버 전체 25초 제한. 응답 리다이렉트는 클라이언트에서 따르지 않습니다.

## 검증

npm test: 외부 호출 없이 인증·만료·검수 문맥·입출력 필터·제공자 실패 등 16개 검사.
Node.js로 실행했습니다. 실제 테스트 결과와 남은 작업은 ../docs/AI_DEPLOYMENT_REPORT.md에 기록했습니다.
시연 접속 코드는 Git 제외된 `.private/family-ai-access.json`에만 로컬 보관하며 APK에는 넣지 않습니다. 7일 만료 후 교체해야 합니다.

## 공식 자료

- https://developers.openai.com/api/docs/quickstart
- https://developers.openai.com/api/docs/guides/migrate-to-responses
- https://developers.openai.com/api/docs/guides/moderation
- https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/

## 현재 동요 전환 상태

위 초기 동화 설명은 과거 이력입니다. 현재 kind는 coach, lyrics, song이며 동화 경로는 제거했습니다. lyrics는 서명된 가사 미리보기를 반환하고 song은 확인한 가사로 ElevenLabs music_v2_5를 호출합니다. Secret 이름은 `ElevenLabs_API`입니다. 음악은 UTC 하루 10회 예약 제한과 중복 차단을 사용하고, 실패도 예약을 소비합니다. 음악 요청 제한 시간은 110초입니다. `MUSIC_ENABLED=false`가 기본이며 실제 생성에는 별도 비용이 발생합니다. Workers는 `redirect:"error"`를 지원하지 않으므로 음악 fetch는 manual로 설정해 리다이렉트를 거부합니다. 공개 서비스용 개인별 인증은 미구현입니다.
