package org.xhy.raglearn.application;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhy.raglearn.application.dto.ShellModuleCard;
import org.xhy.raglearn.application.dto.ShellOverviewResponse;

/**
 * Application service for the learn/00-shell dashboard data.
 */
@Service
public class ShellAppService {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    public ShellOverviewResponse buildOverview() {
        return new ShellOverviewResponse(
                appName,
                "learn/00-shell",
                contextPath + "/v1",
                List.of(
                        new ShellModuleCard("知识库管理", "后面用来创建知识库和查看版本入口。", "/datasets", "P0"),
                        new ShellModuleCard("文件处理实验台", "后面用来上传文件和观察处理状态。", "/files", "P0"),
                        new ShellModuleCard("语料实验台", "后面用来查看、搜索、修改 document_unit。", "/corpus", "P0"),
                        new ShellModuleCard("检索实验台", "后面用来验证向量检索、混合检索和 rerank。", "/retrieval", "P1"),
                        new ShellModuleCard("问答实验台", "后面用来观察同步问答和流式问答。", "/chat", "P1")
                )
        );
    }
}

