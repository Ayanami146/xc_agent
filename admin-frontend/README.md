# 信创智能客服管理端

独立的 Vue 3 + TypeScript + Element Plus 管理端，生产访问路径为 `/admin/`，API 前缀为 `/api/v1/admin`。

## 本机复用用户端依赖

当前工作区的 `admin-frontend/node_modules` 可以直接链接到已经安装好的 `frontend/node_modules`，不会再次下载或复制依赖：

```powershell
cd C:\work_learn\XinChuang_pc\admin-frontend
New-Item -ItemType Junction -Path node_modules -Target C:\work_learn\XinChuang_pc\frontend\node_modules
```

如果目录链接已经存在，不需要重复执行。迁移到其他电脑且没有用户端依赖时，再执行 `npm install`。

## 开发与构建

```powershell
npm run dev
# http://127.0.0.1:5174/admin/

npm run build
# 构建产物：admin-frontend/dist
```

Vite 会把 `/api` 转发到 `http://127.0.0.1:8080`。生产环境使用仓库中的 `deploy/nginx/windows-nginx.conf`，访问：

```text
用户端：http://localhost:8088/
管理端：http://localhost:8088/admin/
```

## 开发账号

所有账号密码均为 `123456`，仅限本机练习：

| 账号 | 角色 | 状态 |
|---|---|---|
| `admin` | ADMIN | 正常 |
| `admin02` | ADMIN | 正常 |
| `support01` | SUPPORT | 正常 |
| `support02` | SUPPORT | 正常 |
| `disabled_admin` | ADMIN | 停用，用于测试 403 |
| `locked_support` | SUPPORT | 锁定，用于测试 403 |

ADMIN 可以管理工单和内容，并查看管理员、审计；SUPPORT 可以处理工单，只读查看内容和仪表盘。管理端不提供注册、账号编辑或密码重置。

## 手册文件

维修手册默认保存到后端工作目录的 `data/manuals`，可用 `MANUAL_STORAGE_DIRECTORY` 覆盖。支持 PDF、DOC、DOCX、TXT、MD，最大 20 MB。本地练习模式上传后直接标记为扫描通过。
