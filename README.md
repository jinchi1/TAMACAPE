# TAMACAPE 타마카페

Pleos Connect 차량에서 부모와 아이가 함께 즐기는 교통안전 퀴즈·캐릭터 성장·AI 동요 앱입니다.

## 현재 기능

- 5개 스테이지와 10개 가족 퀴즈, 상황 속 가상 차량 조작, 힌트·해설·재도전·이어하기
- 모비·루루·보리 친구 해금, 경험치·코인·별, Room 저장 및 최초 보상 중복 방지
- 큰 글자 기본 UI, 안전 도감·부모 설정, 정차 제한 시 게임 입력 차단
- 기기 내 음성 답변, 질문 낭독, 검수된 퀴즈 문맥의 AI 대화
- 가사 미리보기와 30초 동요 생성 중계, 로컬 앨범·반복·즐겨찾기·오프라인 재생
- 실제 생성 예시곡 **기다려요 횡단보도** 포함. 예시 듣기는 외부 API 호출과 생성 XP 지급이 없습니다.
- 차량 누적거리 읽기와 안전 동행 미션 시연

## 실행

Android Studio에서 이 폴더를 엽니다. Android SDK API 37 및 Gradle 9.6을 실행할 수 있는 JDK가 필요합니다. Pleos SDK는 `settings.gradle`의 공식 Maven 저장소에서 받습니다. Pleos 에뮬레이터 또는 지원 차량 환경이 필요합니다.

```sh
./gradlew testDebugUnitTest assembleDebug
```

기기별 `local.properties`와 인증 정보는 직접 설정하며 저장소에 넣지 않습니다.

서버 모의 검사:

```sh
cd server
node test/run.mjs
```

## AI 연결과 비용

Cloudflare Worker 중계 소스는 `server/`에 있습니다. OpenAI 및 ElevenLabs 키는 Cloudflare Secrets에만 저장합니다. 시연 접속 코드는 별도 설정이 필요하며 공개하지 않습니다. 저장된 곡 재생에는 AI 호출 비용이 없습니다. 실제 생성은 유료일 수 있으며 자동 재시도하지 않습니다. 기본 배포 설정은 음악 생성 비활성입니다. 서버 README의 설정 절차를 참고하세요.

## 현재 제한

실차 안전 점수와 성장 보상은 아직 연동되지 않았습니다. 앱은 주행 제한 시 게임과 음악 재생을 정지합니다. Pleos 시스템 미디어 통합·주행 중 승인된 재생·재시동 복구는 후속 검증 항목입니다. 가사 표시는 구간 기준 예상 타이밍이며 정확한 보컬 정렬이 아닙니다. 아동 음성·전체 화면 조합의 현장 검증과 공개 운영용 보호자별 인증이 필요합니다.

현재 상태: [남은 작업](docs/REMAINING_WORK.md), [동요 구현과 실제 생성 결과](docs/SONG_IMPLEMENTATION.md), [구현 이력](docs/IMPLEMENTATION_CHECKLIST.md).

참가자의 연락처·서명이 포함된 공모전 신청서, API 키, 시연 접속 코드, 빌드 캐시와 로컬 설정은 공개 저장소에 포함하지 않습니다.

## 공모전 제출 파일

- 저장소 주소: https://github.com/jinchi1/TAMACAPE
- APK 경로: `apk/TAMACAPE-0.1.0-demo.apk`
- [APK 다운로드](https://github.com/jinchi1/TAMACAPE/raw/refs/heads/main/apk/TAMACAPE-0.1.0-demo.apk)
- [63초 시연 영상](demo/TAMACAPE-demo.mp4)
- 영상 경로: `demo/TAMACAPE-demo.mp4`

APK는 2026-11-15에 빌드한 디버그 서명 시연 버전입니다. 설치 환경과 검증 결과는 [APK 안내](apk/README.md), 영상 구성은 [시연 안내](demo/README.md)를 참고하세요.
