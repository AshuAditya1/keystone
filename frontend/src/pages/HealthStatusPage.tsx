import { Alert, Box, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { getBackendHealth } from '../api/health';

export function HealthStatusPage() {
  const healthQuery = useQuery({
    queryKey: ['backend-health'],
    queryFn: getBackendHealth,
    retry: false,
  });

  return (
    <Paper component="section" elevation={2} sx={{ p: { xs: 3, sm: 4 } }}>
      <Stack spacing={3}>
        <Box>
          <Typography component="h1" variant="h4" gutterBottom>
            System health
          </Typography>
          <Typography color="text.secondary">
            This Day 1 page verifies connectivity between the web application and the KEYSTONE
            backend.
          </Typography>
        </Box>

        {healthQuery.isPending && (
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }} role="status">
            <CircularProgress size={24} aria-label="Checking backend health" />
            <Typography>Checking backend health…</Typography>
          </Stack>
        )}

        {healthQuery.isSuccess && healthQuery.data.status === 'UP' && (
          <Alert severity="success">Backend is healthy.</Alert>
        )}

        {healthQuery.isSuccess && healthQuery.data.status !== 'UP' && (
          <Alert severity="warning">Backend reported status: {healthQuery.data.status}</Alert>
        )}

        {healthQuery.isError && (
          <Alert severity="error">
            Backend health could not be reached. Confirm PostgreSQL and the backend are running,
            then refresh the page.
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}
