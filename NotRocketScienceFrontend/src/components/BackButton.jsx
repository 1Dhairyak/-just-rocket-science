import ArrowLeft from "./icons/ArrowLeft";

/** Styled back navigation button used across all detail pages. */
const BackButton = ({ onClick }) => (
  <button className="back-btn" onClick={onClick}>
    <ArrowLeft /> Back
  </button>
);

export default BackButton;
