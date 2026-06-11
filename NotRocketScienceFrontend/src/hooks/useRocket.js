import { useState, useEffect } from "react";
import API from "../api/config";

/**
 * Fetches full detail for a single rocket by ID.
 * @param {number|string} id - Rocket ID
 * @returns {{ data: object|null, loading: boolean, error: string|null }}
 */
function useRocket(id) {
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    if (!id) return;
    fetch(`${API}/rockets/${id}`)
      .then(r => r.json())
      .then(j => setData(j))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);

  return { data, loading, error };
}

export default useRocket;
