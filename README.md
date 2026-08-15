# 기프티콘닥터 (giftcondoctor, GD)

Android-first MVP for Korean users to manage shared gifticon/coupon images in rooms and receive expiration push notifications.

## 구성

- `android/`: Kotlin, Jetpack Compose, Material 3 Android 앱
- `backend/`: Next.js App Router 기반 Vercel API Routes
- `firebase/`: Firestore rules/indexes
- `QA_CHECKLIST.md`: 수동 QA 체크리스트
- `TODO_v1.1.md`: MVP 이후 작업 목록
- `UX_BENCHMARK_2026-08-14.md`: 유사 앱 비교, 정량 UX 목표, P0~P2 우선순위
- `QUALITY_REVIEW_2026-08-14.md`: 코드 품질 점수와 출시 차단 위험
- `PERFORMANCE_BENCHMARK_2026-08-15.md`: 100개 목록 Baseline Profile 적용 전·후 성능 기준선
- `FIREBASE_ROLLBACK.md`: Rules·Indexes 검증, 승인, 롤백·roll-forward 운영 절차

## 필수 준비물

1. Firebase 프로젝트
2. Android 앱 등록 후 `google-services.json`을 `android/app/google-services.json`에 배치
3. Firebase Auth: Email/Password, Google 로그인 활성화
4. Cloud Firestore, Firebase Cloud Messaging 활성화
5. Firebase Admin service account 값 준비
6. Vercel Blob store 생성 및 `BLOB_READ_WRITE_TOKEN` 준비
7. Vercel 프로젝트 환경 변수 설정

## Backend 환경 변수

`backend/.env.example`을 기준으로 Vercel 환경 변수와 로컬 `.env.local`을 구성합니다.

```bash
cd backend
npm ci
npm test
npm run test:rules
npm run typecheck
npm run build
```

이미지 API는 쓰기 없이 health 또는 인증된 기존 쿠폰의 thumbnail/original 응답을 1~100회 측정할 수 있습니다. Firebase ID token은 URL이나 명령 인자에 넣지 않고 환경 변수로만 전달합니다.

```bash
IMAGE_BENCHMARK_BASE_URL=https://giftcondoctor.vercel.app \
IMAGE_BENCHMARK_MODE=health IMAGE_BENCHMARK_RUNS=5 npm run benchmark:image-api

IMAGE_BENCHMARK_BASE_URL=https://your-preview.vercel.app \
IMAGE_BENCHMARK_MODE=image IMAGE_BENCHMARK_VARIANT=thumbnail \
IMAGE_BENCHMARK_ROOM_ID="$ROOM_ID" IMAGE_BENCHMARK_COUPON_ID="$COUPON_ID" \
IMAGE_BENCHMARK_ID_TOKEN="$FIREBASE_ID_TOKEN" npm run benchmark:image-api
```

Vercel Cron은 `backend/vercel.json`의 `0 0 * * *` 스케줄을 사용합니다. UTC 00:00은 Asia/Seoul 기준 09:00입니다.
같은 날짜의 중복 실행은 Firestore `cronLeases`의 6분 lease로 차단하고, 수신자별 알림은 결정적 ID의 `notificationOutbox`에 먼저 기록합니다. 전송은 2분 lease로 claim하며 일시적 FCM 실패를 최대 5회 지수 backoff 후 dead letter 처리합니다. 클라이언트는 lease/outbox 문서를 읽거나 쓸 수 없습니다.
서버 운영자는 `CRON_SECRET` 인증이 필요한 `GET /api/notifications/status`에서 알림, 이미지 Blob 정리 큐, 30일 복구함의 상태별 건수와 최근 Cron 결과를 확인할 수 있습니다. 완료된 outbox·notification log·Blob cleanup 기록은 일 1회 최대 각 200개씩 정리하고, 보관 기한이 지난 쿠폰은 일 100개씩 영구 삭제합니다.

## Android 실행

1. Android Studio에서 `android/` 폴더를 엽니다.
2. `android/local.properties`에 필요 값을 설정합니다.

