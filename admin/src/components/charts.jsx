import { Area, AreaChart, Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { compact, formatMoney, formatNumber } from "../format.js";

/** Validated categorical slots (see styles/tokens.css): fixed order, never cycled. */
export const SERIES = ["var(--series-1)", "var(--series-2)", "var(--series-3)", "var(--series-4)", "var(--series-5)", "var(--series-6)"];
// Series grow into place when a chart first draws, unless the visitor asked for less motion.
const ANIMATION =
  typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches
    ? { isAnimationActive: false }
    : { isAnimationActive: true, animationDuration: 800, animationEasing: "ease-out" };

const axisTick = { fill: "var(--chart-axis)", fontSize: 11 };

function ChartTooltip({ active, payload, label, series, format, labelFormat }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="tt-title">{labelFormat ? labelFormat(label, payload[0]?.payload) : label}</div>
      {series.map((s, index) => {
        const entry = payload.find((p) => p.dataKey === s.key);
        if (!entry) return null;
        return (
          <div className="tt-row" key={s.key}>
            <span>
              <i style={{ background: s.color || SERIES[index] }} />
              {s.label}
            </span>
            <b>{(s.format || format)(entry.value)}</b>
          </div>
        );
      })}
    </div>
  );
}

export function Legend({ series }) {
  if (series.length < 2) return null;
  return (
    <div className="legend" style={{ marginBottom: 10 }}>
      {series.map((s, index) => (
        <span key={s.key}>
          <i className={s.box ? "box" : ""} style={{ background: s.color || SERIES[index] }} />
          {s.label}
        </span>
      ))}
    </div>
  );
}

/**
 * Change over time. type: area (one series, a wash under the line), line (several series) or bar.
 * One y-axis only: series in one chart always share a unit.
 */
export function TrendChart({ data, xKey = "label", series, type = "area", height = 260, format = (v) => formatMoney(v), axisFormat = compact, labelFormat }) {
  const tooltip = (
    <Tooltip
      content={<ChartTooltip series={series} format={format} labelFormat={labelFormat} />}
      cursor={type === "bar" ? { fill: "var(--surface-2)" } : { stroke: "var(--line-strong)", strokeWidth: 1 }}
    />
  );
  const common = { data, margin: { top: 8, right: 8, bottom: 0, left: 0 } };
  const axes = [
    <CartesianGrid key="grid" vertical={false} stroke="var(--chart-grid)" />,
    <XAxis key="x" dataKey={xKey} tick={axisTick} tickLine={false} axisLine={{ stroke: "var(--chart-grid)" }} minTickGap={18} />,
    <YAxis key="y" tick={axisTick} tickLine={false} axisLine={false} width={48} tickFormatter={axisFormat} allowDecimals={false} />,
  ];
  return (
    <div>
      <Legend series={series.map((s) => ({ ...s, box: type === "bar" }))} />
      <ResponsiveContainer width="100%" height={height}>
        {type === "bar" ? (
          <BarChart {...common} barGap={2} barCategoryGap="22%">
            {axes}
            {tooltip}
            {series.map((s, index) => (
              <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color || SERIES[index]} radius={[4, 4, 0, 0]} maxBarSize={24} {...ANIMATION} />
            ))}
          </BarChart>
        ) : type === "line" ? (
          <LineChart {...common}>
            {axes}
            {tooltip}
            {series.map((s, index) => (
              <Line
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stroke={s.color || SERIES[index]}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--surface)" }}
                {...ANIMATION}
              />
            ))}
          </LineChart>
        ) : (
          <AreaChart {...common}>
            {axes}
            {tooltip}
            {series.map((s, index) => (
              <Area
                key={s.key}
                dataKey={s.key}
                name={s.label}
                stroke={s.color || SERIES[index]}
                fill={s.color || SERIES[index]}
                fillOpacity={0.1}
                strokeWidth={2}
                activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--surface)" }}
                {...ANIMATION}
              />
            ))}
          </AreaChart>
        )}
      </ResponsiveContainer>
    </div>
  );
}

/** Ranked magnitudes (categories, regions, methods) as accessible HTML bars with the value at the tip. */
export function HBarList({ items, format = formatMoney, color = "var(--series-1)", empty = "No data for this period" }) {
  const max = Math.max(...items.map((item) => Number(item.value) || 0), 0);
  if (!items.length || max === 0) return <p className="small muted">{empty}</p>;
  return (
    <ul className="hbars">
      {items.map((item) => (
        <li key={item.key ?? item.label}>
          <div className="hbar-top">
            <span className="ellipsis">{item.label}</span>
            <span className="value">
              {format(item.value)}
              {item.hint && (
                <span className="muted" style={{ fontWeight: 500 }}>
                  {" "}
                  {item.hint}
                </span>
              )}
            </span>
          </div>
          <div className="hbar-track">
            <div className="hbar-fill" style={{ width: `${(Number(item.value) / max) * 100}%`, background: item.color || color }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

/** Parts of a whole (e.g. stock health) as one bar with 2px gaps and a legend with counts. */
export function SegmentBar({ segments }) {
  const total = segments.reduce((sum, s) => sum + (Number(s.value) || 0), 0);
  return (
    <div className="stack-sm">
      <div className="segmented-meter" role="img" aria-label={segments.map((s) => `${s.label}: ${s.value}`).join(", ")}>
        {total > 0 &&
          segments.filter((s) => Number(s.value) > 0).map((s) => <span key={s.label} style={{ width: `${(s.value / total) * 100}%`, background: s.color }} />)}
      </div>
      <div className="legend">
        {segments.map((s) => (
          <span key={s.label}>
            <i className="box" style={{ background: s.color }} />
            {s.label} <b className="num">{formatNumber(s.value)}</b>
          </span>
        ))}
      </div>
    </div>
  );
}
