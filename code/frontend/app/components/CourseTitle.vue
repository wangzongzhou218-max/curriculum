<script setup lang="ts">
import type { Course } from "~/types/domain";
defineProps<{ course: Course }>();
</script>
<template>
  <div class="course-title">
    <span
      class="course-marker"
      :style="{
        background: course.displayColor,
        borderStyle: course.borderStyle,
      }"
    />
    <div>
      <span class="course-code">{{ course.code }}</span
      ><strong>{{ course.name }}</strong>
    </div>
  </div>
</template>
