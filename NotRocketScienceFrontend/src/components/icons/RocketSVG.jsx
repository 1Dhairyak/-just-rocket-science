const RocketSVG = ({ size = 24, color = "currentColor" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke={color}
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M4.5 16.5c-1.5 1.5-1.5 3.5 0 5s3.5 1.5 5 0l7-7-5-5-7 7z" />
    <path d="M12 2s4 2 6 6-2 10-2 10" />
    <path d="M12 2s-4 2-6 6 2 10 2 10" />
    <circle cx="12" cy="12" r="2" />
  </svg>
);

export default RocketSVG;
