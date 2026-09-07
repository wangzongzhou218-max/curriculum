import type { User, Semester } from "~/types/domain";

export function useSession() {
  const user = useState<User | null>("user", () => null);
  const semesters = useState<Semester[]>("semesters", () => []);
  const semesterId = useState("semesterId", () => "");
  const ready = useState("session-ready", () => false);
  const api = useApi();
  const current = computed(() =>
    semesters.value.find((s) => s.id === semesterId.value),
  );
  async function loadSemesters() {
    const data = await api.request<{
      items: Semester[];
      defaultSemesterId: string;
    }>("/semesters");
    semesters.value = data.items;
    if (!data.items.some((s) => s.id === semesterId.value))
      semesterId.value = data.defaultSemesterId;
  }
  async function initialize() {
    if (ready.value) return;
    await api.request("/auth/context");
    try {
      user.value = await api.request<User>("/auth/me");
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
    if (user.value) await loadSemesters();
    api.restore(user.value?.userId || "");
    ready.value = true;
  }
  async function signedIn() {
    user.value = await api.request<User>("/auth/me");
    semesterId.value = "";
    api.clear();
    await loadSemesters();
    ready.value = true;
  }
  function clear() {
    user.value = null;
    semesters.value = [];
    semesterId.value = "";
    ready.value = false;
    api.clear();
  }
  return {
    user,
    semesters,
    semesterId,
    current,
    ready,
    initialize,
    loadSemesters,
    signedIn,
    clear,
  };
}
