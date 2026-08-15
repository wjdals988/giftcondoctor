# v0.1.20 코드 품질 검토 보고서

검토일: 2026-08-15
대상: `0.1.19 (20)` → `0.1.20 (21)` Android 이미지·목록·바코드 및 Backend 안정성 변경분

## 전체 평가: A (89/100)

`code-quality-reviewer`의 6개 가중치로 수동 평가했다. 전체 자동 스크립트는 전역 ESLint 의존과 자체 산술 오류로 완주하지 못해 총점은 N/A로 처리했다. 별도 AST 복잡도 검사는 이번 Backend 변경의 최대값을 11→8로 낮춘 뒤 통과했으며, Kotlin 컴파일, unit/UI/Macrobenchmark, R8 release 빌드를 함께 근거로 점수화했다.

| 평가축 | 점수 | 근거 |
|---|---:|---|
| 가독성 | 80 | cursor 검증·권한별 쿼리·병합·페이지 생성을 분리해 변경 함수 최대 복잡도 11→8, 대형 Screen 파일은 남음 |
| 성능 | 94 | 기존 이미지 최적화에 복구함 20개 paging을 더해 초기 읽기 상한을 일반 멤버 21개·방장 42개로 제한. 스크롤 frame budget 초과 |
| 명시적 I/O | 92 | HTTP 페이지 응답과 nullable cursor, Android paging 상태가 타입으로 드러남 |
| 유지보수성 | 94 | Android 단위 55개·계측 27개, Backend 단위 57개, Rules·저장소 통합 23개, 105개 paging 회귀 포함 |
| 에러 처리 | 92 | paging 자동 재시도 loop 차단·수동 retry와 기존 cancellation·Blob 재시도 경계 유지 |
| 협업 | 96 | 성능 기준선, CI 컴파일 게이트, 사용자 변경 이력과 서명 차단 상태 동기화 |

가중 총점은 `80×0.25 + 94×0.20 + 92×0.15 + 94×0.25 + 92×0.10 + 96×0.05 = 90.1`이다. 물리 기기·매장 스캐너·실네트워크·운영 인덱스 검증이 빠진 점을 보수적으로 반영해 최종 89점으로 판정했다.

## 통과 항목

