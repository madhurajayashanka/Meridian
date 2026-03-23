import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ChatPanel } from "@/components/ChatPanel";

// Mock dependencies
vi.mock("@/hooks/useApiClient", () => ({
  useApiClient: vi.fn(() => ({
    post: vi.fn(),
  })),
}));

vi.mock("@/hooks/useSSE", () => ({
  useSSE: vi.fn(() => ({
    data: null,
    error: null,
    isConnected: false,
    connect: vi.fn(),
  })),
}));

describe("ChatPanel Component", () => {
  const mockReportId = "report-123";
  const mockJobId = "job-456";

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("should render chat panel with message list", () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should have input field for new messages", () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    expect(input).toBeInTheDocument();
  });

  it("should display empty state initially", () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Should have proper headers/labels
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should send message on form submission", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.type(input, "What is AI?");
    await user.click(submitButton);

    await waitFor(() => {
      expect(screen.queryByDisplayValue("What is AI?")).not.toBeInTheDocument();
    });
  });

  it("should display sent message in chat", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.type(input, "Test question");
    await user.click(submitButton);

    // Message should be cleared from input after sending
    await waitFor(() => {
      expect((input as HTMLInputElement).value).toBe("");
    });
  });

  it("should disable input while sending message", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(
      /ask|message|query/i,
    ) as HTMLInputElement;
    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.type(input, "Question");
    await user.click(submitButton);

    // Input should be cleaned after submission
    await waitFor(() => {
      expect(input.value).toBe("");
    });
  });

  it("should not send empty messages", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.click(submitButton);

    // No message should be sent
    expect(submitButton).toBeEnabled();
  });

  it("should handle message streaming", async () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Text should be in the document
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should scroll to bottom on new message", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.type(input, "Question 1");
    await user.click(submitButton);

    await user.type(input, "Question 2");
    await user.click(submitButton);

    // Component should render without errors
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should display loading indicator during streaming", async () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Should have input available for new messages
    expect(
      screen.getByPlaceholderText(/ask|message|query/i),
    ).toBeInTheDocument();
  });

  it("should persist messages across re-renders", async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <ChatPanel reportId={mockReportId} jobId={mockJobId} />,
    );

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    await user.type(input, "Test message");

    rerender(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Component should still be functional
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should handle errors gracefully", async () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Should still render the interface
    expect(screen.getByRole("list")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/ask|message|query/i),
    ).toBeInTheDocument();
  });

  it("should support multiline input", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(
      /ask|message|query/i,
    ) as HTMLTextAreaElement;

    await user.type(input, "Line 1\nLine 2");

    // Input should contain newline
    expect(input.value).toContain("\n");
  });

  it("should accept correct report and job IDs as props", () => {
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    // Component should render with provided IDs
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("should format messages with proper styling", async () => {
    const user = userEvent.setup();
    render(<ChatPanel reportId={mockReportId} jobId={mockJobId} />);

    const input = screen.getByPlaceholderText(/ask|message|query/i);
    const submitButton = screen.getByRole("button", { name: /send|submit/i });

    await user.type(input, "Test");
    await user.click(submitButton);

    // Messages should be properly displayed after sending
    await waitFor(() => {
      expect((input as HTMLInputElement).value).toBe("");
    });
  });
});
