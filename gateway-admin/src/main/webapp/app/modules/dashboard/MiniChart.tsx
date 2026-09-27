import { useMemo } from 'react';
import type { DayCount } from 'app/shared/api/dashboardApi';

interface Props {
  series: DayCount[];
  color?: string;
  title: string;
}

/** Hand-drawn SVG bar chart of a per-day series (no chart library). */
export default function MiniChart({ series, color = '#0d6efd', title }: Props) {
  const width = 600;
  const height = 140;
  const padding = { top: 8, right: 8, bottom: 22, left: 28 };
  const model = useMemo(() => {
    const sorted = [...series].sort((a, b) => a.day.localeCompare(b.day));
    const max = Math.max(1, ...sorted.map((d) => d.total));
    const innerW = width - padding.left - padding.right;
    const innerH = height - padding.top - padding.bottom;
    const step = sorted.length ? innerW / sorted.length : innerW;
    const bars = sorted.map((d, i) => {
      const h = (d.total / max) * innerH;
      return { x: padding.left + i * step + step * 0.15, y: padding.top + innerH - h, w: Math.max(1, step * 0.7), h, day: d.day, total: d.total };
    });
    const labels = sorted.length <= 8 ? sorted.map((_, i) => i) : [0, Math.floor(sorted.length / 2), sorted.length - 1];
    return { bars, max, innerH, labels };
  }, [series]);

  if (!series.length) {
    return <div className="text-muted small py-4 text-center">—</div>;
  }
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="mini-chart" role="img" aria-label={title}>
      <title>{title}</title>
      <line x1={padding.left} y1={padding.top + model.innerH} x2={width - padding.right} y2={padding.top + model.innerH} stroke="#adb5bd" />
      <text x={padding.left - 4} y={padding.top + 10} fontSize="10" textAnchor="end" fill="#6c757d">
        {model.max}
      </text>
      <text x={padding.left - 4} y={padding.top + model.innerH} fontSize="10" textAnchor="end" fill="#6c757d">
        0
      </text>
      {model.bars.map((b) => (
        <g key={b.day}>
          <rect x={b.x} y={b.y} width={b.w} height={b.h} fill={color} rx="1">
            <title>{`${b.day}: ${b.total}`}</title>
          </rect>
        </g>
      ))}
      {model.labels.map((i) => {
        const b = model.bars[i];
        return (
          <text key={b.day} x={b.x + b.w / 2} y={height - 6} fontSize="10" textAnchor="middle" fill="#6c757d">
            {b.day.slice(5)}
          </text>
        );
      })}
    </svg>
  );
}
