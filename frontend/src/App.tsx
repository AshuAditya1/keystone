import { AppBar, Box, Container, Toolbar, Typography } from '@mui/material';
import { Navigate, Route, Routes } from 'react-router-dom';
import { HealthStatusPage } from './pages/HealthStatusPage';

export function App() {
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ fontWeight: 700, letterSpacing: 1 }}>
            KEYSTONE
          </Typography>
        </Toolbar>
      </AppBar>
      <Container component="main" maxWidth="md" sx={{ py: { xs: 4, sm: 7 } }}>
        <Routes>
          <Route path="/" element={<HealthStatusPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Container>
    </Box>
  );
}
