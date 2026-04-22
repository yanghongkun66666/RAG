import { PlaceholderPage } from "@/components/placeholder-page";
import { ShellLayout } from "@/components/shell-layout";

export default function ChatPage() {
  return (
    <ShellLayout
      title="问答实验台"
      description="后面这个页面会承担同步问答、流式问答和引用片段展示。"
    >
      <PlaceholderPage
        title="问答入口页"
        summary="当前先作为 chat 入口占位。你后面每做一步 QA 能力，都应该回到这里看检索上下文和回答是否一致。"
        currentStage="Shell only"
        nextStep="在 P0-3 先接入同步问答和命中片段展示"
        checks={[
          "我知道 RAG 回答为什么不是直接问模型吗？",
          "我知道回答内容应该如何依赖检索上下文吗？",
          "我知道同步问答和流式问答会在哪个阶段分别接入吗？"
        ]}
      />
    </ShellLayout>
  );
}
