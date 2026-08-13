# 코드 품질 엄격 검토 보고서

검토일: 2026-08-14
대상: Android, Next.js Backend, Firestore Rules, GitHub Actions, 릴리스 흐름

## 전체 평가: B (70/100)

`code-quality-reviewer`의 가중치에 따라 평가했습니다. 제공된 자동 품질 스크립트는 숫자 파싱 오류로 중단되어 자동 점수는 사용하지 않았고, 실제 테스트·빌드·보안 감사와 수동 코드 검토를 근거로 계산했습니다.

| 평가축 | 점수 | 주요 근거 |
|---|---:|---|
| 가독성 | 64 | 알림 정책과 권한 조건은 명확해졌지만 500~900줄 UI/ViewModel 파일이 존재 |
| 성능 | 59 | 연결 풀 공유는 개선됐으나 원본 이미지 전체 다운로드와 Cron N+1 조회가 남음 |
| 명시적 I/O | 80 | 알림 payload·권한·transaction 경계는 명확하나 API 입력 스키마 문서화가 부분적 |
| 유지보수성 | 72 | CI·Rules 테스트·Wrapper와 회귀 테스트를 추가했으나 API 통합/UI 테스트가 부족 |
| 에러 처리 | 76 | 탈퇴 고아 데이터와 첫 푸시 채널을 보강했지만 삭제·알림의 재시도 상태가 없음 |
| 협업 | 86 | 8개 논리적 커밋, CI, Changelog를 구성했으나 PR/branch protection 실반영은 미확인 |

가중 총점은 `64×0.25 + 59×0.20 + 80×0.15 + 72×0.25 + 76×0.10 + 86×0.05 = 69.7`이며 반올림해 70점입니다.

## 이번 작업에서 해소한 주요 위험

- Cron secret 미설정 시 공개 실행되던 fail-open 제거
- 취약한 `image-size` 제거 및 JPEG/PNG/WebP magic byte allowlist 적용
- 쿠폰과 Blob 경로의 소유 관계를 서버·Rules 양쪽에서 검증
- 고위험 API에 Firestore transaction 기반 사용자별 rate limit 적용
- 로그아웃 FCM token 정리와 재로그인 알림 설정 덮어쓰기 수정
- 쿠폰 쿼리 병합 경쟁, 최신 댓글 누락, OCR 만료일 우선순위 수정
- Gradle Wrapper, Backend/Android CI, Firestore Rules Emulator 테스트 추가
- 기존 Release 삭제·tag 선생성 제거 및 릴리스 단계를 build/publish/dashboard로 분리
- 알림 deep link의 cold start, `singleTop`, 인증 초기화 순서 보강
- 앱 시작 시 알림 채널을 생성해 첫 백그라운드 푸시의 fallback 의존 제거
- 만료 푸시 제목·본문 단축과 테스트 푸시의 가짜 10초 지연 제거
- 가입·탈퇴·멤버 제거의 `memberCount` transaction 정합성 보강
- 소유 쿠폰이 남은 멤버의 탈퇴·제거와 타 예약자의 사용 완료 차단
- Cron 부분 실패를 HTTP 500으로 노출해 운영 감지 가능성 보강

## 검증 수치

| 항목 | 작업 전 | 작업 후 |
|---|---:|---:|
| 자동 테스트 | 17개 | 36개 |
| Firestore Rules 테스트 | 0개 | 9개 |
| 프로덕션 High/Critical npm 취약점 | 11개 | 0개 |
| 전체 프로덕션 npm 취약점 | 18개 | 6개 Moderate |
| PR/main CI | 0개 | Backend·Android 2개 job |
| Gradle Wrapper | 없음 | 8.10.2 + SHA-256 고정 |

## 5556 디바이스 검증

- `emulator-5556`에 `0.1.13 (14)` debug APK 설치 및 cold start 성공
- Android 13+ 알림 권한 요청 UI 노출과 허용 상태 확인
- 수정 전 앱 시작 시 `coupon_expiry` 채널 없음, 수정 후 시작 즉시 중요도 HIGH(4) 채널 생성 확인
- 공식 `0.1.12 (13)`와 최신 `0.1.13 (14)` 모두 Firebase Installations에서 `SERVICE_NOT_AVAILABLE` 재현
- 5556은 `8.8.8.8` 연결은 되지만 `google.com`과 `firebaseinstallations.googleapis.com` DNS 조회가 실패하며, DNS 서버는 `10.0.2.3`
- 비교 후 5556을 최신 `0.1.13 (14)`와 알림 권한 허용 상태로 복원

## 아직 배포를 막아야 하는 위험

### P1 — 운영 반영과 실제 릴리스 미검증

- 새 CI와 릴리스 workflow는 로컬 구문·명령 검증만 완료됐고 GitHub Actions 실행 이력은 아직 없습니다.
- `v0.1.13` tag와 Release는 생성하지 않았습니다.
- GitHub `main` branch protection, `production` environment 승인, Firebase Rules 실제 배포 상태는 확인·변경하지 않았습니다.

