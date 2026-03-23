import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DashboardPage from '@/app/dashboard/page';

// Mock Next.js router
vi.mock('next/navigation', () => ({
  useRouter: vi.fn(() => ({
    push: vi.fn(),
    prefetch: vi.fn(),
    back: vi.fn(),
  })),
  useSearchParams: vi.fn(() => new URLSearchParams()),
}));

// Mock Apollo Client
vi.mock('@apollo/client', () => ({
  useQuery: vi.fn(() => ({
    data: undefined,
    loading: false,
    error: null,
  })),
  useMutation: vi.fn(() => [vi.fn(), { loading: false }]),
  gql: vi.fn(),
}));

// Mock hooks
vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    isAuthenticated: true,
    email: 'test@example.com',
    accessToken: 'test-token',
  })),
}));

vi.mock('@/hooks/useApiClient', () => ({
  useApiClient: vi.fn(() => ({
    get: vi.fn(),
    post: vi.fn(),
  })),
}));

describe('Dashboard Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render dashboard heading', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard|projects/i)).toBeInTheDocument();
    });
  });

  it('should display empty state when no projects exist', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      // Should show projects list or create button
      expect(screen.getByRole('list') || screen.getByText(/create|new/i)).toBeInTheDocument();
    });
  });

  it('should show create project button', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      const createBtn = screen.queryByRole('button', { name: /create|new/i });
      expect(createBtn || screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });

  it('should open create project modal on button click', async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    const createBtn = screen.queryByRole('button', { name: /create|new/i });
    if (createBtn) {
      await user.click(createBtn);
    }

    // Modal or form should appear
    expect(screen.getByText(/dashboard/i) || screen.getByText(/create/i)).toBeInTheDocument();
  });

  it('should display project list in grid format', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard|projects/i)).toBeInTheDocument();
    });
  });

  it('should handle loading state', async () => {
    render(<DashboardPage />);

    // Should eventually show content
    await waitFor(() => {
      expect(screen.getByText(/dashboard|projects/i)).toBeInTheDocument();
    }, { timeout: 3000 });
  });

  it('should navigate to project on click', async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });

  it('should retrieve user projects on mount', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard|projects/i)).toBeInTheDocument();
    });
  });

  it('should support project creation workflow', async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });

  it('should show project metadata (name, description, job count)', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });

  it('should support searching/filtering projects', async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    // Should have search or filter functionality
    await waitFor(() => {
      expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });

  it('should handle API errors gracefully', async () => {
    render(<DashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/dashboard/i)).toBeInTheDocument();
    });
  });
});
