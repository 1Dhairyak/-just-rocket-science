// Per-rocket stage descriptions shown in the blueprint info box.
// Each stage index maps to a title and an array of rocket-keyed description lines.
// The "default" key is used as a fallback when no rocket-specific entry matches.
const STAGE_DATA = {
  0: {
    title: "STAGE 1 — BOOSTER",
    lines: [
      { "Falcon 9":     "9x Merlin 1D engines, 7,607 kN thrust, reusable booster" },
      { "Falcon Heavy": "27x Merlin engines, 22,819 kN total thrust" },
      { "Starship":     "33x Raptor engines, methane/LOX, sea-level" },
      { "PSLV":         "PS1 core stage, S138 solid motor, 4,797 kN thrust" },
      { "GSLV":         "GS1 stage, Vikas liquid engine, 1,672 kN thrust" },
      { "Soyuz":        "4x liquid boosters + core stage, RD-107A engines" },
      { "Proton":       "6x RD-276 engines, 10,000 kN total thrust at liftoff" },
      { "Ariane 5":     "Vulcain 2 cryogenic main engine + 2 solid boosters" },
      { "SLS":          "4x RS-25 engines + 2 solid rocket boosters, 39,144 kN" },
      { "H-IIA":        "LE-7A cryogenic main engine + 2 SRB-A solid boosters" },
      { "H3":           "2x LE-9 cryogenic engines, liquid hydrogen/oxygen" },
      { "Atlas V":      "RD-180 engine, liquid oxygen/kerosene, 3,827 kN thrust" },
      { "Vulcan":       "2x BE-4 engines, liquefied natural gas/liquid oxygen" },
      { "Long March":   "2x YF-77 cryogenic engines + 4 liquid strap-on boosters" },
      { "Electron":     "9x Rutherford electric-pump engines, 192 kN thrust" },
      { "default":      "Primary propulsion stage. Provides main thrust at liftoff." },
    ],
  },
  1: {
    title: "STAGE 2 — UPPER STAGE",
    lines: [
      { "Falcon 9":     "1x Merlin Vacuum engine, 934 kN thrust, delivers payload to orbit" },
      { "Falcon Heavy": "1x Merlin Vacuum engine, restartable for complex missions" },
      { "Starship":     "6x Raptor Vacuum engines optimised for in-space propulsion" },
      { "PSLV":         "PS2 liquid stage, Vikas engine, 799 kN thrust" },
      { "GSLV":         "GS2 liquid stage with CUS cryogenic upper stage" },
      { "Soyuz":        "Block I stage, RD-0110 engine, 298 kN vacuum thrust" },
      { "Proton":       "RD-0210/0211 engines, 2,399 kN vacuum thrust" },
      { "Ariane 5":     "HM7B cryogenic upper stage, 64.8 kN vacuum thrust" },
      { "SLS":          "Interim Cryogenic Propulsion Stage (ICPS), RL10 engine" },
      { "Atlas V":      "Centaur upper stage, RL10 engine, liquid hydrogen/oxygen" },
      { "Long March":   "YF-75D cryogenic upper stage engine" },
      { "default":      "Delivers payload to final orbit after stage separation." },
    ],
  },
  2: {
    title: "PAYLOAD / FAIRING",
    lines: [
      { "Falcon 9":     "5.2 m diameter fairing, 22,800 kg to LEO payload capacity" },
      { "Falcon Heavy": "5.2 m fairing, up to 63,800 kg to LEO" },
      { "Starship":     "9 m diameter cargo bay, 100-150 t to LEO fully reusable" },
      { "PSLV":         "1 m x 3.2 m fairing, up to 1,750 kg to SSO" },
      { "GSLV":         "3.4 m fairing, 2,500 kg to GTO payload capacity" },
      { "Soyuz":        "Crew or cargo module, up to 7,000 kg to LEO" },
      { "Proton":       "4.35 m fairing, up to 23,000 kg to LEO" },
      { "Ariane 5":     "5.4 m fairing, dual-launch capability to GTO" },
      { "SLS":          "Orion capsule or 8.4 m universal stage adapter for cargo" },
      { "default":      "Protective fairing encloses satellite or cargo payload." },
    ],
  },
};

/**
 * Returns the title and description text for a given rocket and stage index.
 * Falls back to partial name match, then to the "default" entry.
 *
 * @param {string} rocketName
 * @param {number} stageIdx - 0 (booster), 1 (upper), 2 (payload)
 * @returns {{ title: string, text: string }}
 */
export function getStageText(rocketName, stageIdx) {
  const data = STAGE_DATA[stageIdx];
  if (!data) return { title: "", text: "" };

  // Exact match first
  for (const entry of data.lines) {
    if (entry[rocketName]) return { title: data.title, text: entry[rocketName] };
  }

  // Partial match (e.g. "Long March 5" matches "Long March")
  for (const entry of data.lines) {
    const key = Object.keys(entry)[0];
    if (key !== "default" && rocketName?.toLowerCase().includes(key.toLowerCase())) {
      return { title: data.title, text: entry[key] };
    }
  }

  return { title: data.title, text: data.lines.find(e => e.default)?.default ?? "" };
}

export default STAGE_DATA;