- Android 단위 테스트: 55/55
- Android 16 AVD 2대 계측 테스트: 각 27/27, 총 54회 실행
- ZXing Code 128 생성→재감지 왕복, 지원 형식 경계와 분석 bitmap 1600px 상한 검증
- Firestore Rules·복구함 저장소 통합: 23/23, 비공개 삭제 쿠폰 격리·30일 만료·댓글 보존·105개 paging 포함
- Backend 단위 테스트: 57/57, 결정적 upload session 경로·병렬 처리·보상 삭제·cursor 검증 포함
- Release/R8 Baseline Profile A/B Macrobenchmark: 6/6, 각 5회
- R8 release APK와 benchmark APK 빌드: 성공
- 최종 100개 목록 cold start 중앙값: 1,264.3ms → 1,046.9ms, 17.2% 단축
- 교정 전 Activity 시작 포함 복합 스크롤 P50/P90/P99: 22.8/32.9/55.0ms → 22.5/32.1/42.9ms. 역사 비교용이며 합격 판정에서 제외
- Activity 시작을 제외하도록 계측을 교정한 순수 스크롤 5회: P50/P90/P99 23.0/33.0/37.9ms → 22.1/23.8/27.2ms. P90/P99는 27.9%/28.2% 단축됐지만 적용 P90도 16.7ms 예산 초과
- 실제 `MainActivity` cold start 중앙값: 1,061.2ms → 961.6ms, 9.4% 단축
- 앱 전용 baseline 563개·startup 219개 규칙과 release APK 내 profile asset 패키징 확인
- Android lint 오류 0개, 경고 19개→14개로 정리. 잔여는 target SDK 36과 dependency 업그레이드 항목
- 네트워크 취소 테스트: 진행 중 body 다운로드를 취소하면 OkHttp call도 취소됨
- Android 업로드 버퍼는 8KB→64KB로 늘고 서버 원본 업로드와 썸네일 변환·업로드가 병렬 시작됨
- 이미지 선택 직후 OCR·교체 확인과 업로드 준비를 병렬 실행하고 5,707,065B 입력을 2,909,037B로 49.0% 줄임. 준비 537.294~598.459ms·PSS 247~267KB이며 10Mbps payload 환산 순절감은 약 1.64초 이상
- 2,560px·6,553,600px·JPEG 92 상한과 최소 10% 절감 조건, EXIF 방향 보정, 취소·재선택·재시작 임시 파일 정리를 단위·계측 회귀로 고정
- 업로드 응답 후 Firestore 저장 실패·화면 이탈 시 서버가 Admin 조회로 live-reference와 등록자 권한을 확인해 이미 커밋된 쿠폰 이미지 삭제 방지
- 미사용 업로드의 즉시 삭제 실패도 cleanup queue에 남겨 무음 Blob 누수 방지
- Blob 저장 전 1시간 뒤부터 처리 가능한 session cleanup 안전망을 먼저 생성해 다음 일일 Cron 대상에 포함하고, 정상 커밋은 PATCH로 해제하며 취소는 10분 지연 검증과 즉시 삭제를 병행
- upload session 후보 4개 중 현재 쿠폰이 참조하는 경로만 보존하고 나머지만 삭제해 응답 유실·확정 호출 유실 양쪽 방어
- 전체화면 쿠폰에서만 최대 밝기와 화면 켜짐을 적용하고 dispose 시 기존 window 상태를 복원
- OCR과 ZXing이 최대 1600px bitmap 1개를 공유해 고해상도 이미지 중복 디코딩을 제거
- 1D/2D 바코드 길이와 렌더 픽셀을 제한해 비정상 값의 대형 Bitmap 생성 방지
- 번들 ML Kit barcode 모델은 APK를 68,084,965B까지 키워 제외했고, ZXing·업로드 EXIF 보정·교체 사전 준비 포함 최종 46,908,846B로 21,176,119B(31.1%) 축소
- 동일 이미지 요청 gate는 참조 수가 0일 때만 제거해 장기 키 누적과 대기 중 신규 요청의 중복 fetch 경쟁을 함께 방지
- 최신 19개 전체 suite의 24개 썸네일 5회는 첫 조회/bitmap hit 중앙값 209.043/18.848ms, 평균 decode 7.530ms/장, bitmap·압축 cache 1,990,656/5,104,992B, PSS 증가 중앙값 7,000KB였고 동일 이미지 동시 24요청은 fetch 1회로 병합
- 표시 영역 후축소 전보다 24개 bitmap cache 64.0%, PSS 중앙값 31.8% 감소. 시간 변화는 ±5% 안으로 에뮬레이터 노이즈 범위
- 4,550,583B 상세 원본을 64KB씩 앱 전용 cache 파일로 받고 파일에서 preview·zoom을 디코딩해 동일 크기의 ViewModel `ByteArray` 상주 제거
- 3,000×2,000 파일 5회 기준 전체화면 최초 1× 디코드 중앙값 179.892ms/3,110,400B, 확대 후 2× 268.105ms/12,441,600B. 최초 작업은 32.9% 짧고 bitmap은 75.0% 작으며 preview까지 합친 초기 논리 bitmap은 67.5% 감소
- 실제 PSS 중앙값은 최초 11,477KB, 확대 peak 11,562KB로 85KB 차이에 그쳐 allocator·GC 노이즈가 지배하므로 PSS 개선 수치로 주장하지 않음
- 같은 212,708B 썸네일의 56×56→512×360 전환은 8.206→12.092ms, fetch 1회·압축 cache hit 1회였으며 확대 화면은 미리보기→원본 제자리 교체·실패 재시도 UI 테스트를 통과
- 썸네일 쿠폰은 상세 진입 원본 요청을 1회→0회로 줄이고 확대·썸네일 실패에만 요청하며, Idle/Loading/Ready/Error 정책과 중복·닫기 취소 8개 단위 경계를 검증
- 실제 목록 UI 24개 순차 방문은 첫 순회 3,274.261~3,400.052ms→cache 역순회 1,762.757~1,778.225ms로 45.7~47.9% 단축됐고 재구성 중 fetch·decode는 24회에서 증가하지 않음
- 등록 완료 스낵바 노출과 `하나 더 등록` 액션·1회 소비 상태를 Compose UI 테스트로 검증
- 전체화면 이미지의 현재 배율과 조작부 표시·숨기기를 TalkBack action으로 제공하고 터치·semantics 양쪽 전환을 UI 테스트로 검증
- 사용 완료 스낵바의 `실행 취소` 액션과 5분 내 처리자 본인만 허용하는 Rules 전이를 검증하고, 일반 정보 수정으로 상태 전이를 우회하던 등록자 권한을 차단
- benchmark 생성물 1,043개를 `.gitignore`로 제외해 미추적 후보를 1,077개→실제 소스·문서 34개로 축소
- 이미지 교체는 등록자 권한·원본 경로를 transaction에서 재검증하고 실패 시 새 원본·썸네일을 보상 삭제
- 이미지 교체·쿠폰 영구 삭제·방 삭제 Blob은 영속 cleanup queue에 기록하고 live-reference 방어·5회 backoff·lease·dead-letter와 health 집계로 무음 누수와 사용 중 이미지 삭제를 방지
- 쿠폰은 30일 soft-delete 후 복원하거나 확인 뒤 영구 삭제하며, 삭제 직후 목록의 실행 취소와 이미지·댓글·상태 복원을 UI·Firestore 통합 테스트로 검증
- 삭제된 쿠폰의 Firestore 직접 읽기·이미지 API를 차단하고 비공개 복구함은 등록자에게만 보이도록 프라이버시 경계를 회귀 테스트로 고정
- 복구함 100개 고정 상한을 20개 cursor paging으로 바꾸고, 권한별 쿼리를 분리해 일반 멤버 첫 요청 21개·방장 최대 42개로 제한
- 검색·사용 완료 필터·만료일 달력과 복구함 자동 선조회·오류 수동 재시도를 Android Compose 계측으로 고정