```properties
sdk.dir=C:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
apiBaseUrl=https://your-vercel-project.vercel.app
googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

3. `android/app/google-services.json`을 배치합니다.
4. Android Studio에서 Gradle Sync 후 앱을 실행합니다. CLI 빌드는 저장소의 Gradle Wrapper를 사용합니다.

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :benchmark:assembleBenchmark
```

디버그 APK는 `android/app/build/outputs/apk/debug/giftcondoctor-{versionName}-{versionCode}-debug.apk` 형식으로 생성됩니다.

Android 사진 앱이나 갤러리에서 최대 10장을 선택하거나 공유할 수 있습니다. 외부 공유 파일은 장당 최대 10MB·묶음 전체 50MB의 앱 전용 cache로 64KB씩 복사하며, 로그인 전이라면 로그인 뒤 등록할 쿠폰방을 선택합니다. 등록 화면에서는 `1/N` 순서대로 OCR·바코드·만료일을 확인한 뒤 한 장씩 저장하므로 잘못 읽은 내용을 자동으로 일괄 저장하지 않습니다. 잘못 고른 현재 이미지는 확인 후 제외할 수 있고, 뒤로가기에서는 남은 묶음 취소를 확인합니다. 등록 상태 객체는 묶음당 1개만 재사용하되 이미지별 자동 입력은 다음 장으로 이동할 때 초기화합니다.

에뮬레이터에서 푸시 token이 생성되지 않고 `Firebase Installations Service is unavailable`이 보이면 앱 설정보다 DNS를 먼저 확인합니다.

```bash
adb -s emulator-5556 shell ping -c 1 8.8.8.8
adb -s emulator-5556 shell ping -c 1 firebaseinstallations.googleapis.com
adb -s emulator-5556 logcat -d | rg "FirebaseMessaging|FirebaseInstallations|SERVICE_NOT_AVAILABLE"
```

IP 연결은 되고 도메인만 실패하면 AVD를 정상 DNS로 재시작한 뒤 FCM token 생성을 다시 확인합니다.

이 저장소에는 secret 파일을 포함하지 않습니다.

## Android Release 자동화

GitHub Actions의 `Android Release APK` workflow는 기본 `signing-dry-run` 모드에서 선택한 branch의 테스트·빌드·서명·인증서 검증과 임시 artifact 업로드만 수행합니다. tag, GitHub Release, 대시보드는 변경하지 않습니다. 실제 `release` 모드는 `main`에서만 허용되며, 검증된 APK로 GitHub Release를 발행한 뒤 성공한 Release를 기준으로 `wjdals988/mydashboard`의 APK 메타데이터를 별도 job에서 갱신합니다.

현재 공식 `v0.1.12`의 기존 release keystore는 복구되지 않았고 `0.1.20 (21)`부터 새 인증서를 사용합니다. 따라서 기존 앱을 덮어쓸 수 없으며, 기존 사용자는 가능하면 먼저 로그아웃한 뒤 기존 앱 삭제 → 새 APK 설치 → 재로그인이 필요합니다. 앱 삭제 시 로컬 데이터와 권한은 지워지고 Firebase 계정 데이터는 같은 계정으로 다시 조회합니다. 인증서 지문, 검증 상태와 전환 절차는 [`RELEASE_SIGNING.md`](RELEASE_SIGNING.md)를 따릅니다.

앱 저장소 GitHub Secrets에 아래 값을 설정해야 합니다.

- `GOOGLE_SERVICES_JSON_BASE64`: `android/app/google-services.json`의 base64 값
- `ANDROID_GOOGLE_WEB_CLIENT_ID`: Android Google 로그인 Web client ID
- `ANDROID_RELEASE_KEYSTORE_BASE64`: release keystore `.jks`의 base64 값
- `ANDROID_RELEASE_STORE_PASSWORD`: keystore 비밀번호
- `ANDROID_RELEASE_KEY_ALIAS`: release key alias
- `ANDROID_RELEASE_KEY_PASSWORD`: release key 비밀번호
- `DASHBOARD_UPDATE_TOKEN`: `wjdals988/mydashboard` contents read/write 권한 토큰

