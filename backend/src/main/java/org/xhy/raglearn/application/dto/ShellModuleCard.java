package org.xhy.raglearn.application.dto;

/**
 * Lightweight description of one learning module shown in the shell dashboard.
 *
 * @param title module title
 * @param description short summary of what the page is for
 * @param path frontend route
 * @param phase learning stage
 */
public record ShellModuleCard(String title, String description, String path, String phase) {
}

