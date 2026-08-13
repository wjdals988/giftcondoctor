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

### 개발·배포

- Gradle 8.10.2 Wrapper와 배포 파일 SHA-256 검증 추가
- PR/main Backend·Android CI와 Firestore Rules Emulator 테스트 추가
- 릴리스를 build/test/sign 이후에 발행하고 dashboard 갱신을 별도 job으로 분리
- 기존 Release/tag 덮어쓰기 기능 제거
