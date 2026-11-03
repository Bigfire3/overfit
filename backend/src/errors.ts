export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public details?: Record<string, unknown>,
  ) {
    super(message);
  }
}

export function errorResponse(e: ApiError): Response {
  return Response.json(
    { error: { code: e.code, message: e.message, ...(e.details ? { details: e.details } : {}) } },
    { status: e.status },
  );
}
