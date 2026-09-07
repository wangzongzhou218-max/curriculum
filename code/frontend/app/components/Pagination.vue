<script setup lang="ts">
const props = defineProps<{
  page: number;
  total: number;
  size: number;
  busy?: boolean;
}>();
const emit = defineEmits<{ change: [page: number] }>();
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));
</script>
<template>
  <div class="pagination">
    <span>共 {{ total }} 条记录</span>
    <div>
      <button :disabled="page <= 1 || busy" @click="emit('change', page - 1)">
        上一页</button
      ><span>第 {{ page }} / {{ pages }} 页</span
      ><button
        :disabled="page >= pages || busy"
        @click="emit('change', page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>
