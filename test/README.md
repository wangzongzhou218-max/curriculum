# 重复验证

所有测试源码位于本目录。结果保存在 `results`，Java 原始报告在 `../code/backend/target/surefire-reports` 和 `failsafe-reports`。

```powershell
./test/Run-Tests.cmd -Browser
./test/Run-Tests.cmd -Integration -Browser
```

首次拷贝项目到另一台电脑，需先执行根目录开发环境脚本并安装 `code/frontend` 和 `test` 的 pnpm 依赖。Chrome 测试默认使用 `.tools/chrome-win64/chrome.exe`，也可通过 `CHROME_PATH` 指向本机 Chrome。Playwright 自动启动并关闭前端开发服务，已有同端口服务会复用。

## 证据边界

- `backend/RulesTest`：时间区间相邻不冲突、非法授课安排、上海时区与学期截止边界、密码字符和规范化规则。
- `backend/CryptoTest`：签名篡改与用途隔离。
- `backend/OwnershipTest`：大于 127 的用户 ID 归属判定，以及变更前后的审计快照。
- `frontend/timetable.test.ts`：重叠课程分列、完整数据不截断、午休空间。
- `browser/interface.spec.ts`：真实 Chrome 中的登录导航、课程列表、完整课表、详情/Esc、历史提示和断线重试。**接口由测试夹具提供，仅证明界面交互，不证明后端或持久化。**
- `backend/CurriculumIT`：独立 MySQL 容器中的注册、权限边界、发布、选课重放、管理员与注销。Docker 不可用即失败，不跳过，也不替换成 H2。

## 验收状态

2026-09-07 已在 Docker Desktop 的独立 MySQL 8.4.11 容器中通过 2 条集成场景，覆盖注册、角色隔离、课程发布、选课幂等重放、管理员登录和注销。原始结果位于 `../code/backend/target/failsafe-reports`。

AC-01～AC-38、INV-01～INV-16 的完整验收仍有未验证部分：尚无完整的并发、故障注入和性能证据。上述单元、界面和集成测试是部分证据，不能据此宣布每条验收均已完整通过。后续优先扩充换课事务、删除历史保留、并发和故障覆盖。
