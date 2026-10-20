import { test, expect } from "@playwright/test";

const quiz = {
  id: "9b9f65e7-1ce1-421a-a353-958f91719087",
  filename: "notes.txt",
  status: "READY",
  error: null,
  createdAt: "2026-10-20T10:00:00Z",
};
const questions = Array.from({ length: 10 }, (_, index) => ({
  index,
  prompt: `What does concept ${index + 1} describe?`,
  options: ["Correct choice", "Second choice", "Third choice", "Fourth choice"],
}));

test("upload, consent, answer navigation, score review, and retake", async ({
  page,
}) => {
  let created = false;
  await page.route("**/api/quizzes**", async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (request.method() === "POST" && pathname.endsWith("/attempts")) {
      const answers = request.postDataJSON().answers as number[];
      expect(answers).toEqual(Array(10).fill(0));
      return route.fulfill({
        json: {
          score: 10,
          total: 10,
          review: questions.map((q) => ({
            ...q,
            selectedIndex: 0,
            correctIndex: 0,
            correct: true,
            explanation: "This is supported by the source.",
            sourceExcerpt: "The source explains the concept in this excerpt.",
          })),
        },
      });
    }
    if (request.method() === "POST") {
      created = true;
      return route.fulfill({
        status: 202,
        json: { ...quiz, status: "QUEUED" },
      });
    }
    if (pathname === "/api/quizzes")
      return route.fulfill({ json: created ? [quiz] : [] });
    return route.fulfill({ json: { quiz, questions } });
  });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: /Your documents/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Generate my quiz" }),
  ).toBeDisabled();
  await page
    .getByLabel("Upload a PDF or TXT document")
    .setInputFiles({
      name: "notes.txt",
      mimeType: "text/plain",
      buffer: Buffer.from("A document for the mocked API test. ".repeat(20)),
    });
  await expect(
    page.getByRole("button", { name: "Generate my quiz" }),
  ).toBeDisabled();
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Generate my quiz" }).click();
  for (let i = 0; i < 10; i++) {
    await expect(
      page.getByRole("heading", { name: questions[i].prompt }),
    ).toBeVisible();
    await page.getByRole("button", { name: "A Correct choice" }).click();
    if (i < 9)
      await page.getByRole("button", { name: "Next question" }).click();
  }
  await page.getByRole("button", { name: "Submit answers" }).click();
  await expect(
    page.getByRole("heading", { name: "Every attempt is progress." }),
  ).toBeVisible();
  await expect(page.getByText("100% correct", { exact: false })).toBeVisible();
  await expect(
    page.getByText("The source explains the concept in this excerpt."),
  ).toHaveCount(10);
  await page.getByRole("button", { name: "Retake quiz" }).click();
  await expect(page.getByText("0/10 answered")).toBeVisible();
});

test("handles server upload rejection with a visible error", async ({
  page,
}) => {
  await page.route("**/api/quizzes", (route) =>
    route.request().method() === "GET"
      ? route.fulfill({ json: [] })
      : route.fulfill({
          status: 400,
          json: { message: "This file has no readable text." },
        }),
  );
  await page.goto("/");
  await page
    .getByLabel("Upload a PDF or TXT document")
    .setInputFiles({
      name: "scan.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from("invalid pdf"),
    });
  await page.getByRole("checkbox").check();
  await page.getByRole("button", { name: "Generate my quiz" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This file has no readable text.",
  );
});

test("mobile upload controls fit the viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route("**/api/quizzes", (route) => route.fulfill({ json: [] }));
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "Generate my quiz" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});
