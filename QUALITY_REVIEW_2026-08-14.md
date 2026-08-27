# 코드 품질 엄격 검토 보고서

검토일: 2026-08-15
대상: Android, Next.js Backend, Firestore Rules, GitHub Actions, 릴리스 흐름

## v0.1.24 업로드 이탈 UX 변경 범위: A+ (94/100)

업로드 중 뒤로가기가 아무 피드백 없이 무시되던 v0.1.23 잔여 위험을 단계별 확인창으로 해소했습니다. 준비·전송만 취소 가능하고, 취소 요청이 실제 수락된 뒤 `busy=false`가 된 경우에만 화면을 나갑니다. Firestore 저장과 이미 진행 중인 정리는 중간 취소하지 않아 저장 완료 콜백·다음 이미지 큐·뒤로가기의 경쟁을 차단합니다.

- 상태 정책: `Preparing/Uploading` 2개 단계만 취소 가능, `Idle/Cancelling/Saving` 3개 단계는 안내 후 유지
- 자동 검증: 단위 86/86, Android 16 AVD 계측 46/46
- 남은 위험: 네트워크 응답이 없는 실제 서버 취소 지연과 OEM 시스템 뒤로가기 제스처는 물리 기기에서 별도 확인 필요

`code-quality-reviewer` 자동 스크립트는 Kotlin을 지원하지 않고 macOS `awk` 숫자 파싱도 실패해 N/A이며, 실제 lint·테스트·R8 빌드와 수동 경쟁 조건 검토를 판정 근거로 사용합니다.

## v0.1.23 일괄 등록 UX 변경 범위: A+ (93/100)

`code-quality-reviewer` 기준으로 일괄 등록 변경분을 재평가했습니다. 제공된 자동 스크립트는 TypeScript 전용 규칙과 macOS `awk` 숫자 파싱 문제 때문에 Kotlin 결과를 만들 수 없어 N/A 처리하고, 실제 Android Lint·단위/계측 테스트·R8 빌드·수동 상태 수명 검토를 사용했습니다.

| 평가축 | 점수 | 근거 |
|---|---:|---|
| 가독성 | 90 | 큐 전진, 뒤로가기 판단, 이미지 분석 source를 분리했지만 최상위 내비게이션의 큐 책임은 여전히 큼 |
| 성능 | 94 | 이미지별 등록 상태 최대 10개를 묶음당 1개로 줄이고 준비 파일을 즉시 닫음 |
| 명시적 I/O | 95 | 남은 장수, 제외·전체 취소 확인, URI 접근 해제 시점이 코드와 UI에 노출 |
| 유지보수성 | 95 | 단위 84/84, Android 16 AVD 계측 44/44, CI 대상 app benchmark 누락까지 보강 |
| 에러 처리 | 94 | 느린 이전 OCR·바코드 결과를 source URI로 격리하고 진행 중 화면 이탈을 차단 |
| 협업 | 96 | `0.1.23 (24)`, 변경 이력, QA, TODO, UX·성능 근거를 함께 갱신 |

가중 점수는 `90×0.25 + 94×0.20 + 95×0.15 + 95×0.25 + 94×0.10 + 96×0.05 = 93.5`이며 보수적으로 93점입니다. 이번 변경의 P0/P1 코드 결함은 발견되지 않았습니다. 다만 업로드 중 뒤로가기는 데이터 정리를 우선해 반응하지 않으므로 취소 버튼을 사용해야 하며, OEM Photo Picker URI 권한과 실제 10장 연속 등록 peak PSS는 물리 기기 검증 전 보장하지 않습니다.

## v0.1.21 공유 등록 변경 범위: A+ (92/100)

`code-quality-reviewer` 기준으로 Android 공유 시트 변경분만 다시 평가했습니다. 자동 스크립트는 TypeScript 전용 규칙과 macOS `awk` 숫자 파싱 오류로 유효한 Kotlin 점수를 만들지 못해 N/A 처리하고, Android Lint·테스트와 수동 보안 검토로 산정했습니다.

