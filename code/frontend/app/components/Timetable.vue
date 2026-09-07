<script setup lang="ts">
import { weekdays, publicationNames, type Course } from "~/types/domain";
import { layoutDay } from "~/utils/timetable";
const props = defineProps<{ courses: Course[]; teacher: boolean }>();
const emit = defineEmits<{ detail: [course: Course] }>();
const days = computed(() =>
  weekdays.map((label, i) => {
    const blocks = layoutDay(props.courses, i + 1);
    return {
      label,
      blocks,
      width: Math.max(168, ...blocks.map((b) => b.columns * 140)),
    };
  }),
);
const times = Array.from(
  { length: 21 },
  (_, i) =>
    `${String(8 + Math.floor(i / 2)).padStart(2, "0")}:${i % 2 ? "30" : "00"}`,
);
</script>
<template>
  <div
    class="timetable-scroll"
    tabindex="0"
    aria-label="每周课表，可横向滚动查看重叠课程"
  >
    <div
      class="timetable"
      :style="{
        gridTemplateColumns: `72px ${days.map((d) => `${d.width}px`).join(' ')}`,
      }"
    >
      <div class="time-heading">时间</div>
      <div v-for="day in days" :key="day.label" class="day-heading">
        {{ day.label }}
      </div>
      <div class="time-axis">
        <span
          v-for="(time, i) in times"
          :key="time"
          :style="{ top: `${i * 40}px` }"
          >{{ time }}</span
        >
      </div>
      <div v-for="day in days" :key="day.label" class="day-column">
        <div class="lunch">午休 · 12:00—14:00</div>
        <button
          v-for="block in day.blocks"
          :key="`${block.course.id}-${block.meeting.weekday}`"
          class="class-block"
          :style="{
            top: `${block.top}px`,
            height: `${block.height}px`,
            left: `calc(${(block.column / block.columns) * 100}% + 3px)`,
            width: `calc(${100 / block.columns}% - 6px)`,
            background: block.course.displayColor,
            borderStyle: block.course.borderStyle,
          }"
          :aria-label="`${block.course.code} ${block.course.name} ${block.meeting.startTime}至${block.meeting.endTime}，查看详情`"
          @click="emit('detail', block.course)"
        >
          <strong>{{ block.course.code }} · {{ block.course.name }}</strong
          ><span v-if="block.height >= 80"
            >{{ block.meeting.startTime }}—{{ block.meeting.endTime }}</span
          ><small v-if="teacher && block.height >= 120">{{
            publicationNames[block.course.publication]
          }}</small>
        </button>
      </div>
    </div>
  </div>
  <p class="hint">
    每周重复，仅在本学期起止日期内有效。点击课程查看完整安排；重叠课程并列显示。
  </p>
</template>
