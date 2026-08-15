# Android 릴리스 서명 관리

기준일: 2026-08-15

## 현재 상태

- 마지막 공식 APK: `0.1.12 (13)`
- 기존 공식 APK 인증서 SHA-256: `4a8a6cc7b3af9d46846922a1a7bcc0843a49485530528a7cf30a1d3371401fc4`
- 기존 공식 release keystore: 분실되어 복구되지 않음
- 새 release key alias: `giftcondoctor-release` (RSA 4096bit)
- 새 인증서 SHA-1: `0a1dc141c98c9b76cc1a6ec9af1096619cc36ac9`
- 새 인증서 SHA-256: `3b4a7f1f68e5c4977fd5fe625534529c86d8f025770def4119bce4451c8fb0ad`
- 다음 배포 후보: `0.1.29 (30)`
- 최신 GitHub signed R8 APK: `0.1.28 (29)`, 47,012,293 bytes, SHA-256 `f1569432c76004974696615fa9af3c59f347edc267eb095f8fd0243bc4e4f266`
- GitHub Actions Secrets 6개: signing dry-run `31884412198`에서 값 조합, package·버전·production API와 새 인증서 일치 확인
- `v0.1.29` tag와 GitHub Release: 실기기 로그인·FCM 회귀와 PR 검토 전까지 생성 금지

인증서 지문은 공개 검증값이며 keystore와 비밀번호는 Git, 문서, 채팅에 기록하지 않습니다. 새 키 후보나 백업 사본을 복원할 때는 다음 명령의 SHA-256이 위의 새 인증서 지문과 정확히 같은지 확인합니다.

```bash
keytool -list -v -keystore /path/to/candidate.jks
```

## v0.1.12 사용자 전환 절차

새 키로 같은 `applicationId`를 서명한 `0.1.20`은 기존 `0.1.12` 위에 덮어쓸 수 없습니다. 설치 관리자의 서명 불일치 오류는 정상적인 보안 차단이며 우회하지 않습니다.

1. 가능하면 기존 앱에서 먼저 로그아웃합니다.
2. 기존 `0.1.12` 앱을 삭제합니다.
3. 공식 Release의 새 signed APK를 설치합니다.
4. 기존에 사용하던 Google 또는 이메일 계정으로 다시 로그인합니다.
5. 알림 권한을 다시 허용하고 설정의 푸시 연결 테스트를 실행합니다.

앱 삭제 시 단말의 로컬 앱 데이터와 권한은 삭제됩니다. Firebase에 저장된 방·쿠폰은 같은 계정으로 재로그인하면 다시 조회할 수 있습니다. 단, `0.1.12`의 로그아웃은 서버 FCM token 제거를 보장하지 않으므로 이전 token은 서버의 invalid-token 정리 대상이며, 교차 계정 알림이 없는지 출시 전 별도로 검증합니다.

## 새 키 전환 진행 상태

1. [완료] 새 release keystore 생성과 별도 백업
2. [완료] SHA-1·SHA-256을 Firebase Android 앱에 등록
3. [완료] 새 Android OAuth client와 `google-services.json` 일치 확인
4. [완료] 로컬 signed APK의 인증서·v2/v3 서명·ZIP alignment 확인
5. [완료] GitHub Actions signing dry-run `31884412198`에서 Secrets 6개, `0.1.28 (29)`, 인증서 SHA-256, v2/v3 서명과 ZIP alignment 검증
6. [대기] 기존 앱이 없는 AVD 또는 물리 기기에서 Google 로그인·FCM·알림 4상태·딥링크·로그아웃 token 삭제 확인
7. [대기] 전체 테스트와 PR 검토 완료 후 `main` 병합
8. [대기] GitHub Release 생성 후 대시보드의 APK URL·버전·SHA-256·설명을 같은 버전으로 갱신

`Android Release APK` workflow의 기본 모드는 `signing-dry-run`입니다. 이 모드는 테스트·빌드·서명·인증서 검증과 7일 보관 artifact 업로드까지만 수행하고 tag, GitHub Release, 대시보드를 변경하지 않습니다. `release` 모드는 `main`에서만 허용되며 production environment 승인 뒤 실제 배포를 수행합니다.

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
