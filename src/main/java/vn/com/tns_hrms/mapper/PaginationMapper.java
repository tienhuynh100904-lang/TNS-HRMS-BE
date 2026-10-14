package vn.com.tns_hrms.mapper;

import java.util.List;

import org.springframework.data.domain.Page;

import vn.com.tns_hrms.dto.response.ResultPagination;

public class PaginationMapper {
    private PaginationMapper() {
    }

    public static <T, D> ResultPagination toPaginationDTO(Page<T> page, List<D> dtoList) {
        ResultPagination result = new ResultPagination();
        ResultPagination.Meta meta = new ResultPagination.Meta();

        meta.setPage(page.getNumber() + 1);
        meta.setPageSize(page.getSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());

        result.setMeta(meta);
        result.setResult(dtoList);
        return result;
    }
}
