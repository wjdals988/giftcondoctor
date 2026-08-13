# 기프티콘닥터 (giftcondoctor, GD)

Android-first MVP for Korean users to manage shared gifticon/coupon images in rooms and receive expiration push notifications.

## 구성

- `android/`: Kotlin, Jetpack Compose, Material 3 Android 앱
- `backend/`: Next.js App Router 기반 Vercel API Routes
- `firebase/`: Firestore rules/indexes
- `QA_CHECKLIST.md`: 수동 QA 체크리스트
- `TODO_v1.1.md`: MVP 이후 작업 목록

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

Vercel Cron은 `backend/vercel.json`의 `0 0 * * *` 스케줄을 사용합니다. UTC 00:00은 Asia/Seoul 기준 09:00입니다.

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
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

디버그 APK는 `android/app/build/outputs/apk/debug/giftcondoctor-{versionName}-{versionCode}-debug.apk` 형식으로 생성됩니다.

에뮬레이터에서 푸시 token이 생성되지 않고 `Firebase Installations Service is unavailable`이 보이면 앱 설정보다 DNS를 먼저 확인합니다.

```bash
adb -s emulator-5556 shell ping -c 1 8.8.8.8
adb -s emulator-5556 shell ping -c 1 firebaseinstallations.googleapis.com
adb -s emulator-5556 logcat -d | rg "FirebaseMessaging|FirebaseInstallations|SERVICE_NOT_AVAILABLE"
```

IP 연결은 되고 도메인만 실패하면 AVD를 정상 DNS로 재시작한 뒤 FCM token 생성을 다시 확인합니다.

이 저장소에는 secret 파일을 포함하지 않습니다.

## Android Release 자동화

GitHub Actions의 `Android Release APK` workflow는 `main`에서만 실행됩니다. 테스트·빌드·서명 검증을 먼저 끝낸 뒤 `v{versionName}` GitHub Release를 발행하고, 성공한 Release를 기준으로 `wjdals988/mydashboard`의 APK 메타데이터를 별도 job에서 갱신합니다.

앱 저장소 GitHub Secrets에 아래 값을 설정해야 합니다.

- `GOOGLE_SERVICES_JSON_BASE64`: `android/app/google-services.json`의 base64 값
- `ANDROID_GOOGLE_WEB_CLIENT_ID`: Android Google 로그인 Web client ID
- `ANDROID_RELEASE_KEYSTORE_BASE64`: release keystore `.jks`의 base64 값
- `ANDROID_RELEASE_STORE_PASSWORD`: keystore 비밀번호
- `ANDROID_RELEASE_KEY_ALIAS`: release key alias
- `ANDROID_RELEASE_KEY_PASSWORD`: release key 비밀번호
- `DASHBOARD_UPDATE_TOKEN`: `wjdals988/mydashboard` contents read/write 권한 토큰

선택 값으로 GitHub Actions Variables의 `ANDROID_API_BASE_URL`을 설정할 수 있습니다. 없으면 `https://giftcondoctor.vercel.app`를 사용합니다.

동일한 Release 또는 tag가 있으면 실패합니다. 공개 버전은 덮어쓰거나 이동하지 않으며, 정정이 필요하면 `versionName`과 `versionCode`를 모두 증가시켜 새 버전으로 발행합니다. `production` environment에 승인 규칙을 설정하는 것을 권장합니다.

## CI 검증

PR과 `main` push에서는 다음 항목을 자동 검증합니다.

- Backend 단위 테스트, Firestore Rules Emulator 테스트, TypeScript 검사, 프로덕션 빌드
- 프로덕션 의존성의 High/Critical 취약점 차단
- Android Gradle Wrapper 검증, 단위 테스트, debug APK 빌드

## 보안 원칙

- 쿠폰 이미지는 Vercel Blob Private Storage에 저장합니다.
- Firestore에는 Blob path와 메타데이터만 저장합니다.
- Android 클라이언트는 이미지 URL을 직접 받지 않고, Firebase ID token이 포함된 인증 API를 통해 이미지를 읽습니다.
- Firestore rules는 비멤버의 room/coupon 접근을 차단합니다.
- 이미지 업로드는 JPEG, PNG, WebP magic byte를 검증하고 쿠폰별 Blob 경로에 묶습니다.
- OCR은 Android ML Kit의 한국어 텍스트 인식을 사용하며, 인식 결과는 사용자가 확인·수정한 뒤 저장합니다.
- Firebase Storage, Firebase Cloud Functions, SMS는 MVP에서 사용하지 않습니다.
