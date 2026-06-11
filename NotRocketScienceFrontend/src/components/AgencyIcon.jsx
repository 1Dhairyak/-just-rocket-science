import { AGENCY_CONFIGS, LOGO_MAP } from "../constants/agencyConfig";

/**
 * Renders an agency logo if one exists in /public/logos/,
 * otherwise falls back to a coloured SVG badge with initials.
 */
const AgencyIcon = ({ name, size = 48 }) => {
  const logo = LOGO_MAP[name];

  if (logo) {
    return (
      <div style={{
        width: size, height: size, borderRadius: 12,
        background: logo.bg, display: "flex",
        alignItems: "center", justifyContent: "center",
        overflow: "hidden", flexShrink: 0,
      }}>
        <img src={logo.src} alt={name} style={{ width: "92%", height: "92%", objectFit: "contain" }} />
      </div>
    );
  }

  const cfg = AGENCY_CONFIGS[name] ?? {
    bg: "#2563eb", text: "#fff",
    label: (name ?? "?").slice(0, 4),
    fontSize: 10,
  };

  return (
    <svg width={size} height={size} viewBox="0 0 48 48" style={{ borderRadius: 10, display: "block" }}>
      <rect width="48" height="48" rx="10" fill={cfg.bg} />
      <text x="24" y="28" textAnchor="middle" fill={cfg.text}
        fontSize={cfg.fontSize} fontWeight="bold" fontFamily="monospace">
        {cfg.label}
      </text>
    </svg>
  );
};

export default AgencyIcon;
