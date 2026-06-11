import { useState, useEffect } from "react";
import API from "../api/config";

/**
 * Fetches all space agencies from the backend.
 * @returns {{ data: Array, loading: boolean, error: string|null }}
 */
function useAgencies() {
  const [data, setData]       = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  useEffect(() => {
    fetch(`${API}/agencies?size=100`)
      .then(r => r.json())
      .then(j => setData(j.content ?? j))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return { data, loading, error };
}

export default useAgencies;
