# learn/00-shell Review

## Aim

为学习版 RAG 项目搭好后端、前端、基础环境和最小管理台入口，让项目具备后续逐步开发的承载能力。

## Validation

- 后端打包通过：`backend\\mvnw.cmd -DskipTests package`
- 前端依赖安装通过：`npm install --no-audit --fund=false --prefer-offline --fetch-retries=8 --fetch-retry-factor=2 --fetch-retry-mintimeout=10000 --fetch-retry-maxtimeout=120000 --fetch-timeout=600000`
- 前端构建通过：`npm run build`

## Review Checklist

### 1. 这个模块入口在哪

- 后端入口是 `ShellController`
- 前端入口是首页 `app/page.tsx`
- 菜单入口固定为 5 个页面：知识库、文件处理、语料、检索、问答

### 2. 主流程是什么

- 前端打开首页
- 首页展示学习模块和当前阶段说明
- 后端提供一个最小总览接口，返回阶段和模块卡片
- 这一分支先只完成“壳子”和“页面入口”，不进入真实 RAG 业务

### 3. 数据从哪来，到哪去

- 当前演示数据主要来自后端应用服务返回的固定模块卡片
- 基础环境配置通过 `.env.example` 和 `application.yml` 进入后端与前端
- 数据库迁移先建最小壳子表，为后续模块预留起点

### 4. 失败会发生什么

- 后端异常会走统一异常返回，不会直接把堆栈抛给调用方
- 前端依赖安装在弱网络环境下可能出现 `ECONNRESET`
- 当前已验证：清理 `node_modules` 后，配合 `prefer-offline` 和更高重试参数可以恢复安装

### 5. 我一周后还能不能看懂

- 当前目录结构已经按“后端 / 前端 / 基础设施 / 文档 / 任务卡 / review”拆开
- 类、方法和页面都带了面向学习的最小注释
- 这一分支的范围比较小，后续可以直接从 `learn/01-basic-rag-text` 往下接

## Residual Risk

- 当前只验证了构建和打包，没有启动联调，也没有补自动化测试
- 前端最初使用的 `next@15.1.0` 存在安全提示，现已升级到 `15.5.14`

## Decision

状态：`Continue`

这支分支已经达到可提交状态，可以进入自查、commit 和 PR 准备阶段。
