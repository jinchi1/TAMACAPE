# TAMACAPE 초기 기술 검토

2026-11-12 기준. 대상은 Pleos Connect의 Android Automotive OS이며 앱 ID는 `ai.tamacape`입니다. 레스트웨이에는 기존 미커밋 변경이 있어 루트 Gradle 모듈 등록이나 파일 이동 없이 `TAMACAPE/`를 별도 Gradle 루트로 생성했습니다.

| 파일 | 역할 |
|---|---|
| `settings.gradle`, `gradle/wrapper/` | 독립 프로젝트 구성과 Pleos Maven 저장소, Gradle 9.6.0 |
| `app/build.gradle` | AGP 9.5.1 내장 Kotlin, compile SDK 37, target SDK 34, min SDK 28, Vehicle SDK 2.3.2 |
| `app/src/main/AndroidManifest.xml` | Automotive 필수 기능 선언, 게임 분류, 런처 |
| `app/src/main/java/ai/tamacape/MainActivity.kt` | 시작 화면 스모크 검증용 Kotlin Activity |

초기 빌드에는 설치된 SDK와 캐시를 활용했습니다. Compose, Room, DataStore는 해당 기능 단계에서 도입할 예정입니다. 제공된 캐릭터 시트의 모비를 캐릭터 기준으로, UI 시안을 가로형 화면과 세계관의 참고로 사용합니다.

## 차량 데이터

Pleos 가이드 기준은 Vehicle SDK 2.3.2와 Vehicle Service 2.3.21입니다. 레스트웨이와 TAMACAPE에서 같은 Vehicle SDK 버전을 선언하며, 로컬 2.3.2 AAR의 `classes.jar`에서 아래 도메인 타입을 확인했습니다.

| 도메인 | 공식 문서 및 AAR에서 확인 | 컴파일 및 런타임 |
|---|---|---|
| Odometer | 총 주행거리, 트립 거리·시간 | 호출 검증 전 |
| Door | 도어 상태 | 호출 검증 전 |
| Light | 조명 상태 | 호출 검증 전 |
| Tire | 타이어 상태 | 호출 검증 전 |
| Safety | 차량 경고 정보 | 호출 검증 전 |

APK 빌드는 SDK 의존성 해석과 패키징을 확인합니다. 도메인별 호출의 컴파일 검증, 권한 승인, Pleos 에뮬레이터 및 실차 조회 검증은 후속 작업입니다. 대상 차량에서 지원 Capability를 확인하고 승인된 조회 권한만 사용합니다. 외부 안전운전 점수는 별도 Hyundai Developers 연동으로, API 승인·사용자 동의·OAuth 검증 이후에 검토합니다.

- [Vehicle SDK 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
- [Vehicle SDK 의존성 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [Hyundai Developers 데이터 API](https://developers.hyundai.com/web/v1/hyundai/data_api)

## 구현 순서

1. Room으로 모비 프로필, 퍼즐 진행, 인벤토리와 장착 상태를 저장합니다. 최초 보상은 트랜잭션과 고유 이벤트 키로 중복을 방지합니다.
2. Compose로 모비의 방, 모험 지도, 꾸미기와 도감을 구현합니다. 16:9 차량 화면을 우선 검증합니다.
3. ‘별빛 차고의 비밀’ 5개 스테이지를 차량 연결 없이 플레이할 수 있게 구현합니다. 퍼즐별 해설·검수 출처·체크포인트를 기록합니다.
4. 차량 연동을 어댑터로 분리합니다. 데모 데이터에는 DEMO DATA를 표시하고, 실제 차량 연동은 지원 여부와 권한을 확인한 후 정차 시 읽기 전용 스냅샷으로 검증합니다.
5. 앱 매니페스트에 `distractionOptimized=true`를 지정하지 않습니다. 시스템 UX 제한 및 Activity 생명주기에 맞춰 입력·타이머·오디오를 중단하고 재진입 시 체크포인트를 복원합니다. 게임이 들어간 빌드에서 Pleos 에뮬레이터의 제한 전환을 검증합니다. [AAOS 정차 앱 가이드](https://developer.android.com/training/cars/parked/automotive-os)

## 공모전

공식 공고의 접수 마감일은 2026년 11월 15일입니다. 공식 기획서 양식은 필수이며 시연 영상과 코드·APK는 선택 가산점 항목입니다. APK를 제출할 경우 GitHub에 소스 코드와 함께 올립니다. [공식 모집 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001)

## 빌드 결과

Android Studio JBR에서 레스트웨이와 TAMACAPE의 `assembleDebug`가 모두 통과했습니다. TAMACAPE 산출물은 `app/build/outputs/apk/debug/app-debug.apk`입니다. 에뮬레이터 설치와 실행은 검증하지 않았습니다.
