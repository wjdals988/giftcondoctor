const features = [
  "공유방 기반 기프티콘 관리",
  "민감한 쿠폰 이미지를 private Blob에 저장",
  "만료일 푸시 알림과 Android deep link 지원"
];

const checks = [
  { label: "API", value: "운영 중" },
  { label: "이미지", value: "인증 후 접근" },
  { label: "플랫폼", value: "Android 앱 전용" }
];

export default function HomePage() {
  return (
    <main
      style={{
        minHeight: "100svh",
        background: "#f6f8fb",
        color: "#111827",
        fontFamily:
          "Pretendard, Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
      }}
    >
      <section
        style={{
          display: "grid",
          minHeight: "100svh",
          placeItems: "center",
          padding: "32px 18px"
        }}
      >
        <div
          style={{
            width: "min(100%, 760px)",
            border: "1px solid #d8dee9",
            borderRadius: 8,
            background: "#ffffff",
            boxShadow: "0 18px 50px rgba(17, 24, 39, 0.08)",
            overflow: "hidden"
          }}
        >
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 16,
              borderBottom: "1px solid #e5e7eb",
              padding: "24px"
            }}
          >
            <div
              aria-hidden="true"
              style={{
                display: "grid",
                width: 54,
                height: 54,
                placeItems: "center",
                borderRadius: 8,
                background: "#111827",
                color: "#ffffff",
                fontSize: 20,
                fontWeight: 800,
                letterSpacing: 0
              }}
            >
              GD
            </div>
            <div>
              <p style={{ margin: "0 0 4px", color: "#4b5563", fontSize: 14 }}>
                giftcondoctor
              </p>
              <h1 style={{ margin: 0, fontSize: "clamp(28px, 5vw, 44px)", letterSpacing: 0 }}>
                기프티콘닥터
              </h1>
            </div>
          </div>

          <div style={{ padding: "28px 24px 24px" }}>
            <p style={{ margin: 0, color: "#374151", fontSize: 18, lineHeight: 1.7 }}>
              이 주소는 기프티콘닥터 Android 앱에서 사용하는 백엔드 API 서버입니다.
              웹에서는 쿠폰을 관리할 수 없으며, 앱에서 로그인한 사용자만 방과 쿠폰 이미지에
              접근할 수 있습니다.
            </p>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
                gap: 10,
                marginTop: 24
              }}
            >
              {checks.map((item) => (
                <div
                  key={item.label}
                  style={{
                    border: "1px solid #e5e7eb",
                    borderRadius: 8,
                    padding: "14px 16px",
                    background: "#f9fafb"
                  }}
                >
                  <p style={{ margin: "0 0 6px", color: "#6b7280", fontSize: 13 }}>
                    {item.label}
                  </p>
                  <p style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{item.value}</p>
                </div>
              ))}
            </div>

            <ul
              style={{
                display: "grid",
                gap: 10,
                margin: "26px 0 0",
                padding: 0,
                listStyle: "none"
              }}
            >
              {features.map((feature) => (
                <li
                  key={feature}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                    color: "#374151",
                    fontSize: 15,
                    lineHeight: 1.55
                  }}
                >
                  <span
                    aria-hidden="true"
                    style={{
                      width: 7,
                      height: 7,
                      flex: "0 0 auto",
                      borderRadius: 99,
                      background: "#2563eb"
                    }}
                  />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>
    </main>
  );
}
