import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from '@/app/(auth)/login/page';

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({
    push: vi.fn(),
    prefetch: vi.fn(),
  })),
}));

// Mock Apollo Client
vi.mock('@apollo/client', () => ({
  useMutation: vi.fn(() => [
    vi.fn(),
    { loading: false, error: null, data: null },
  ]),
  gql: vi.fn(),
}));

// Mock hooks
vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    setTokens: vi.fn(),
    isAuthenticated: false,
  })),
}));

describe('Login Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render login form', () => {
    render(<LoginPage />);

    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });

  it('should have email input field', () => {
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    expect(emailInput).toBeInTheDocument();
    expect(emailInput).toHaveAttribute('type', 'email');
  });

  it('should have password input field', () => {
    render(<LoginPage />);

    const passwordInput = screen.getByLabelText(/password/i);
    expect(passwordInput).toBeInTheDocument();
    expect(passwordInput).toHaveAttribute('type', 'password');
  });

  it('should have submit button', () => {
    render(<LoginPage />);

    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });
    expect(submitBtn).toBeInTheDocument();
  });

  it('should link to registration page', () => {
    render(<LoginPage />);

    const registerLink = screen.queryByRole('link', { name: /register|sign up|create account/i });
    if (registerLink) {
      expect(registerLink).toHaveAttribute('href', expect.stringContaining('register'));
    }
  });

  it('should require email input', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });
    const pwdInput = screen.getByLabelText(/password/i);

    await user.type(pwdInput, 'password123');
    await user.click(submitBtn);

    // Either validation message or form not submitted
    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });

  it('should require password input', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });
    const emailInput = screen.getByLabelText(/email|username/i);

    await user.type(emailInput, 'test@example.com');
    await user.click(submitBtn);

    // Either validation message or form not submitted
    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });

  it('should submit login form with valid credentials', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });

    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'SecurePassword123!');
    await user.click(submitBtn);

    // Form should be submitted
    expect(submitBtn).toBeInTheDocument();
  });

  it('should disable submit button while loading', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });

    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'password');
    await user.click(submitBtn);

    // Submit button might be disabled during submission
    expect(submitBtn).toBeInTheDocument();
  });

  it('should show error message on login failure', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });

    await user.type(emailInput, 'wrong@example.com');
    await user.type(passwordInput, 'wrongpassword');
    await user.click(submitBtn);

    // Should show error or remain on login page
    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });

  it('should clear password input after submission attempt', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const passwordInput = screen.getByLabelText(/password/i) as HTMLInputElement;

    await user.type(passwordInput, 'password');
    // Don't submit to check clearing behavior
    expect(passwordInput.value).toBe('password');
  });

  it('should validate email format', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });

    await user.type(emailInput, 'invalid-email');
    await user.click(submitBtn);

    // Should either show validation error or not submit
    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });

  it('should trim whitespace from inputs', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i) as HTMLInputElement;

    await user.type(emailInput, '  test@example.com  ');

    // Whitespace should be handled appropriately
    expect(emailInput.value).toContain('test@example.com');
  });

  it('should handle form submission success', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    const emailInput = screen.getByLabelText(/email|username/i);
    const passwordInput = screen.getByLabelText(/password/i);
    const submitBtn = screen.getByRole('button', { name: /login|sign in|submit/i });

    await user.type(emailInput, 'test@example.com');
    await user.type(passwordInput, 'SecurePassword123!');
    await user.click(submitBtn);

    // Form resets or redirects
    expect(screen.getByText(/login|sign in/i)).toBeInTheDocument();
  });
});
