/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分页结果DTO
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> content;

    @Schema(description = "当前页码（从0开始）", example = "0")
    private int page;

    @Schema(description = "每页大小", example = "15")
    private int size;

    @Schema(description = "总记录数", example = "100")
    private long totalElements;

    @Schema(description = "总页数", example = "7")
    private int totalPages;

    @Schema(description = "是否为最后一页", example = "false")
    private boolean last;

    @Schema(description = "是否为第一页", example = "true")
    private boolean first;

    /**
     * 默认构造器
     */
    public PageResult() {
    }

    /**
     * Builder方法
     */
    public static <T> PageResultBuilder<T> builder() {
        return new PageResultBuilder<>();
    }

    /**
     * Builder类
     */
    public static class PageResultBuilder<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;
        private boolean first;

        public PageResultBuilder<T> content(List<T> content) {
            this.content = content;
            return this;
        }

        public PageResultBuilder<T> page(int page) {
            this.page = page;
            return this;
        }

        public PageResultBuilder<T> size(int size) {
            this.size = size;
            return this;
        }

        public PageResultBuilder<T> totalElements(long totalElements) {
            this.totalElements = totalElements;
            return this;
        }

        public PageResultBuilder<T> totalPages(int totalPages) {
            this.totalPages = totalPages;
            return this;
        }

        public PageResultBuilder<T> last(boolean last) {
            this.last = last;
            return this;
        }

        public PageResultBuilder<T> first(boolean first) {
            this.first = first;
            return this;
        }

        public PageResult<T> build() {
            PageResult<T> result = new PageResult<>();
            result.content = content;
            result.page = page;
            result.size = size;
            result.totalElements = totalElements;
            result.totalPages = totalPages;
            result.last = last;
            result.first = first;
            return result;
        }
    }

    // Getters and Setters
    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }
}
