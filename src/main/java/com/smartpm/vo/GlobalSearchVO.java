package com.smartpm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GlobalSearchVO {
    private List<Item> projects = new ArrayList<>();
    private List<Item> tasks = new ArrayList<>();
    private List<Item> wikis = new ArrayList<>();

    @Data
    @AllArgsConstructor
    public static class Item {
        private String type;
        private Long id;
        private Long projectId;
        private String title;
        private String description;
        private String route;
    }
}
