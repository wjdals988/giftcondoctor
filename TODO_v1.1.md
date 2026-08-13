# v1.1 TODO

## 남은 작업

- 방장 이전 플로우
- 초대 링크 공유 UI와 초대코드 재발급 감사 로그
- 쿠폰 이미지 교체/재업로드
- 쿠폰 만료 상태 자동 갱신 배치
- 오프라인 캐시/동기화 충돌 UX 개선
- 알림 quiet hours와 사용자별 발송 시간 설정
- 이메일 없는 Google 계정 표시명 보강
- OCR 다중 날짜·저화질 이미지 정확도 개선
- 웹 관리자/운영 대시보드
- Android instrumented UI 테스트
- Firebase App Check와 IP 기반 abuse 방어
- 쿠폰 목록 썸네일 API·캐시·paging으로 이미지 메모리 사용량 개선
- Cron 알림 발송 lease/outbox와 삭제 cleanup 재시도 구조
- 방 탈퇴 전 쿠폰 소유권 이전 플로우
- 쿠폰 상세 바코드 우선 보기와 화면 밝기 임시 상승
- 사용 완료·삭제 확인 및 실행 취소 피드백
- 쿠폰 상태 필터, 검색, 만료 임박순 정렬
- Android 공유 시트와 갤러리 다중 선택 등록

## 완료된 기반 작업

- [x] OCR 기반 쿠폰 정보 자동 입력
- [x] Firestore Rules Emulator 보안 테스트
- [x] 사용자별 API rate limit 기본 방어
- [x] PR/main Backend·Android CI
- [x] 첫 쿠폰방 빈 상태와 쿠폰 등록 3단계 UI 단순화
- [x] 푸시 권한·FCM 등록·서버 전송 단계별 오류 안내
