// Maps rocket display names to their .glb model paths in /public/models/
const ROCKET_MODELS = {
  "SLS":          "/models/sls.glb",
  "Falcon 9":     "/models/falcon9.glb",
  "Starship":     "/models/starship.glb",
  "PSLV":         "/models/pslv.glb",
  "Dragon":       "/models/dragon.glb",
  "Saturn V":     "/models/saturn5.glb",
  "Falcon Heavy": "/models/falconheavy.glb",
  "H-IIB":        "/models/h2b.glb",
  "N1":           "/models/n1.glb",
  "Epsilon":      "/models/epsilon.glb",
  "New Glenn":    "/models/newglenn.glb",
  "GSLV Mk III":  "/models/gslvmk3.glb",
  "Proton-M":     "/models/protonm.glb",
  "H-IIA":        "/models/h2a.glb",
  "Soyuz":        "/models/soyuz.glb",
  "Titan IV":     "/models/titan4.glb",
  "Long March 5": "/models/longmarch5.glb",
  "GSLV Mk II":   "/models/gslvmk2.glb",
};

/**
 * Returns the .glb path for a rocket, or null if no model exists.
 * @param {string} name - Rocket display name
 * @returns {string|null}
 */
export function getRocketModel(name) {
  return ROCKET_MODELS[name] ?? null;
}

export default ROCKET_MODELS;
