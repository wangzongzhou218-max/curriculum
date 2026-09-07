<script setup lang="ts">
import {
  publicationNames,
  semesterLabel,
  type Course,
  type Enrollment,
  type Page,
} from "~/types/domain";
const props = defineProps<{ mode: "teacher" | "catalog" | "selected" }>();
const api = useApi(),
  session = useSession(),
  state = usePageState();
const page = ref(1),
  total = ref(0),
  selectedCount = ref(0),
  rows = ref<Course[]>([]),
  selections = ref<Enrollment[]>([]);
const modal = ref<
  | "detail"
  | "create"
  | "edit"
  | "publish"
  | "unpublish"
  | "delete"
  | "drop"
  | "swap"
  | null
>(null);
const course = ref<Course>(),
  source = ref<Enrollment>(),
  formError = ref("");
const candidates = ref<Course[]>([]),
  candidatePage = ref(1),
  candidateTotal = ref(0),
  target = ref(""),
  candidateBusy = ref(false);
const teacher = computed(() => props.mode === "teacher"),
  ended = computed(() => session.current.value?.status === "ENDED");
const locked = computed(
  () =>
    api.busy.value ||
    !!api.pending.value.length ||
    state.loading.value ||
    !state.trusted.value,
);
const title = computed(() =>
  teacher.value
    ? "课程管理"
    : props.mode === "catalog"
      ? "课程选择"
      : "我的课程",
);
let contextGeneration = 0;
let detailGeneration = 0;
async function load() {
  if (!session.semesterId.value) return;
  const semesterId = session.semesterId.value,
    mode = props.mode;
  await state.load(
    async () => {
      if (mode === "selected")
        return {
          selected: await api.request<
            Page<Enrollment> & { totalSelected: number }
          >(`/student/enrollments?semesterId=${semesterId}`),
        };
      const list = await api.request<Page<Course>>(
        `/${mode === "teacher" ? "teacher/courses" : "student/catalog"}?semesterId=${semesterId}&page=${page.value}&size=20`,
      );
      const selected =
        mode === "catalog"
          ? await api.request<Page<Enrollment> & { totalSelected: number }>(
              `/student/enrollments?semesterId=${semesterId}`,
            )
          : undefined;
      return { list, selected };
    },
    (data) => {
      selections.value = data.selected?.items || [];
      selectedCount.value = data.selected?.totalSelected || 0;
      rows.value = data.list?.items || selections.value.map((e) => e.course);
      total.value = data.list?.total || data.selected?.total || 0;
    },
  );
}
async function open(
  kind: Exclude<typeof modal.value, null | "create">,
  row: Course,
) {
  formError.value = "";
  const generation = contextGeneration,
    detail = ++detailGeneration,
    semesterId = session.semesterId.value;
  try {
    let loadedCourse: Course;
    let loadedSource: Enrollment | undefined;
    if (teacher.value)
      loadedCourse = await api.request<Course>(
        `/teacher/courses/${row.id}?semesterId=${semesterId}`,
      );
    else {
      const enrollment = selections.value.find((e) => e.course.id === row.id);
      if (enrollment) {
        loadedSource = await api.request<Enrollment>(
          `/student/enrollments/${enrollment.id}?semesterId=${semesterId}`,
        );
        loadedCourse = loadedSource.course;
      } else {
        loadedCourse = await api.request<Course>(
          `/student/catalog/${row.id}?semesterId=${semesterId}`,
        );
      }
    }
    if (generation !== contextGeneration || detail !== detailGeneration) return;
    course.value = loadedCourse;
    source.value = loadedSource;
    modal.value = kind;
    if (kind === "swap") {
      target.value = "";
      candidatePage.value = 1;
      await loadCandidates();
    }
  } catch (e) {
    if (generation === contextGeneration)
      state.error.value = (e as Error).message;
  }
}
async function loadCandidates() {
  const generation = contextGeneration;
  candidateBusy.value = true;
  try {
    const data = await api.request<Page<Course>>(
      `/student/catalog?semesterId=${session.semesterId.value}&page=${candidatePage.value}&size=20`,
    );
    if (generation === contextGeneration) {
      candidates.value = data.items;
      candidateTotal.value = data.total;
    }
  } catch (e) {
    if (generation === contextGeneration)
      formError.value = (e as Error).message;
  } finally {
    candidateBusy.value = false;
  }
}
function close() {
  if (
    ["edit", "create"].includes(modal.value || "") &&
    !api.busy.value &&
    !window.confirm("放弃未保存的课程修改？")
  )
    return;
  modal.value = null;
  detailGeneration++;
  formError.value = "";
}
async function saved() {
  modal.value = null;
  await load();
  if (!rows.value.length && page.value > 1) {
    page.value--;
    await load();
  }
}
async function save(body: unknown) {
  formError.value = "";
  const editing = modal.value === "edit",
    generation = contextGeneration;
  try {
    const result = await api.mutate<{ code: string }>(
      `/teacher/courses${editing ? `/${course.value!.id}` : ""}`,
      {
        method: editing ? "PUT" : "POST",
        body,
        version: editing ? course.value!.version : undefined,
        label: editing ? "编辑课程" : "创建课程",
        semesterId: session.semesterId.value,
      },
    );
    if (generation === contextGeneration) {
      state.notice.value = `${result.code} ${editing ? "已保存" : "已创建，尚未上架"}`;
      await saved();
    }
  } catch (e) {
    if (generation === contextGeneration)
      formError.value = (e as Error).message;
  }
}
async function select(row: Course) {
  state.error.value = "";
  const generation = contextGeneration;
  try {
    await api.mutate("/student/enrollments", {
      label: `选入 ${row.code}`,
      body: { semesterId: session.semesterId.value, courseId: row.id },
      semesterId: session.semesterId.value,
    });
    if (generation === contextGeneration) {
      state.notice.value = `${row.code} 已选入`;
      await load();
    }
  } catch (e) {
    if (generation === contextGeneration)
      state.error.value = (e as Error).message;
  }
}
async function confirm() {
  const action = modal.value,
    current = course.value!,
    semesterId = session.semesterId.value,
    generation = contextGeneration;
  formError.value = "";
  try {
    if (action === "publish" || action === "unpublish")
      await api.mutate(`/teacher/courses/${current.id}/publication`, {
        body: {
          semesterId,
          target: action === "publish" ? "PUBLISHED" : "UNPUBLISHED",
        },
        version: current.version,
        label: `${action === "publish" ? "上架" : "下架"} ${current.code}`,
        semesterId,
      });
    else if (action === "delete")
      await api.mutate(
        `/teacher/courses/${current.id}?semesterId=${semesterId}`,
        {
          method: "DELETE",
          version: current.version,
          label: `删除 ${current.code}`,
          semesterId,
        },
      );
    else if (action === "drop")
      await api.mutate(
        `/student/enrollments/${source.value!.id}?semesterId=${semesterId}`,
        {
          method: "DELETE",
          version: source.value!.version,
          generation: source.value!.generation,
          label: `退选 ${current.code}`,
          semesterId,
        },
      );
    else if (action === "swap") {
      if (!target.value) {
        formError.value = "请选择换入课程";
        return;
      }
      await api.mutate(`/student/enrollments/${source.value!.id}/swap`, {
        version: source.value!.version,
        body: {
          semesterId,
          targetCourseId: target.value,
          sourceGeneration: source.value!.generation,
        },
        label: `换出 ${current.code}`,
        semesterId,
      });
    }
    if (generation === contextGeneration) {
      state.notice.value =
        action === "swap"
          ? "换课已完成，已选课程已更新"
          : action === "drop"
            ? "退课已完成，名额已释放"
            : action === "delete"
              ? "课程已删除"
              : action === "publish"
                ? "课程已上架"
                : "课程已下架，已有选课保留";
      await saved();
    }
  } catch (e) {
    if (generation === contextGeneration)
      formError.value = (e as Error).message;
  }
}
watch(
  () => [session.semesterId.value, props.mode],
  () => {
    contextGeneration++;
    state.invalidate();
    rows.value = [];
    selections.value = [];
    modal.value = null;
    state.notice.value = "";
    page.value = 1;
    load();
  },
);
onMounted(() => {
  load();
  window.addEventListener("data-refresh", load);
});
onBeforeUnmount(() => {
  contextGeneration++;
  window.removeEventListener("data-refresh", load);
});
const modalTitle = computed(
  () =>
    ({
      detail: "课程详情",
      create: "添加课程",
      edit: "编辑课程",
      publish: "上架课程",
      unpublish: "下架课程",
      delete: "删除课程",
      drop: "确认退课",
      swap: "更换课程",
    })[modal.value || "detail"],
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>
        {{ title }}
        <span v-if="!teacher" class="selection-counter"
          >{{ selectedCount }} / 4 <small>已选</small></span
        >
      </h1>
      <p>
        {{
          teacher
            ? "录入完整授课安排，核对后上架课程。"
            : mode === "catalog"
              ? "选择适合你的课程，每学期最多四门，时间不可冲突。"
              : "查看本学期的学习安排，按需退课或更换课程。"
        }}
      </p>
    </div>
    <div class="button-row">
      <NuxtLink
        v-if="mode === 'selected'"
        class="button primary"
        to="/student/catalog"
        >去选课</NuxtLink
      ><button
        v-if="teacher && !ended"
        class="primary"
        :disabled="locked"
        @click="
          modal = 'create';
          course = undefined;
          formError = '';
        "
      >
        ＋ 添加课程</button
      ><NuxtLink
        class="button"
        :to="teacher ? '/teacher/timetable' : '/student/timetable'"
        >查看周课表</NuxtLink
      >
    </div>
  </div>
  <div v-if="state.notice.value" class="message success" role="status">
    {{ state.notice.value }}
  </div>
  <div v-if="state.error.value" class="message error" role="alert">
    {{ state.error.value }} <button @click="load">重新读取</button>
  </div>
  <section class="data-sheet">
    <div class="sheet-caption">
      <strong>{{
        teacher
          ? "本学期课程"
          : mode === "catalog"
            ? "已上架课程"
            : "本学期已选课程"
      }}</strong
      ><span>{{
        teacher
          ? "仅显示本人创建的课程"
          : mode === "selected"
            ? "下架课程仍保留已选安排"
            : "一次选课包含该课程的全部授课安排"
      }}</span
      ><button
        class="text-button"
        :disabled="state.loading.value"
        @click="load"
      >
        刷新
      </button>
    </div>
    <div v-if="state.loading.value" class="empty" role="status">
      正在加载课程…
    </div>
    <table v-else-if="rows.length" class="courses-table">
      <thead>
        <tr>
          <th>课程</th>
          <th>每周授课安排</th>
          <th>{{ teacher ? "选课人数" : "任课教师" }}</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td><CourseTitle :course="row" /></td>
          <td><MeetingList :meetings="row.meetings" /></td>
          <td>{{ teacher ? `${row.selectedCount} 人` : row.teacher.name }}</td>
          <td>
            <span class="badge" :class="row.publication.toLowerCase()">{{
              publicationNames[row.publication]
            }}</span
            ><small
              v-if="!teacher && row.publication === 'UNPUBLISHED'"
              class="table-subline"
              >已有选课保留</small
            >
          </td>
          <td>
            <div class="row-actions">
              <button @click="open('detail', row)">详情</button
              ><template v-if="teacher"
                ><button
                  v-if="row.allowedActions.includes('edit')"
                  :disabled="locked"
                  @click="open('edit', row)"
                >
                  编辑</button
                ><button
                  v-if="row.allowedActions.includes('publish')"
                  :disabled="locked"
                  @click="open('publish', row)"
                >
                  上架</button
                ><button
                  v-if="row.allowedActions.includes('unpublish')"
                  :disabled="locked"
                  @click="open('unpublish', row)"
                >
                  下架</button
                ><NuxtLink :to="`/teacher/courses/${row.id}/students`"
                  >学生名单</NuxtLink
                ></template
              ><template v-else-if="mode === 'catalog'"
                ><span v-if="row.selected" class="selected-label">✓ 已选</span
                ><button
                  v-else-if="row.allowedActions.includes('select')"
                  class="small-primary"
                  :disabled="locked || selectedCount >= 4"
                  @click="select(row)"
                >
                  {{ selectedCount >= 4 ? "已满四门" : "选课" }}
                </button></template
              ><template v-else
                ><button
                  v-if="row.allowedActions.includes('swap')"
                  :disabled="locked"
                  @click="open('swap', row)"
                >
                  换课</button
                ><button
                  v-if="row.allowedActions.includes('drop')"
                  class="danger-text"
                  :disabled="locked"
                  @click="open('drop', row)"
                >
                  退课
                </button></template
              >
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty">
      <span class="empty-icon">课</span>
      <h3>{{ mode === "selected" ? "本学期尚未选课" : "本学期暂无课程" }}</h3>
      <p>
        {{
          teacher
            ? "添加一门课程，从安排好第一次授课开始。"
            : "可以切换学期查看，或稍后刷新课程目录。"
        }}
      </p>
    </div>
    <Pagination
      v-if="mode !== 'selected'"
      :page="page"
      :total="total"
      :size="20"
      :busy="state.loading.value"
      @change="
        page = $event;
        load();
      "
    />
  </section>
  <Modal
    v-if="modal"
    :title="modalTitle"
    :wide="modal === 'swap'"
    @close="close"
    ><div v-if="formError" class="message error" role="alert">
      {{ formError }}
    </div>
    <CourseForm
      v-if="(modal === 'create' || modal === 'edit') && session.current.value"
      :course="modal === 'edit' ? course : undefined"
      :semester="session.current.value"
      :busy="locked"
      @submit="save"
    /><template v-else-if="course"
      ><div class="detail-course">
        <CourseTitle :course="course" /><span
          class="badge"
          :class="course.publication.toLowerCase()"
          >{{ publicationNames[course.publication] }}</span
        >
      </div>
      <p class="hint">
        {{ semesterLabel(course.semester) }} · {{ course.teacher.name }}
      </p>
      <MeetingList :meetings="course.meetings" /><template
        v-if="modal === 'detail'"
        ><h3 class="detail-label">课程描述</h3>
        <p class="course-description">{{ course.description }}</p>
        <div class="button-row">
          <button @click="modal = null">关闭</button
          ><button
            v-if="teacher && course.allowedActions.includes('delete')"
            class="danger-text"
            :disabled="locked"
            @click="open('delete', course)"
          >
            删除课程</button
          ><span v-else-if="teacher && course.selectedCount" class="hint"
            >已有学生选课，无法删除或修改时间</span
          >
        </div></template
      ><template v-else
        ><p v-if="modal === 'publish'" class="confirmation-copy">
          上架后，这门课程将出现在学生课程目录中。
        </p>
        <p v-if="modal === 'unpublish'" class="confirmation-copy">
          停止新的选课，已有选课、学生课表和名单将保留。
        </p>
        <p v-if="modal === 'delete'" class="confirmation-copy">
          课程将从日常列表与课表中移除，历史记录保留。仅在没有有效选课时允许删除。
        </p>
        <p v-if="modal === 'drop'" class="confirmation-copy">
          退课成功后释放一个名额。之后仍可在符合条件时重新选入。
        </p>
        <template v-if="modal === 'swap'"
          ><div class="section-label">
            <h3>选择换入课程</h3>
            <span>失败时原课程保留</span>
          </div>
          <p class="hint">
            目标仅与换出课程重叠时可以换入，还须与其余已选课程校验。
          </p>
          <div v-if="candidateBusy" role="status">正在读取候选课程…</div>
          <div v-else class="swap-options">
            <label
              v-for="candidate in candidates"
              :key="candidate.id"
              :class="{ unavailable: candidate.selected }"
              ><input
                v-model="target"
                type="radio"
                name="swap-target"
                :value="candidate.id"
                :disabled="candidate.selected"
              />
              <div>
                <CourseTitle :course="candidate" /><MeetingList
                  :meetings="candidate.meetings"
                /><small
                  >{{ candidate.teacher.name
                  }}{{ candidate.selected ? " · 已选，不能换入" : "" }}</small
                >
              </div></label
            >
            <p v-if="!candidates.length" class="hint">
              暂无已上架课程可供选择。
            </p>
          </div>
          <Pagination
            :page="candidatePage"
            :total="candidateTotal"
            :size="20"
            :busy="candidateBusy"
            @change="
              candidatePage = $event;
              loadCandidates();
            "
        /></template>
        <div class="button-row">
          <button @click="modal = null">取消</button
          ><button
            :class="
              modal === 'delete' || modal === 'drop' ? 'danger' : 'primary'
            "
            :disabled="locked || (modal === 'swap' && !target)"
            @click="confirm"
          >
            {{
              api.busy.value
                ? "正在提交…"
                : modal === "swap"
                  ? "确认换课"
                  : modal === "drop"
                    ? "确认退课"
                    : modal === "delete"
                      ? "确认删除"
                      : modal === "publish"
                        ? "确认上架"
                        : "确认下架"
            }}
          </button>
        </div></template
      ></template
    ></Modal
  >
</template>
