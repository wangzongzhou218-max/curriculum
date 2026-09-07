# 本地开发环境

安装日期：2026-09-06。当前项目的五份原始文档已阅读；业务规格保持 v1.0.6。

## 使用方式

在项目目录的 PowerShell 中执行：

```powershell
. .\scripts\Enter-DevEnvironment.ps1
.\scripts\Verify-DevEnvironment.ps1
```

JDK、Maven、Node、pnpm 和 k6 安装在 `.tools`。用户级 JAVA_HOME、MAVEN_HOME 和 PATH 已配置；已打开的终端可用上述激活脚本立即生效。旧环境变量备份在 `.tools/user-environment-before.json`。项目移动后需重新配置路径。

## 重启后完成数据库准备

WSL 2.7.13 和 Docker Desktop 4.89.0 已安装；Windows 虚拟机平台已启用，返回 `RestartNeeded=true`。请保存工作并重启 Windows，再启动 Docker Desktop，等待 Linux 引擎就绪。安装过程没有自动重启电脑。

```powershell
. .\scripts\Enter-DevEnvironment.ps1
.\scripts\Verify-DatabaseEnvironment.ps1
```

该脚本检查 Docker Client/Server 和 Linux 模式，启动开发 MySQL，再由 Testcontainers 创建独立临时 MySQL，验证版本、字符集、排序规则、隔离级别、唯一键、CHECK、外键与事务回滚。无可用 Docker 时会失败，不会跳过集成测试冒充通过。

开发数据库固定为 MySQL 8.4.11（镜像摘要见 `compose.yaml`），只监听 `127.0.0.1:3307`，数据库为 `curriculum`，数据保存在 Compose 专用卷 `curriculum-system-dev_mysql-data`。没有连接或迁移其他数据库。

`.env.local` 已生成随机凭据及四个独立密钥，并限制文件 ACL，不提交版本控制。`curriculum_migrator` 负责迁移；`curriculum_app` 仅有 SELECT/INSERT/UPDATE/DELETE 权限。重复运行初始化脚本不会改密。

常用命令：

```powershell
.\scripts\Start-DevDatabase.ps1
docker compose --env-file .env.local ps
docker compose --env-file .env.local stop
docker compose --env-file .env.local start
```

`stop` 保留数据卷。不要为了日常停止数据库使用 `down -v`。

## 隔离构建检查

`tools/environment-check` 仅用于验证依赖和工具链，不是业务前后端工程，不代表 P-04 或业务验收完成。

```powershell
mvn.cmd -B -f tools/environment-check/backend/pom.xml package
java -jar tools/environment-check/backend/target/environment-check-0.0.1-SNAPSHOT.jar
pnpm.cmd --dir tools/environment-check/frontend install --frozen-lockfile
pnpm.cmd --dir tools/environment-check/frontend build
pnpm.cmd --dir tools/environment-check/frontend typecheck
pnpm.cmd --dir tools/environment-check/quality install --frozen-lockfile
pnpm.cmd --dir tools/environment-check/quality exec playwright --version
pnpm.cmd --dir tools/environment-check/quality exec vitest --version
pnpm.cmd --dir tools/environment-check/quality exec node check-browser.mjs
k6 version
```

Playwright 已配置为项目测试依赖，并已通过独立无头 Edge 启动及操作检查；浏览器业务验收仍应按计划使用 Chrome/Edge，这次工具检查不能代替实际业务验证。

详细结果见 [P-02 环境记录](verification/P-02.md)。
