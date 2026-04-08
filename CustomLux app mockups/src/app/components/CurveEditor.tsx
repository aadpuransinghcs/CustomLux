import { useState } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  Button,
  ToggleButtonGroup,
  ToggleButton,
} from '@mui/material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  ReferenceLine,
} from 'recharts';
import { Save, RotateCcw } from 'lucide-react';

interface CurvePoint {
  lux: number;
  brightness: number;
}

const PRESET_CURVES = {
  default: [
    { lux: 0, brightness: 10 },
    { lux: 50, brightness: 20 },
    { lux: 100, brightness: 30 },
    { lux: 200, brightness: 45 },
    { lux: 400, brightness: 65 },
    { lux: 800, brightness: 85 },
    { lux: 1000, brightness: 100 },
  ],
  darkRoom: [
    { lux: 0, brightness: 5 },
    { lux: 50, brightness: 15 },
    { lux: 100, brightness: 25 },
    { lux: 200, brightness: 40 },
    { lux: 400, brightness: 60 },
    { lux: 800, brightness: 80 },
    { lux: 1000, brightness: 95 },
  ],
  outdoor: [
    { lux: 0, brightness: 20 },
    { lux: 50, brightness: 35 },
    { lux: 100, brightness: 50 },
    { lux: 200, brightness: 65 },
    { lux: 400, brightness: 80 },
    { lux: 800, brightness: 95 },
    { lux: 1000, brightness: 100 },
  ],
};

export function CurveEditor() {
  const [curvePoints, setCurvePoints] = useState<CurvePoint[]>(PRESET_CURVES.default);
  const [selectedPreset, setSelectedPreset] = useState<'default' | 'darkRoom' | 'outdoor'>('default');
  const [draggedPoint, setDraggedPoint] = useState<number | null>(null);

  const handlePresetChange = (_: React.MouseEvent<HTMLElement>, newPreset: 'default' | 'darkRoom' | 'outdoor' | null) => {
    if (newPreset !== null) {
      setSelectedPreset(newPreset);
      setCurvePoints(PRESET_CURVES[newPreset]);
    }
  };

  const handleReset = () => {
    setCurvePoints(PRESET_CURVES[selectedPreset]);
  };

  const handlePointDrag = (index: number, event: any) => {
    if (event && event.chartY && event.chartX) {
      const newPoints = [...curvePoints];
      // Convert pixel coordinates back to data values (approximate)
      // This is a simplified version - in a real app you'd calculate exact values
      const newBrightness = Math.max(0, Math.min(100, 100 - (event.chartY / 3)));
      newPoints[index] = { ...newPoints[index], brightness: Math.round(newBrightness) };
      setCurvePoints(newPoints);
    }
  };

  return (
    <Box sx={{ p: 2 }}>
      <Card elevation={2} sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Brightness Curve
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Define how your screen brightness responds to ambient light. Drag points to customize.
          </Typography>

          {/* Preset Profiles */}
          <Box sx={{ mb: 3 }}>
            <Typography variant="body2" sx={{ mb: 1 }}>
              Presets:
            </Typography>
            <ToggleButtonGroup
              value={selectedPreset}
              exclusive
              onChange={handlePresetChange}
              size="small"
              fullWidth
            >
              <ToggleButton value="default">
                Default
              </ToggleButton>
              <ToggleButton value="darkRoom">
                Dark Room
              </ToggleButton>
              <ToggleButton value="outdoor">
                Outdoor
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>

          {/* Chart */}
          <Box sx={{ width: '100%', height: 400, mb: 2 }}>
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={curvePoints}
                margin={{ top: 20, right: 20, left: 0, bottom: 20 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
                <XAxis
                  dataKey="lux"
                  label={{ value: 'Ambient Light (lux)', position: 'insideBottom', offset: -10 }}
                  domain={[0, 1000]}
                />
                <YAxis
                  label={{ value: 'Screen Brightness (%)', angle: -90, position: 'insideLeft' }}
                  domain={[0, 100]}
                />
                <Tooltip
                  formatter={(value: number, name: string) => [
                    `${Math.round(value)}${name === 'brightness' ? '%' : ' lux'}`,
                    name === 'brightness' ? 'Brightness' : 'Lux',
                  ]}
                />
                <Line
                  type="monotone"
                  dataKey="brightness"
                  stroke="#6200EE"
                  strokeWidth={3}
                  dot={{ fill: '#6200EE', r: 6, cursor: 'grab' }}
                  activeDot={{ r: 8, cursor: 'grabbing' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </Box>

          {/* Reference Info */}
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
            <Chip
              label="Dark Room: 0-50 lux"
              size="small"
              variant="outlined"
            />
            <Chip
              label="Indoor: 100-400 lux"
              size="small"
              variant="outlined"
            />
            <Chip
              label="Outdoor: 800+ lux"
              size="small"
              variant="outlined"
            />
          </Box>

          {/* Action Buttons */}
          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button
              variant="outlined"
              startIcon={<RotateCcw size={18} />}
              onClick={handleReset}
              fullWidth
            >
              Reset
            </Button>
            <Button
              variant="contained"
              startIcon={<Save size={18} />}
              fullWidth
            >
              Save Curve
            </Button>
          </Box>
        </CardContent>
      </Card>

      {/* Advanced Settings Card */}
      <Card elevation={2}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Curve Settings
          </Typography>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box>
              <Typography variant="body2" color="text.secondary">
                Minimum Brightness
              </Typography>
              <Typography variant="body1">
                {Math.min(...curvePoints.map(p => p.brightness))}%
              </Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary">
                Maximum Brightness
              </Typography>
              <Typography variant="body1">
                {Math.max(...curvePoints.map(p => p.brightness))}%
              </Typography>
            </Box>

            <Box>
              <Typography variant="body2" color="text.secondary">
                Data Points
              </Typography>
              <Typography variant="body1">
                {curvePoints.length} control points
              </Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
