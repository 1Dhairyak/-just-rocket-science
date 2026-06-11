import { useState } from "react";
import AgencyIcon from "../components/AgencyIcon";
import Footer from "../components/Footer";
import useAllRockets from "../hooks/useAllRockets";

/**
 * Landing page — shows the agency grid and the head-to-head compare widget.
 *
 * @param {Array}    agencies         - List of agency objects from the backend
 * @param {Function} onAgencyClick    - Called with an agency object when a card is clicked
 * @param {Function} onCompareRockets - Called with (rocketId1, rocketId2) to open compare page
 */
function HomePage({ agencies, onAgencyClick, onCompareRockets }) {
  const [activeCard, setActiveCard] = useState(null);
  const [sel1, setSel1] = useState("");
  const [sel2, setSel2] = useState("");
  const { data: allRockets } = useAllRockets();

  const handleCardClick = (agency) => {
    setActiveCard(agency.id);
    onAgencyClick(agency);
  };

  return (
    <div className="home-wrap">

      {/* Hero */}
      <div className="hero">
        <img
          src="/logos/jrs.png"
          alt="JRS"
          className="hero-logo"
          onError={e => (e.target.style.display = "none")}
        />
        <h1>Just Rocket Science</h1>
        <p>Explore every rocket ever built, by every agency that dared to try.</p>
      </div>

      {/* Agency grid */}
      <div className="agencies-section">
        <div className="agencies-section-label">Space Agencies</div>
        <div className="agency-grid">
          {agencies
            .filter(a => a.name !== "ULA")
            .map(a => (
              <div
                key={a.id}
                className={`agency-card${activeCard === a.id ? " selected" : ""}`}
                onClick={() => handleCardClick(a)}
              >
                <div className="agency-card-icon">
                  <AgencyIcon name={a.name} size={56} />
                </div>
                <div className="agency-card-name">{a.name}</div>
                <div className="agency-card-country">{a.country}</div>
              </div>
            ))}
        </div>
      </div>

      {/* Head-to-head compare widget */}
      <div className="home-compare-section">
        <div className="home-compare-label">Compare Rockets</div>
        <div className="home-compare-box">
          <div className="home-compare-title">Head-to-Head Comparison</div>
          <div style={{ display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap", justifyContent: "center" }}>
            <select className="compare-select" value={sel1} onChange={e => setSel1(e.target.value)}>
              <option value="">Select rocket...</option>
              {allRockets.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
            </select>

            <span className="compare-vs">VS</span>

            <select className="compare-select" value={sel2} onChange={e => setSel2(e.target.value)}>
              <option value="">Select rocket...</option>
              {allRockets.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
            </select>

            <button
              className="compare-btn"
              disabled={!sel1 || !sel2 || sel1 === sel2}
              onClick={() => onCompareRockets(sel1, sel2)}
            >
              Compare
            </button>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}

export default HomePage;
