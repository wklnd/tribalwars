import { useState } from "react";

const swatch = (rgb) => ({ width: 15, minWidth: 15, padding: 0, backgroundColor: rgb });

/* Colour legend below the map plus the (server-side, here inert) "Manage groups" box. */
export function MapLegend() {
  const [open, setOpen] = useState(false);
  const [newGroup, setNewGroup] = useState(false);
  const cell = (rgb, text) => (
    <>
      <td style={swatch(rgb)}></td>
      <td style={{ whiteSpace: "normal" }} className="small">
        {text}
      </td>
    </>
  );
  return (
    <>
      <div style={{ float: "left", marginBottom: 15 }} className="containerBorder">
        <table
          style={{
            border: "solid 1px #8c5f0d",
            backgroundColor: "#f4e4bc",
            marginLeft: 0,
            borderCollapse: "separate",
            textAlign: "left",
          }}
        >
          <tbody>
            <tr className="nowrap">
              <td valign="top" className="small">
                Standard:
              </td>
              {cell("rgb(255, 255, 255)", "Selected")}
              {cell("rgb(240, 200, 0)", "Your villages")}
              {cell("rgb(0, 0, 244)", "Your tribe")}
              {cell("rgb(150, 150, 150)", "Abandoned villages")}
              {cell("rgb(130, 60, 10)", "Miscellaneous")}
            </tr>
            <tr className="nowrap">
              <td valign="top" className="small">
                Tribe:
              </td>
              {cell("rgb(0, 160, 244)", "Allies")}
              {cell("rgb(128, 0, 128)", "Non-Aggression-Pact (NAP)")}
              {cell("rgb(244, 0, 0)", "Enemies")}
              <td></td>
              <td></td>
            </tr>
          </tbody>
        </table>
      </div>
      <br />
      <div style={{ width: "100%", textAlign: "left", clear: "both" }}>
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault();
            setOpen(!open);
          }}
        >
          » Manage groups
        </a>
      </div>
      <br />
      <div
        style={{ float: "left", clear: "both", display: open ? "block" : "none" }}
        className="containerBorder"
        id="village_colors"
      >
        <table style={{ backgroundColor: "#f4e4bc", border: "solid 1px #8c5f0d" }}>
          <tbody>
            <tr>
              <td valign="top">
                <h5>Your villages</h5>
              </td>
              <td>&nbsp;&nbsp;&nbsp;&nbsp;</td>
              <td valign="top">
                <h5>Other villages</h5>
                <form
                  action="game.php?screen=map&type=for&action=activate_group"
                  method="post"
                  onSubmit={(e) => e.preventDefault()}
                >
                  <table id="for_groups" className="vis">
                    <tbody>
                      <tr style={{ display: newGroup ? "table-row" : "none" }} id="new_group">
                        <td colSpan={5}>
                          <input
                            type="text"
                            name="new_group_name"
                            onKeyDown={(e) => {
                              if (e.key === "Enter") {
                                e.preventDefault();
                                document.getElementById("for_new_group")?.click();
                              }
                            }}
                          />
                          <input type="submit" value="OK" id="for_new_group" name="for_new_group" />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </form>
                <br />
                <a
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    setNewGroup(true);
                  }}
                >
                  » Create new group
                </a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </>
  );
}