## 즉시 수정한 항목

1. nullable HTTP body의 안전한 빈 응답 처리
2. 코루틴 내부 API 대신 공개 continuation API 사용
3. Android 16과 호환되는 JUnit 1.3.0·Espresso 3.7.0 적용
4. benchmark test APK 서명 누락으로 인한 설치 실패 수정
5. 누락된 project R8 rule 파일을 명시해 빌드 경고 제거
6. 쿠폰·크기별 Mutex가 무한 누적되거나 대기 중 다른 gate가 생길 수 있는 경쟁을 참조 수 gate로 수정
7. 상세 원본 body를 메모리 상태에서 제거하고, 취소·화면 종료·로그아웃·다음 시작 시 임시 파일을 정리하도록 변경
8. 샘플 bitmap이 표시 영역보다 큰 문제를 후축소하고 bitmap cache 크기 회귀를 고정
9. 확대 화면의 즉시 2× 디코딩을 1× 최초 표시→사용자 확대 시 2× 준비로 분리하고 상태·조작부를 별도 Composable로 추출
10. 스크롤 계측 블록에 섞였던 Activity 시작을 setup 구간으로 옮겨 프레임 지표의 의미를 교정

## 남은 위험

- Macrobenchmark는 이미지가 없는 100개 합성 데이터다. 별도 24개 계측은 디코드·메모리 캐시와 목록 Compose 경로를 포함하지만 실제 인증 HTTP는 포함하지 않는다.
- emulator-5556 결과는 물리 기기의 발열, 저장장치, GPU, 네트워크를 대표하지 않는다.
- 교정된 순수 스크롤도 프로필 적용 후 P50 22.1ms와 P90 23.8ms가 60fps frame budget 16.7ms를 넘는다.
- 교정 전 복합 정의의 세 번 A/B에서 P90 개선폭이 29.6%, 6.8%, 2.3%로 달랐다. 교정된 정의도 에뮬레이터 순차 1쌍뿐이라 재현성 판단에는 부족하다.
- R8 release는 빌드만 통과했으며 새 서명키·실제 계정으로 로그인, Firestore, OCR, FCM 회귀를 수행하지 못했다.
- 재생성한 Code 128의 소프트웨어 왕복은 통과했지만 QR/EAN/PDF417과 실제 매장 레이저·카메라 스캐너 인식률은 확인하지 못했다.
- Compose 1.7.6은 최신 안정판보다 오래됐다. 최신 BOM은 AGP 9.1+·compileSdk 37을 요구해 별도 마이그레이션 이터레이션이 필요하다.
- 상세 원본의 압축 body 상주는 제거했지만 최대 8MP zoom bitmap은 약 32MB가 될 수 있어 2GB RAM 물리 기기 OOM 회귀가 필요하다.
- 앱이 비정상 종료되면 앱 전용 cache 원본은 다음 앱 시작의 abandoned-file purge 전까지 남을 수 있다. Android sandbox·backup 제외로 보호되지만 별도 파일 암호화는 적용하지 않았다.
- 압축 썸네일 cache는 프로세스 메모리에 2~8MB를 추가 사용한다. 로그아웃·이미지 교체 시 비우고 디스크에는 남기지 않으며, bitmap cache와 합친 최신 24개 계측 PSS 중앙값은 7,000KB다.
- cleanup queue는 기존 daily Cron에서 처리하므로 즉시 삭제가 실패하면 다음 정기 실행까지 이전 Blob이 남을 수 있다.
- cleanup queue의 live-reference 방어는 단위·Rules 테스트로 검증했지만 실제 Vercel Blob 삭제 부분 실패는 로컬에서 재현하지 못했다.
- upload session 정책은 단위 테스트와 production build까지 통과했지만 실제 Vercel Blob PUT·DELETE 경합 및 네트워크 fault injection은 수행하지 못했다.
- 이미지 교체 API는 로컬 production build까지 통과했지만 미배포 상태라 실제 Vercel Blob·Firestore 통합 성공은 확인하지 못했다.
- 업로드 49.0% 절감은 합성 JPEG 1장 기준이다. 실제 스크린샷·HEIC·저조도 사진과 LTE/Wi-Fi별 end-to-end 시간, 최적화 후 매장 바코드 인식률은 물리 검증 전 보장할 수 없다.
- 복구함 자동 정리는 일일 Cron이 한 번에 최대 100개를 처리하므로 대량 backlog가 생기면 여러 날 남을 수 있다. 운영 health에 due/purging 건수를 노출하지만 외부 경보 연결은 남아 있다.
- 복구함 query용 composite index 2개가 프로덕션에 생성되기 전에 Backend를 배포하면 목록 API가 `FAILED_PRECONDITION`으로 실패한다. 인덱스 선배포와 실제 쿼리 smoke가 릴리스 게이트다.
- 복구함 추가 후 목록 스크롤 1차 재측정 P50/P90은 22.1/31.3ms였지만 2차는 22.0/24.6ms로 기존 22.0/24.7ms와 같아 퇴행이 재현되지 않았다. 다만 P90 변동 범위 24.6~31.3ms가 넓고 모두 16.7ms를 넘으므로 물리 기기 A/B 전에는 절대 성능 합격으로 처리하지 않는다.
- `npm audit --omit=dev`는 High/Critical 0건이지만 Firebase Admin 14.2.0→Cloud Storage→`uuid` 9.0.1 간접 경로의 Moderate 6건을 보고한다. 자동 수정안은 Firebase Admin 10.3.0 강제 다운그레이드이므로 적용하지 않았고 상위 의존성 갱신을 추적해야 한다.