조치: 브랜치 push와 PR 검증 → 필수 check·승인 규칙 설정 → Rules 배포 확인 → `main`에서 수동 릴리스 순서로 진행합니다.

### P1 — 알림 중복과 삭제 복구 구조

- Cron은 로그 확인 후 FCM 발송, 마지막 로그 기록 순서라 동시 실행 또는 발송 후 장애에서 중복 알림 가능성이 있습니다.
- 쿠폰·방 삭제는 Firestore와 Blob을 여러 단계로 지워 중간 실패 시 부분 삭제 또는 orphan이 남을 수 있습니다.
- 쿠폰 생성 실패 시 즉시 보상 삭제를 추가했지만 보상 삭제 자체가 실패할 경우 정리 job이 없습니다.

조치: 알림 lease/outbox, 삭제 tombstone, 재개 가능한 cleanup job을 도입합니다.

### P1 — 실제 푸시 E2E 미검증

- 디바이스 권한·FCM 채널·서버 payload와 token 저장 코드는 확인했지만, 5556의 DNS 장애로 FCM token 자체가 생성되지 않습니다.
- 공식 서명 APK에서도 동일해 앱 서명보다는 `HoneymoonDoctor_Dev` AVD DNS 환경 문제로 판정했습니다.
- DNS 복구 후에도 변경 백엔드 배포와 로그인된 QA 계정이 있어야 token 문서 및 실제 도착을 검증할 수 있습니다.

조치: AVD를 정상 DNS로 재시작 → 전용 QA 계정으로 로그인 → token 문서 확인 → 즉시 테스트와 만료 형식 테스트 수신 → foreground/background/종료 상태 딥링크를 동일 APK·배포로 검증합니다.

### P1 — 이미지 메모리와 목록 확장성

- 목록·상세 이미지는 인증 API에서 원본 bytes를 내려받습니다.
- 쿠폰 목록에 paging이 없고 Bitmap 크기 제한·공용 캐시가 부족해 다수의 고해상도 이미지에서 OOM 또는 UI 지연 가능성이 있습니다.

조치: 서버 thumbnail 생성, Android 이미지 캐시·sample decode, Firestore paging을 함께 적용합니다.

### P1 — abuse 방어의 우회 가능성

- 현재 rate limit은 인증 UID 기준이므로 공격자가 여러 Firebase 계정을 만들면 우회할 수 있습니다.
- Firebase App Check와 신뢰 가능한 client IP 기준 제한은 아직 없습니다.
- 공개방 비밀번호가 숫자 4자리라면 탐색 공간이 10,000개에 불과합니다.

조치: App Check, UID+IP 복합 제한, 실패 잠금과 지수 backoff, 더 강한 방 비밀번호 정책을 적용합니다.

### P2 — 테스트와 구조

- API route 통합 테스트와 Android instrumented UI 테스트는 아직 0개입니다.
- `RoomScreens.kt`, `CouponScreens.kt`, `ViewModels.kt`가 각각 여러 책임을 가진 대형 파일입니다.
- Google Sign-In API와 일부 Material icon API에서 deprecation 경고가 발생합니다.
- 구조화된 request ID 로깅과 운영 관측 지표가 없습니다.

조치: 인증·방 가입·업로드·알림 route 통합 테스트를 먼저 추가하고, 화면 단위 분리와 Credential Manager 전환을 진행합니다.

## 긍정적 평가

- Firebase ID token, 방 멤버·방장, private 쿠폰 접근 경계가 명시적으로 분리돼 있습니다.
- Firestore Rules는 기본 거부이며 서버 전용 문서 접근을 차단합니다.
- private Blob은 인증 프록시와 `no-store`, `nosniff` 응답을 사용합니다.
- 예약 경쟁은 Firestore transaction으로 막고 listener는 `awaitClose`에서 해제합니다.
- 이번 변경으로 보안·빌드·Rules 검증을 PR 단계에서 반복 실행할 기반이 마련됐습니다.

## UI/UX 추가 검토

- Google Wallet, Samsung Wallet, Stocard, 기프티스타, 니콘내콘의 빠른 접근·스캔·자동 인식 패턴과 비교했습니다.
- 로그인, 첫 쿠폰방, 쿠폰 등록의 경쟁 CTA를 줄이고 쿠폰 등록은 이미지 선택 전 핵심 행동 1개만 노출하도록 변경했습니다.
- 기존 사용자는 방 선택과 등록 FAB의 2탭으로 등록 화면에 진입할 수 있으며, 신규 사용자는 입력 행위를 제외하고 목표 4탭 이내로 정의했습니다.
- 실제 5556 화면에서 Google CTA와 이메일 대체 흐름, 48dp 이상 터치 영역, 접근성 노드 노출을 확인했습니다.
- 다만 로그인된 테스트 계정 없이 방 상세·등록 화면의 시각 회귀를 실제 렌더링하지 못했고 instrumented UI 테스트는 여전히 0개입니다.
- 세부 비교와 후속 우선순위는 `UX_BENCHMARK_2026-08-14.md`에 기록했습니다.
