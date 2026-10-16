package lt.baltic.gateway.platform.web;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
    }
}