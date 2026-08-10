export function QuickBar({ go }) {
  const item = (screen, icon, label) => (
    <li>
      <span>
        <a href="#" onClick={(e) => go(e, screen)}>
          {icon && <img src={`/graphic/buildings/${icon}.png`} className="middle" alt="" />} {label}
        </a>
      </span>
    </li>
  );
  return (
    <table id="quickbar_outer" align="center" width="100%" cellSpacing="0">
      <tbody>
        <tr>
          <td>
            <table id="quickbar_inner" style={{ borderCollapse: "collapse" }} width="100%">
              <tbody>
                <tr className="topborder"><td className="left" /><td className="main" /><td className="right" /></tr>
                <tr>
                  <td className="left" />
                  <td className="main">
                    <ul className="menu quickbar">
                      {item("building:HEADQUARTERS", "main", "Headquarters")}
                      {item("train", "barracks", "Recruit")}
                      {item("place", "place", "Rally point")}
                    </ul>
                  </td>
                  <td className="right" />
                </tr>
                <tr className="bottomborder"><td className="left" /><td className="main" /><td className="right" /></tr>
                <tr>
                  <td className="shadow" colSpan="3">
                    <div className="leftshadow" />
                    <div className="rightshadow" />
                  </td>
                </tr>
              </tbody>
            </table>
          </td>
        </tr>
      </tbody>
    </table>
  );
}
