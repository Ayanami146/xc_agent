# 信创智能客服用户端

Vue 3 + TypeScript + Vite + Element Plus + Pinia 用户端。生产默认使用真实 API，页面组件通过 `src/services` 统一对接 `/api/v1`。

如果只是使用 Windows 本机练习后端，请优先阅读仓库根目录的 `前端简单上线流程.md`。该流程只需要 Node.js、Windows Nginx 和你的本地后端；Redis 虚拟机由后端自行连接。

## 配置

复制 `.env.example` 为 `.env.local`，按环境修改：

```dotenv
VITE_API_MODE=remote
VITE_API_BASE_URL=/api/v1
VITE_API_PROXY_TARGET=http://127.0.0.1:8080
```

- `remote`：真实动态接口，生产必须使用该模式。
- `mock`：只用于无后端的前端演示，可运行 `npm run dev -- --mode mock`。
- `VITE_API_PROXY_TARGET` 只供本地 Vite 代理；生产构建仍请求同源 `/api/v1`，由 Nginx 反代。

## 用户自行执行

```bash
npm ci
npm run dev
npm run build
```

构建产物位于 `dist/`。完整部署和验收步骤见仓库 `实施文档/动态网页部署与测试操作手册.md`。

## 已对接能力

- 密码/短信登录、refresh cookie 恢复、单飞刷新、注销
- 会话分页、消息分页、重命名、软删除
- POST SSE 问答、事件顺序校验、取消生成、状态查询、回答反馈
- FAQ 与手册搜索、FAQ 标准答案抽屉、继续向智能体提问、手册下载授权
- 工单附件真实上传、创建、列表、详情、补充、关闭、重开、附件下载授权

接口以 `接口文档/openapi.yaml` 为准。Access Token 仅保存在内存，不写入 LocalStorage 或 SessionStorage。
