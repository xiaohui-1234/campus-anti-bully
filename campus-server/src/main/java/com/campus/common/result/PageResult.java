package com.campus.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> of(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
