<script setup lang="ts">
import {
  dateTime,
  publicationNames,
  type Page,
  type Student,
  type RosterItem,
  type Course,
} from "~/types/domain";
const props = defineProps<{ courseId?: string }>();
const api = useApi(),
  session = useSession(),
  state = usePageState();
const page = ref(1),
  total = ref(0),
  q = ref(""),
  selection = ref("ALL"),
  courseIdFilter = ref(""),
  ownCourses = ref<Course[]>([]),
  students = ref<Student[]>([]),
  roster = ref<RosterItem[]>([]),
  course = ref<Course>(),
  expanded = ref(new Set<string>());
async function load() {
  const semester = session.semesterId.value;
  if (!semester) return;
  await state.load(
    async () =>
      props.courseId
        ? {
            roster: await api.request<Page<RosterItem> & { course: Course }>(
              `/teacher/courses/${props.courseId}/students?semesterId=${semester}&page=${page.value}`,
            ),
          }
        : {
            students: await api.request<Page<Student>>(
              `/teacher/students?${new URLSearchParams({ semesterId: semester, q: q.value, selection: selection.value, courseId: courseIdFilter.value, page: String(page.value) })}`,
            ),
            courses: await (async () => {
              const first = await api.request<Page<Course>>(
                `/teacher/courses?semesterId=${semester}&page=1&size=100`,
              );
              const rest = await Promise.all(
                Array.from(
                  { length: Math.ceil(first.total / 100) - 1 },
                  (_, index) =>
                    api.request<Page<Course>>(
                      `/teacher/courses?semesterId=${semester}&page=${index + 2}&size=100`,
                    ),
                ),
              );
              return { ...first, items: [...first.items, ...rest.flatMap((item) => item.items)] };
            })(),
          },
    (result) => {
      if (result.roster) {
        roster.value = result.roster.items;
        total.value = result.roster.total;
        course.value = result.roster.course;
      }
      if (result.students) {
        students.value = result.students.items;
        total.value = result.students.total;
        ownCourses.value = result.courses?.items || [];
      }
    },
  );
}
function toggle(id: string) {
  const next = new Set(expanded.value);
  next.has(id) ? next.delete(id) : next.add(id);
  expanded.value = next;
}
watch(
  () => [session.semesterId.value, props.courseId],
  () => {
    state.invalidate();
    page.value = 1;
    students.value = [];
    roster.value = [];
    expanded.value = new Set();
    course.value = undefined;
    load();
  },
);
onMounted(() => {
  load();
  window.addEventListener("data-refresh", load);
});
onBeforeUnmount(() => window.removeEventListener("data-refresh", load));
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>{{ courseId ? "课程学生名单" : "学生总览" }}</h1>
      <p>
        {{
          courseId
            ? "查看本课程的有效选课记录；历史名单保留已删除账号标记。"
            : "查看全部学生在所选学期的完整选课情况，包含尚未选课的学生。"
        }}
      </p>
    </div>
    <NuxtLink v-if="courseId" class="button" to="/teacher/courses"
      >返回课程管理</NuxtLink
    ><span v-else class="read-only-label">只读查询</span>
  </div>
  <div v-if="state.error.value" class="message error" role="alert">
    {{ state.error.value }} <button @click="load">重试</button>
  </div>
  <section class="data-sheet">
    <div v-if="course" class="roster-heading">
      <CourseTitle :course="course" /><span
        class="badge"
        :class="course.publication.toLowerCase()"
        >{{ publicationNames[course.publication] }}</span
      ><strong>有效选课 {{ total }} 人</strong>
    </div>
    <form
      v-if="!courseId"
      class="toolbar"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label class="sr-only" for="student-search">学生姓名或账号</label
      ><input
        id="student-search"
        v-model="q"
        class="search"
        placeholder="搜索学生姓名或登录账号"
        maxlength="100"
      /><label class="sr-only" for="selection-filter">选课状态</label
      ><select id="selection-filter" v-model="selection">
        <option value="ALL">全部学生</option>
        <option value="SELECTED">已选课</option>
        <option value="NONE">未选课</option></select
      ><label class="sr-only" for="course-filter">我的课程</label
      ><select id="course-filter" v-model="courseIdFilter">
        <option value="">我的全部课程</option>
        <option v-for="item in ownCourses" :key="item.id" :value="item.id">
          {{ item.code }} - {{ item.name }}
        </option>
      </select
      ><button>查询</button>
    </form>
    <div v-if="state.loading.value" class="empty" role="status">
      正在读取学生数据…
    </div>
    <template v-else-if="courseId"
      ><table v-if="roster.length">
        <thead>
          <tr>
            <th>姓名</th>
            <th>学生登录账号</th>
            <th>选课时间</th>
            <th>账号状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="student in roster" :key="student.studentId">
            <td>
              <strong>{{ student.studentName }}</strong>
            </td>
            <td>{{ student.studentAccount }}</td>
            <td>{{ dateTime(student.enrolledAt) }}</td>
            <td>
              <span class="badge neutral">{{
                student.accountDeleted ? "账号已删除" : "正常"
              }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">
        <h3>暂无学生选择此课程</h3>
        <p>学生选入后，可在这里查看名单。</p>
      </div></template
    ><template v-else
      ><table v-if="students.length" class="student-table">
        <thead>
          <tr>
            <th>学生姓名</th>
            <th>登录账号</th>
            <th>当期选课</th>
            <th>课程清单</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="student in students" :key="student.studentId"
            ><tr>
              <td>
                <strong>{{ student.studentName }}</strong>
              </td>
              <td>{{ student.studentAccount }}</td>
              <td>
                <span class="count-meter">{{ student.selectedCount }} / 4</span
                ><span v-if="!student.selectedCount" class="hint"> 未选课</span>
              </td>
              <td>
                <button
                  class="text-button"
                  :disabled="!student.courses.length"
                  :aria-expanded="expanded.has(student.studentId)"
                  @click="toggle(student.studentId)"
                >
                  {{
                    expanded.has(student.studentId) ? "收起课程" : "展开课程"
                  }}
                </button>
              </td>
            </tr>
            <tr v-if="expanded.has(student.studentId)" class="expanded-row">
              <td colspan="4">
                <div
                  v-for="c in student.courses"
                  :key="c.id"
                  class="student-course"
                >
                  <CourseTitle :course="c" /><span>{{ c.teacher.name }}</span
                  ><MeetingList :meetings="c.meetings" /><span
                    class="badge"
                    :class="c.publication.toLowerCase()"
                    >{{ publicationNames[c.publication] }}</span
                  >
                </div>
              </td>
            </tr></template
          >
        </tbody>
      </table>
      <div v-else class="empty">
        <h3>暂无匹配学生</h3>
        <p>可以调整姓名、账号或选课筛选条件。</p>
      </div></template
    ><Pagination
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
</template>
