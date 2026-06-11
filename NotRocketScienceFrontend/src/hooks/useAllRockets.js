import { useState, useEffect } from "react";
import API from "../api/config";

/**
 * Fetches the full rocket catalogue (used by the Compare dropdowns).
 * @returns {{ data: Array, loading: boolean }}
 */
function useAllRockets() {
  const [data, setData]       = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch(`${API}/rockets?size=200`)
      .then(r => r.json())
      .then(j => setData(j.content ?? j))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return { data, loading };
}

export default useAllRockets;
