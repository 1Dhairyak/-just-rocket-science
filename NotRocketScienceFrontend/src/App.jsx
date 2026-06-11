import useAgencies from "./hooks/useAgencies";
import HomePage from "./pages/HomePage";
import AgencyPage from "./pages/AgencyPage";
import RocketPage from "./pages/RocketPage";
import ComparePage from "./pages/ComparePage";
import RocketSVG from "./components/icons/RocketSVG";
import { useState } from "react";
import "./App.css";

/**
 * Root component — owns all navigation state and renders the correct page.
 * No business logic lives here; each page manages its own data fetching.
 */
export default function App() {
  const { data: agencies, loading, error } = useAgencies();

  const [page,           setPage]           = useState("home");
  const [selectedAgency, setSelectedAgency] = useState(null);
  const [selectedRocket, setSelectedRocket] = useState(null);
  const [compareRockets, setCompareRockets] = useState(null);

  const goHome = () => {
    setPage("home");
    setSelectedAgency(null);
    setSelectedRocket(null);
    setCompareRockets(null);
  };

  return (
    <>
      <div className="stars-bg" />

      {/* ── Navigation ── */}
      <nav className="nav">
        <div className="nav-brand" onClick={goHome}>
          <img
            src="/logos/jrs.png"
            alt="JRS"
            style={{ height: 26, width: "auto" }}
            onError={e => (e.target.style.display = "none")}
          />
          Just Rocket Science
        </div>
        <div className="nav-links">
          <button
            className="nav-link"
            onClick={() => { setPage("home"); setSelectedAgency(null); setSelectedRocket(null); }}
          >
            Rockets
          </button>
        </div>
      </nav>

      {/* ── Loading / error states ── */}
      {loading && <div className="spinner-wrap"><div className="spinner" /></div>}
      {error && (
        <div style={{ padding: 40 }}>
          <div className="error-box">
            Backend unreachable: {error}<br />
            Make sure Spring Boot is running on port 8080.
          </div>
        </div>
      )}

      {/* ── Page router ── */}
      {page === "home" && !loading && (
        <HomePage
          agencies={agencies}
          onAgencyClick={a  => { setSelectedAgency(a);    setPage("agency");  }}
          onCompareRockets={(r1, r2) => { setCompareRockets([r1, r2]); setPage("compare"); }}
        />
      )}

      {page === "agency" && selectedAgency && (
        <AgencyPage
          agency={selectedAgency}
          onBack={goHome}
          onRocketClick={r => { setSelectedRocket(r); setPage("rocket"); }}
        />
      )}

      {page === "rocket" && selectedRocket && (
        <RocketPage
          rocket={selectedRocket}
          onBack={() => setPage("agency")}
        />
      )}

      {page === "compare" && (
        <ComparePage
          rocketIds={compareRockets}
          onBack={goHome}
        />
      )}
    </>
  );
}
