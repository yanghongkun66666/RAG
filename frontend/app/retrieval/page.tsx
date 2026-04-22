import { PlaceholderPage } from "@/components/placeholder-page";
import { ShellLayout } from "@/components/shell-layout";

export default function RetrievalPage() {
  return (
    <ShellLayout
      title="检索实验台"
      description="后面这个页面会逐步验证向量检索、关键词检索、混合检索、RRF、rerank、HyDE 和查询扩展。"
    >
      <PlaceholderPage
        title="检索入口页"
        summary="当前先作为 retrieval 的总入口。后面每加一层检索增强，都要回到这里做前后对比。"
        currentStage="Shell only"
        nextStep="在最小 RAG 闭环阶段先接入向量检索结果展示"
        checks={[
          "我知道最小检索闭环最先要验证什么吗？",
          "我知道混合检索之前必须先把向量检索跑通吗？",
          "我知道后面哪些增强属于 P1，而不是现在就做吗？"
        ]}
      />
    </ShellLayout>
  );
}

