<script setup lang="ts">
const route = useRoute(),
  session = useSession();
const parts = computed(() => route.path.split("/").filter(Boolean));
const role = computed(() => parts.value[0]),
  section = computed(() => parts.value[1]);
const rosterId = computed(() =>
  role.value === "teacher" &&
  section.value === "courses" &&
  parts.value[3] === "students"
    ? parts.value[2]
    : undefined,
);
</script>
<template>
  <AppShell
    ><AccountsPanel
      v-if="role === 'admin' && section === 'accounts'"
    /><StudentsPanel
      v-else-if="role === 'teacher' && (section === 'students' || rosterId)"
      :key="rosterId || 'all-students'"
      :course-id="rosterId"
    /><TimetablePanel
      v-else-if="
        section === 'timetable' && (role === 'teacher' || role === 'student')
      "
    /><CoursesPanel
      v-else-if="role === 'teacher' && section === 'courses'"
      mode="teacher"
    /><CoursesPanel
      v-else-if="
        role === 'student' && (section === 'courses' || section === 'catalog')
      "
      :mode="section === 'catalog' ? 'catalog' : 'selected'"
    />
    <div v-else class="empty">
      <h1>页面不存在</h1>
      <NuxtLink :to="session.user.value?.homePath || '/login'"
        >返回工作区</NuxtLink
      >
    </div></AppShell
  >
</template>
