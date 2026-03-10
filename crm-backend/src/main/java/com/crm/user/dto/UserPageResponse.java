package com.crm.user.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.util.List;

@Getter
@Setter
@Builder
public class UserPageResponse {
    private List<UserResponse> content;
    private int  page;
    private int  size;
    private long totalElements;
    private int  totalPages;
}
