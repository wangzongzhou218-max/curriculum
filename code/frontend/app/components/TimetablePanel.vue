<script setup lang="ts">
import {
  publicationNames,
  semesterLabel,
  type Course,
  type TimetableData,
} from "~/types/domain";
const session = useSession(),
  api = useApi(),
  state = usePageState();
const rows = ref<Course[]>([]),
  detail = ref<Course>();
const teacher = computed(() => session.user.value?.role === "TEACHER");
async function load() {
  if (session.semesterId.value)
    await state.load(
      () =>
        api.request<TimetableData>(
          `/${teacher.value ? "teacher" : "student"}/timetable?semesterId=${session.semesterId.value}`,
        ),
      (data) => {
        rows.value = data.courses;
      },
    );
}
watch(session.semesterId, () => {
  state.invalidate();
  rows.value = [];
  detail.value = undefined;
  load();
});
onMounted(() => {
  load();
  window.addEventListener("data-refresh", load);
});
onBeforeUnmount(() => window.removeEventListener("data-refresh", load));
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>周课表</h1>
      <p>
        {{
          teacher
            ? "本人课程的完整周安排，包含备课中及已下架课程。"
            : "本学期全部有效已选课程，下架课程的安排仍然保留。"
        }}
      </p>
    </div>
    <NuxtLink
      class="button"
      :to="teacher ? '/teacher/courses' : '/student/courses'"
      >返回课程列表</NuxtLink
    >
  </div>
  <div v-if="state.error.value" class="message error" role="alert">
    {{ state.error.value }} <button @click="load">重新读取</button>
  </div>
  <section class="data-sheet timetable-sheet">
    <div class="sheet-caption">
      <strong>周一至周五</strong
      ><span>{{ rows.length }} 门课程 · 08:00—18:00</span
      ><button
        class="text-button"
        :disabled="state.loading.value"
        @click="load"
      >
        刷新课表
      </button>
    </div>
    <div v-if="state.loading.value" class="empty" role="status">
      正在读取完整课表…
    </div>
    <template v-else-if="!state.error.value"
      ><div v-if="!rows.length" class="timetable-empty">
        本学期暂无授课安排。可返回课程列表添加或选择课程。
      </div>
      <Timetable :courses="rows" :teacher="teacher" @detail="detail = $event"
    /></template>
  </section>
  <Modal v-if="detail" title="课程详情" @close="detail = undefined"
    ><CourseTitle :course="detail" />
    <p class="hint">
      {{ semesterLabel(detail.semester) }} · {{ detail.teacher.name }}
    </p>
    <span class="badge" :class="detail.publication.toLowerCase()">{{
      publicationNames[detail.publication]
    }}</span>
    <h3 class="detail-label">每周授课安排</h3>
    <MeetingList :meetings="detail.meetings" />
    <h3 class="detail-label">课程描述</h3>
    <p class="course-description">{{ detail.description }}</p></Modal
  >
</template>
