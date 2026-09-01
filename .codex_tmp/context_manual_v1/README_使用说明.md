# 智能体上下文系统实施方案 v1.0

本文件夹是“实施手册 + 可复制示例”的交付包，不会自动修改当前项目，也不会自动连接虚拟机。

## 文件说明

- `智能体上下文系统实施方案_v1.0.docx`：完整开发实施手册。
- `示例文件/mongodb/compose.yaml`：MongoDB 单节点开发环境 Compose 示例。
- `示例文件/mongodb/.env.example`：MongoDB 部署变量模板，不含真实密码。
- `示例文件/mongodb/init/01-create-agent-context.js`：首次启动时创建应用账号、集合和索引。
- `示例文件/agent_service/.env.context.example`：Python Agent 上下文配置模板。
- `示例文件/python/*.py`：数据模型、仓储和 LangGraph 节点参考实现。

## 建议使用顺序

1. 先完整阅读 Word 手册，确认 Redis DB 4 的逻辑隔离边界和 MongoDB 部署安全要求。
2. 将 `示例文件/mongodb` 整个目录复制到虚拟机的独立部署目录。
3. 将 `.env.example` 复制为 `.env`，替换其中所有占位密码。
4. 使用 `docker compose config` 检查配置，再执行 `docker compose up -d`。
5. 按手册中的 `mongosh` 命令检查账号、集合和索引。
6. 实际开发时参考 Python 示例，将代码按当前 `agent_service` 的目录和依赖注入方式拆分接入。

## 重要提醒

- Redis database 4 只提供逻辑分库，仍与 database 3 共享内存、CPU、持久化文件和淘汰策略。
- 初始化脚本只在 MongoDB 数据卷为空时执行。已有数据卷情况下修改 `.env` 不会自动重建用户或修改密码。
- 示例中的 `<请替换...>` 必须替换，不要把真实密码提交到 Git。
- 当前交付只提供实施方案和参考代码，不包含对 `agent_service`、Java 后端或虚拟机的实际修改。
