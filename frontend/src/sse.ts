import { getIdentity } from "./api";

export type SseEvent = {
  type: string;
  agentId?: string;
  sessionId?: string;
  sequence?: number;
  timestamp?: number;
  content?: string;
  text?: string;
  status?: string;
  questionId?: string;
  [key: string]: unknown;
};

export type SseCallback = (event: SseEvent) => void;

export class SseStream {
  private abort: AbortController | null = null;
  private lastSequence = 0;
  private listeners: SseCallback[] = [];

  constructor(
    private projectId: string,
    private taskId: string
  ) {}

  onEvent(cb: SseCallback) {
    this.listeners.push(cb);
  }

  setLastSequence(seq: number) {
    this.lastSequence = seq;
  }

  async connect() {
    this.abort?.abort();
    const abort = new AbortController();
    this.abort = abort;
    const { tenant, user } = getIdentity();

    while (true) {
      try {
        const res = await fetch(
          `/api/v1/projects/${this.projectId}/tasks/${this.taskId}/events`,
          {
            headers: {
              "X-Tenant-Id": tenant,
              "X-User-Id": user,
              "Last-Event-ID": String(this.lastSequence),
            },
            signal: abort.signal,
          }
        );
        if (!res.ok) throw new Error(`SSE connect failed: ${res.status}`);

        const reader = res.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          let idx: number;
          while ((idx = buffer.indexOf("\n\n")) >= 0) {
            const block = buffer.slice(0, idx);
            buffer = buffer.slice(idx + 2);
            // 断线游标一律取自 SSE 帧的 id 字段：后端已把 id 统一为
            // 持久化事件水位（live 帧沿用当前水位），与 data 里的
            // agent 会话序列不属于同一命名空间，绝不能混用。
            const idLine = block.split("\n").find((l) => l.startsWith("id:"));
            const frameId = idLine
              ? Number(idLine.slice(3).trim())
              : Number.NaN;
            const data = block
              .split("\n")
              .filter((l) => l.startsWith("data:"))
              .map((l) => l.slice(5).trim())
              .join("");
            if (data) {
              try {
                const event: SseEvent = JSON.parse(data);
                if (Number.isFinite(frameId)) {
                  this.lastSequence = Math.max(this.lastSequence, frameId);
                }
                for (const cb of this.listeners) {
                  try {
                    cb(event);
                  } catch {
                    /* isolate listeners */
                  }
                }
              } catch {
                /* skip unparseable chunks */
              }
            }
          }
        }
        // Connection ended normally — reconnect after a short delay
        await sleep(1000);
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") return;
        await sleep(2000);
      }
    }
  }

  disconnect() {
    this.abort?.abort();
  }
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}
