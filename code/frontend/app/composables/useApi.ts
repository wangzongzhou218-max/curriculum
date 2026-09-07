export class ApiError extends Error {
  constructor(
    message: string,
    public code = "NETWORK_ERROR",
    public status = 0,
    public requestId = "",
  ) {
    super(message);
  }
}
interface Envelope<T> {
  data: T;
  error?: { code: string; message: string };
  meta?: { requestId?: string };
}
export interface PendingOperation {
  key: string;
  label: string;
  anonymous: boolean;
  userId: string;
  semesterId: string;
  createdAt: string;
}
interface MutationOptions {
  method?: string;
  body?: unknown;
  version?: string;
  generation?: string;
  anonymous?: boolean;
  label: string;
  semesterId?: string;
}
const pendingStorage = "curriculum-pending";
const operationRetries = new Map<string, () => Promise<unknown>>();
const csrf = () =>
  decodeURIComponent(
    document.cookie
      .split("; ")
      .find((c) => c.startsWith("CURRICULUM_CSRF="))
      ?.split("=")
      .slice(1)
      .join("=") || "",
  );

export function useApi() {
  const pending = useState<PendingOperation[]>("pending", () => []);
  const busy = useState("mutation-busy", () => false);
  const persist = () => {
    try {
      sessionStorage.setItem(pendingStorage, JSON.stringify(pending.value));
    } catch {
      /* The visible pending state remains available in memory. */
    }
  };
  const remove = (key: string) => {
    pending.value = pending.value.filter((p) => p.key !== key);
    operationRetries.delete(key);
    persist();
  };
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`/api/v1${path}`, {
      ...init,
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", ...init.headers },
    });
    let envelope: Envelope<T>;
    try {
      envelope = await response.json();
    } catch {
      throw new ApiError(
        "服务响应异常，请稍后重试",
        "SERVICE_UNAVAILABLE",
        response.status,
      );
    }
    if (!response.ok) {
      if (
        response.status === 401 &&
        path !== "/auth/me" &&
        path !== "/auth/login"
      )
        window.dispatchEvent(new Event("session-expired"));
      throw new ApiError(
        envelope.error?.message || "请求失败，请重试",
        envelope.error?.code,
        response.status,
        envelope.meta?.requestId,
      );
    }
    return envelope.data;
  }
  async function resolve<T>(operation: PendingOperation): Promise<T> {
    let outcome: { outcome: string; httpStatus: number; result: Envelope<T> };
    try {
      outcome = await request<{
        outcome: string;
        httpStatus: number;
        result: Envelope<T>;
      }>(`${operation.anonymous ? "/auth" : ""}/operations/${operation.key}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 410) {
        remove(operation.key);
        throw new ApiError(
          "操作记录已过期。请核对当前数据后再操作。",
          "OPERATION_EXPIRED",
          410,
        );
      }
      throw error;
    }
    remove(operation.key);
    if (outcome.outcome !== "SUCCEEDED")
      throw new ApiError(
        outcome.result.error?.message || "操作未成功",
        outcome.result.error?.code,
        outcome.httpStatus,
      );
    return outcome.result.data;
  }
  async function mutate<T>(path: string, options: MutationOptions): Promise<T> {
    if (busy.value) throw new ApiError("操作正在提交，请稍候");
    if (pending.value.length >= 20)
      throw new ApiError("请先确认已有待处理操作的结果");
    const operation: PendingOperation = {
      key: crypto.randomUUID(),
      label: options.label,
      anonymous: !!options.anonymous,
      userId: useState<{ userId: string } | null>("user").value?.userId || "",
      semesterId: options.semesterId || "",
      createdAt: new Date().toISOString(),
    };
    pending.value.push(operation);
    persist();
    busy.value = true;
    const send = () =>
      request<T>(path, {
        method: options.method || "POST",
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
        headers: {
          "X-CSRF-Token": csrf(),
          "Idempotency-Key": operation.key,
          ...(options.version ? { "If-Match": `"${options.version}"` } : {}),
          ...(options.generation
            ? { "X-Enrollment-Generation": options.generation }
            : {}),
        },
        signal: AbortSignal.timeout(10000),
      });
    operationRetries.set(operation.key, send);
    try {
      const result = await send();
      remove(operation.key);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status > 0 && error.status < 500) {
        remove(operation.key);
        throw error;
      }
      try {
        return await resolve<T>(operation);
      } catch (lookup) {
        if (!pending.value.some((p) => p.key === operation.key)) throw lookup;
        throw new ApiError(
          "结果待确认。请使用页面上方的“查询结果”，不要重复提交。",
          "OPERATION_PENDING",
        );
      }
    } finally {
      busy.value = false;
    }
  }
  function restore(userId: string) {
    try {
      const entries = JSON.parse(
        sessionStorage.getItem(pendingStorage) || "[]",
      );
      pending.value = Array.isArray(entries)
        ? entries
            .filter(
              (p) => p && typeof p.key === "string" && p.userId === userId,
            )
            .slice(0, 20)
        : [];
    } catch {
      pending.value = [];
    }
  }
  function clear() {
    pending.value = [];
    operationRetries.clear();
    persist();
  }
  // A session touch must not acquire the form submission lock on pointerdown.
  async function activity() {
    await request("/auth/activity", {
      method: "POST",
      headers: {
        "X-CSRF-Token": csrf(),
        "Idempotency-Key": crypto.randomUUID(),
      },
      signal: AbortSignal.timeout(10000),
    });
  }
  const canRetry = (operation: PendingOperation) =>
    operationRetries.has(operation.key);
  async function retry<T>(operation: PendingOperation): Promise<T> {
    const send = operationRetries.get(operation.key);
    if (!send)
      throw new ApiError(
        "页面刷新后无法恢复原请求内容，请继续查询原操作结果。",
        "RETRY_UNAVAILABLE",
      );
    busy.value = true;
    try {
      const result = (await send()) as T;
      remove(operation.key);
      return result;
    } finally {
      busy.value = false;
    }
  }
  return {
    request,
    mutate,
    resolve,
    retry,
    canRetry,
    pending,
    busy,
    restore,
    clear,
    remove,
    activity,
  };
}
