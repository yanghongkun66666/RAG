import { PlaceholderPage } from "@/components/placeholder-page";
import { ShellLayout } from "@/components/shell-layout";

export default function CorpusPage() {
  return (
    <ShellLayout
      title="语料实验台"
      description="后面这个页面会专门观察 document_unit 的生成、分页、搜索、编辑和删除。"
    >
      <PlaceholderPage
        title="语料入口页"
        summary="当前先作为语料层入口。后续会用它来证明 document_unit 这一层为什么有价值。"
        currentStage="Shell only"
        nextStep="在 P0-6 接入语料列表、关键词搜索和人工修正"
        checks={[
          "我能解释 document_unit 和 vector_store 的职责差异吗？",
          "我知道编辑语料后为什么要重新向量化吗？",
          "我知道这个页面以后怎么验证检索结果有没有变化吗？"
        ]}
      />
    </ShellLayout>
  );
}

