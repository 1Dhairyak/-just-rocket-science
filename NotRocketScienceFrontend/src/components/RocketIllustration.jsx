/**
 * Blueprint-style SVG schematic of a generic two-stage rocket.
 * Highlights active stages based on the `stage` prop:
 *   0 = both stages visible (booster view)
 *   1 = upper stage only
 *   2 = payload only
 *
 * @param {number} stage  - Active stage index (0 | 1 | 2)
 * @param {number} height - SVG render height in px
 */
const RocketIllustration = ({ stage = 0, height = 300 }) => {
  const w        = height * 0.6;
  const dimColor = "#1a4a6b";
  const gridColor= "rgba(0,180,220,0.08)";
  const lineW    = "1";
  const dashLine = "3,3";
  const CYAN     = "#00e5ff";
  const DIM      = "rgba(0,229,255,0.18)";

  const showS1 = stage === 0;
  const showS2 = stage === 0 || stage === 1;
  const s1Color= showS1 ? CYAN : DIM;
  const s2Color= showS2 ? CYAN : DIM;
  const plColor= CYAN;

  return (
    <svg
      width={w}
      height={height}
      viewBox="0 0 160 280"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={{ filter: "drop-shadow(0 0 24px rgba(0,229,255,0.3))" }}
    >
      {/* Background */}
      <rect width="160" height="280" fill="#020d1a" rx="8" />

      {/* Blueprint grid */}
      {[20,40,60,80,100,120,140].map(x => (
        <line key={`vg${x}`} x1={x} y1="0" x2={x} y2="280" stroke={gridColor} strokeWidth="1" />
      ))}
      {[20,40,60,80,100,120,140,160,180,200,220,240,260].map(y => (
        <line key={`hg${y}`} x1="0" y1={y} x2="160" y2={y} stroke={gridColor} strokeWidth="1" />
      ))}
      <line x1="80" y1="0" x2="80" y2="280" stroke="rgba(0,229,255,0.06)" strokeWidth="1.5" />

      {/* Dimension lines */}
      <line x1="18" y1="30" x2="18" y2="240" stroke={dimColor} strokeWidth={lineW} strokeDasharray={dashLine} />
      <line x1="14" y1="30"  x2="22" y2="30"  stroke={dimColor} strokeWidth={lineW} />
      <line x1="14" y1="240" x2="22" y2="240" stroke={dimColor} strokeWidth={lineW} />
      <text x="10" y="138" fill="#1a6a8a" fontSize="6" fontFamily="monospace" textAnchor="middle" transform="rotate(-90,10,138)">TOTAL HEIGHT</text>

      <line x1="142" y1="155" x2="142" y2="240" stroke={dimColor} strokeWidth={lineW} strokeDasharray={dashLine} />
      <line x1="138" y1="155" x2="146" y2="155" stroke={dimColor} strokeWidth={lineW} />
      <line x1="138" y1="240" x2="146" y2="240" stroke={dimColor} strokeWidth={lineW} />
      <text x="152" y="200" fill="#1a6a8a" fontSize="6" fontFamily="monospace" textAnchor="middle" transform="rotate(90,152,200)">STAGE 1</text>

      <line x1="142" y1="80"  x2="142" y2="155" stroke={dimColor} strokeWidth={lineW} strokeDasharray={dashLine} />
      <line x1="138" y1="80"  x2="146" y2="80"  stroke={dimColor} strokeWidth={lineW} />
      <text x="152" y="120" fill="#1a6a8a" fontSize="6" fontFamily="monospace" textAnchor="middle" transform="rotate(90,152,120)">STAGE 2</text>

      <line x1="142" y1="30" x2="142" y2="80" stroke={dimColor} strokeWidth={lineW} strokeDasharray={dashLine} />
      <line x1="138" y1="30" x2="146" y2="30" stroke={dimColor} strokeWidth={lineW} />
      <text x="152" y="57" fill="#1a6a8a" fontSize="6" fontFamily="monospace" textAnchor="middle" transform="rotate(90,152,57)">PAYLOAD</text>

      {/* Stage 1 — booster */}
      {showS1 && <>
        <ellipse cx="80" cy="255" rx="12" ry="18" fill="rgba(0,229,255,0.07)" stroke="rgba(0,229,255,0.2)" strokeWidth="1" strokeDasharray="2,2" />
        <ellipse cx="80" cy="252" rx="7"  ry="12" fill="rgba(0,229,255,0.05)" stroke="rgba(0,229,255,0.15)" strokeWidth="1" strokeDasharray="2,2" />
        <path d="M65 242 L68 255 L73 255 L72 242Z" fill="rgba(0,229,255,0.15)" stroke={s1Color} strokeWidth="0.8" />
        <path d="M87 242 L88 255 L93 255 L95 242Z" fill="rgba(0,229,255,0.15)" stroke={s1Color} strokeWidth="0.8" />
        <path d="M74 240 L76 253 L84 253 L86 240Z" fill="rgba(0,229,255,0.15)" stroke={s1Color} strokeWidth="0.8" />
        <ellipse cx="80" cy="241" rx="6" ry="3" fill="rgba(0,229,255,0.1)" stroke={s1Color} strokeWidth="0.8" />
        <rect x="55" y="155" width="50" height="85" fill="rgba(0,229,255,0.04)" stroke={s1Color} strokeWidth="1.2" />
        <line x1="55" y1="175" x2="105" y2="175" stroke={s1Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <line x1="55" y1="195" x2="105" y2="195" stroke={s1Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <line x1="55" y1="215" x2="105" y2="215" stroke={s1Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <ellipse cx="80" cy="170" rx="18" ry="4" fill="none" stroke={s1Color} strokeWidth="0.6" opacity="0.4" />
        <ellipse cx="80" cy="210" rx="18" ry="4" fill="none" stroke={s1Color} strokeWidth="0.6" opacity="0.4" />
        <rect x="68" y="189" width="24" height="12" rx="2" fill="rgba(0,229,255,0.08)" stroke={s1Color} strokeWidth="0.6" />
        <text x="80" y="198" textAnchor="middle" fill={s1Color} fontSize="7" fontFamily="monospace" fontWeight="bold">STG 1</text>
        <path d="M55 200 L38 235 L55 228Z" fill="rgba(0,229,255,0.06)" stroke={s1Color} strokeWidth="1" />
        <path d="M105 200 L122 235 L105 228Z" fill="rgba(0,229,255,0.06)" stroke={s1Color} strokeWidth="1" />
        <rect x="53" y="151" width="54" height="6" rx="1" fill="rgba(0,229,255,0.08)" stroke={CYAN} strokeWidth="1" />
        <text x="80" y="150" textAnchor="middle" fill="#1a6a8a" fontSize="5" fontFamily="monospace">SEP</text>
      </>}

      {/* Stage 2 — upper stage */}
      {showS2 && <>
        <rect x="58" y="80" width="44" height="73" fill="rgba(0,229,255,0.04)" stroke={s2Color} strokeWidth="1.2" />
        <line x1="58" y1="100" x2="102" y2="100" stroke={s2Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <line x1="58" y1="120" x2="102" y2="120" stroke={s2Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <line x1="58" y1="140" x2="102" y2="140" stroke={s2Color} strokeWidth="0.5" strokeDasharray="2,4" opacity="0.5" />
        <circle cx="80" cy="112" r="8" fill="rgba(0,229,255,0.06)" stroke={s2Color} strokeWidth="1" />
        <circle cx="80" cy="112" r="5" fill="rgba(0,20,40,0.8)"   stroke={s2Color} strokeWidth="0.6" />
        <circle cx="77" cy="110" r="1.5" fill={s2Color} opacity="0.6" />
        <rect x="68" y="130" width="24" height="12" rx="2" fill="rgba(0,229,255,0.08)" stroke={s2Color} strokeWidth="0.6" />
        <text x="80" y="139" textAnchor="middle" fill={s2Color} fontSize="7" fontFamily="monospace" fontWeight="bold">STG 2</text>
      </>}

      {/* Payload fairing */}
      <rect x="61" y="52" width="38" height="30" fill="rgba(0,229,255,0.04)" stroke={plColor} strokeWidth="1.2" />
      <rect x="66" y="61" width="28" height="12" rx="2" fill="rgba(0,229,255,0.08)" stroke={plColor} strokeWidth="0.6" />
      <text x="80" y="70" textAnchor="middle" fill={plColor} fontSize="6" fontFamily="monospace" fontWeight="bold">PAYLOAD</text>
      <path d="M80 15 L61 52 L99 52 Z" fill="rgba(0,229,255,0.05)" stroke={plColor} strokeWidth="1.2" />
      <text x="80" y="12" textAnchor="middle" fill={plColor} fontSize="5.5" fontFamily="monospace" letterSpacing="0.5">NOSE CONE</text>

      {/* Corner registration marks */}
      {[[6,6],[154,6],[6,274],[154,274]].map(([x,y],i) => (
        <g key={i}>
          <line x1={x} y1={y} x2={x + (x < 80 ? 8 : -8)} y2={y} stroke="rgba(0,229,255,0.3)" strokeWidth="0.8" />
          <line x1={x} y1={y} x2={x} y2={y + (y < 140 ? 8 : -8)} stroke="rgba(0,229,255,0.3)" strokeWidth="0.8" />
        </g>
      ))}

      {/* Footer labels */}
      <text x="6"   y="275" fill="rgba(0,229,255,0.25)" fontSize="5" fontFamily="monospace">SCHEMATIC v2.4 // CLASSIFIED</text>
      <text x="154" y="275" fill="rgba(0,229,255,0.25)" fontSize="5" fontFamily="monospace" textAnchor="end">REV-{stage + 1}</text>
    </svg>
  );
};

export default RocketIllustration;