| 평가축 | 점수 | 근거 |
|---|---:|---|
| 가독성 | 88 | Intent 판정, 제한 복사, 파일 소유권, 화면 상태를 분리했지만 최상위 앱 내비게이션 책임은 여전히 큼 |
| 성능 | 96 | 64KB 단일 패스 복사, 10MB·4천만 화소 상한, main thread 밖 I/O |
| 명시적 I/O | 96 | `None/Copying/Ready/Error` 상태와 `Uri`·MIME·크기 경계가 타입과 메시지로 노출 |
| 유지보수성 | 92 | 단위 78/78, 계측 39/39와 실제 `MainActivity` 재생성 회귀를 포함 |
| 에러 처리 | 94 | MIME 불일치·빈 파일·상한·빈 읽기·취소를 분리하고 취소 예외를 다시 전파 |
| 협업 | 96 | v0.1.21 버전, 변경 이력, QA, TODO, 벤치마크 근거를 같은 변경에 반영 |

가중 점수는 `88×0.25 + 96×0.20 + 96×0.15 + 92×0.25 + 94×0.10 + 96×0.05 = 92.8`이며 보수적으로 92점으로 기록합니다. 변경 범위에서 P0/P1 결함은 발견되지 않았습니다. 남은 범위는 `ACTION_SEND_MULTIPLE`, OEM 갤러리별 URI 권한, 물리 기기 프로세스 종료 복원이며 출시 전 기능 약속에는 포함하지 않습니다.

## 전체 평가: B+ (79/100)

`code-quality-reviewer`의 가중치에 따라 평가했습니다. 제공된 자동 품질 스크립트는 숫자 파싱 오류로 중단되어 자동 점수는 사용하지 않았고, 실제 테스트·빌드·보안 감사와 수동 코드 검토를 근거로 계산했습니다.

| 평가축 | 점수 | 주요 근거 |
|---|---:|---|
| 가독성 | 66 | 페이징을 별도 `CouponPager`로 분리했지만 500~900줄 Compose 화면 파일이 존재 |
| 성능 | 84 | 512px WebP, 8KB 업로드 버퍼, 최대 8MP 상세와 초기 최대 24개 cursor paging을 적용했으나 Cron N+1이 남음 |
| 명시적 I/O | 82 | 페이지 크기·선조회 거리·오류/끝 상태가 타입으로 드러나지만 API 입력 스키마 문서화가 부분적 |
| 유지보수성 | 82 | 총 62개 자동 테스트와 cursor 경계 테스트를 추가했으나 Android UI/API route 통합 테스트가 부족 |
| 에러 처리 | 80 | 목록 오류 재시도와 Blob 보상 삭제를 제공하지만 삭제·알림의 영속 재시도 구조는 없음 |
| 협업 | 94 | 기능·문서 커밋을 분리하고 PR/main CI·운영 인덱스 선배포를 적용했으나 main branch protection은 미적용 |

가중 총점은 `66×0.25 + 84×0.20 + 82×0.15 + 82×0.25 + 80×0.10 + 94×0.05 = 78.8`이며 반올림해 79점입니다. 직전 74점보다 5점 상승했습니다.

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
- 백그라운드 FCM `deepLink` extra 복원과 앱 전용 URI 검증
- 쿠폰 목록·상세·등록 미리보기에 샘플 디코딩과 UID별 LRU 캐시 적용
- 신규·기존 쿠폰 512px WebP 썸네일과 실패 시 원본 fallback 적용
- 공개·본인 비공개 쿠폰을 각각 12개씩 읽는 cursor paging과 끝 4개 전 선조회 적용
- cursor anchor 1개를 겹쳐 실시간 앞페이지 삽입 시 누락을 방지하고 ID map으로 중복 제거
- GitHub Actions 전체를 Node 24 기반 고정 SHA로 전환

## 검증 수치

| 항목 | 작업 전 | 작업 후 |
|---|---:|---:|
| 자동 테스트 | 17개 | 62개(Backend 22, Rules 12, Android 28) |
| Firestore Rules 테스트 | 0개 | 12개 |
| 프로덕션 High/Critical npm 취약점 | 11개 | 0개 |
| 전체 프로덕션 npm 취약점 | 18개 | 6개 Moderate |
| PR/main CI | 0개 | Backend·Android 2개 job, 최근 PR 및 main 실행 반복 통과 |
| Gradle Wrapper | 없음 | 8.10.2 + SHA-256 고정 |

