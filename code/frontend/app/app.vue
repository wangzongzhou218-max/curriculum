<script setup lang="ts">
const session = useSession();
const connectionError = ref("");
const route = useRoute();
async function initialize() {
  connectionError.value = "";
  try {
    await session.initialize();
    if (!session.user.value && route.path !== "/register")
      await navigateTo("/login");
    else if (
      session.user.value &&
      ["/", "/login", "/register"].includes(route.path)
    )
      await navigateTo(session.user.value.homePath);
  } catch {
    connectionError.value =
      "暂时无法连接服务。请确认后端与数据库已启动，然后重试。";
  }
}
async function expired() {
  session.clear();
  await navigateTo("/login");
  await initialize();
}
onMounted(() => {
  initialize();
  window.addEventListener("session-expired", expired);
});
onBeforeUnmount(() => window.removeEventListener("session-expired", expired));
</script>

<template>
  <div v-if="connectionError" class="connection-page">
    <div class="connection-box">
      <span class="brand-symbol">课</span>
      <h1>连接选课系统</h1>
      <p role="alert">{{ connectionError }}</p>
      <button class="primary" @click="initialize">重新连接</button>
    </div>
  </div>
  <div v-else-if="!session.ready.value" class="connection-page">
    <p role="status">正在连接选课系统…</p>
  </div>
  <div v-show="!connectionError && session.ready.value">
    <NuxtPage />
  </div>
</template>
