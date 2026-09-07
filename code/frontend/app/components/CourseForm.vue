<script setup lang="ts">
import {
  weekdays,
  semesterLabel,
  type Course,
  type Semester,
  type Meeting,
} from "~/types/domain";
const props = defineProps<{
  course?: Course;
  semester: Semester;
  busy?: boolean;
}>();
const emit = defineEmits<{
  submit: [
    body: {
      semesterId: string;
      name: string;
      description: string;
      meetings: Meeting[];
    },
  ];
}>();
const fields = reactive({
  name: props.course?.name || "",
  description: props.course?.description || "",
  meetings: props.course?.meetings.map((m) => ({ ...m })) || [
    { weekday: 1, startTime: "08:00", endTime: "09:00" },
  ],
});
const error = ref("");
const times = Array.from(
  { length: 21 },
  (_, i) =>
    `${String(8 + Math.floor(i / 2)).padStart(2, "0")}:${i % 2 ? "30" : "00"}`,
);
const timeLocked = computed(() => !!props.course?.selectedCount);
function submit() {
  error.value = "";
  if (
    !fields.name.trim() ||
    Array.from(fields.name.trim()).length > 100 ||
    !fields.description.trim() ||
    Array.from(fields.description.trim()).length > 2000
  ) {
    error.value = "请填写有效课程名称（1～100字）和描述（1～2000字）";
    return;
  }
  if (
    new Set(fields.meetings.map((m) => m.weekday)).size !==
      fields.meetings.length ||
    fields.meetings.some(
      (m) =>
        m.startTime >= m.endTime ||
        !(
          (m.startTime >= "08:00" && m.endTime <= "12:00") ||
          (m.startTime >= "14:00" && m.endTime <= "18:00")
        ),
    )
  ) {
    error.value = "每次授课须在不同工作日，且完整处于上午或下午，不能跨午休";
    return;
  }
  emit("submit", {
    semesterId: props.semester.id,
    name: fields.name.trim(),
    description: fields.description.trim(),
    meetings: fields.meetings.map((m) => ({ ...m })),
  });
}
</script>
<template>
  <form class="stack-form" @submit.prevent="submit">
    <div class="readonly-info">
      {{ semesterLabel(semester)
      }}<span>{{ semester.startsOn }} 至 {{ semester.endsOnInclusive }}</span>
    </div>
    <div v-if="error" class="message error" role="alert">{{ error }}</div>
    <label
      >课程名称<input
        v-model="fields.name"
        required
        maxlength="200"
        placeholder="请输入课程名称"
      /><small>{{ Array.from(fields.name).length }} / 100</small></label
    ><label
      >课程描述<textarea
        v-model="fields.description"
        required
        rows="4"
        maxlength="4000"
        placeholder="说明课程内容与学习安排"
      /><small>{{ Array.from(fields.description).length }} / 2000</small></label
    >
    <div class="section-label">
      <h3>每周授课安排</h3>
      <span>一至两次，半小时对齐</span>
    </div>
    <p v-if="timeLocked" class="message neutral">
      已有
      {{ course?.selectedCount }}
      名学生选课，授课安排不可修改。仍可编辑名称和描述。
    </p>
    <fieldset
      v-for="(meeting, i) in fields.meetings"
      :key="i"
      class="meeting-fields"
      :disabled="timeLocked"
    >
      <legend>第 {{ i + 1 }} 次授课</legend>
      <label
        >星期<select v-model.number="meeting.weekday">
          <option
            v-for="(day, index) in weekdays"
            :key="day"
            :value="index + 1"
          >
            {{ day }}
          </option>
        </select></label
      ><label
        >开始<select v-model="meeting.startTime">
          <option
            v-for="time in times.filter(
              (t) => t < '12:00' || (t >= '14:00' && t < '18:00'),
            )"
            :key="time"
          >
            {{ time }}
          </option>
        </select></label
      ><label
        >结束<select v-model="meeting.endTime">
          <option
            v-for="time in times.filter(
              (t) => (t > '08:00' && t <= '12:00') || t > '14:00',
            )"
            :key="time"
          >
            {{ time }}
          </option>
        </select></label
      ><button
        v-if="i === 1"
        type="button"
        class="text-button danger-text"
        @click="fields.meetings.splice(1, 1)"
      >
        移除
      </button>
    </fieldset>
    <button
      v-if="fields.meetings.length < 2 && !timeLocked"
      type="button"
      class="add-meeting"
      @click="
        fields.meetings.push({
          weekday: fields.meetings[0]!.weekday === 3 ? 4 : 3,
          startTime: '14:00',
          endTime: '15:00',
        })
      "
    >
      ＋ 添加第二次授课
    </button>
    <p class="hint">
      {{
        course
          ? "编号、任课教师、学期和上架状态保持不变。"
          : "保存后课程为“未上架”，你可以在核对后单独上架。"
      }}
    </p>
    <button class="primary" :disabled="busy">
      {{ busy ? "正在保存…" : "保存课程" }}
    </button>
  </form>
</template>
