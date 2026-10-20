export type Summary = {
  id: string;
  filename: string;
  status: "QUEUED" | "PROCESSING" | "READY" | "FAILED";
  error: string | null;
  createdAt: string;
};
export type Question = { index: number; prompt: string; options: string[] };
export type QuizView = { quiz: Summary; questions: Question[] };
export type Review = {
  prompt: string;
  options: string[];
  selectedIndex: number;
  correctIndex: number;
  correct: boolean;
  explanation: string;
  sourceExcerpt: string;
};
export type Result = { score: number; total: number; review: Review[] };
function session(): string {
  let token = localStorage.getItem("documind-session");
  if (!token) {
    token = crypto.randomUUID();
    localStorage.setItem("documind-session", token);
  }
  return token;
}
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch("/api" + path, {
    ...init,
    headers: { "X-Session-Id": session(), ...init?.headers },
    signal: init?.signal ?? AbortSignal.timeout(20000),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.message ?? `Request failed (${response.status}). Please try again.`,
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
