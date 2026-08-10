/* Village groups (Overviews > Groups, the header's village-group popup). The list comes from /api/groups; which group
   is selected is remembered per world in the browser, like the original's "last selected" group. Group 0 = "all". */
import { useCallback, useEffect, useState } from "react";
import { api } from "../api";

const key = (worldId) => "twlan_group_" + worldId;

function storedGroup(worldId) {
  try {
    const v = Number(window.localStorage.getItem(key(worldId)));
    return Number.isFinite(v) ? v : 0;
  } catch {
    return 0;
  }
}

/** Groups of the viewer in `worldId` plus the selected one; `active` = logged in with a village. */
export function useGroups(worldId, active) {
  const [groups, setGroups] = useState([]);
  const [picked, setPicked] = useState({}); // worldId -> group id chosen in this session

  useEffect(() => {
    if (!active || worldId == null) return undefined;
    const load = () => api.groups().then(setGroups).catch(() => {});
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, [active, worldId]);

  const wanted = picked[worldId] ?? storedGroup(worldId);
  const groupId = wanted === 0 || groups.some((g) => g.id === wanted) ? wanted : 0;

  const selectGroup = useCallback(
    (id) => {
      setPicked((p) => ({ ...p, [worldId]: id }));
      try {
        window.localStorage.setItem(key(worldId), String(id));
      } catch {
        /* storage unavailable: the choice just isn't remembered */
      }
    },
    [worldId]
  );

  // every action answers with the fresh list of groups; a refused rule throws (its message is shown by the caller)
  const actions = {
    create: (name) => api.createGroup(name).then(setGroups),
    rename: (id, name) => api.renameGroup(id, name).then(setGroups),
    remove: (id) => api.deleteGroup(id).then(setGroups),
    assign: (villageId, groupIds) => api.assignGroups(villageId, groupIds).then(setGroups),
  };

  const current = groups.find((g) => g.id === groupId) ?? null;
  /** the villages of the selected group (all of them for "all") */
  const inGroup = (list) => (current ? list.filter((v) => current.villageIds.includes(v.id)) : list);

  return { groups, groupId, group: current, selectGroup, actions, inGroup };
}
