/** Site-wide footer shown at the bottom of every page. */
const Footer = () => (
  <div className="footer">
    <span className="footer-brand">Just Rocket Science</span>
    <div className="footer-links">
      {["Agencies", "Socials", "Legal", "Privacy"].map(link => (
        <span key={link} className="footer-link">{link}</span>
      ))}
    </div>
    <span className="footer-copy">© 2024 Just Rocket Science. All rights reserved.</span>
  </div>
);

export default Footer;
