import { useState } from "react";
import ArrowLeft from "../components/icons/ArrowLeft";
import RocketSVG from "../components/icons/RocketSVG";
import Footer from "../components/Footer";
import useAllRockets from "../hooks/useAllRockets";
import useRocket from "../hooks/useRocket";

// ─── Single rocket column ─────────────────────────────────────────────────────

/**
 * Fetches and renders the spec card for one rocket in the comparison grid.
 * @param {string|number} rocketId
 */
function RocketCompareCol({ rocketId }) {
  const { data: r, loading } = useRocket(rocketId);

  if (loading) {
    return (
      <div className="compare-col">
        <div className="spinner-wrap"><div className="spinner" /></div>
      </div>
    );
  }

  if (!r) return null;

  const rows = [
    { label: "Height",        value: r.height        != null ? `${r.height} m`                                : "—" },
    { label: "Diameter",      value: r.diameter       != null ? `${r.diameter} m`                             : "—" },
    { label: "Mass",          value: r.mass           != null ? `${Number(r.mass).toLocaleString()} kg`        : "—" },
    { label: "Payload to LEO",value: r.payloadToLeo   != null ? `${Number(r.payloadToLeo).toLocaleString()} kg`: "—" },
    { label: "Stages",        value: r.numberOfStages != null ? String(r.numberOfStages)                       : "—" },
    { label: "Reusability",   value: r.reusable       != null ? (r.reusable ? "Partial" : "None")             : "—" },
  ];

  return (
    <div className="compare-col">
      <div className="compare-col-rocket-name">
        {r.name}
        <div className="compare-col-rocket-icon">
          <RocketSVG size={20} color="rgba(0,229,255,0.5)" />
        </div>
      </div>
      <div className="compare-col-agency">{r.agency?.name ?? ""}</div>
      <div className="compare-divider" />
      {rows.map(row => (
        <div key={row.label} className="compare-stat-row">
          <span className="compare-stat-label">{row.label}</span>
          <span className="compare-stat-value">{row.value}</span>
        </div>
      ))}
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

/**
 * Head-to-head comparison page.
 * Pre-populates selectors if launched from the HomePage compare widget.
 *
 * @param {Function}       onBack     - Navigate back to home
 * @param {Array|null}     rocketIds  - [id1, id2] pre-selected from homepage, or null
 */
function ComparePage({ onBack, rocketIds }) {
  const { data: allRockets, loading } = useAllRockets();

  const [s1, setS1]           = useState(rocketIds ? String(rocketIds[0]) : "");
  const [s2, setS2]           = useState(rocketIds ? String(rocketIds[1]) : "");
  const [compared, setCompared] = useState(rocketIds ?? null);

  return (
    <div className="compare-page">

      <button className="back-btn" onClick={onBack}>
        <ArrowLeft /> Back to Home
      </button>

      <div className="compare-page-header">
        <div className="compare-page-title">Vehicle Comparison</div>
        <div className="compare-page-sub">
          Detailed technical specifications analysis between leading launch vehicles.
        </div>
      </div>

      {/* Selector bar */}
      <div className="compare-selector-bar">
        {loading ? (
          <span style={{ fontSize: 13, color: "rgba(226,232,240,0.4)" }}>Loading rockets…</span>
        ) : (
          <>
            <select
              className="compare-select"
              value={s1}
              onChange={e => { setS1(e.target.value); setCompared(null); }}
            >
              <option value="">Select Rocket A</option>
              {allRockets.map(r => <option key={r.id} value={String(r.id)}>{r.name}</option>)}
            </select>

            <span className="compare-vs">VS</span>

            <select
              className="compare-select"
              value={s2}
              onChange={e => { setS2(e.target.value); setCompared(null); }}
            >
              <option value="">Select Rocket B</option>
              {allRockets
                .filter(r => String(r.id) !== s1)
                .map(r => <option key={r.id} value={String(r.id)}>{r.name}</option>)}
            </select>

            <button
              className="compare-btn"
              disabled={!s1 || !s2}
              onClick={() => setCompared([s1, s2])}
            >
              Compare
            </button>
          </>
        )}
      </div>

      {/* Comparison grid */}
      {compared && (
        <div className="compare-grid">
          <RocketCompareCol rocketId={compared[0]} />
          <RocketCompareCol rocketId={compared[1]} />
        </div>
      )}

      <Footer />
    </div>
  );
}

export default ComparePage;
