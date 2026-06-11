import { useState, useEffect } from "react";
import API from "../api/config";

/**
 * Fetches all rockets belonging to a specific agency.
 * @param {number|string} id - Agency ID
 * @returns {{ data: Array, loading: boolean, error: string|null }}
 */
function useAgencyRockets(id) {
  const [data, setData]       = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    if (!id) return;
    fetch(`${API}/agencies/${id}/rockets?size=100`)
      .then(r => r.json())
      .then(j => setData(j.content ?? j))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { data, loading, error };
}

export default useAgencyRockets;
