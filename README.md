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
npm install
npm run test
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
4. Android Studio에서 Gradle Sync 후 앱을 실행합니다. CLI 빌드는 Android Studio가 생성한/설치한 Gradle을 사용합니다.

```bash
cd android
gradle :app:assembleDebug
```

디버그 APK는 `android/app/build/outputs/apk/debug/giftcondoctor-{versionName}-{versionCode}-debug.apk` 형식으로 생성됩니다.

이 저장소에는 secret 파일을 포함하지 않습니다.

## 보안 원칙

- 쿠폰 이미지는 Vercel Blob Private Storage에 저장합니다.
- Firestore에는 Blob path와 메타데이터만 저장합니다.
- Android 클라이언트는 이미지 URL을 직접 받지 않고, Firebase ID token이 포함된 인증 API를 통해 이미지를 읽습니다.
- Firestore rules는 비멤버의 room/coupon 접근을 차단합니다.
- Firebase Storage, Firebase Cloud Functions, OCR, SMS는 MVP에서 사용하지 않습니다.
