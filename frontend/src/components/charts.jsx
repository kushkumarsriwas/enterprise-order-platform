// Charts follow one system: thin marks, hairline solid grid, values in text colours (never the series colour),
// a legend whenever there are 2+ series, a hover tooltip, and a table view for every chart.
// Series colours come from CSS variables (--series-1..3), validated for colour-vision deficiency in light and dark.
import { useState } from "react";
import { BarChart3, Table2 } from "lucide-react";
import { Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { compact } from "../format.js";

const SERIES = ["var(--series-1)", "var(--series-2)", "var(--series-3)"];
const AXIS_TICK = { fill: "var(--muted)", fontSize: 12 };

export function ChartCard({ title, subtitle, table, children, actions, empty, loading }) {
  const [asTable, setAsTable] = useState(false);
  return (
    <section className={`card chart-card ${loading ? "refetching" : ""}`}>
      <header className="chart-head">
        <div>
          <h3>{title}</h3>
          {subtitle && <p className="muted small">{subtitle}</p>}
        </div>
        <div className="row">
          {actions}
          {table && !empty && (
            <button className="btn btn-ghost btn-sm" onClick={() => setAsTable(!asTable)} aria-pressed={asTable}>
              {asTable ? <BarChart3 size={14} /> : <Table2 size={14} />} {asTable ? "Chart" : "Table"}
            </button>
          )}
        </div>
      </header>
      {empty ? <div className="chart-empty">{empty}</div> : asTable ? <DataTable {...table} /> : children}
    </section>
  );
}

export function DataTable({ columns, rows }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} className={column.numeric ? "num" : ""}>
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column.key} className={column.numeric ? "num" : ""}>
                  {column.format ? column.format(row[column.key], row) : row[column.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Legend({ series, kind = "line" }) {
  return (
    <div className="chart-legend">
      {series.map((s, index) => (
        <span key={s.key}>
          <i className={kind === "line" ? "key-line" : "key-box"} style={{ background: SERIES[index] }} />
          {s.name}
        </span>
      ))}
    </div>
  );
}

function TooltipBox({ active, payload, label, labelFormat, valueFormat }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tooltip">
      <div className="tt-label">{labelFormat ? labelFormat(label) : label}</div>
      {payload.map((entry) => (
        <div className="tt-row" key={entry.dataKey}>
          <i className="key-line" style={{ background: entry.color }} />
          <strong>{valueFormat(entry.value)}</strong>
          <span className="muted">{entry.name}</span>
        </div>
      ))}
    </div>
  );
}

/** Trend over time: 2px lines, crosshair tooltip, the first series' latest value labelled at the end. */
export function TrendChart({ data, xKey, series, height = 260, valueFormat = compact, labelFormat }) {
  const last = data.length - 1;
  const EndLabel = ({ x, y, index, value }) =>
    index === last ? (
      <text x={x + 8} y={y} dy={4} fontSize={12} fill="var(--text)" fontWeight={600}>
        {compact(value)}
      </text>
    ) : null;
  return (
    <div className="stack tight">
      {series.length > 1 && <Legend series={series} kind="line" />}
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 10, right: 44, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--grid)" />
            <XAxis dataKey={xKey} tickFormatter={labelFormat} tick={AXIS_TICK} axisLine={{ stroke: "var(--axis)" }} tickLine={false} minTickGap={28} />
            <YAxis tickFormatter={compact} tick={AXIS_TICK} axisLine={false} tickLine={false} width={46} />
            <Tooltip cursor={{ stroke: "var(--axis)", strokeWidth: 1 }} content={<TooltipBox labelFormat={labelFormat} valueFormat={valueFormat} />} />
            {series.map((s, index) => (
              <Line
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.name}
                stroke={SERIES[index]}
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
                dot={false}
                activeDot={{ r: 5, stroke: "var(--panel)", strokeWidth: 2 }}
                isAnimationActive={false}
                label={index === 0 ? <EndLabel /> : undefined}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/** Column with a 4px rounded data end and a square base; negative values hang below the zero line. */
function RoundedColumn({ x, y, width, height, fill, negative }) {
  if (!width || !height) return null;
  const top = Math.min(y, y + height);
  const h = Math.abs(height);
  const r = Math.min(4, h, width / 2);
  const right = x + width;
  const bottom = top + h;
  const d = negative
    ? `M${x},${top} H${right} V${bottom - r} Q${right},${bottom} ${right - r},${bottom} H${x + r} Q${x},${bottom} ${x},${bottom - r} Z`
    : `M${x},${bottom} V${top + r} Q${x},${top} ${x + r},${top} H${right - r} Q${right},${top} ${right},${top + r} V${bottom} Z`;
  return <path d={d} fill={fill} />;
}

/** Grouped columns (e.g. revenue vs net profit per month). Bars are at most 24px wide with a 2px gap. */
export function ColumnsChart({ data, xKey, series, height = 260, valueFormat = compact, labelFormat }) {
  return (
    <div className="stack tight">
      {series.length > 1 && <Legend series={series} kind="bar" />}
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 10, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="22%">
            <CartesianGrid vertical={false} stroke="var(--grid)" />
            <XAxis dataKey={xKey} tickFormatter={labelFormat} tick={AXIS_TICK} axisLine={false} tickLine={false} minTickGap={8} />
            <YAxis tickFormatter={compact} tick={AXIS_TICK} axisLine={false} tickLine={false} width={46} />
            <ReferenceLine y={0} stroke="var(--axis)" />
            <Tooltip cursor={{ fill: "var(--hover)" }} content={<TooltipBox labelFormat={labelFormat} valueFormat={valueFormat} />} />
            {series.map((s, index) => (
              <Bar
                key={s.key}
                dataKey={s.key}
                name={s.name}
                fill={SERIES[index]}
                maxBarSize={24}
                isAnimationActive={false}
                shape={(props) => <RoundedColumn {...props} negative={Number(props.payload?.[s.key]) < 0} />}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/** Ranked horizontal bars (one series, one colour) with the value at the bar's tip. */
export function BarList({ items, valueFormat = compact }) {
  const max = Math.max(0, ...items.map((item) => Number(item.value) || 0));
  return (
    <ul className="barlist">
      {items.map((item) => (
        <li key={item.key} title={`${item.label}: ${valueFormat(item.value)}`}>
          <div className="barlist-label">
            <span>{item.label}</span>
            {item.note && <span className="muted small">{item.note}</span>}
          </div>
          <div>
            {Number(item.value) > 0 && max > 0 && <div className="barlist-bar" style={{ width: `${Math.max(1, (Number(item.value) / max) * 100)}%` }} />}
          </div>
          <strong className="barlist-value">{valueFormat(item.value)}</strong>
        </li>
      ))}
    </ul>
  );
}