## 다음 우선순위

1. [P0] 물리 디바이스 FCM 6개 상태와 A→B 교차 계정 프라이버시 검증
2. [P0] 새 release keystore 전환·Firebase 인증서 재등록·signed R8 release 회귀
3. [P1] 실제 Vercel Blob cleanup 실패·재시도 운영 통합 테스트
4. [P1] 실제 계정·Vercel Blob 24개 WebP의 인증 HTTP·Compose 최초 표시 시간·wire byte·release PSS 계측
5. [P1] 교정된 순수 스크롤 P90 23.8ms를 16.7ms 이하로 낮추고 물리 기기 A/B 10회로 재현성 확인
6. [P1] QR·EAN·Code 128 실제 매장 스캐너와 저화질 이미지 감지 회귀
7. [P1] 최근 쿠폰 암호화 로컬 캐시와 오프라인 매장 사용 경로
8. [P1] 복구함 페이지별 Firestore read·latency 운영 관측과 인덱스 smoke 자동화
9. [P2] Compose/AGP/compileSdk 업그레이드 별도 검증

## 긍정적 평가

이번 변경은 체감 개선을 추측으로 끝내지 않고 취소 전파·중복 억제 단위 테스트, 100개 UI 회귀, Release/R8 Macrobenchmark까지 같은 경로로 묶었다. 이후 최적화가 실제로 좋아졌는지 같은 5회 기준선으로 비교할 수 있다.
