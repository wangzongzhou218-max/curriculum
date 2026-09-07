<script setup lang="ts">
defineProps<{ title: string; wide?: boolean; busy?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
let previous: HTMLElement | null = null;
onMounted(() => {
  previous = document.activeElement as HTMLElement;
  dialog.value?.showModal();
});
onBeforeUnmount(() => {
  dialog.value?.close();
  previous?.focus();
});
</script>
<template>
  <dialog
    ref="dialog"
    class="modal"
    :class="{ wide }"
    aria-labelledby="modal-title"
    @cancel.prevent="emit('close')"
  >
    <div class="modal-header">
      <h2 id="modal-title">{{ title }}</h2>
      <button
        class="icon-button"
        aria-label="关闭对话框"
        autofocus
        @click="emit('close')"
      >
        ×
      </button>
    </div>
    <div class="modal-content"><slot /></div>
    <div v-if="$slots.footer" class="modal-footer"><slot name="footer" /></div>
  </dialog>
</template>
