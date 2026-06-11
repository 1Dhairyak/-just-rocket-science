import { useState } from "react";
import BackButton from "../components/BackButton";
import { StatusBadge } from "../components/StatusBadge";
import RocketSVG from "../components/icons/RocketSVG";
import { LOGO_MAP } from "../constants/agencyConfig";
import { getRocketModel } from "../constants/rocketModels";
import useAgencyRockets from "../hooks/useAgencyRockets";

/**
 * Displays the fleet roster for a single space agency.
 * Clicking a row navigates to the rocket detail page.
 *
 * @param {object}   agency        - Agency object (id, name, country, description)
 * @param {Function} onBack        - Navigate back to the home page
 * @param {Function} onRocketClick - Called with a rocket summary object on row click
 */
function AgencyPage({ agency, onBack, onRocketClick }) {
  const { data: rockets, loading, error } = useAgencyRockets(agency.id);
  const [sort, setSort] = useState("name");

  const sorted = [...rockets].sort((a, b) =>
    sort === "name"
      ? a.name.localeCompare(b.name)
      : (a.status ?? "").localeCompare(b.status ?? "")
  );

  const logoInfo = LOGO_MAP[agency.name];

  return (
    <div className="page-wrap">

      <div className="back-btn-container">
        <BackButton onClick={onBack} />
      </div>

      {/* Agency hero banner */}
      <div className="agency-hero">
        <div className="agency-hero-icon">
          {logoInfo ? (
            <img
              src={logoInfo.src}
              alt={agency.name}
              style={{ width: "92%", height: "92%", objectFit: "contain" }}
              onError={e => (e.target.style.display = "none")}
            />
          ) : (
            <div className="agency-badge">{agency.name.slice(0, 2).toUpperCase()}</div>
          )}
        </div>
        <div>
          <div className="agency-hero-name">{agency.name}</div>
          <div className="agency-hero-meta">
            {agency.country}&nbsp;·&nbsp;{rockets.length} Vehicle{rockets.length !== 1 ? "s" : ""}
          </div>
        </div>
      </div>

      {agency.description && (
        <p style={{ textAlign: "center", color: "rgba(226,232,240,0.55)", fontSize: "14px", maxWidth: "600px", margin: "0 auto 32px" }}>
          {agency.description}
        </p>
      )}

      {/* Sort controls */}
      <div className="fleet-label">
        <span className="fleet-label-text">Fleet Roster</span>
        <div className="fleet-sort">
          <button className={`fleet-sort-btn${sort === "name"   ? " active" : ""}`} onClick={() => setSort("name")}>By Name</button>
          <button className={`fleet-sort-btn${sort === "status" ? " active" : ""}`} onClick={() => setSort("status")}>By Status</button>
        </div>
      </div>

      {loading && <div className="spinner-wrap"><div className="spinner" /></div>}
      {error   && <div className="error-box">Could not load rockets: {error}</div>}

      {!loading && !error && (
        <table className="fleet-table">
          <thead className="fleet-table-head">
            <tr>
              <th>Vehicle Name</th>
              <th>Operational Status</th>
              <th>Payload to LEO</th>
              <th>First Launch</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map(r => (
              <tr key={r.id} className="fleet-row" onClick={() => onRocketClick(r)}>

                {/* Name + 3D badge */}
                <td>
                  <div className="fleet-rocket-name">
                    <div className="fleet-rocket-icon">
                      <RocketSVG size={14} color="rgba(0,229,255,0.6)" />
                    </div>
                    {r.name}
                    {getRocketModel(r.name) && (
                      <span style={{
                        fontSize: "8px", fontWeight: 700, fontFamily: "Orbitron,monospace",
                        letterSpacing: "0.1em", padding: "2px 7px", borderRadius: "4px",
                        background: "rgba(0,229,255,0.1)", color: "#00e5ff",
                        border: "1px solid rgba(0,229,255,0.35)", marginLeft: 6,
                      }}>3D</span>
                    )}
                  </div>
                </td>

                <td><StatusBadge status={r.status} /></td>

                <td>
                  <span className="fleet-stat">
                    {r.payloadToLeo != null ? `${Number(r.payloadToLeo).toLocaleString()} kg` : "—"}
                  </span>
                </td>

                <td>
                  <span className="fleet-date">
                    {r.firstLaunchDate
                      ? new Date(r.firstLaunchDate + "T00:00:00").getFullYear()
                      : "—"}
                  </span>
                </td>

                <td><span className="fleet-action">VIEW →</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default AgencyPage;
