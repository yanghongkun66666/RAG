import { PlaceholderPage } from "@/components/placeholder-page";
import { ShellLayout } from "@/components/shell-layout";

export default function FilesPage() {
  return (
    <ShellLayout
      title="文件处理实验台"
      description="后面这个页面会承担上传、状态观察、异步处理链验证和不同文件类型实验。"
    >
      <PlaceholderPage
        title="文件处理入口页"
        summary="当前先把文件处理实验台的入口固定下来，后续从 txt 开始逐步扩展到 md、word、pdf。"
        currentStage="Shell only"
        nextStep="在文件入库阶段加入 txt 上传和处理结果展示"
        checks={[
          "我能解释文件为什么不能直接进向量库吗？",
          "我知道 file_detail 后面负责记录什么吗？",
          "我知道同步处理和异步处理会在哪一步分叉吗？"
        ]}
      />
    </ShellLayout>
  );
}

