# AgentX-RAG-Learn

这是一个和 `AgentX-Newest` 并列的学习版项目。

目标不是一开始就完整复刻 AgentX，而是按分支逐步重做 RAG 主链：

- 知识库
- 文件
- 语料
- 向量化
- 检索
- 问答
- 检索增强
- 版本化

## 当前目录

```text
AgentX-RAG-Learn
├─ backend   # Spring Boot 学习版后端
├─ frontend  # Next.js 最小管理台
├─ infra     # PostgreSQL(pgvector) + RabbitMQ 本地依赖
├─ docs
├─ task-cards
└─ review-notes
```

## 当前约定

- `AgentX-Newest` 是参考项目，只读对照
- `AgentX-RAG-Learn` 是主开发项目，只在这里改代码
- 每个功能点一个分支
- 每个分支都要先验证、再 review、再提交

## learn/00-shell 的目标

当前分支只做最小工程壳子：

- Spring Boot 后端基础工程
- Next.js 前端基础管理台
- Docker 本地依赖环境
- 统一返回结构、统一异常处理
- 5 个 RAG 学习入口页面

## 本地启动

### 1. 启动基础依赖

```powershell
cd infra
docker compose up -d
```

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

### 3. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

- `http://localhost:3000`

后端默认地址：

- `http://localhost:8181/api`
