import Link from "next/link";
import { ShellLayout } from "@/components/shell-layout";
import { shellModules } from "@/lib/modules";

export default function HomePage() {
  return (
    <ShellLayout
      title="最小管理台"
      description="这一页不是产品首页，而是你的学习驾驶舱。它的目标是让你知道当前分支做了什么、下一步该补什么、每个模块将来会落到哪里。"
    >
      <section className="shell-panel hero-grid">
        <div>
          <div className="brand-kicker">Phase Zero</div>
          <h2 className="hero-title">Build the shell before the chain.</h2>
          <p className="hero-copy">
            先把工程壳子、依赖环境、页面入口和后端统一结构搭起来。等这层稳定以后，
            你再往里面一支一支填充 dataset、file_detail、document_unit、vector_store。
          </p>

          <div className="hero-badges">
            <div className="badge">
              <div className="badge-label">Main Project</div>
              <span className="badge-value">AgentX-RAG-Learn</span>
            </div>
            <div className="badge">
              <div className="badge-label">Reference Project</div>
              <span className="badge-value">AgentX-Newest</span>
            </div>
            <div className="badge">
              <div className="badge-label">Current Branch</div>
              <span className="badge-value">learn/00-shell</span>
            </div>
          </div>
        </div>

        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-title">Backend Stack</div>
            <div className="stat-value">Spring Boot</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">Frontend Stack</div>
            <div className="stat-value">Next.js</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">Database</div>
            <div className="stat-value">Postgres + pgvector</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">Queue</div>
            <div className="stat-value">RabbitMQ</div>
          </div>
        </div>
      </section>

      <section className="shell-panel">
        <div className="brand-kicker">Learning Entrances</div>
        <h3 className="section-title">五个固定入口</h3>
        <p className="section-copy">
          这五个页面会在后续分支里持续复用。你以后做一个功能，不是先问“写哪个接口”，而是先问“它落在哪个入口页里验证”。
        </p>

        <div className="module-grid">
          {shellModules.map((module) => (
            <Link key={module.href} href={module.href} className="module-card">
              <div className="module-phase">{module.phase}</div>
              <div className="module-title">{module.title}</div>
              <div className="module-copy">{module.description}</div>
              <div className="module-link">进入这个入口 →</div>
            </Link>
          ))}
        </div>
      </section>
    </ShellLayout>
  );
}

