import { PlaceholderPage } from "@/components/placeholder-page";
import { ShellLayout } from "@/components/shell-layout";

export default function DatasetsPage() {
  return (
    <ShellLayout
      title="知识库管理"
      description="后面这个页面会负责 dataset、版本列表、当前知识库状态和最小管理操作。"
    >
      <PlaceholderPage
        title="知识库入口页"
        summary="当前先作为管理入口占位页，后续在 P0-2 到 P0-4 之间逐步填入最小知识库能力。"
        currentStage="Shell only"
        nextStep="在最小 RAG 闭环阶段加入 dataset 创建入口"
        checks={[
          "我能解释 dataset 为什么要独立存在吗？",
          "我知道这个页面以后要验证哪些接口吗？",
          "我知道知识库和文件、语料、向量层的边界吗？"
        ]}
      />
    </ShellLayout>
  );
}

