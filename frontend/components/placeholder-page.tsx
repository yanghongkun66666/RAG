type PlaceholderPageProps = {
  title: string;
  summary: string;
  currentStage: string;
  nextStep: string;
  checks: string[];
};

export function PlaceholderPage({
  title,
  summary,
  currentStage,
  nextStep,
  checks
}: PlaceholderPageProps) {
  return (
    <section className="shell-panel placeholder-panel">
      <div className="brand-kicker">Module Placeholder</div>
      <h3 className="section-title">{title}</h3>
      <p className="section-copy">{summary}</p>

      <div className="placeholder-banner">
        <span className="placeholder-pill">当前阶段：{currentStage}</span>
        <span className="placeholder-pill">下一个动作：{nextStep}</span>
      </div>

      <div className="placeholder-note">
        这个页面现在先承担学习入口和验收入口。后面的真实功能会一支一支地填进来，不会一次塞满。
      </div>

      <div>
        <div className="brand-kicker">完成这一页之前要能回答</div>
        <div className="module-grid">
          {checks.map((item) => (
            <div key={item} className="module-card">
              <div className="module-copy">{item}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

