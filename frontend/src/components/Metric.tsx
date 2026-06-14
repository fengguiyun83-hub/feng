import { Statistic } from "antd";

interface MetricProps {
  icon: React.ReactNode;
  title: string;
  value: number;
  suffix: string;
  precision?: number;
}

export function Metric({ icon, title, value, suffix, precision }: MetricProps) {
  const displayValue = precision === undefined ? value : value.toFixed(precision);

  return (
    <section className="metric">
      <div className="metric-icon">{icon}</div>
      <Statistic title={title} value={displayValue} suffix={suffix} />
    </section>
  );
}
