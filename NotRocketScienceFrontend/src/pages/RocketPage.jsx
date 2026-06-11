import { useState, useEffect } from "react";
import ArrowLeft from "../components/icons/ArrowLeft";
import { RocketStatusBadge } from "../components/StatusBadge";
import RocketViewer3D from "../components/RocketViewer3D";
import RocketIllustration from "../components/RocketIllustration";
import Footer from "../components/Footer";
import { getRocketModel } from "../constants/rocketModels";
import { getStageText } from "../constants/stageData";
import useRocket from "../hooks/useRocket";

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Formats a boolean field for display. */
function fmtBool(val) {
  if (val == null) return "—";
  return val ? "Yes" : "No";
}

/**
 * Returns the stage button labels for a given rocket.
 * Special-cases Starship and Falcon Heavy, then falls back to generic labels.
 */
function buildStageLabels(name, numStages) {
  if (name === "Starship")     return ["S1 SUPER HEAVY", "S2 STARSHIP"];
  if (name === "Falcon Heavy") return ["S1 CORE", "S1 SIDE", "S2 UPPER"];
  if (numStages === 2)         return ["S1 BOOSTER", "S2 UPPER"];
  if (numStages === 3)         return ["S1 BOOSTER", "S2 UPPER", "S3 FINAL"];
  return ["S1 BOOSTER"];
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * Detailed rocket view: status badge, spec table, stage selector,
 * and a togglable 3D model / blueprint panel.
 *
 * @param {object}   rocket - Rocket summary object passed from AgencyPage
 * @param {Function} onBack - Navigates back to the agency fleet roster
 */
function RocketPage({ rocket: summary, onBack }) {
  const { data: rocket, loading, error } = useRocket(summary.id);
  const [stage, setStage]   = useState(0);
  const [use3d,  setUse3d]  = useState(true);

  // Merge full data on top of the summary so we can render immediately
  const r = rocket ?? summary;

  const numStages  = Math.max(1, Math.min(r.numberOfStages ?? 1, 3));
  const stageInfo  = getStageText(r.name, stage);
  const modelUrl   = getRocketModel(r.name);
  const stageLabels = buildStageLabels(r.name, numStages);

  // Reset viewer state when navigating to a different rocket
  useEffect(() => {
    setStage(0);
    setUse3d(true);
  }, [summary.id]);

  const dash = "—";
  const stats = [
    { label: "Height",       value: r.height          != null ? `${r.height} m`                                        : dash },
    { label: "Diameter",     value: r.diameter         != null ? `${r.diameter} m`                                     : dash },
    { label: "Mass",         value: r.mass             != null ? `${Number(r.mass).toLocaleString()} kg`                : dash },
    { label: "Payload to LEO",value: r.payloadToLeo    != null ? `${Number(r.payloadToLeo).toLocaleString()} kg`        : dash },
    { label: "Thrust",       value: r.thrustKn         != null ? `${Number(r.thrustKn).toLocaleString()} kN`            : dash },
    { label: "Stages",       value: r.numberOfStages   != null ? String(r.numberOfStages)                               : dash },
    { label: "Reusable",     value: fmtBool(r.reusable) },
    { label: "Crew Capacity",value: r.humanCrewCapacity > 0   ? String(r.humanCrewCapacity)
                                  : r.humanCrewCapacity === 0 ? "Uncrewed"
                                  : dash },
  ];

  return (
    <div className="rocket-detail">
      <div className="rocket-detail-inner">

        {/* Back button */}
        <div className="back-btn-container">
          <button className="back-btn" onClick={onBack}>
            <ArrowLeft /> Back
          </button>
        </div>

        {loading && <div className="spinner-wrap"><div className="spinner" /></div>}
        {error   && <div className="error-box">{error}</div>}

        <div className="rocket-detail-layout">

          {/* ── Left: specs ── */}
          <div>
            <RocketStatusBadge status={r.status} />
            <div className="rocket-name-heading">{r.name}</div>

            {r.description && (
              <div className="rocket-description-text">{r.description}</div>
            )}

            {/* Stage selector (only shown for multi-stage rockets) */}
            {numStages > 1 && (
              <div className="stage-selector">
                {stageLabels.slice(0, numStages).map((label, i) => (
                  <button
                    key={i}
                    className={`stage-btn${stage === i ? " active" : ""}`}
                    onClick={() => setStage(i)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            )}

            {/* Spec rows */}
            <div className="rocket-stats-panel">
              {stats.map(s => (
                <div key={s.label} className="rocket-stat-row">
                  <span className="rocket-stat-label">{s.label}</span>
                  <span className="rocket-stat-value">{s.value}</span>
                </div>
              ))}
            </div>

            {/* Stage description */}
            {stageInfo.text && (
              <div className="stage-info-box">
                <div className="stage-info-title">{stageInfo.title}</div>
                <div className="stage-info-text">{stageInfo.text}</div>
              </div>
            )}
          </div>

          {/* ── Right: 3D viewer / blueprint ── */}
          <div className="rocket-viewer-panel">
            <div className="viewer-header">
              <div>
                <div className="viewer-title">
                  {modelUrl && use3d ? "INTERACTIVE 3D MODEL" : "BLUEPRINT VIEW"}
                </div>
                <div className="viewer-hint">
                  {modelUrl && use3d ? "↔ Drag to rotate · Scroll to zoom" : ""}
                </div>
              </div>

              {modelUrl && (
                <button className="viewer-toggle-btn" onClick={() => setUse3d(v => !v)}>
                  {use3d ? "↙ BLUEPRINT VIEW" : "↗ 3D MODEL"}
                </button>
              )}
            </div>

            {modelUrl && use3d
              ? <div className="viewer-canvas"><RocketViewer3D modelUrl={modelUrl} /></div>
              : <div className="blueprint-wrap"><RocketIllustration stage={stage} height={600} /></div>
            }
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}

export default RocketPage;
