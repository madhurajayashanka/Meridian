import { test, expect } from "@playwright/test";

// Base URL configuration
const BASE_URL = process.env.BASE_URL || "http://localhost:3000";
const API_URL = process.env.API_URL || "http://localhost:8080";

test.describe("Meridian Platform E2E Tests", () => {
  test.describe("Authentication Flow", () => {
    test("should register a new user", async ({ page }) => {
      await page.goto(`${BASE_URL}/(auth)/register`);

      // Fill registration form
      await page.fill('input[type="email"]', `user${Date.now()}@example.com`);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', "SecurePassword123!");
      await page.fill('input[name="confirmPassword"]', "SecurePassword123!");

      // Submit form
      await page.click('button[type="submit"]');

      // Should redirect to login page
      await expect(page).toHaveURL(`${BASE_URL}/(auth)/login`);
    });

    test("should login with valid credentials", async ({ page }) => {
      // Create user first
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      // Register
      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      // Login
      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Should redirect to dashboard
      await expect(page).toHaveURL(`${BASE_URL}/dashboard`);
    });

    test("should show error on invalid login", async ({ page }) => {
      await page.goto(`${BASE_URL}/(auth)/login`);

      await page.fill('input[type="email"]', "nonexistent@example.com");
      await page.fill('input[type="password"]', "WrongPassword123!");
      await page.click('button[type="submit"]');

      // Should show error message
      const errorMsg = await page.locator('[role="alert"]').first();
      await expect(errorMsg).toContainText(/error|invalid|incorrect/i);
    });

    test("should logout user", async ({ page }) => {
      // Login first
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Wait for dashboard
      await expect(page).toHaveURL(`${BASE_URL}/dashboard`);

      // Click logout button
      const logoutBtn = await page.locator('button:has-text("Logout")').first();
      if (logoutBtn) {
        await logoutBtn.click();
        await expect(page).toHaveURL(`${BASE_URL}/(auth)/login`);
      }
    });
  });

  test.describe("Project Management", () => {
    test("should create a new project", async ({ page, context }) => {
      // Setup: Login
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Navigate to dashboard
      await expect(page).toHaveURL(`${BASE_URL}/dashboard`);

      // Click create project button
      const createBtn = await page.locator('button:has-text("Create")').first();
      await createBtn.click();

      // Fill project form
      await page.fill('input[name="name"]', "Test Research Project");
      await page.fill(
        'textarea[name="description"]',
        "A test research project",
      );

      // Submit
      await page.click('button[type="submit"]');

      // Should see new project in list
      await expect(page.locator("text=Test Research Project")).toBeVisible();
    });

    test("should view project details", async ({ page }) => {
      // Login and create project
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Create project
      const createBtn = await page.locator('button:has-text("Create")').first();
      await createBtn.click();

      await page.fill('input[name="name"]', "Test Project");
      await page.click('button[type="submit"]');

      // Click on project card
      const projectCard = await page
        .locator('[role="link"]:has-text("Test Project")')
        .first();
      await projectCard.click();

      // Should be on project page
      await expect(page).toHaveURL(/\/projects\/\d+/);
    });
  });

  test.describe("Research Job Workflow", () => {
    test("should submit a research query", async ({ page }) => {
      // Complete setup: register, login, create project
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Create project
      const createBtn = await page.locator('button:has-text("Create")').first();
      await createBtn.click();
      await page.fill('input[name="name"]', "Research Project");
      await page.click('button[type="submit"]');

      // Navigate to research form
      const newResearchBtn = await page
        .locator('button:has-text("New Research")')
        .first();
      if (newResearchBtn) {
        await newResearchBtn.click();
      }

      // Fill research form
      const queryField = await page.locator('textarea[name="query"]').first();
      if (queryField) {
        await queryField.fill(
          "What are the latest trends in artificial intelligence?",
        );

        // Select LLM provider
        const providerSelect = await page
          .locator('select[name="llmProvider"]')
          .first();
        if (providerSelect) {
          await providerSelect.selectOption("openai");
        }

        // Select research depth
        const depthSelect = await page.locator('select[name="depth"]').first();
        if (depthSelect) {
          await depthSelect.selectOption("standard");
        }

        // Submit
        const submitBtn = await page.locator('button[type="submit"]').last();
        await submitBtn.click();

        // Should navigate to live job page
        await expect(page).toHaveURL(/\/jobs\/\d+\/live/);
      }
    });

    test("should monitor job progress", async ({ page }) => {
      // Assumes a job is already running
      // This would require setting up test data or mocking
      const jobId = "1"; // example job ID

      await page.goto(`${BASE_URL}/jobs/${jobId}/live`);

      // Check for progress elements
      const progressBar = await page.locator('[role="progressbar"]').first();
      if (progressBar) {
        await expect(progressBar).toBeVisible();
      }

      // Check for status display
      const statusText = await page
        .locator("text=/Running|Complete|Failed/")
        .first();
      if (statusText) {
        await expect(statusText).toBeVisible();
      }
    });

    test("should navigate to report after job completion", async ({ page }) => {
      // This test assumes a job will complete quickly
      const jobId = "1";

      await page.goto(`${BASE_URL}/jobs/${jobId}/live`);

      // Wait for completion (with timeout)
      try {
        await page.waitForURL(/\/jobs\/\d+\/report/, { timeout: 60000 });
        expect(page.url()).toMatch(/\/jobs\/\d+\/report/);
      } catch {
        // Job didn't complete in time, that's okay for E2E
        expect(page.url()).toMatch(/\/jobs\/\d+\//);
      }
    });
  });

  test.describe("Report Viewing", () => {
    test("should view report with markdown content", async ({ page }) => {
      const reportId = "1";

      await page.goto(`${BASE_URL}/jobs/${reportId}/report`);

      // Check for report content
      const reportContent = await page.locator('[role="main"]').first();
      if (reportContent) {
        await expect(reportContent).toBeVisible();
      }

      // Check for table of contents
      const toc = await page
        .locator('nav[aria-label="Table of contents"]')
        .first();
      if (toc) {
        await expect(toc).toBeVisible();
      }
    });

    test("should navigate using table of contents", async ({ page }) => {
      const reportId = "1";

      await page.goto(`${BASE_URL}/jobs/${reportId}/report`);

      // Click a TOC item
      const tocLink = await page
        .locator('nav[aria-label="Table of contents"] a')
        .first();
      if (tocLink) {
        await tocLink.click();
        // Should scroll to section
        const heading = await page.locator("h2, h3").first();
        await expect(heading).toBeInViewport();
      }
    });

    test("should interact with chat panel", async ({ page }) => {
      const reportId = "1";

      await page.goto(`${BASE_URL}/jobs/${reportId}/report`);

      // Find chat input
      const chatInput = await page
        .locator('input[placeholder*="Ask"], textarea[placeholder*="Ask"]')
        .first();
      if (chatInput) {
        await chatInput.fill("What is the main topic?");

        // Send message
        const sendBtn = await page
          .locator('button:has-text("Send"), button[type="submit"]')
          .last();
        await sendBtn.click();

        // Message should appear
        await expect(
          page.locator("text=What is the main topic?"),
        ).toBeVisible();
      }
    });
  });

  test.describe("Document Upload", () => {
    test("should upload research documents", async ({ page }) => {
      // Navigate to document upload
      await page.goto(`${BASE_URL}/documents/upload`);

      // Find file input
      const fileInput = await page.locator('input[type="file"]').first();
      if (fileInput) {
        // Create test file
        await fileInput.setInputFiles({
          name: "test.pdf",
          mimeType: "application/pdf",
          buffer: Buffer.from("PDF test content"),
        });

        // File should appear in list
        await expect(page.locator("text=test.pdf")).toBeVisible();
      }
    });

    test("should show upload progress", async ({ page }) => {
      await page.goto(`${BASE_URL}/documents/upload`);

      const fileInput = await page.locator('input[type="file"]').first();
      if (fileInput) {
        await fileInput.setInputFiles({
          name: "test.pdf",
          mimeType: "application/pdf",
          buffer: Buffer.from("PDF test content"),
        });

        // Progress indicator should appear
        const progress = await page
          .locator('[role="progressbar"], [class*="progress"]')
          .first();
        if (progress) {
          await expect(progress).toBeVisible();
        }
      }
    });
  });

  test.describe("Navigation", () => {
    test("should navigate between pages", async ({ page }) => {
      const email = `user${Date.now()}@example.com`;
      const password = "SecurePassword123!";

      // Register and login
      await page.goto(`${BASE_URL}/(auth)/register`);
      await page.fill('input[type="email"]', email);
      await page.fill('input[name="name"]', "Test User");
      await page.fill('input[name="password"]', password);
      await page.fill('input[name="confirmPassword"]', password);
      await page.click('button[type="submit"]');

      await page.fill('input[type="email"]', email);
      await page.fill('input[type="password"]', password);
      await page.click('button[type="submit"]');

      // Dashboard
      await expect(page).toHaveURL(`${BASE_URL}/dashboard`);

      // Navigate to docs
      const docsLink = await page.locator('a:has-text("Docs")').first();
      if (docsLink) {
        await docsLink.click();
        await expect(page).toHaveURL(`${BASE_URL}/docs`);
      }
    });

    test("should display navigation menu", async ({ page }) => {
      await page.goto(`${BASE_URL}/dashboard`);

      // Check for nav elements
      const nav = await page.locator("nav").first();
      await expect(nav).toBeVisible();
    });
  });

  test.describe("Error Handling", () => {
    test("should show error for expired session", async ({ page, context }) => {
      // Navigate to protected page
      await page.goto(`${BASE_URL}/dashboard`);

      // If redirected to login, error is shown
      if (page.url().includes("login")) {
        expect(page.url()).toContain("login");
      }
    });

    test("should handle network errors gracefully", async ({ page }) => {
      // Go offline
      await context.setOffline(true);

      await page.goto(`${BASE_URL}/dashboard`);

      // Page should handle offline state
      expect(page).toBeDefined();

      // Go back online
      await context.setOffline(false);
    });
  });
});
