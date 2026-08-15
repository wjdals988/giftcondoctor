# Firestore Rules·Indexes 롤백 운영 절차

## 목적

보안 Rules 또는 composite index 배포로 장애가 발생했을 때 검증된 `main` 이력의 파일만 다시 배포한다. 임의 브랜치 파일, 로컬 미커밋 파일과 외부 URL은 롤백 입력으로 허용하지 않는다.

## 사전 설정

GitHub 저장소에 아래 값을 등록한다.

- Actions variable `FIREBASE_PROJECT_ID`: 운영 Firebase project ID
- Actions secret `FIREBASE_SERVICE_ACCOUNT_BASE64`: 해당 프로젝트 배포용 service account JSON의 base64 값
- Actions environment `production`: 실제 배포 전 승인자와 deployment branch를 `main`으로 제한

서비스 계정에는 Rules 또는 Indexes 배포에 필요한 최소 권한만 부여한다. Android·Vercel 런타임 credential을 재사용하지 않는다.

## 실행

GitHub Actions에서 `Firestore Rules and Indexes Rollback`을 선택하고 반드시 `main`에서 실행한다.

1. `target_ref`에 복원할 과거 `main` commit SHA 또는 tag를 입력한다. 현재 `main`을 입력하면 과거 롤백 뒤 최신 상태로 복구하는 roll-forward가 된다.
2. `scope`를 선택한다.
   - `rules`: Rules만 복원하고 현재 Indexes 파일은 유지한다.
   - `rules-and-indexes`: Rules와 Indexes를 모두 대상 이력으로 되돌린다. 대상 파일에 없는 운영 Index가 삭제될 수 있다.
3. 첫 실행은 `execute=false`로 둔다. 이 경로는 운영 Firebase에 접속하지 않고 commit 제약, 파일 hash, diff, JSON, Firestore Emulator 보안 회귀만 검증한다.
4. Actions summary와 `firestore-rollback-{SHA}` artifact의 `manifest.json`, Rules, Indexes를 검토한다.
5. 실제 반영이 필요할 때만 `execute=true`와 아래 확인 문구를 정확히 입력한다.
   - Rules: `ROLLBACK FIRESTORE RULES`
   - Rules+Indexes: `ROLLBACK FIRESTORE RULES AND INDEXES`
6. `production` 승인을 거친 뒤 cloud dry-run과 실제 배포가 순서대로 실행된다. 완료 후 30일 보존되는 evidence artifact의 운영 Indexes 결과를 확인한다.

## 안전 경계

- 대상은 실행 시점 `main` commit 또는 조상 commit만 허용한다.
- 검증 artifact와 배포 artifact가 동일하고 배포 직전 manifest의 target/scope와 Rules·Indexes SHA-256을 다시 대조하므로 승인 뒤 파일 변형을 차단한다.
- Rules+Indexes만 `--force`를 사용하며 별도 확인 문구를 요구한다.
- 동시 실행을 1개로 제한하고 진행 중 롤백을 자동 취소하지 않는다.
- target Rules가 현재 보안 회귀 테스트를 통과하지 못하면 실제 배포 job이 생성되지 않는다.
- `--dry-run`도 운영 API를 활성화할 가능성이 있으므로 실제 실행은 production 승인 뒤에만 수행한다.

## 실패 시 복구

- 검증 실패: 대상 commit을 바꾸거나 호환성 수정 commit을 `main`에 먼저 반영한다. 테스트를 우회하지 않는다.
- cloud dry-run 실패: service account 권한, project ID, API 상태를 수정한 뒤 새 workflow run으로 다시 검증한다.
- Rules 배포 뒤 앱 장애: 장애 전 Rules commit 또는 현재 `main`을 대상으로 `rules` scope를 다시 실행한다.
- Indexes 배포 뒤 쿼리 장애: 기존 Index가 다시 준비되는 데 시간이 걸릴 수 있다. 현재 `main`을 `rules-and-indexes`로 roll-forward하고 Firebase Console에서 생성 완료 전까지 앱·Backend 배포를 중단한다.
