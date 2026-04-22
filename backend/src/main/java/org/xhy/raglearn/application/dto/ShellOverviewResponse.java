package org.xhy.raglearn.application.dto;

import java.util.List;

/**
 * Overview payload used by the shell dashboard.
 *
 * @param appName display name of the learning project
 * @param branch current learning branch
 * @param backendPath backend base path
 * @param modules dashboard modules
 */
public record ShellOverviewResponse(
        String appName,
        String branch,
        String backendPath,
        List<ShellModuleCard> modules
) {
}

