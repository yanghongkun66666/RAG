"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { shellModules } from "@/lib/modules";

type ShellLayoutProps = {
  title: string;
  description: string;
  children: React.ReactNode;
};

export function ShellLayout({ title, description, children }: ShellLayoutProps) {
  const pathname = usePathname();

  return (
    <div className="shell-page">
      <div className="shell-frame">
        <aside className="shell-sidebar">
          <div className="brand-kicker">Learn / 00 Shell</div>
          <h1 className="brand-title">AgentX RAG Learn</h1>
          <p className="brand-copy">
            A restrained editorial shell for learning the full RAG chain one branch at a time.
          </p>

          <nav className="nav-list">
            <Link href="/" className={`nav-link ${pathname === "/" ? "active" : ""}`}>
              <div className="nav-title">总览面板</div>
              <div className="nav-caption">看整个学习项目现在到哪一步</div>
            </Link>

            {shellModules.map((module) => (
              <Link
                key={module.href}
                href={module.href}
                className={`nav-link ${pathname === module.href ? "active" : ""}`}
              >
                <div className="nav-title">{module.title}</div>
                <div className="nav-caption">{module.description}</div>
              </Link>
            ))}
          </nav>
        </aside>

        <main className="shell-main">
          <section className="shell-panel">
            <div className="brand-kicker">Current Focus</div>
            <h2 className="section-title">{title}</h2>
            <p className="section-copy">{description}</p>
          </section>
          {children}
        </main>
      </div>
    </div>
  );
}