선택 값으로 GitHub Actions Variables의 `ANDROID_API_BASE_URL`을 설정할 수 있지만 release 서명 workflow에서는 `https://giftcondoctor.vercel.app`와 정확히 같아야 합니다. 없으면 이 production URL을 사용합니다.

동일한 Release 또는 tag가 있으면 실패합니다. 공개 버전은 덮어쓰거나 이동하지 않으며, 정정이 필요하면 `versionName`과 `versionCode`를 모두 증가시켜 새 버전으로 발행합니다. `production` environment에 승인 규칙을 설정하는 것을 권장합니다.

## CI 검증

PR과 `main` push에서는 다음 항목을 자동 검증합니다.

- Backend 단위 테스트, Firestore Rules Emulator 테스트, TypeScript 검사, 프로덕션 빌드
- 프로덕션 의존성의 High/Critical 취약점 차단
- Android Gradle Wrapper 검증, 단위 테스트, debug APK·R8 release·benchmark APK 빌드

복구함 cursor paging은 `firebase/firestore.indexes.json`의 composite index 2개를 추가로 요구합니다. Backend/App 배포 전에 인덱스를 먼저 배포하고 생성 완료 후 실제 복구함 쿼리를 확인해야 합니다.
운영 Rules·Indexes 장애 복구는 임의 로컬 파일을 직접 배포하지 않고 [`FIREBASE_ROLLBACK.md`](FIREBASE_ROLLBACK.md)의 `main` 조상 commit 기반 workflow를 사용합니다. 기본 실행은 Firebase에 접속하지 않는 검증 전용입니다.

## 보안 원칙