## 5556 디바이스 검증

- `emulator-5556`에 `0.1.17 (18)` 64MB debug APK 설치 및 cold start 성공
- FCM과 같은 `OPEN_COUPON_DETAIL + deepLink extra` cold start 성공
- Android 13+ 알림 권한 요청 UI 노출과 허용 상태 확인
- 수정 전 앱 시작 시 `coupon_expiry` 채널 없음, 수정 후 시작 즉시 중요도 HIGH(4) 채널 생성 확인
- 5556의 DNS는 이후 정상화돼 `firebaseinstallations.googleapis.com` 해석과 FCM token key 생성을 확인
- 로그인 세션이 없어 Firestore token 등록 → 서버 전송 → 알림 트레이 표시는 아직 미검증
- v0.1.17 시작 화면을 1080×2400으로 확인하고 실행 직후 crash·`FAILED_PRECONDITION` 로그가 없음을 확인

## 아직 배포를 막아야 하는 위험

### P1 — Android 실제 릴리스 미검증

- PR/main CI는 실제 GitHub Actions에서 반복 통과했고 Node 20/setup-java 폐기 annotation도 0건으로 정리했습니다.
- `v0.1.13` tag와 Release는 생성하지 않았습니다.
- Firestore Rules와 인덱스 5개는 운영 `giftcondoctor` 프로젝트에 배포했습니다.
- GitHub Actions repository secret과 variable이 각각 0개라 Android release workflow는 실행 전에 차단했습니다.

조치: 기존 v0.1.12와 동일한 release keystore를 확보하고 필수 secret 7개를 등록한 뒤 `main`에서 수동 릴리스를 실행합니다.

### P1 — 알림 중복과 삭제 복구 구조

- Cron은 로그 확인 후 FCM 발송, 마지막 로그 기록 순서라 동시 실행 또는 발송 후 장애에서 중복 알림 가능성이 있습니다.
- 쿠폰·방 삭제는 Firestore와 Blob을 여러 단계로 지워 중간 실패 시 부분 삭제 또는 orphan이 남을 수 있습니다.
- 쿠폰 생성 실패 시 즉시 보상 삭제를 추가했지만 보상 삭제 자체가 실패할 경우 정리 job이 없습니다.

조치: 알림 lease/outbox, 삭제 tombstone, 재개 가능한 cleanup job을 도입합니다.

### P1 — 실제 푸시 E2E 미검증

- 디바이스 권한·FCM 채널·server payload와 FCM token key 생성까지 확인했습니다.
- 다만 5556은 물리 디바이스가 아니며 로그인 세션이 없어 token 문서 저장과 실제 알림 도착은 증명하지 못했습니다.
- 로그인된 물리 Android에서 계정 전환을 포함한 6개 상태 조합을 통과하기 전에는 푸시 해결 완료로 판정하지 않습니다.

조치: AVD를 정상 DNS로 재시작 → 전용 QA 계정으로 로그인 → token 문서 확인 → 즉시 테스트와 만료 형식 테스트 수신 → foreground/background/종료 상태 딥링크를 동일 APK·배포로 검증합니다.

### P1 — 목록 확장성의 남은 검증

- 목록은 512px WebP와 cursor paging을 사용하고 상세는 최대 8MP로 제한합니다.
- 쿠폰 100개 기준 초기 표시 상한은 100개에서 최대 24개로 76% 줄었고, 9페이지 무누락·무중복 단위 테스트를 통과했습니다.
- 검색어가 뒤 페이지에만 있으면 일치 항목을 찾는 동안 최악의 경우 전체 문서를 다시 읽으며, 실제 Firestore 읽기·프레임 시간 계측은 없습니다.

조치: 로그인된 100개 쿠폰 fixture에서 페이지별 읽기 수, 첫 렌더, 스크롤 프레임과 메모리를 계측합니다.

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
