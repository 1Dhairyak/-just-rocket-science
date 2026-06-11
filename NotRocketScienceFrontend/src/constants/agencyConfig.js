// Brand colours and fallback labels for each space agency
export const AGENCY_CONFIGS = {
  NASA:          { bg: "#0b3d91", text: "#fff",    label: "NASA",  fontSize: 11 },
  ISRO:          { bg: "#ff9933", text: "#fff",    label: "ISRO",  fontSize: 11 },
  Roscosmos:     { bg: "#cc0000", text: "#fff",    label: "RCSM",  fontSize: 10 },
  CNSA:          { bg: "#de2910", text: "#ffde00", label: "CNSA",  fontSize: 11 },
  SpaceX:        { bg: "#000000", text: "#fff",    label: "SpaceX",fontSize: 7  },
  ESA:           { bg: "#003399", text: "#fff",    label: "ESA",   fontSize: 11 },
  JAXA:          { bg: "#003087", text: "#fff",    label: "JAXA",  fontSize: 11 },
  ULA:           { bg: "#1a47a0", text: "#fff",    label: "ULA",   fontSize: 11 },
  "Blue Origin": { bg: "#1c4e80", text: "#fff",    label: "BO",    fontSize: 11 },
  "Rocket Lab":  { bg: "#cc0000", text: "#fff",    label: "RL",    fontSize: 11 },
};

// Local logo assets (served from /public/logos/)
export const LOGO_MAP = {
  NASA:          { src: "/logos/nasa.jpg",         bg: "transparent" },
  SpaceX:        { src: "/logos/spacex.jpg",       bg: "transparent" },
  ISRO:          { src: "/logos/isro.jpg",         bg: "transparent" },
  ESA:           { src: "/logos/esa.jpg",          bg: "transparent" },
  Roscosmos:     { src: "/logos/roscosmos.jpg",    bg: "transparent" },
  CNSA:          { src: "/logos/cnsa.jpg",         bg: "transparent" },
  JAXA:          { src: "/logos/jaxa.jpg",         bg: "transparent" },
  "Blue Origin": { src: "/logos/blue-origin.png",  bg: "transparent" },
};
