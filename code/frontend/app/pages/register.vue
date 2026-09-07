<script setup lang="ts">
const api = useApi();
const error = ref(""),
  generated = ref(""),
  copied = ref(false);
async function submit(body: unknown) {
  error.value = "";
  try {
    const result = await api.mutate<{ account: string }>("/auth/register", {
      label: "注册账号",
      anonymous: true,
      body,
    });
    generated.value = result.account;
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function copy() {
  try {
    await navigator.clipboard.writeText(generated.value);
    copied.value = true;
  } catch {
    error.value = "复制未成功，请选中下方账号手动复制。";
  }
}
async function retryPending() {
  error.value = "";
  try {
    generated.value = (
      await api.retry<{ account: string }>(api.pending.value[0]!)
    ).account;
  } catch (e) {
    error.value = (e as Error).message;
  }
}
</script>
<template>
  <div class="register-page">
    <NuxtLink to="/login" class="brand"
      ><span class="brand-symbol">课</span>选课系统</NuxtLink
    >
    <section class="register-sheet">
      <div v-if="generated" class="registration-success">
        <span class="success-symbol">✓</span>
        <h1>账号已创建</h1>
        <p>请保存下方登录账号，用它登录选课系统。</p>
        <strong class="generated-account">{{ generated }}</strong>
        <div class="button-row">
          <button @click="copy">{{ copied ? "已复制" : "复制账号" }}</button
          ><NuxtLink class="button primary" to="/login">去登录</NuxtLink>
        </div>
      </div>
      <template v-else
        ><h1>注册师生账号</h1>
        <p class="subtle">选择身份并填写资料，系统将为你生成登录账号。</p>
        <div v-if="error" class="message error" role="alert">{{ error }}</div>
        <AccountForm
          :busy="api.busy.value || !!api.pending.value.length"
          :show-registration-number="false"
          submit-label="注册账号"
          @submit="submit"
        />
        <p class="auth-switch">
          已有账号？ <NuxtLink to="/login">返回登录</NuxtLink>
        </p>
        <div v-if="api.pending.value.length" class="message warning">
          <p>注册结果待确认，请查询原操作结果。</p>
          <button
            @click="
              async () => {
                try {
                  const data = await api.resolve<{ account: string }>(
                    api.pending.value[0]!,
                  );
                  generated = data.account;
                } catch (e) {
                  error = (e as Error).message;
                }
              }
            "
          >
            查询结果
          </button>
          <button
            v-if="api.canRetry(api.pending.value[0]!)"
            @click="retryPending"
          >
            使用原操作重试
          </button>
        </div></template
      >
    </section>
  </div>
</template>
