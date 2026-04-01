export default function Logo({ size = 'text-2xl' }) {
  return (
    <div className={`flex items-center gap-2 font-display font-semibold ${size}`}>
      <div className="relative">
        <span className="text-primary">Q</span>
        <span className="text-secondary">Print</span>
      </div>
    </div>
  );
}
