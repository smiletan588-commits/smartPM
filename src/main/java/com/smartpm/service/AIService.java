package com.smartpm.service;

import reactor.core.publisher.Flux;

public interface AIService {

    /**
     * 流式调用 AI 大模型，返回逐 token 的响应流。
     */
    Flux<String> streamChat(String prompt);

    /**
     * 非流式调用 AI 大模型，返回完整回复文本。
     * 用于需要一次性获取完整结果的场景（如 JSON 解析）。
     */
    String chat(String prompt);

    /** 非流式 JSON 输出，用于需要解析为业务草稿的场景。 */
    String chatJson(String prompt);
}
