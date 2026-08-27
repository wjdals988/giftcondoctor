# 대시보드 APK 등재 절차

기준일: 2026-08-28
대상 대시보드: <https://coldbrewventi.vercel.app>
데이터 원본: `wjdals988/mydashboard` 저장소의 `src/lib/projects.json`
프로젝트 slug: `gifticon-doctor`

## 현재 상태

| 항목 | 값 |
| --- | --- |
| 대시보드 등재 버전 | `0.1.12 (13)` |
| `main` 최신 버전 | `0.1.31 (32)` |
| 격차 | 19개 버전 |
| GitHub Release 최신 | `v0.1.12` (2026-05-22) |
| `DASHBOARD_UPDATE_TOKEN` | 미등록 |

## 경로가 두 개인 이유

`Android Release APK` workflow의 `dashboard` job은 `DASHBOARD_UPDATE_TOKEN`을 요구하고, 이 토큰이 없으면 `mode=release` 실행이 시크릿 검증 단계에서 즉시 실패한다. 반면 `honeymoondoctor`의 `동행일기 v0.7.10`~`v0.7.16` 등재 커밋 작성자는 `github-actions[bot]`이 아니라 `jeongmin2`다. 즉 기존 등재는 workflow가 아니라 로컬 수동 경로로 이루어졌다.

두 경로 모두 최종 결과(`projects.json` 갱신)는 같고, 차이는 자동화 여부와 토큰 필요 여부뿐이다.

## 경로 A — 수동 등재 (honeymoondoctor와 동일, 토큰 불필요)

`DASHBOARD_UPDATE_TOKEN` 없이 진행할 수 있다. 대시보드 저장소 push 권한이 있는 계정으로 로그인된 `gh` 인증만 필요하다.

1. **서명 APK 확보**

   `Android Release APK` workflow를 `signing-dry-run` 모드로 실행한다. 이 모드는 tag·Release·대시보드를 건드리지 않고 서명·인증서·ZIP alignment 검증과 artifact 업로드까지만 수행한다.

   ```bash
   gh workflow run "Android Release APK" --repo wjdals988/giftcondoctor -f mode=signing-dry-run
   ```

   실행이 끝나면 artifact를 내려받는다. artifact 보관 기간은 7일이므로 기간이 지난 run은 재실행해야 한다.

   ```bash
   gh run download <RUN_ID> --repo wjdals988/giftcondoctor --dir ./dist
   ```

2. **APK 메타데이터 계산**

   ```bash
   shasum -a 256 ./dist/*/giftcondoctor-0.1.31-32-release-signed.apk
   stat -f%z ./dist/*/giftcondoctor-0.1.31-32-release-signed.apk
   ```

   SHA-256이 `RELEASE_SIGNING.md`에 기록된 값과 일치하는지, 인증서 SHA-256이 `3b4a7f1f68e5c4977fd5fe625534529c86d8f025770def4119bce4451c8fb0ad`인지 확인한다.

3. **GitHub Release 발행**

   ```bash
   gh release create v0.1.31 \
     --repo wjdals988/giftcondoctor \
     --title "기프티콘닥터 v0.1.31" \
     --notes-file <릴리스노트파일> \
     ./dist/*/giftcondoctor-0.1.31-32-release-signed.apk
   ```

   이 단계에서 APK가 공개된다. 되돌리려면 Release와 tag를 모두 삭제해야 하므로 이전 단계 검증을 끝낸 뒤에만 실행한다.

4. **대시보드 갱신**

   ```bash
   git clone https://github.com/wjdals988/mydashboard.git
   cd mydashboard && npm ci
   npm run update:apk -- \
     --slug gifticon-doctor \
     --url "https://github.com/wjdals988/giftcondoctor/releases/download/v0.1.31/giftcondoctor-0.1.31-32-release-signed.apk" \
     --fileName "giftcondoctor-0.1.31-32-release-signed.apk" \
     --version "0.1.31" \
     --versionCode 32 \
     --size "<바이트수> bytes" \
     --sha256 "<SHA256>" \
     --releaseUrl "https://github.com/wjdals988/giftcondoctor/releases/tag/v0.1.31"
   npm test
   git add src/lib/projects.json
   git commit -m "chore: 기프티콘닥터 v0.1.31(32) APK 등재"
   git push
   ```

5. **배포 확인**

   Vercel 재배포 후 <https://coldbrewventi.vercel.app>에서 버전·크기·SHA-256·다운로드 링크가 새 값인지 확인한다.

## 경로 B — workflow 자동 등재 (토큰 필요, 권장)

1. `mydashboard` 저장소에 `Contents: Read and write` 권한만 가진 fine-grained PAT를 발급한다. 만료일을 설정하고 권한 범위를 해당 저장소로 한정한다.
2. `giftcondoctor` 저장소 시크릿에 `DASHBOARD_UPDATE_TOKEN`으로 등록한다.

   ```bash
   gh secret set DASHBOARD_UPDATE_TOKEN --repo wjdals988/giftcondoctor
   ```

3. `signing-dry-run`으로 서명 검증을 먼저 통과시킨다.
4. `mode=release`로 실행한다. `main`에서만 허용되고 production environment 승인이 필요하다. 이 한 번의 실행이 tag 생성, GitHub Release 발행, `projects.json` 갱신 push까지 모두 수행한다.

   ```bash
   gh workflow run "Android Release APK" --repo wjdals988/giftcondoctor -f mode=release --ref main
   ```

경로 B는 SHA-256·크기·URL을 workflow 출력값에서 그대로 주입하므로 사람이 값을 옮겨 적을 때 생기는 불일치가 구조적으로 발생하지 않는다. 장기적으로는 경로 B로 수렴해야 한다.

## 등재 전 게이트

대시보드 등재는 공개 배포와 같다. 아래 두 항목이 `RELEASE_SIGNING.md`에서 여전히 `[대기]`다.

- 실기기에서 Google 로그인, FCM 토큰 발급, 알림 4상태, 딥링크, 로그아웃 시 토큰 삭제 회귀
- 새 키 전환 안내 — 기존 `0.1.12` 사용자는 인증서가 달라 덮어쓰기 설치가 불가능하다. 삭제 후 재설치가 필요하다는 안내를 Release 노트와 대시보드 설명에 함께 넣어야 한다

두 번째 항목을 빠뜨리면 기존 사용자는 원인 불명의 설치 실패를 겪는다. 2026-08-28 로컬 검증에서도 같은 차단이 재현됐다(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
