<script setup lang="ts">
import { dateTime, type Account, type Page } from "~/types/domain";
const api = useApi(),
  state = usePageState();
const rows = ref<Account[]>([]),
  page = ref(1),
  total = ref(0),
  q = ref(""),
  role = ref("");
const modal = ref<"create" | "edit" | "reset" | "delete" | null>(null),
  selected = ref<Account>(),
  formError = ref(""),
  created = ref("");
const password = ref(""),
  confirmPassword = ref("");
let detailGeneration = 0;
async function load() {
  await state.load(
    () =>
      api.request<Page<Account>>(
        `/admin/accounts?${new URLSearchParams({ q: q.value, role: role.value, page: String(page.value), size: "20" })}`,
      ),
    (result) => {
      rows.value = result.items;
      total.value = result.total;
    },
  );
}
async function open(kind: "edit" | "reset" | "delete", row: Account) {
  formError.value = "";
  const generation = ++detailGeneration;
  try {
    const account = await api.request<Account>(`/admin/accounts/${row.id}`);
    if (generation !== detailGeneration) return;
    selected.value = account;
    password.value = "";
    confirmPassword.value = "";
    modal.value = kind;
  } catch (e) {
    state.error.value = (e as Error).message;
  }
}
function close() {
  if (
    (modal.value === "edit" || modal.value === "create") &&
    !api.busy.value &&
    !window.confirm("放弃未保存的修改？")
  )
    return;
  modal.value = null;
  detailGeneration++;
  password.value = "";
  confirmPassword.value = "";
  formError.value = "";
}
async function save(data: {
  name: string;
  registrationNumber?: string;
  email: string;
}) {
  formError.value = "";
  try {
    if (modal.value === "create") {
      const result = await api.mutate<Account>("/admin/accounts", {
        label: "创建账号",
        body: data,
      });
      created.value = result.account;
      state.notice.value = `账号已创建：${result.account}`;
    } else {
      await api.mutate(`/admin/accounts/${selected.value!.id}`, {
        method: "PATCH",
        label: "保存账号资料",
        version: selected.value!.version,
        body: {
          name: data.name,
          registrationNumber: data.registrationNumber!,
          email: data.email,
        },
      });
      state.notice.value = "账号资料已保存";
    }
    modal.value = null;
    await load();
  } catch (e) {
    formError.value = (e as Error).message;
  }
}
async function confirm() {
  formError.value = "";
  if (
    modal.value === "reset" &&
    (password.value !== confirmPassword.value ||
      Array.from(password.value).length < 8 ||
      Array.from(password.value).length > 64 ||
      !password.value.trim())
  ) {
    formError.value = "请填写相同的两次密码，长度为 8～64 个字符";
    return;
  }
  try {
    if (modal.value === "delete") {
      const result = await api.mutate<{ cancelledCount: number }>(
        `/admin/accounts/${selected.value!.id}`,
        {
          method: "DELETE",
          version: selected.value!.version,
          label: "删除账号",
        },
      );
      state.notice.value = `账号已删除，取消 ${result.cancelledCount} 条未结束学期的选课`;
    } else {
      await api.mutate(`/admin/accounts/${selected.value!.id}/password-reset`, {
        version: selected.value!.version,
        label: "重置密码",
        body: {
          password: password.value,
          confirmPassword: confirmPassword.value,
        },
      });
      state.notice.value = "密码已重置，该账号原有会话已失效";
    }
    modal.value = null;
    password.value = "";
    confirmPassword.value = "";
    await load();
    if (rows.value.length === 0 && page.value > 1) {
      page.value--;
      await load();
    }
  } catch (e) {
    formError.value = (e as Error).message;
  }
}
const locked = computed(
  () => api.busy.value || !!api.pending.value.length || !state.trusted.value,
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
      <h1>账号管理</h1>
      <p>维护教师与学生资料，管理账号访问权限。</p>
    </div>
    <button
      class="primary"
      :disabled="locked"
      @click="
        modal = 'create';
        formError = '';
      "
    >
      ＋ 新增账号
    </button>
  </div>
  <div v-if="state.notice.value" class="message success" role="status">
    {{ state.notice.value }}
  </div>
  <div v-if="state.error.value" class="message error" role="alert">
    {{ state.error.value }} <button @click="load">重试</button>
  </div>
  <section class="data-sheet">
    <form
      class="toolbar"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label class="sr-only" for="account-search">姓名、账号或编号</label
      ><input
        id="account-search"
        v-model="q"
        class="search"
        placeholder="搜索姓名、登录账号或编号"
        maxlength="100"
      /><label class="sr-only" for="account-role">角色</label
      ><select id="account-role" v-model="role">
        <option value="">全部身份</option>
        <option value="TEACHER">教师</option>
        <option value="STUDENT">学生</option></select
      ><button type="submit">查询</button>
    </form>
    <div v-if="state.loading.value" class="empty" role="status">
      正在加载账号…
    </div>
    <table v-else-if="rows.length">
      <thead>
        <tr>
          <th>姓名 / 登录账号</th>
          <th>身份</th>
          <th>工号 / 学号</th>
          <th>邮箱</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>
            <strong>{{ row.name }}</strong
            ><small class="table-subline">{{ row.account }}</small>
          </td>
          <td>
            <span class="badge neutral">{{
              row.role === "TEACHER" ? "教师" : "学生"
            }}</span>
          </td>
          <td>{{ row.registrationNumber }}</td>
          <td>{{ row.email }}</td>
          <td>
            <div class="row-actions">
              <button @click="open('edit', row)">编辑资料</button
              ><button @click="open('reset', row)">重置密码</button
              ><button class="danger-text" @click="open('delete', row)">
                删除
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty">
      <h3>暂无匹配账号</h3>
      <p>试试其他查询条件，或新增教师、学生账号。</p>
    </div>
    <Pagination
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
  <Modal
    v-if="modal"
    :title="
      modal === 'create'
        ? '新增师生账号'
        : modal === 'edit'
          ? '编辑账号资料'
          : modal === 'reset'
            ? '重置密码'
            : '删除账号'
    "
    @close="close"
    ><div v-if="formError" class="message error" role="alert">
      {{ formError }}
    </div>
    <AccountForm
      :key="modal === 'edit' ? selected?.id : 'create'"
      v-if="modal === 'create' || modal === 'edit'"
      :account="modal === 'edit' ? selected : undefined"
      :busy="locked"
      @submit="save"
    /><template v-else
      ><div class="confirmation-object">
        <strong>{{ selected?.name }}</strong
        ><span>{{ selected?.account }}</span>
      </div>
      <form
        v-if="modal === 'reset'"
        class="stack-form"
        @submit.prevent="confirm"
      >
        <p>重置后，该账号所有已登录会话将失效。</p>
        <label
          >新密码<input
            v-model="password"
            type="password"
            autocomplete="new-password"
            required /></label
        ><label
          >确认新密码<input
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
            required /></label
        ><button class="primary" :disabled="locked">确认重置密码</button>
      </form>
      <template v-else
        ><p v-if="selected?.role === 'STUDENT'">
          将取消该学生所有未结束学期的有效选课。历史结果保留，账号不能再次登录，编号和邮箱不再复用。
        </p>
        <p v-else>
          若仍有未结束学期课程，将拒绝删除。历史课程及教师归属将保留。
        </p>
        <div class="button-row">
          <button @click="modal = null">取消</button
          ><button class="danger" :disabled="locked" @click="confirm">
            确认删除
          </button>
        </div></template
      ></template
    ></Modal
  >
</template>
