<script setup lang="ts">
const session = useSession(),
  api = useApi();
const account = ref(""),
  password = ref(""),
  visible = ref(false),
  error = ref("");
async function submit() {
  error.value = "";
  try {
    await api.mutate("/auth/login", {
      label: "登录",
      anonymous: true,
      body: { account: account.value.trim(), password: password.value },
    });
    password.value = "";
    await session.signedIn();
    await navigateTo(session.user.value!.homePath);
  } catch (e) {
    password.value = "";
    error.value = (e as Error).message;
  }
}
async function recover(retry: boolean) {
  error.value = "";
  try {
    const operation = api.pending.value[0]!;
    if (retry) await api.retry(operation);
    else await api.resolve(operation);
    await session.signedIn();
    await navigateTo(session.user.value!.homePath);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
onBeforeUnmount(() => {
  password.value = "";
});
</script>
<template>
  <div class="auth-page">
    <section class="auth-intro">
      <div class="brand"><span class="brand-symbol">课</span>选课系统</div>
      <div>
        <p class="auth-kicker">教学与学习，从清晰的安排开始</p>
        <h1>每一门课程，<br />都有合适的位置。</h1>
        <p>
          按学期管理课程，查看授课安排，<br />在无冲突的时间里开始新的学习。
        </p>
        <div class="schedule-motif" aria-hidden="true">
          <span>周一</span><span>周二</span><span>周三</span><span>周四</span
          ><span>周五</span><i /><i /><i /><i /><i />
        </div>
      </div>
      <small>教师 · 学生 · 管理员</small>
    </section>
    <section class="auth-form-area">
      <form class="auth-form" @submit.prevent="submit">
        <h2>登录选课系统</h2>
        <p class="subtle">使用你的登录账号进入对应工作区</p>
        <div v-if="error" class="message error" role="alert">{{ error }}</div>
        <label
          >登录账号<input
            v-model="account"
            name="username"
            autocomplete="username"
            required
            placeholder="请输入登录账号"
            maxlength="32" /></label
        ><label
          >密码
          <div class="password-input">
            <input
              v-model="password"
              name="password"
              :type="visible ? 'text' : 'password'"
              autocomplete="current-password"
              required
              maxlength="128"
              placeholder="请输入密码"
            /><button
              type="button"
              :aria-label="visible ? '隐藏密码' : '显示密码'"
              @click="visible = !visible"
            >
              {{ visible ? "隐藏" : "显示" }}
            </button>
          </div></label
        ><button
          class="primary auth-submit"
          :disabled="api.busy.value || !!api.pending.value.length"
        >
          {{ api.busy.value ? "正在登录…" : "登录" }}
        </button>
        <div v-if="api.pending.value.length" class="message warning">
          <p>登录结果待确认，请查询原操作结果。</p>
          <button type="button" @click="recover(false)">查询结果</button>
          <button
            v-if="api.canRetry(api.pending.value[0]!)"
            type="button"
            @click="recover(true)"
          >
            使用原操作重试
          </button>
        </div>
        <p class="auth-switch">
          还没有师生账号？ <NuxtLink to="/register">注册账号</NuxtLink>
        </p>
        <p class="hint">请使用系统生成的登录账号，邮箱或工号不能用于登录。</p>
      </form>
    </section>
  </div>
</template>
