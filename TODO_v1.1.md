# v1.1 TODO

## P0 — 출시 전 차단 항목

- 물리 디바이스 푸시 6개 상태 조합과 A→B 교차 계정 개인정보 회귀 검증
- `main` branch protection과 Backend·Android 필수 check 강제
- Firestore Rules·Indexes rollback 경로 자동화
- Cron 알림 발송 lease/outbox와 중복·유실 관측 지표
- Firebase App Check와 UID+IP 기반 abuse 방어

## P1 — 실제 사용 신뢰성

- 방장 이전 플로우
- 초대 링크 공유 UI와 초대코드 재발급 감사 로그
- 쿠폰 이미지 교체/재업로드
- 쿠폰 만료 상태 자동 갱신 배치
- 오프라인 캐시/동기화 충돌 UX 개선
- 최근 쿠폰 암호화 로컬 캐시와 네트워크 없는 매장 사용 경로
- 알림 quiet hours와 사용자별 발송 시간 설정
- 이메일 없는 Google 계정 표시명 보강
- OCR 다중 날짜·저화질 이미지 정확도 개선
- Android instrumented UI 테스트
- 삭제 cleanup 재시도 구조
- 방 탈퇴 전 쿠폰 소유권 이전 플로우
- 쿠폰 상세 바코드 감지·재생성, 전체 화면 보기와 화면 밝기 임시 상승
- 사용 완료·삭제 실행 취소 피드백
- Android 공유 시트와 갤러리 다중 선택 등록
- 스크린샷 일괄 OCR과 중복 쿠폰 감지

## P2 — 탐색·확장

- 웹 관리자/운영 대시보드
- 즐겨찾기, 검색, 최근 사용 정렬과 전역 `내 쿠폰` 정보 구조 실험
- 즐겨찾기 쿠폰 홈 위젯과 Wear OS 접근
- 컬러·간격·모서리 디자인 토큰과 다크모드 시각 회귀 테스트

## 완료된 기반 작업

- [x] OCR 기반 쿠폰 정보 자동 입력
- [x] Firestore Rules Emulator 보안 테스트
- [x] 사용자별 API rate limit 기본 방어
- [x] PR/main Backend·Android CI
- [x] 첫 쿠폰방 빈 상태와 쿠폰 등록 3단계 UI 단순화
- [x] 푸시 권한·FCM 등록·서버 전송 단계별 오류 안내
- [x] Firestore Rules·Indexes 프로덕션 배포와 운영 인덱스 5개 확인
- [x] Vercel 운영 배포와 `/api/health` 200 스모크 확인
- [x] 백그라운드 FCM deep link extra 복원과 앱 전용 URI 검증
- [x] GitHub Actions Node 24 전환과 check annotation 0건 확인
- [x] 쿠폰 이미지 샘플 디코딩·UID별 4~24MB LRU 캐시
- [x] 512px 비공개 WebP 썸네일 API·목록 우선 로딩·원본 fallback
- [x] 기존 쿠폰 인증 POST 기반 1회성 썸네일 백필·동시 요청 고정 경로 수렴
- [x] 공개·본인 비공개 쿠폰 12개 단위 cursor paging·끝 4개 전 선조회·오류 재시도
- [x] Android 이미지 스트리밍 업로드·진행률·저장 단계 피드백
- [x] 상세 이미지 8MP 상한 고해상도 지연 디코딩·핀치/더블탭 확대
- [x] 쿠폰 검색·상태 필터·만료 임박순 정렬
- [x] 사용 완료·쿠폰 삭제 확인 절차
- [x] 댓글·쿠폰 수정의 서버 성공 후 화면 상태 전환
- [x] 쿠폰 등록·수정 만료일 달력 선택기
