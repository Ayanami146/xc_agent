/**
 * MongoDB 首次启动初始化脚本。
 *
 * 参考实现，尚未自动接入当前项目。
 * 该脚本仅在 /data/db 对应数据卷为空时执行一次。
 */

const databaseName = "xinchuang_agent_context";
const appUsername = process.env.MONGO_APP_USERNAME;
const appPassword = process.env.MONGO_APP_PASSWORD;

if (!appUsername || !appPassword) {
  throw new Error("必须通过环境变量提供 MONGO_APP_USERNAME 和 MONGO_APP_PASSWORD");
}

const contextDb = db.getSiblingDB(databaseName);

// 只向 Agent 授予目标数据库的 readWrite，禁止 Agent 使用 root 账号。
if (contextDb.getUser(appUsername) === null) {
  contextDb.createUser({
    user: appUsername,
    pwd: appPassword,
    roles: [{ role: "readWrite", db: databaseName }],
  });
}

// 显式创建集合，便于初始化阶段立刻建立索引和完成验收。
if (!contextDb.getCollectionNames().includes("context_sessions")) {
  contextDb.createCollection("context_sessions");
}
if (!contextDb.getCollectionNames().includes("context_turns")) {
  contextDb.createCollection("context_turns");
}

// 每个用户会话只允许存在一个当前上下文快照。
contextDb.context_sessions.createIndex(
  { userId: 1, sessionId: 1 },
  { unique: true, name: "uk_context_session_user_session" },
);

// requestId 对应一轮 Java 问答请求，用于保证上下文轮次幂等写入。
contextDb.context_turns.createIndex(
  { requestId: 1 },
  { unique: true, name: "uk_context_turn_request" },
);

// 按会话和时间读取轮次或执行故障重建时使用该索引。
contextDb.context_turns.createIndex(
  { userId: 1, sessionId: 1, createdAt: 1 },
  { name: "idx_context_turn_session_created" },
);

print(`MongoDB Agent 上下文数据库初始化完成：${databaseName}`);
