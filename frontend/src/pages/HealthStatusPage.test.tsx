import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getBackendHealth } from '../api/health';
import { HealthStatusPage } from './HealthStatusPage';

vi.mock('../api/health', () => ({
  getBackendHealth: vi.fn(),
}));

const mockedGetBackendHealth = vi.mocked(getBackendHealth);

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <HealthStatusPage />
    </QueryClientProvider>,
  );
}

describe('HealthStatusPage', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('shows a loading state while checking the backend', () => {
    mockedGetBackendHealth.mockReturnValue(new Promise(() => undefined));

    renderPage();

    expect(screen.getByText('Checking backend health…')).toBeInTheDocument();
  });

  it('shows success when the backend reports UP', async () => {
    mockedGetBackendHealth.mockResolvedValue({ status: 'UP' });

    renderPage();

    expect(await screen.findByText('Backend is healthy.')).toBeInTheDocument();
  });

  it('shows an actionable error when the backend cannot be reached', async () => {
    mockedGetBackendHealth.mockRejectedValue(new Error('Network error'));

    renderPage();

    expect(await screen.findByText(/Backend health could not be reached/)).toBeInTheDocument();
  });
});
