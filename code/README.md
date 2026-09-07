# 选课系统

PC Chrome 应用，Nuxt/Vue 前端与 Spring Boot/MySQL 后端。界面围绕学期、课程列表和完整周课表组织。管理员管理师生账号，教师管理课程与学生名单，学生选课、退课和换课。

## 本地运行

首次启用 Windows 虚拟化组件后需重启 Windows。开发工具已安装到项目 `.tools`；后端入口会自动启动 Docker Desktop 并等待数据库。数据库启动脚本会读取根目录 `.env.local`，不会显示密码。

若 Docker 报告 `Virtual Machine Platform not enabled`，请在“以管理员身份运行”的 PowerShell 中执行以下命令，然后重启 Windows：

```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
bcdedit.exe /set hypervisorlaunchtype auto
Restart-Computer
```

Docker Desktop 必须由当前登录用户安装，以便注册表和文件权限属于同一用户。若安装损坏，请在普通（无需管理员）PowerShell 中运行 `./scripts/Install-Docker-ForCurrentUser.cmd`，安装完成后重启 Windows。

在项目根目录打开两个 PowerShell 终端，分别运行下面的 `.cmd` 入口。该入口只为当前进程绕过 PowerShell 脚本限制，不修改系统或用户的执行策略：

```powershell
./code/Start-App.cmd -Service backend
./code/Start-App.cmd -Service frontend
```

Chrome 打开 http://localhost:3000。后端监听 127.0.0.1:8081，前端同源代理 `/api`；请始终使用 localhost 访问前端，CSRF Origin 校验与此对应。可使用 `.tools/chrome-win64/chrome.exe` 启动已安装的 Google Chrome for Testing。

全新数据库仅创建管理员 `admin` / `admin`。学生和教师通过注册获得系统生成的登录账号；普通业务数据不会预置。停止服务使用所在终端 Ctrl+C，数据库保留数据。

## 结构

- `backend/src/main/java/com/curriculum`：identity、account、course、enrollment、query 与公共事务/安全设施。
- `backend/src/main/resources/db/migration`：Flyway 数据库迁移；运行时 Hibernate 仅验证结构。
- `frontend/app/components`：账号、课程、学生和课表交互组件。
- `frontend/app/composables`：登录上下文、API 操作回执、异步页面状态。
- `../test`：重复测试、结果与浏览器截图。

写操作在数据库写闸门内验证并提交，操作回执与领域审计同事务保存。前端以版本和选课代次确认变更，不把登录令牌存入浏览器存储。

## 验证与当前限制

运行 `./test/Run-Tests.cmd -Browser` 执行规则、类型与 Chrome 界面验证；加 `-Integration` 启动独立真实 MySQL 测试容器。测试说明和未验证项见 `../test/README.md`。

本地 MySQL 8.4.11 与前后端代理已验证可用。完整验收仍需继续补充规格中的并发、故障注入和性能场景，不能只根据构建或界面夹具测试宣布全部验收通过。
