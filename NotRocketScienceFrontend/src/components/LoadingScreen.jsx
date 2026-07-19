import { useState, useEffect } from "react";
import RocketSVG from "./icons/RocketSVG";

export default function LoadingScreen() {
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => {
      setSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const getLoadingMessage = () => {
    if (seconds < 8) {
      return {
        title: "Waking up the backend service...",
        detail: "Our server is hosted on Render's free tier. It spins down after inactivity and needs a moment to warm up."
      };
    } else if (seconds < 25) {
      return {
        title: "Initializing Spring Boot application...",
        detail: "Spring Boot takes 40–60 seconds to boot up and initialize all services on a free-tier resource allocation."
      };
    } else if (seconds < 50) {
      return {
        title: "Establishing PostgreSQL database connection...",
        detail: "Starting database connections, running schema migrations, and priming configurations. Hang tight!"
      };
    } else {
      return {
        title: "Almost there, finalizing startup...",
        detail: "Warming up the API endpoints. This should finish shortly. Thank you for your patience!"
      };
    }
  };

  const msg = getLoadingMessage();

  return (
    <div className="loading-screen">
      <div className="loading-content">
        <div className="loading-rocket-container">
          <RocketSVG size={48} color="#00e5ff" />
        </div>
        <div className="loading-spinner-container">
          <div className="spinner" />
        </div>
        <h2 className="loading-title">{msg.title}</h2>
        <p className="loading-detail">{msg.detail}</p>
        <div className="loading-timer">
          Time elapsed: {Math.floor(seconds / 60)}m {seconds % 60}s
        </div>
      </div>
    </div>
  );
}
