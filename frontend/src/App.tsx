import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  BookOpen,
  Check,
  ChevronLeft,
  Clock3,
  FileText,
  FolderOpen,
  GraduationCap,
  LayoutDashboard,
  LoaderCircle,
  Plus,
  ShieldCheck,
  Sparkles,
  Trash2,
  UploadCloud,
  X,
} from "lucide-react";
import { api, type Summary, type QuizView, type Result } from "./api";

export default function App() {
  const [history, setHistory] = useState<Summary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [view, setView] = useState<QuizView | null>(null);
  const [result, setResult] = useState<Result | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [consent, setConsent] = useState(false);
  const [answers, setAnswers] = useState<number[]>(Array(10).fill(-1));
  const [index, setIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<"create" | "library">("create");
  const [dragging, setDragging] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const requestNumber = useRef(0);
  const answered = answers.filter((a) => a !== -1).length;
  async function refresh() {
    setHistory(await api<Summary[]>("/quizzes"));
  }
  useEffect(() => {
    refresh().catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    if (!selected) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const controller = new AbortController();
    async function poll() {
      try {
        const next = await api<QuizView>(`/quizzes/${selected}`, {
          signal: controller.signal,
        });
        if (cancelled) return;
        setView(next);
        if (next.quiz.status === "QUEUED" || next.quiz.status === "PROCESSING")
          timer = setTimeout(poll, 2000);
        else refresh().catch(() => {});
      } catch (e) {
        if (!cancelled) setError((e as Error).message);
      }
    }
    poll();
    return () => {
      cancelled = true;
      clearTimeout(timer);
      controller.abort();
    };
  }, [selected]);
  function reset() {
    requestNumber.current++;
    setSelected(null);
    setView(null);
    setResult(null);
    setAnswers(Array(10).fill(-1));
    setIndex(0);
    setError("");
    setTab("create");
    setBusy(false);
  }
  function choose(next: File | undefined) {
    if (!next) return;
    if (!/\.(pdf|txt)$/i.test(next.name)) {
      setError("Please choose a PDF or TXT file.");
      return;
    }
    if (next.size === 0 || next.size > 10 * 1024 * 1024) {
      setError("Choose a nonempty file up to 10 MB.");
      return;
    }
    setFile(next);
    setError("");
  }
  async function upload() {
    if (!file || !consent || busy) return;
    const request = ++requestNumber.current;
    setBusy(true);
    setError("");
    try {
      const form = new FormData();
      form.append("file", file);
      form.append("consent", "true");
      const quiz = await api<Summary>("/quizzes", {
        method: "POST",
        body: form,
      });
      if (request !== requestNumber.current) return;
      setView({ quiz, questions: [] });
      setSelected(quiz.id);
      setResult(null);
      setAnswers(Array(10).fill(-1));
      setIndex(0);
      await refresh();
    } catch (e) {
      if (request === requestNumber.current) setError((e as Error).message);
    } finally {
      if (request === requestNumber.current) setBusy(false);
    }
  }
  function openQuiz(q: Summary) {
    reset();
    setTab("create");
    setSelected(q.id);
  }
  async function submit() {
    if (answered !== 10 || !selected || busy) return;
    const request = ++requestNumber.current;
    setBusy(true);
    setError("");
    try {
      const next = await api<Result>(`/quizzes/${selected}/attempts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answers }),
      });
      if (request === requestNumber.current) setResult(next);
    } catch (e) {
      if (request === requestNumber.current) setError((e as Error).message);
    } finally {
      if (request === requestNumber.current) setBusy(false);
    }
  }
  async function remove(q: Summary) {
    try {
      await api(`/quizzes/${q.id}`, { method: "DELETE" });
      if (selected === q.id) reset();
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  const pending = view && ["QUEUED", "PROCESSING"].includes(view.quiz.status);
  return (
    <div className="app">
      <aside className="sidebar">
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            reset();
          }}
        >
          <span className="brand-icon">
            <BookOpen size={23} />
          </span>
          <span>
            DocuMind <b>AI</b>
          </span>
        </a>
        <div className="workspace-label">YOUR WORKSPACE</div>
        <button
          className={`nav ${tab === "create" ? "active" : ""}`}
          onClick={reset}
        >
          <LayoutDashboard size={18} /> Create a quiz{" "}
          <Plus size={16} className="nav-plus" />
        </button>
        <button
          className={`nav ${tab === "library" ? "active" : ""}`}
          onClick={() => {
            reset();
            setTab("library");
            refresh().catch((e) => setError(e.message));
          }}
        >
          <FolderOpen size={18} /> My quizzes{" "}
          <span className="count">{history.length}</span>
        </button>
        <div className="workspace-label recent-label">RECENT QUIZZES</div>
        {history.slice(0, 5).map((q) => (
          <button key={q.id} className="recent" onClick={() => openQuiz(q)}>
            <FileText size={15} />
            <span>{q.filename}</span>
            <i className={`dot ${q.status.toLowerCase()}`} />
          </button>
        ))}
        {!history.length && (
          <p className="empty-recent">
            Your next learning session starts here.
          </p>
        )}
        <div className="sidebar-tip">
          <Sparkles size={21} />
          <strong>A little recall. A lot of progress.</strong>
          <p>Turn what you have read into what you remember.</p>
        </div>
        <div className="profile">
          <span className="avatar">Y</span>
          <div>
            <strong>Your study space</strong>
            <small>Private browser session</small>
          </div>
        </div>
      </aside>
      <div className="main">
        <header>
          <span>
            Workspace <span className="slash">/</span>{" "}
            <strong>
              {tab === "library" ? "My quizzes" : "Create a quiz"}
            </strong>
          </span>
          <span className="header-badge">
            <span /> Document-powered learning
          </span>
        </header>
        <main>
          {error && (
            <div className="error" role="alert">
              <span>{error}</span>
              <button aria-label="Dismiss error" onClick={() => setError("")}>
                <X size={17} />
              </button>
            </div>
          )}
          {tab === "library" ? (
            <>
              <div className="eyebrow">
                <FolderOpen size={15} /> YOUR COLLECTION
              </div>
              <h1>Make knowledge stick.</h1>
              <p className="subtitle">
                Revisit a quiz, test yourself again, or start something new.
              </p>
              <section className="library card">
                <div className="card-title">
                  <h2>My quizzes</h2>
                  <button className="text-button" onClick={reset}>
                    New quiz <Plus size={16} />
                  </button>
                </div>
                {!history.length && (
                  <div className="empty">
                    <BookOpen size={38} />
                    <h3>A fresh page.</h3>
                    <p>Upload your first document to begin your collection.</p>
                    <button className="primary" onClick={reset}>
                      Create a quiz <ArrowRight size={17} />
                    </button>
                  </div>
                )}
                {history.map((q) => (
                  <div className="library-row" key={q.id}>
                    <FileText size={22} />
                    <button onClick={() => openQuiz(q)}>
                      <strong>{q.filename}</strong>
                      <small>
                        {new Date(q.createdAt).toLocaleDateString()} ·{" "}
                        {q.status.toLowerCase()}
                      </small>
                    </button>
                    <button
                      className="icon-button"
                      aria-label={`Delete ${q.filename}`}
                      disabled={q.status === "PROCESSING"}
                      onClick={() => remove(q)}
                    >
                      <Trash2 size={17} />
                    </button>
                  </div>
                ))}
                <p className="fine-print">
                  Latest 20 quizzes in this browser. Quizzes expire after 7
                  days.
                </p>
              </section>
            </>
          ) : selected ? (
            <>
              <button className="text-button back" onClick={reset}>
                <ChevronLeft size={16} /> Back to workspace
              </button>
              {pending ? (
                <section className="card processing" aria-live="polite">
                  <div className="processing-orbit">
                    <Sparkles size={32} />
                  </div>
                  <div className="eyebrow">A LITTLE THINKING IN PROGRESS</div>
                  <h1>
                    {view.quiz.status === "QUEUED"
                      ? "Your document is in line."
                      : "Finding your next aha moment."}
                  </h1>
                  <p>
                    Preparing 10 questions from{" "}
                    <strong>{view.quiz.filename}</strong>.
                  </p>
                  <div className="process-steps">
                    <span>
                      <Check size={16} /> Document received
                    </span>
                    <span>
                      <LoaderCircle className="spin" size={16} />{" "}
                      {view.quiz.status === "QUEUED"
                        ? "Waiting for the worker"
                        : "Generating & checking questions"}
                    </span>
                    <span>
                      <Clock3 size={16} /> Quiz ready
                    </span>
                  </div>
                  <p className="fine-print">
                    You can leave this screen and reopen the quiz from your
                    collection.
                  </p>
                </section>
              ) : view?.quiz.status === "FAILED" ? (
                <section className="card processing">
                  <FileText size={40} />
                  <h1>We could not create this quiz.</h1>
                  <p>{view.quiz.error}</p>
                  <button className="primary" onClick={reset}>
                    Try another upload <ArrowRight size={17} />
                  </button>
                </section>
              ) : result ? (
                <>
                  <div className="eyebrow">
                    <GraduationCap size={15} /> SESSION COMPLETE
                  </div>
                  <h1>Every attempt is progress.</h1>
                  <p className="subtitle">
                    Review the reasoning. Take the understanding with you.
                  </p>
                  <section className="card result-summary">
                    <div className="score">
                      {result.score}
                      <span>/{result.total}</span>
                    </div>
                    <div>
                      <h2>
                        {result.score >= 8
                          ? "A strong understanding."
                          : "A good place to grow."}
                      </h2>
                      <p>
                        {Math.round((result.score / result.total) * 100)}%
                        correct · {view?.quiz.filename}
                      </p>
                    </div>
                    <button
                      className="secondary"
                      onClick={() => {
                        setResult(null);
                        setAnswers(Array(10).fill(-1));
                        setIndex(0);
                      }}
                    >
                      Retake quiz
                    </button>
                  </section>
                  {result.review.map((q, i) => (
                    <section className="card review" key={i}>
                      <div className="review-label">
                        <span>QUESTION {i + 1}</span>
                        <b className={q.correct ? "correct" : "incorrect"}>
                          {q.correct ? "Correct" : "Needs review"}
                        </b>
                      </div>
                      <h3>{q.prompt}</h3>
                      <p>
                        Your answer:{" "}
                        <strong>{q.options[q.selectedIndex]}</strong>
                      </p>
                      {!q.correct && (
                        <p>
                          Correct answer:{" "}
                          <strong>{q.options[q.correctIndex]}</strong>
                        </p>
                      )}
                      <div className="explanation">
                        <strong>Why this answer</strong>
                        <p>{q.explanation}</p>
                        <blockquote>{q.sourceExcerpt}</blockquote>
                        <small>
                          Supporting excerpt from your document. AI can make
                          mistakes; verify against the source.
                        </small>
                      </div>
                    </section>
                  ))}
                  <button className="primary" onClick={reset}>
                    Create another quiz <ArrowRight size={17} />
                  </button>
                </>
              ) : view?.quiz.status === "READY" ? (
                <>
                  <div className="eyebrow">
                    <BookOpen size={15} /> YOUR FOCUSED STUDY SESSION
                  </div>
                  <h1>Read it. Recall it. Own it.</h1>
                  <p className="subtitle file-label">
                    <FileText size={16} />
                    {view.quiz.filename}
                  </p>
                  <section className="card quiz">
                    <div className="quiz-meta">
                      <span>Question {index + 1} of 10</span>
                      <span>{answered}/10 answered</span>
                    </div>
                    <div className="progress">
                      <div style={{ width: `${answered * 10}%` }} />
                    </div>
                    <h2>{view.questions[index].prompt}</h2>
                    <div className="options">
                      {view.questions[index].options.map((option, i) => (
                        <button
                          key={i}
                          className={`option ${answers[index] === i ? "selected" : ""}`}
                          onClick={() =>
                            setAnswers((old) =>
                              old.map((a, j) => (j === index ? i : a)),
                            )
                          }
                        >
                          <span>{"ABCD"[i]}</span>
                          <strong>{option}</strong>
                          {answers[index] === i && <Check size={18} />}
                        </button>
                      ))}
                    </div>
                    <div className="question-nav">
                      {view.questions.map((_, i) => (
                        <button
                          aria-label={`Question ${i + 1}`}
                          aria-current={index === i ? "step" : undefined}
                          className={`${answers[i] !== -1 ? "answered" : ""} ${index === i ? "current" : ""}`}
                          key={i}
                          onClick={() => setIndex(i)}
                        >
                          {i + 1}
                        </button>
                      ))}
                    </div>
                    <div className="quiz-footer">
                      <button
                        className="secondary"
                        disabled={index === 0}
                        onClick={() => setIndex(index - 1)}
                      >
                        <ChevronLeft size={16} /> Previous
                      </button>
                      {answered === 10 ? (
                        <button
                          className="primary"
                          onClick={submit}
                          disabled={busy}
                        >
                          {busy ? (
                            <LoaderCircle size={17} className="spin" />
                          ) : null}{" "}
                          Submit answers <ArrowRight size={17} />
                        </button>
                      ) : (
                        <button
                          className="primary"
                          disabled={index === 9}
                          onClick={() => setIndex(index + 1)}
                        >
                          Next question <ArrowRight size={17} />
                        </button>
                      )}
                    </div>
                  </section>
                </>
              ) : (
                <section className="card processing">
                  <LoaderCircle size={30} className="spin" />
                  <p>Loading your quiz…</p>
                </section>
              )}
            </>
          ) : (
            <>
              <div className="eyebrow">
                <Sparkles size={15} /> LESS REREADING. MORE UNDERSTANDING.
              </div>
              <h1>
                Your documents.
                <br />
                Your next <span>aha moment.</span>
              </h1>
              <p className="subtitle">
                Turn your notes into a quiz that helps it all click.
                <br className="desktop-break" /> Upload a document. Challenge
                yourself. Learn what sticks.
              </p>
              <div className="create-grid">
                <section className="card upload-card">
                  <div className="card-title">
                    <div className="section-icon">
                      <UploadCloud size={20} />
                    </div>
                    <div>
                      <h2>Start with a document</h2>
                      <p>We will turn it into 10 thoughtful questions.</p>
                    </div>
                    <span className="step-number">01</span>
                  </div>
                  <input
                    ref={input}
                    className="file-input"
                    type="file"
                    accept=".pdf,.txt"
                    aria-label="Upload a PDF or TXT document"
                    onChange={(e) => {
                      choose(e.target.files?.[0]);
                      e.target.value = "";
                    }}
                  />
                  <button
                    className={`dropzone ${dragging ? "dragging" : ""} ${file ? "has-file" : ""}`}
                    onClick={() => input.current?.click()}
                    onDragOver={(e) => {
                      e.preventDefault();
                      setDragging(true);
                    }}
                    onDragLeave={() => setDragging(false)}
                    onDrop={(e) => {
                      e.preventDefault();
                      setDragging(false);
                      choose(e.dataTransfer.files[0]);
                    }}
                  >
                    <span className="upload-icon">
                      {file ? (
                        <FileText size={29} />
                      ) : (
                        <UploadCloud size={29} />
                      )}
                    </span>
                    <strong>
                      {file ? file.name : "Drop your document here"}
                    </strong>
                    <span>
                      {file ? (
                        `${(file.size / 1024).toFixed(0)} KB · Click to choose another file`
                      ) : (
                        <>
                          or <b>browse files</b> to get started
                        </>
                      )}
                    </span>
                    <small>PDF or TXT · Up to 10 MB</small>
                  </button>
                  <div className="settings-line">
                    <span>
                      <BookOpen size={16} /> 10 questions
                    </span>
                    <span>
                      <GraduationCap size={17} /> Multiple choice
                    </span>
                    <span>
                      <FileText size={16} /> Source excerpts
                    </span>
                  </div>
                  <label className="consent">
                    <input
                      type="checkbox"
                      checked={consent}
                      onChange={(e) => setConsent(e.target.checked)}
                    />
                    <span>
                      I agree to send this document's text to Google's Gemini
                      API to generate a quiz. Free-tier data may be used to
                      improve Google's products. Use non-sensitive study
                      material.
                    </span>
                  </label>
                  <button
                    className="primary generate"
                    disabled={!file || !consent || busy}
                    onClick={upload}
                  >
                    {busy ? (
                      <LoaderCircle size={18} className="spin" />
                    ) : (
                      <Sparkles size={18} />
                    )}{" "}
                    {busy ? "Uploading document…" : "Generate my quiz"}
                    <ArrowRight size={18} />
                  </button>
                  <div className="safe-note">
                    <ShieldCheck size={14} /> Original files are not retained.
                    Extracted text is cleared after processing.
                  </div>
                </section>
                <aside className="guide">
                  <div className="guide-label">FROM NOTES TO KNOWLEDGE</div>
                  <h2>
                    A better way
                    <br />
                    to check what you know.
                  </h2>
                  <div className="guide-step">
                    <span>1</span>
                    <div>
                      <strong>Bring your material</strong>
                      <p>
                        Lecture notes, study guides, or that chapter you just
                        finished.
                      </p>
                    </div>
                  </div>
                  <div className="guide-step">
                    <span>2</span>
                    <div>
                      <strong>Put your recall to work</strong>
                      <p>Answer 10 questions grounded in your document.</p>
                    </div>
                  </div>
                  <div className="guide-step">
                    <span>3</span>
                    <div>
                      <strong>Learn from every answer</strong>
                      <p>
                        See your score, explanations, and supporting source
                        excerpts.
                      </p>
                    </div>
                  </div>
                  <div className="sample">
                    <FileText size={20} />
                    <div>
                      <strong>Just looking around?</strong>
                      <a href="/sample-notes.txt" download>
                        Download sample study notes <ArrowRight size={13} />
                      </a>
                    </div>
                  </div>
                </aside>
              </div>
              <div className="bottom-strip">
                <span>
                  <ShieldCheck size={17} /> Your material, your control
                </span>
                <p>
                  Text PDFs up to 100 pages and 60,000 extracted characters.
                  Scans are not supported yet.
                </p>
              </div>
              <div className="recent-heading">
                <h2>Pick up where you left off</h2>
                <button
                  className="text-button"
                  onClick={() => setTab("library")}
                >
                  View all quizzes <ArrowRight size={15} />
                </button>
              </div>
              {!history.length ? (
                <div className="empty-inline">
                  <div className="empty-icon">
                    <BookOpen size={24} />
                  </div>
                  <div>
                    <strong>Your first quiz is one upload away.</strong>
                    <p>Your recent quizzes will appear here.</p>
                  </div>
                  <span>LET'S BEGIN ↗</span>
                </div>
              ) : (
                <div className="recent-cards">
                  {history.slice(0, 3).map((q) => (
                    <button
                      className="card recent-card"
                      key={q.id}
                      onClick={() => openQuiz(q)}
                    >
                      <FileText size={23} />
                      <strong>{q.filename}</strong>
                      <small>
                        {q.status.toLowerCase()} ·{" "}
                        {new Date(q.createdAt).toLocaleDateString()}
                      </small>
                      <ArrowRight size={17} />
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
          <footer>
            <span>Built for the way you learn.</span>
            <span>
              DocuMind AI <span className="footer-dot">·</span> A little
              smarter, every session.
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