- 쿠폰 이미지는 Vercel Blob Private Storage에 저장합니다.
- Firestore에는 Blob path와 메타데이터만 저장합니다.
- Android 클라이언트는 이미지 URL을 직접 받지 않고, Firebase ID token이 포함된 인증 API를 통해 이미지를 읽습니다.
- Firestore rules는 비멤버의 room/coupon 접근을 차단합니다.
- 이미지 업로드는 JPEG, PNG, WebP magic byte를 검증하고 쿠폰별 Blob 경로에 묶습니다.
- 등록 시 512px 이하 비공개 WebP 썸네일을 함께 만들며, 목록은 썸네일을 우선 사용하고 기존 쿠폰은 원본으로 호환합니다.
- 기존 쿠폰은 인증된 목록 조회 시 전용 POST API가 쿠폰별 고정 썸네일을 1회 생성하며, 실패하면 원본 fallback으로 표시합니다.
- 쿠폰 목록은 공개·본인 비공개 쿠폰을 각각 12개씩 cursor로 조회하고, 스크롤 끝 4개 전에 다음 페이지를 가져옵니다.
- 복구함은 20개 단위 cursor paging을 사용합니다. 일반 멤버는 본인 삭제 쿠폰만, 방장은 본인 삭제 쿠폰과 원래 공개였던 쿠폰만 서버 쿼리 단계에서 합치며 타인의 비공개 쿠폰 ID를 cursor에 노출하지 않습니다.
- Android 업로드는 원본 전체를 메모리에 복사하지 않고 64KB 버퍼로 전송합니다. 서버도 magic byte에 필요한 앞 12바이트만 검사한 뒤 원본 `File`의 Blob 전송을 시작하고, 전체 `Buffer` 복사는 썸네일 생성에만 사용합니다. 신규 등록은 이미지 선택 직후 OCR·바코드 분석과 업로드 준비를 독립적으로 병렬 실행하며, 분석이 먼저 끝나면 전처리를 기다리지 않고 자동 입력값을 표시합니다. 이미지 교체는 확인 단계와 업로드 준비를 병렬 실행합니다. 1.5MB 이상 또는 2,560px 초과 입력은 JPEG 품질 92로 준비해 10% 이상 작을 때만 전송합니다. 준비·진행률·취소·정리·서버 저장 단계와 최적화 전후 용량을 표시합니다. upload session ID와 다음 일일 Cron용 선생성 cleanup 안전망으로 응답을 잃어도 후보 경로를 추적하고, 서버는 live-reference를 확인해 미사용 Blob만 정리합니다.
- 등록 성공 후 상세 화면에서 완료 스낵바를 보여 주며 `하나 더 등록`으로 연속 등록을 바로 시작할 수 있습니다.
- 사용 완료 직후에는 `실행 취소`를 제공하며 Firestore Rules가 5분 이내 처리자 본인의 복원만 허용합니다.
- 쿠폰 삭제는 이미지·댓글·기존 상태를 30일 보존하는 복구함으로 이동하며 목록에서 즉시 실행 취소할 수 있습니다. 공개 쿠폰은 등록자와 방장, 비공개 쿠폰은 등록자만 복원하거나 영구 삭제할 수 있습니다. 보관 기한이 지나면 Cron이 문서·댓글을 삭제하고 Blob cleanup queue로 이미지 정리를 보장합니다.
- 목록 썸네일은 UID·쿠폰·Blob 경로별 압축 데이터 2~8MB LRU와 표시 크기별 bitmap 4~24MB LRU를 사용합니다. 같은 이미지의 동시 요청은 크기와 무관하게 참조 수 gate로 fetch 1회에 병합하고, 목록→상세 전환은 압축 cache에서 다시 디코딩합니다. emulator-5556 내부 경로 24개 기준 최신 중앙값은 첫 조회 209.043ms, bitmap 재조회 18.848ms이며 실제 네트워크 표시 시간은 아닙니다.
- 상세 원본은 최대 10MB `ByteArray`로 ViewModel에 보관하지 않고 앱 전용 cache 파일에 64KB씩 스트리밍합니다. 화면 종료·취소·로그아웃·다음 앱 시작 때 삭제하며, 파일에서 표시 크기로 직접 디코딩합니다.
- 썸네일이 있는 쿠폰은 상세 화면만 열었을 때 원본을 받지 않습니다. 확대를 탭하거나 썸네일 표시가 실패할 때만 원본을 요청하고, 준비 중 확대를 닫으면 다운로드도 취소합니다.
- 쿠폰 등록자는 새 이미지 미리보기 후 원본·썸네일을 교체할 수 있으며 서버가 동시 수정 충돌과 실패 시 새 Blob 정리를 처리합니다. 이미지 교체·쿠폰 영구 삭제·방 삭제의 Blob은 영속 cleanup queue에 기록하고, 현재 문서가 참조 중인 경로는 삭제하지 않습니다. 일시 실패는 최대 5회 재시도한 뒤 dead-letter와 운영 health에 노출합니다.
- 전체화면 쿠폰은 표시 중에만 최대 밝기와 화면 켜짐을 적용하며 닫으면 사용자의 이전 window 상태를 복원합니다.
- 전체화면 확대는 썸네일 미리보기로 즉시 열고 화면 크기 1× 원본을 먼저 준비합니다. 핀치·확대 버튼·더블탭을 사용할 때만 2× 원본을 추가 준비하며, 한 번 탭하면 안내와 조작부를 숨기거나 다시 표시할 수 있습니다. 원본 실패 시 미리보기를 유지하면서 재시도할 수 있습니다.
- OCR은 Android ML Kit의 한국어 텍스트 인식을 사용하며, 인식 결과는 사용자가 확인·수정한 뒤 저장합니다.
- 바코드는 기존 ZXing으로 오프라인 감지하며 저장 전 값 수정·제외와 6개 주요 형식의 직접 입력이 가능합니다. 1D/2D 길이·EAN/UPC 숫자 형식을 앱과 Rules에서 검사하고, 저장된 코드는 계산대용 흰 배경 전체화면으로 재생성합니다. 이미지 교체 시 오래된 바코드 값은 제거합니다.
- Firebase Storage, Firebase Cloud Functions, SMS는 MVP에서 사용하지 않습니다.
