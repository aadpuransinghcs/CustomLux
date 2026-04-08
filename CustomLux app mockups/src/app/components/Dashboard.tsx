import { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  List,
  ListItem,
  ListItemText,
  IconButton,
  Chip,
  Fab,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Slider,
  Switch,
  FormControlLabel,
} from '@mui/material';
import { Sun, Smartphone, Plus, Edit2, Trash2 } from 'lucide-react';

interface AppProfile {
  id: string;
  appName: string;
  brightness: number;
  enabled: boolean;
}

export function Dashboard() {
  const [luxValue, setLuxValue] = useState(245);
  const [currentBrightness, setCurrentBrightness] = useState(68);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [appProfiles, setAppProfiles] = useState<AppProfile[]>([
    { id: '1', appName: 'YouTube', brightness: 80, enabled: true },
    { id: '2', appName: 'Kindle', brightness: 35, enabled: true },
    { id: '3', appName: 'Camera', brightness: 100, enabled: true },
  ]);
  const [newProfile, setNewProfile] = useState({
    appName: '',
    brightness: 50,
    enabled: true,
  });

  // Simulate live lux data updates
  useState(() => {
    const interval = setInterval(() => {
      setLuxValue((prev) => Math.max(0, Math.min(1000, prev + (Math.random() - 0.5) * 20)));
      setCurrentBrightness((prev) => Math.max(0, Math.min(100, prev + (Math.random() - 0.5) * 3)));
    }, 2000);
    return () => clearInterval(interval);
  });

  const handleAddProfile = () => {
    if (newProfile.appName.trim()) {
      setAppProfiles([
        ...appProfiles,
        {
          id: Date.now().toString(),
          ...newProfile,
        },
      ]);
      setNewProfile({ appName: '', brightness: 50, enabled: true });
      setDialogOpen(false);
    }
  };

  const handleDeleteProfile = (id: string) => {
    setAppProfiles(appProfiles.filter((p) => p.id !== id));
  };

  const handleToggleProfile = (id: string) => {
    setAppProfiles(
      appProfiles.map((p) =>
        p.id === id ? { ...p, enabled: !p.enabled } : p
      )
    );
  };

  return (
    <Box sx={{ p: 2 }}>
      {/* Current Environment Card */}
      <Card elevation={2} sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Current Environment
          </Typography>

          <Box sx={{ mt: 3 }}>
            {/* Lux Reading */}
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  bgcolor: 'primary.main',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  mr: 2,
                }}
              >
                <Sun size={28} color="white" />
              </Box>
              <Box sx={{ flex: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Ambient Light
                </Typography>
                <Typography variant="h4">{Math.round(luxValue)} lux</Typography>
              </Box>
            </Box>

            {/* Screen Brightness */}
            <Box sx={{ display: 'flex', alignItems: 'center' }}>
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  borderRadius: '50%',
                  bgcolor: 'secondary.main',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  mr: 2,
                }}
              >
                <Smartphone size={28} color="white" />
              </Box>
              <Box sx={{ flex: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  Screen Luminance
                </Typography>
                <Typography variant="h4">{Math.round(currentBrightness)}%</Typography>
              </Box>
            </Box>
          </Box>

          {/* Status Chip */}
          <Box sx={{ mt: 3 }}>
            <Chip
              label="Auto-adjusting"
              color="primary"
              size="small"
              sx={{ mr: 1 }}
            />
            <Chip
              label="Indoor"
              variant="outlined"
              size="small"
            />
          </Box>
        </CardContent>
      </Card>

      {/* App Profiles Section */}
      <Card elevation={2}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            App Profiles
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Set custom brightness levels for specific apps
          </Typography>

          {appProfiles.length === 0 ? (
            <Box sx={{ py: 4, textAlign: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                No app profiles yet. Tap + to add one.
              </Typography>
            </Box>
          ) : (
            <List sx={{ pt: 0 }}>
              {appProfiles.map((profile) => (
                <ListItem
                  key={profile.id}
                  sx={{
                    bgcolor: profile.enabled ? 'transparent' : 'grey.100',
                    borderRadius: 1,
                    mb: 1,
                    px: 2,
                  }}
                  secondaryAction={
                    <Box>
                      <IconButton
                        edge="end"
                        onClick={() => handleDeleteProfile(profile.id)}
                        size="small"
                      >
                        <Trash2 size={18} />
                      </IconButton>
                    </Box>
                  }
                >
                  <Box sx={{ flex: 1, mr: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
                      <Typography variant="body1" sx={{ flex: 1 }}>
                        {profile.appName}
                      </Typography>
                      <Switch
                        checked={profile.enabled}
                        onChange={() => handleToggleProfile(profile.id)}
                        size="small"
                      />
                    </Box>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body2" color="text.secondary" sx={{ minWidth: 40 }}>
                        {profile.brightness}%
                      </Typography>
                      <Box
                        sx={{
                          flex: 1,
                          height: 4,
                          bgcolor: 'grey.300',
                          borderRadius: 2,
                          overflow: 'hidden',
                        }}
                      >
                        <Box
                          sx={{
                            width: `${profile.brightness}%`,
                            height: '100%',
                            bgcolor: 'primary.main',
                          }}
                        />
                      </Box>
                    </Box>
                  </Box>
                </ListItem>
              ))}
            </List>
          )}
        </CardContent>
      </Card>

      {/* Add Profile FAB */}
      <Fab
        color="primary"
        sx={{ position: 'fixed', bottom: 80, right: 16 }}
        onClick={() => setDialogOpen(true)}
      >
        <Plus />
      </Fab>

      {/* Add Profile Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth>
        <DialogTitle>Add App Profile</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <TextField
              fullWidth
              label="App Name"
              value={newProfile.appName}
              onChange={(e) =>
                setNewProfile({ ...newProfile, appName: e.target.value })
              }
              sx={{ mb: 3 }}
            />

            <Typography gutterBottom>
              Brightness: {newProfile.brightness}%
            </Typography>
            <Slider
              value={newProfile.brightness}
              onChange={(_, value) =>
                setNewProfile({ ...newProfile, brightness: value as number })
              }
              min={0}
              max={100}
              valueLabelDisplay="auto"
              sx={{ mb: 2 }}
            />

            <FormControlLabel
              control={
                <Switch
                  checked={newProfile.enabled}
                  onChange={(e) =>
                    setNewProfile({ ...newProfile, enabled: e.target.checked })
                  }
                />
              }
              label="Enable profile"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleAddProfile} variant="contained">
            Add
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
