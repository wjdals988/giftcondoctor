export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly details?: unknown
  ) {
    super(message);
  }
}

export function json(data: unknown, init?: ResponseInit) {
  return Response.json(data, init);
}

export function jsonError(error: unknown) {
  if (error instanceof ApiError) {
    return json({ error: error.message, details: error.details }, { status: error.status });
  }

  console.error(error);
  return json({ error: "서버 오류가 발생했습니다." }, { status: 500 });
}

export async function readJson<T>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    throw new ApiError(400, "JSON 요청 본문이 올바르지 않습니다.");
  }
}

export function requireCronSecret(request: Request) {
  const expected = process.env.CRON_SECRET;
  if (!expected) return;

  const actual = request.headers.get("authorization");
  if (actual !== `Bearer ${expected}`) {
    throw new ApiError(401, "Cron 인증에 실패했습니다.");
  }
}
