# 기프티콘닥터 MVP 수동 QA 체크리스트

## 인증
- [ ] 이메일/비밀번호 회원가입 성공
- [ ] 이메일/비밀번호 로그인 성공
- [ ] Google 로그인 성공
- [ ] 로그인 후 `users/{uid}` 문서 생성/갱신 확인
- [ ] 로그아웃 후 로그인 화면 복귀

## 푸시 토큰/권한
- [ ] Android 13+에서 알림 권한 요청 UI 노출
- [ ] 권한 허용 후 `users/{uid}/pushTokens/{tokenId}` 저장
- [ ] FCM token refresh 시 같은 경로가 갱신됨
- [ ] 알림 설정에서 pushEnabled 변경 저장

## 방
- [ ] 방 생성 성공
- [ ] 방장 member 문서와 사용자 roomMembership 문서 생성
- [ ] 6자리 초대코드 생성
- [ ] 초대코드로 다른 계정 입장 성공
- [ ] 만료/오류 초대코드는 입장 실패
- [ ] 멤버 목록 표시
- [ ] 방장이 일반 멤버 제거 가능
- [ ] 일반 멤버 방 나가기 가능
- [ ] 방장 방 나가기는 MVP에서 차단

## 쿠폰
- [ ] 이미지 선택 후 쿠폰 추가 성공
- [ ] Firestore에는 public URL이 아니라 `imageBlobPath`만 저장
- [ ] 상세 화면에서 인증 API를 통해 이미지 표시
- [ ] 방 공개 쿠폰은 멤버가 목록/상세에서 확인
- [ ] private 쿠폰은 등록자만 목록/상세/이미지 접근
- [ ] 예약, 예약 취소, 사용 완료 상태 변경
- [ ] 쿠폰 수정 저장
- [ ] 쿠폰 삭제 시 Firestore 문서와 Blob 삭제

## 알림
- [ ] 기본 모드 `[7,3,1,0]` 저장
- [ ] 최소/기본/꼼꼼 모드 저장
- [ ] 방 기본 알림 override 저장
- [ ] 멤버별 방 알림 override 저장
- [ ] Cron 수동 호출이 summary JSON 반환
- [ ] 동일 쿠폰/날짜/일수는 notificationLogs로 중복 발송 방지
- [ ] 알림 탭 시 `CouponDetailScreen`으로 이동

## 보안
- [ ] 비로그인 API 요청은 401
- [ ] 비멤버 이미지 조회는 403
- [ ] 비멤버 room/coupon read 차단
- [ ] private 쿠폰은 방장이라도 등록자가 아니면 이미지 조회 불가
- [ ] `.env*`, service account JSON, `google-services.json` 미커밋
