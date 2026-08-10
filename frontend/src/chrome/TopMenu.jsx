import { OV, PLAYER_NAME } from "../lib/data";

export const MENU = [
  {
    label: "Overviews",
    screen: OV("combined"),
    children: [
      ["Combined", OV("combined")],
      ["Production", OV("prod")],
      ["Transports", OV("trader")],
      ["Troops", OV("units")],
      ["Commands", OV("commands")],
      ["Incoming", OV("incomings")],
      ["Buildings", OV("buildings")],
      ["Research", OV("tech")],
      ["Groups", OV("groups")],
    ],
  },
  { label: "Map", screen: "map", id: "menu_map_link" },
  {
    label: "Reports",
    screen: "report:all",
    reports: true,
    children: [
      ["All reports", "report:all"],
      ["Attacks", "report:attack"],
      ["Defenses", "report:defense"],
      ["Support", "report:support"],
      ["Trade", "report:trade"],
      ["Miscellaneous", "report:other"],
      ["Forwarded", "report:forwarded"],
      ["Filter", "report:filter"],
      ["Block sender", "settings:block"],
      ["Publicized reports", "report:public"],
    ],
  },
  {
    label: "Mail",
    mail: true,
    screen: "mail:in",
    children: [
      ["Mail", "mail:in"],
      ["Circular mail", "mail:mass_out"],
      ["Write message", "mail:new"],
      ["Block sender", "settings:block"],
      ["Address book", "mail:address"],
      ["Folders", "mail:groups"],
    ],
  },
  { pad: "lpad" },
  {
    ranking: true,
    label: "Ranking",
    screen: "ranking:player",
    children: [
      ["Tribes", "ranking:ally"],
      ["Players", "ranking:player"],
      ["Continent Tribes", "ranking:con_ally"],
      ["Continent Players", "ranking:con_player"],
      ["Opponents defeated tribal ranking", "ranking:kill_ally"],
      ["Opponents defeated", "ranking:kill_player"],
      ["Wars", "ranking:wars"],
    ],
  },
  {
    label: "Tribe",
    screen: "ally",
    ally: true,
    // layouts/game.php: with an unread forum post the entry gets the "new_post" icon and this dropdown
    children: [
      ["Overview", "ally:overview"],
      ["Profile", "ally:profile"],
      ["Members", "ally:members"],
      ["Tribal forum", "ally:forum"],
    ],
  },
  { pad: "rpad" },
  {
    label: "Profile",
    screen: "info_player",
    profile: true,
    children: [
      ["__player__", "info_player", "badge"],
      ["inventory", "inventory", "badge badge-inventory"],
      ["achievements", "info_player:awards", "badge"],
      ["statistics", "info_player:stats_own", "badge"],
      ["Friends", "buddies", "badge badge-buddy"],
      ["Blocked players", "info_player:block", "badge"],
    ],
  },
  {
    label: "Settings",
    screen: "settings",
    children: [
      ["Profile", "settings:profile"],
      ["Settings", "settings:settings"],
      ["Change password", "settings:change_passwd"],
      ["Delete account", "settings:delete"],
      ["Notifications", "settings:notify"],
      ["Edit quick bar", "settings:quickbar"],
      ["Logins", "settings:logins"],
      ["Surveys", "settings:poll"],
      ["Toolbar", "settings:toolbar"],
      ["iPhone & Android", "settings:push"],
      ["Block player", "settings:block"],
      ["Support ticket", "settings:ticket"],
    ],
  },
];

export function DropDown({ items, go, playerName }) {
  return (
    <table cellSpacing="0" className="menu_column">
      <tbody>
        {items.map(([label, target, badge]) => (
          <tr key={label + target}>
            <td className="menu-column-item">
              <a href="#" onClick={(e) => go(e, target)}>
                {label === "__player__" ? playerName : label}
                {badge && <span className={badge}> </span>}
              </a>
            </td>
          </tr>
        ))}
        <tr>
          <td className="bottom">
            <div className="corner" />
            <div className="decoration" />
          </td>
        </tr>
      </tbody>
    </table>
  );
}

export function TopMenu({ go, unreadReports = 0, newForumPost = false, newMails = 0, points, playerName = PLAYER_NAME, rank = 1 }) {
  return (
    <div id="topContainer">
      <table id="topTable" style={{ textAlign: "center" }} cellSpacing="0">
        <tbody>
          <tr>
            <td style={{ textAlign: "center" }}>
              <table className="menu nowrap" style={{ whiteSpace: "nowrap" }}>
                <tbody>
                  <tr id="menu_row">
                    <td className="menu-side" />
                    {MENU.map((item, i) => {
                      if (item.pad) return <td key={i} className={"menu-item " + item.pad} />;
                      if (item.ally && newForumPost) {
                        return (
                          <td key={i} className="menu-item">
                            <a href="#" onClick={(e) => go(e, item.screen)}>
                              &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;{item.label}
                            </a>
                            <div className="buttonicon">
                              <a href="#" onClick={(e) => go(e, "ally:forum")} style={{ display: "inline" }}>
                                <span className="icon header new_post" title="New posts in the tribal forum" />
                              </a>
                            </div>
                            <DropDown items={item.children} go={go} playerName={playerName} />
                          </td>
                        );
                      }
                      const dropdown = !item.ally && item.children && (
                        <DropDown items={item.children} go={go} playerName={playerName} />
                      );
                      if (item.ranking) {
                        return (
                          <td key={i} className="menu-item" id="topdisplay">
                            <div className="bg">
                              <a href="#" onClick={(e) => go(e, item.screen)}>{item.label}</a>
                              <div className="rank">
                                (<span id="rank_rank">{rank}</span>.|<span id="rank_points">{points}</span> P)
                              </div>
                              {dropdown}
                            </div>
                          </td>
                        );
                      }
                      return (
                        <td key={i} className="menu-item">
                          <a id={item.id} href="#" onClick={(e) => go(e, item.screen)}>
                            {item.reports && (
                              <span id="new_report" className={"icon header new_report" + (unreadReports > 0 ? "" : "_faded")} />
                            )}
                            {item.mail && newMails > 0 && <span className="icon header new_mail" title="New mail" />}
                            {item.profile ? <> {item.label} <span id="menu_counter_profile" className="badge" /></> : item.label}
                            {item.reports && (
                              <>
                                {" "}
                                <span id="menu_report_count" className="badge badge-report">
                                  {unreadReports > 0 ? `(${unreadReports})` : ""}
                                </span>
                              </>
                            )}
                          </a>
                          {dropdown}
                        </td>
                      );
                    })}
                    <td className="menu-side">
                      <img src="/graphic/loading.gif" id="loading_content" style={{ display: "none" }} alt="" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
