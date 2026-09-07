<script setup lang="ts">
import { weekdays, type Meeting } from "~/types/domain";
defineProps<{ meetings: Meeting[] }>();
</script>
<template>
  <div class="meeting-list">
    <span v-for="meeting in meetings" :key="meeting.weekday"
      ><span>{{ weekdays[meeting.weekday - 1] }}</span
      ><b>{{ meeting.startTime }}—{{ meeting.endTime }}</b></span
    >
  </div>
</template>
