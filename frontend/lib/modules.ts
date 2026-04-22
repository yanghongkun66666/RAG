export type ModuleLink = {
  title: string;
  description: string;
  href: string;
  phase: string;
};

export const shellModules: ModuleLink[] = [
  {
    title: "知识库管理",
    description: "后面从这里进入 dataset、版本和安装态能力。",
    href: "/datasets",
    phase: "P0"
  },
  {
    title: "文件处理实验台",
    description: "后面用来观察上传、解析、异步处理和状态变化。",
    href: "/files",
    phase: "P0"
  },
  {
    title: "语料实验台",
    description: "后面用来查看、编辑和搜索 document_unit。",
    href: "/corpus",
    phase: "P0"
  },
  {
    title: "检索实验台",
    description: "后面用来验证向量检索、混合检索、RRF 与 rerank。",
    href: "/retrieval",
    phase: "P1"
  },
  {
    title: "问答实验台",
    description: "后面用来观察同步问答与流式问答的差异。",
    href: "/chat",
    phase: "P1"
  }
];

