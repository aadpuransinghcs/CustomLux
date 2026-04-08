import { Outlet, useLocation, useNavigate } from 'react-router';
import { AppBar, Toolbar, Typography, BottomNavigation, BottomNavigationAction, Box } from '@mui/material';
import { Home, TrendingUp } from 'lucide-react';

export function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const currentPath = location.pathname;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: '#f5f5f5' }}>
      {/* Top App Bar */}
      <AppBar position="static" elevation={0}>
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            CustomLux
          </Typography>
        </Toolbar>
      </AppBar>

      {/* Main Content */}
      <Box sx={{ flex: 1, overflow: 'auto', pb: 7 }}>
        <Outlet />
      </Box>

      {/* Bottom Navigation */}
      <BottomNavigation
        value={currentPath === '/' ? 0 : 1}
        onChange={(_, newValue) => {
          if (newValue === 0) navigate('/');
          if (newValue === 1) navigate('/curve-editor');
        }}
        sx={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          borderTop: '1px solid #e0e0e0',
        }}
      >
        <BottomNavigationAction
          label="Dashboard"
          icon={<Home size={24} />}
        />
        <BottomNavigationAction
          label="Curve Editor"
          icon={<TrendingUp size={24} />}
        />
      </BottomNavigation>
    </Box>
  );
}
