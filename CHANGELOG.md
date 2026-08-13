# 변경 이력

이 프로젝트는 공개된 버전을 덮어쓰지 않고 새 `versionName`과 `versionCode`로 발행합니다.

## Unreleased

### 보안

- Cron secret 누락 시 비인증 실행을 허용하지 않도록 fail-closed 처리
- JPEG, PNG, WebP magic byte 검증으로 취약한 범용 이미지 파서 제거
- 쿠폰 ID와 Blob 경로를 서버·Firestore Rules 양쪽에서 결합 검증
- 방 생성·가입, 이미지 업로드, 테스트 푸시 등에 사용자별 rate limit 적용
- 로그아웃 시 FCM token 문서와 로컬 token 정리
- Next.js 16.3.0, Firebase Admin 14.2.0, Vitest 4.1.10으로 갱신

### 안정성

- 로그인할 때 기존 알림 설정이 기본값으로 덮이는 문제 수정
- 공개·비공개 쿠폰 쿼리 병합 경쟁과 최신 댓글 100개 조회 오류 수정
- OCR에서 발행일보다 명시된 만료일을 우선하도록 개선
- 손상된 날짜 문서가 전체 쿠폰 목록을 중단하지 않도록 격리
- 쿠폰 문서 저장 실패 시 업로드 Blob을 보상 삭제
- foreground·cold start 알림 deep link와 인증 초기화 순서 보강
- 앱 시작 시 만료 알림 채널을 미리 생성해 첫 백그라운드 푸시의 fallback 의존 제거
- 만료 푸시를 `쿠폰 만료 D-n` 형식으로 간결하게 정리하고 테스트 푸시의 불필요한 10초 대기 제거
- 방 가입·탈퇴·멤버 제거를 transaction으로 처리해 `memberCount` 정합성 보강
- 소유 쿠폰이 남은 멤버의 탈퇴·제거를 차단해 고아 쿠폰 방지
- 예약 쿠폰은 예약자만 사용 완료할 수 있고 완료 시 예약자 정보를 정리하도록 보강
- Cron 부분 실패를 HTTP 500과 summary로 노출해 무음 실패 방지
- 푸시 연결 테스트에서 알림 권한, FCM 기기 등록, 서버 전송 실패를 구분해 안내

### UI/UX

- Google 로그인을 첫 핵심 CTA로 배치하고 이메일 로그인을 보조 흐름으로 정리
- 빈 쿠폰방 화면에 만들기와 초대코드 입장 행동을 직접 제공
- 방 상세의 작은 추가 아이콘을 라벨이 있는 `쿠폰 등록` FAB로 변경
- 쿠폰 등록을 이미지 선택, 자동 인식 결과 확인, 저장의 3단계로 단순화
- 공유·알림 옵션을 접을 수 있게 하고 만료일 빠른 선택 제공
- 운영용 푸시 테스트방 참가 기능을 홈에서 알림 설정의 진단 영역으로 이동

### 개발·배포

- Gradle 8.10.2 Wrapper와 배포 파일 SHA-256 검증 추가
- PR/main Backend·Android CI와 Firestore Rules Emulator 테스트 추가
- 릴리스를 build/test/sign 이후에 발행하고 dashboard 갱신을 별도 job으로 분리
- 기존 Release/tag 덮어쓰기 기능 제거
