# v1.1 TODO

## P0 — 출시 전 차단 항목

- 물리 디바이스 푸시 6개 상태 조합과 A→B 교차 계정 개인정보 회귀 검증
- `main` branch protection과 Backend·Android 필수 check 강제
- Firestore rollback workflow를 `main` 병합한 뒤 검증 전용 run과 production Rules roll-forward smoke 실행
- 복구함 cursor query용 composite index 2개를 프로덕션에 선배포하고 실제 쿼리 smoke 확인
- [x] Cron 6분 lease, 수신자별 outbox, 2분 delivery claim, 일시 실패 최대 5회 지수 backoff
- [x] notificationOutbox 상태별 건수, retry/deadLetter, 최장 대기시간, 최근 Cron 결과 조회와 30일 보존 정리
- notificationOutbox `warning/critical` 상태를 외부 운영 경보 채널로 전달
- Firebase App Check와 UID+IP 기반 abuse 방어
- Firebase Admin의 Cloud Storage→`uuid` Moderate advisory 6건이 상위 버전에서 해소되는지 추적

## P1 — 실제 사용 신뢰성

- 방장 이전 플로우
- 초대 링크 공유 UI와 초대코드 재발급 감사 로그
- [x] 등록자 전용 쿠폰 이미지 교체/재업로드, 동시 수정 충돌 방어와 실패 보상 삭제
- 쿠폰 만료 상태 자동 갱신 배치
- 오프라인 캐시/동기화 충돌 UX 개선
- 최근 쿠폰 암호화 로컬 캐시와 네트워크 없는 매장 사용 경로
- 알림 quiet hours와 사용자별 발송 시간 설정
- 이메일 없는 Google 계정 표시명 보강
- OCR 다중 날짜·저화질 이미지 정확도 개선
- [x] 100개 쿠폰 목록·고해상도 확대 Android instrumented UI 테스트
- [x] 검색·필터·달력 Android instrumented UI 테스트 확대
- [x] 핵심 쿠폰 목록의 200% 글꼴·48dp 터치 영역·접힘/펼침 접근성 회귀
- [x] 로그인·알림 설정·방장/멤버 설정·멤버 제거의 200% 글꼴·48dp·semantics 자동 회귀
- 실제 TalkBack 수동 탐색과 Accessibility Scanner로 로그인·알림·회원 관리 전수 검증
- [x] 이미지 교체·쿠폰 삭제·방 삭제 Blob cleanup queue, live-reference 방어·5회 재시도·lease·dead-letter·운영 상태 집계
- 방 탈퇴 전 쿠폰 소유권 이전 플로우
- [x] 전체화면 쿠폰의 최대 밝기·화면 켜짐과 닫을 때 기존 상태 복원
- [x] 쿠폰 이미지 바코드 감지·저장 전 값 수정/제외·전체화면 재생성·이미지 교체 시 stale 값 제거
- [x] 자동 감지 실패 시 6개 주요 형식의 바코드 값 수동 입력과 저장 전 재생성 검증
- QR·EAN·Code 128 실제 매장 스캐너와 저화질 이미지 회귀
- [x] 사용 완료 직후 5분 내 처리자 본인 실행 취소 피드백
- [x] 쿠폰 삭제 30일 soft-delete·복구함·즉시 실행 취소·자동/수동 영구 삭제
- [x] Android 공유 시트에서 단일 이미지 가져오기, 로그인 유지, 방 선택 후 등록
- [x] 갤러리 최대 10장 선택과 다중 공유 이미지의 순차 확인 등록
- [x] 일괄 등록의 현재 이미지 제외·남은 묶음 취소 확인과 단일 등록 상태 재사용
- [x] 업로드 중 뒤로가기 취소 확인, 정리 완료 후 이탈과 저장 단계 경쟁 차단
- [x] 일괄 등록 현재 장 검토 중 다음 이미지 1장 OCR·바코드·업로드 준비 선처리와 완료 결과 승격
- [x] 등록 폼 화면 재생성 복원과 수동 수정값의 늦은 OCR·바코드 결과 우선순위 보장
- [x] 단일 등록·일괄 등록 마지막 장의 미저장 초안 이탈 확인과 빈 등록 화면 즉시 이탈
- 스크린샷 일괄 OCR
- [x] 등록 전 공개·본인 비공개 쿠폰의 제한 조회 기반 중복 후보 경고와 강제 등록 선택

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
- [x] Firestore Rules·Indexes의 main 조상 commit 검증, production 승인, cloud dry-run, evidence 기반 rollback·roll-forward workflow 구현·정적 검사
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
- [x] 복구함 20개 cursor paging·105개 무중복 순회·자동 선조회·오류 수동 재시도
- [x] Android 이미지 스트리밍 업로드·진행률·저장 단계 피드백
- [x] 64KB 업로드 버퍼와 원본 전송·썸네일 생성 병렬화, 부분 실패 보상 테스트
- [x] 서버 원본 File 조기 전송, 업로드·조회 `Server-Timing`, 비파괴 인증 이미지 API 벤치마크 도구
- [x] 업로드 응답 후 Firestore 커밋 확인과 미사용 Blob cleanup queue 보상
- [x] upload session ID 기반 응답 유실 Blob 정리와 안전한 업로드 취소
- [x] 이미지 선택 시 OCR·업로드 전처리 병렬 실행, 2,560px/JPEG 92/최소 10% 절감 정책과 전후 용량 표시
- [x] OCR·바코드 자동 입력을 업로드 전처리 완료와 분리해 먼저 끝난 결과를 즉시 확인·수정하는 점진적 등록 흐름
- [x] 업로드 실패·취소 시 준비 파일을 유지하고 성공·재선택·화면 종료에서만 정리해 빠른 재시도 지원
- [x] 상세 이미지 교체 선택 즉시 전처리, 준비 중 확인 차단과 취소·재선택 임시 파일 정리
- [x] Vercel Function 4.5MB 본문 제한을 고려한 4MiB 적응 압축·크기 미상 원본 제한 복사와 바코드 보존 계측
- [x] 10MB 쿠폰 이미지에는 100MB 초과 권장인 multipart/resumable upload를 적용하지 않고, 입력 상한이 커질 때 direct client upload로 재검토
- [x] 상세 이미지 8MP 상한 고해상도 지연 디코딩·핀치/더블탭 확대
- [x] 상세 이미지 1×~4× 명시적 확대/축소와 원본 맞춤 조작
- [x] 전체화면 최초 1× 디코딩·실제 확대 시 2× 점진 준비와 단일 탭 조작부 표시 전환
- [x] 1.5× 미만 작은 핀치에서 2× bitmap 준비를 생략해 확대 조작 중 불필요한 디코드 방지
- [x] 핀치 배율·이동값을 lambda형 `graphicsLayer`에서 지연 읽기해 다이얼로그 전체 재구성 범위 축소
- [x] 실제 확대 다이얼로그 6회 핀치 왕복×5회 Release/R8 Macrobenchmark·Perfetto trace·Baseline Profile 수집
- [x] 전체화면 이미지의 현재 배율·조작부 표시/숨기기 TalkBack 액션
- [x] 취소 가능한 이미지 HTTP 요청·동시 중복 요청 억제·탭 위치 중심 확대
- [x] 100개 목록 Release/R8 Macrobenchmark 기준선과 CI 컴파일 게이트
- [x] 스크롤 Macrobenchmark에서 Activity 시작 비용을 분리한 순수 fling A/B 계측
- [x] 앱 시작·100개 목록 Baseline Profile 생성, APK 패키징, 적용 전·후 5회 비교
- [x] 생산 캐시·디코드 경로 24개 썸네일 miss/hit·payload·PSS 5회 계측과 동일 key 동시 요청 병합 검증
- [x] 실제 RoomDashboard 24개 행 첫 순회·역방향 cache 순회와 재구성 중 fetch/decode 불변 검증
- [x] 표시 크기와 무관한 압축 썸네일 LRU·동시 fetch 병합과 목록→상세 재디코딩 검증
- [x] 전체화면 확대의 썸네일 즉시 표시·고해상도 원본 교체·실패 재시도 UX
- [x] 상세 원본 64KB 앱 전용 임시 파일 스트리밍·취소/로그아웃/화면 종료/다음 시작 정리
- [x] 샘플 bitmap을 실제 표시 크기로 후축소해 목록·상세 preview 메모리 절감
- [x] 썸네일 쿠폰 상세 진입 원본 요청 1회→0회 지연, 확대/썸네일 실패 시 요청, 닫기 취소
- [x] 쿠폰 등록 완료 스낵바와 상세 확인·하나 더 등록 후속 행동
- 실제 계정·Vercel WebP 24개 인증 HTTP·Compose 최초 표시·wire byte·release PSS 계측
- [x] 쿠폰 검색·상태 필터·만료 임박순 정렬
- [x] 사용 완료·쿠폰 삭제 확인 절차
- [x] 댓글·쿠폰 수정의 서버 성공 후 화면 상태 전환
- [x] 쿠폰 등록·수정 만료일 달력 선택기
