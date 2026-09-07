<script setup lang="ts">
import { roleNames, semesterLabel, semesterNames } from "~/types/domain";
const session = useSession(),
  api = useApi(),
  route = useRoute();
const notice = ref(""),
  error = ref("");
const user = session.user;
const menus = computed(() =>
  user.value?.role === "ADMIN"
    ? [{ path: "/admin/accounts", label: "账号管理", mark: "人" }]
    : user.value?.role === "TEACHER"
      ? [
          { path: "/teacher/courses", label: "课程管理", mark: "课" },
          { path: "/teacher/students", label: "学生总览", mark: "人" },
          { path: "/teacher/timetable", label: "周课表", mark: "周" },
        ]
      : [
          { path: "/student/courses", label: "我的课程", mark: "课" },
          { path: "/student/catalog", label: "课程选择", mark: "选" },
          { path: "/student/timetable", label: "周课表", mark: "周" },
        ],
);
async function logout() {
  error.value = "";
  try {
    await api.mutate("/auth/logout", { label: "退出登录" });
    session.clear();
    await navigateTo("/login");
    await session.initialize();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function resolve(operation: PendingOperation) {
  error.value = "";
  notice.value = "";
  try {
    await api.resolve(operation);
    notice.value = `${operation.label}已完成，请查看最新数据`;
    window.dispatchEvent(new Event("data-refresh"));
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function retry(operation: PendingOperation) {
  error.value = "";
  notice.value = "";
  try {
    await api.retry(operation);
    notice.value = `${operation.label}已完成，请查看最新数据`;
    window.dispatchEvent(new Event("data-refresh"));
  } catch (e) {
    error.value = (e as Error).message;
  }
}
let lastActivity = 0;
function activity() {
  if (
    !user.value ||
    Date.now() - lastActivity < 60000 ||
    api.busy.value ||
    api.pending.value.length
  )
    return;
  lastActivity = Date.now();
  api.activity().catch(() => {});
}
onMounted(() => {
  lastActivity = Date.now();
  window.addEventListener("pointerdown", activity);
  window.addEventListener("keydown", activity);
});
onBeforeUnmount(() => {
  window.removeEventListener("pointerdown", activity);
  window.removeEventListener("keydown", activity);
});
watch(
  [() => route.path, () => session.ready.value],
  () => {
    if (!session.ready.value) return;
    const expected = user.value?.role.toLowerCase();
    if (!user.value) navigateTo("/login");
    else if (!route.path.startsWith(`/${expected}/`))
      navigateTo(user.value.homePath);
  },
  { immediate: true },
);
</script>
<template>
  <div v-if="user" class="app-shell">
    <aside class="sidebar">
      <NuxtLink :to="user.homePath" class="brand"
        ><span class="brand-symbol">课</span
        ><span>选课系统<small>教学与学习安排</small></span></NuxtLink
      >
      <div class="nav-caption">{{ roleNames[user.role] }}工作区</div>
      <nav aria-label="主导航">
        <NuxtLink
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          :class="{ selected: route.path.startsWith(item.path) }"
          ><span class="nav-mark">{{ item.mark }}</span
          >{{ item.label }}</NuxtLink
        >
      </nav>
      <div class="sidebar-note">
        <strong>安排清楚，学习有序</strong>
        <p>课程与选课按学期分别管理。</p>
      </div>
    </aside>
    <div class="main-column">
      <header class="topbar">
        <span
          >教务服务 <span class="separator">/</span>
          {{ menus.find((m) => route.path.startsWith(m.path))?.label }}</span
        >
        <div class="user-area">
          <span class="avatar">{{ user.name.slice(0, 1) }}</span
          ><span
            ><strong>{{ user.name }}</strong
            ><small>{{ user.account }}　{{ roleNames[user.role] }}</small></span
          ><button
            class="text-button"
            :disabled="api.busy.value"
            @click="logout"
          >
            退出登录
          </button>
        </div>
      </header>
      <main class="workspace">
        <div
          v-if="api.pending.value.length"
          class="message warning"
          role="status"
        >
          <strong>有操作结果待确认</strong>
          <div
            v-for="operation in api.pending.value"
            :key="operation.key"
            class="pending-line"
          >
            <span
              >{{ operation.label
              }}{{ operation.semesterId ? "（原学期）" : "" }}</span
            ><button @click="resolve(operation)">查询结果</button>
            <button v-if="api.canRetry(operation)" @click="retry(operation)">
              使用原操作重试
            </button>
          </div>
          <p>可以继续查看数据。确认结果前，请勿重复提交同一操作。</p>
        </div>
        <div v-if="error" class="message error" role="alert">{{ error }}</div>
        <div v-if="notice" class="message success" role="status">
          {{ notice }}
        </div>
        <section
          v-if="user.role !== 'ADMIN'"
          class="semester-bar"
          aria-label="学期选择"
        >
          <label for="semester">当前学期</label
          ><select id="semester" v-model="session.semesterId.value">
            <option
              v-for="s in session.semesters.value"
              :key="s.id"
              :value="s.id"
            >
              {{ semesterLabel(s) }} · {{ semesterNames[s.status] }}
            </option></select
          ><span v-if="session.current.value" class="semester-dates"
            >{{ session.current.value.startsOn }} 至
            {{ session.current.value.endsOnInclusive }}</span
          ><span
            v-if="session.current.value"
            class="badge"
            :class="session.current.value.status.toLowerCase()"
            >{{ semesterNames[session.current.value.status] }}</span
          >
        </section>
        <p
          v-if="session.current.value?.status === 'ENDED'"
          class="historical-note"
        >
          本学期已结束，仅可查看历史课程与最终选课结果。
        </p>
        <slot />
      </main>
      <footer class="app-footer">
        选课系统 <span>学校时区：Asia/Shanghai</span>
      </footer>
    </div>
  </div>
</template>
