<script setup lang="ts">
import type { Account } from "~/types/domain";
const props = defineProps<{
  account?: Account;
  busy?: boolean;
  submitLabel?: string;
  showRegistrationNumber?: boolean;
}>();
const emit = defineEmits<{
  submit: [
    data: {
      role: string;
      name: string;
      registrationNumber?: string;
      email: string;
      password: string;
      confirmPassword: string;
    },
  ];
}>();
const fields = reactive({
  role: props.account?.role || "STUDENT",
  name: props.account?.name || "",
  registrationNumber: props.account?.registrationNumber || "",
  email: props.account?.email || "",
  password: "",
  confirmPassword: "",
});
const error = ref("");
function submit() {
  error.value = "";
  if (
    !props.account &&
    (Array.from(fields.password).length < 8 ||
      Array.from(fields.password).length > 64 ||
      fields.password.trim() === "")
  ) {
    error.value = "密码须为 8～64 个字符，且不能全为空白";
    return;
  }
  if (!props.account && fields.password !== fields.confirmPassword) {
    error.value = "两次密码不一致";
    return;
  }
  const data: {
    role: string;
    name: string;
    registrationNumber?: string;
    email: string;
    password: string;
    confirmPassword: string;
  } = { ...fields };
  if (props.showRegistrationNumber === false) delete data.registrationNumber;
  emit("submit", data);
}
onBeforeUnmount(() => {
  fields.password = "";
  fields.confirmPassword = "";
});
</script>
<template>
  <form class="stack-form" @submit.prevent="submit">
    <div v-if="error" class="message error" role="alert">{{ error }}</div>
    <label v-if="!account"
      >账号身份<select v-model="fields.role">
        <option value="STUDENT">学生</option>
        <option value="TEACHER">教师</option>
      </select></label
    >
    <div v-else class="readonly-info">
      {{ account.account }}
      <span
        >{{ account.role === "TEACHER" ? "教师" : "学生" }} ·
        账号及身份不可修改</span
      >
    </div>
    <label
      >姓名<input
        v-model="fields.name"
        required
        maxlength="100"
        autocomplete="name"
        placeholder="请输入姓名" /></label
    ><label v-if="props.showRegistrationNumber !== false"
      >{{ fields.role === "TEACHER" ? "工号" : "学号"
      }}<input
        v-model="fields.registrationNumber"
        required
        maxlength="32"
        pattern="[A-Za-z0-9_-]+"
        placeholder="字母、数字、短横线或下划线" /></label
    ><label
      >邮箱<input
        v-model="fields.email"
        type="email"
        required
        maxlength="254"
        autocomplete="email"
        placeholder="name@example.com" /></label
    ><template v-if="!account"
      ><label
        >密码<input
          v-model="fields.password"
          type="password"
          autocomplete="new-password"
          required
          maxlength="128"
          placeholder="8～64 个字符" /></label
      ><label
        >确认密码<input
          v-model="fields.confirmPassword"
          type="password"
          autocomplete="new-password"
          required
          maxlength="128"
          placeholder="再次输入密码" /></label></template
    ><button class="primary" :disabled="busy">
      {{
        busy ? "正在保存…" : submitLabel || (account ? "保存资料" : "创建账号")
      }}
    </button>
  </form>
</template>
