export function usePageState() {
  const loading = ref(false),
    error = ref(""),
    notice = ref(""),
    trusted = ref(false);
  let generation = 0;
  async function load<T>(action: () => Promise<T>, apply: (result: T) => void) {
    const current = ++generation;
    loading.value = true;
    error.value = "";
    try {
      const data = await action();
      if (current === generation) {
        apply(data);
        trusted.value = true;
      }
    } catch (e) {
      if (current === generation) {
        error.value = (e as Error).message;
        trusted.value = false;
      }
    } finally {
      if (current === generation) loading.value = false;
    }
  }
  function invalidate() {
    generation++;
  }
  onBeforeUnmount(invalidate);
  return { loading, error, notice, trusted, load, invalidate };
}
