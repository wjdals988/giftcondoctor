# Android 릴리스 서명 관리

기준일: 2026-08-15

## 현재 상태

- 마지막 공식 APK: `0.1.12 (13)`
- 공식 APK 인증서 SHA-256: `4a8a6cc7b3af9d46846922a1a7bcc0843a49485530528a7cf30a1d3371401fc4`
- 기존 release keystore: 복구되지 않음
- 로컬에서 확인된 `debug.keystore`: 공식 인증서와 불일치
- 다음 개발 버전: `0.1.15 (16)`
- `v0.1.15` tag와 GitHub Release: 생성 금지 상태

APK에서 공개 인증서 지문은 확인할 수 있지만 개인키를 복구할 수는 없습니다. 후보 keystore를 찾으면 다음 명령의 SHA-256이 공식 지문과 정확히 같은지 확인합니다.

```bash
keytool -list -v -keystore /path/to/candidate.jks
```

## 기존 키를 복구하지 못할 때

새 키로 같은 `applicationId`를 서명하면 기존 설치본 위에 업데이트할 수 없습니다. 사용자는 기존 앱을 삭제한 뒤 새 APK를 설치해야 하며, 로컬 앱 데이터는 삭제될 수 있습니다. Firebase에 저장된 방과 쿠폰은 같은 계정으로 로그인하면 다시 조회할 수 있습니다.

새 서명키 전환은 아래 순서로 진행합니다.

1. 대시보드와 Release Notes에 재설치 필요성을 공지합니다.
2. 새 release keystore를 대화형 `keytool -genkeypair`로 생성합니다.
3. SHA-1과 SHA-256을 Firebase Android 앱에 등록합니다.
4. 새 `google-services.json`과 Google 로그인 OAuth 설정을 검증합니다.
5. debug가 아닌 signed release APK로 로그인·방 조회·쿠폰 이미지·FCM을 확인합니다.
6. 기존 앱 삭제 후 설치 시나리오를 별도 검증합니다.
7. 검증이 끝난 뒤에만 GitHub Release와 대시보드 APK 링크를 갱신합니다.

## 키와 비밀번호 보관 원칙

- keystore와 비밀번호를 Git 또는 APK에 포함하지 않습니다.
- 최소 3개 사본, 2개 매체, 1개 외부 보관소의 3-2-1 원칙을 적용합니다.
- 권장 위치: 암호화된 비밀번호 관리자 첨부파일, 암호화 외장 저장장치, 별도 암호화 클라우드 보관소.
- keystore와 비밀번호 복구 문서는 서로 다른 위치에 보관합니다.
- 복원 시험으로 백업 사본의 인증서 SHA-256을 분기마다 비교합니다.

## GitHub Actions Secrets

- `GOOGLE_SERVICES_JSON_BASE64`
- `ANDROID_GOOGLE_WEB_CLIENT_ID`
- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`
- `DASHBOARD_UPDATE_TOKEN`

GitHub Secrets는 저장 후 원문을 다시 내려받을 수 없으므로 GitHub만 유일한 백업 장소로 사용하면 안 됩니다.
