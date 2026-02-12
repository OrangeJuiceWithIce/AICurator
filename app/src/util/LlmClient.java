package util;

import config.LlmConfig;
import config.LlmConfigStore;
import model.AiAnalysis;
import model.FileRecord;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class LlmClient {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private LlmClient() {}

    public static AiAnalysis analyseFileStructured(FileRecord r) {
        LlmConfig cfg = LlmConfigStore.load();
        if (cfg == null || !cfg.isComplete()) {
            AiAnalysis a = new AiAnalysis();
            a.origin = "未配置大模型";
            a.risk = -1;
            a.advice = "请在 config/llm.properties 填写 baseUrl/apiKey/model";
            a.raw = "llm_not_configured";
            return a;
        }

        String prompt = buildPrompt(r);

        String raw = "";
        try {
            String url = normalizeBaseUrl(cfg.baseUrl) + "/chat/completions";

            String jsonReq = """
                    {
                      "model": "%s",
                      "messages": [
                        { "role": "user", "content": "%s" }
                      ],
                      "temperature": %s,
                      "stream": false
                    }
                    """.formatted(
                    escape(cfg.model),
                    escape(prompt),
                    cfg.temperature
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(5, cfg.timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            raw = extractContent(resp.body());

            AiAnalysis parsed = AiAnalysis.fromJsonLenient(raw);
            parsed.raw = raw;
            return parsed;

        } catch (Exception e) {
            AiAnalysis a = new AiAnalysis();
            a.origin = "连接超时或分析出错";
            a.risk = -1;
            a.advice = "请检查网络/配置";
            a.raw = raw.isEmpty() ? ("error=" + e.getMessage()) : raw;
            return a;
        }
    }

    private static String buildPrompt(FileRecord r) {
        return """
                你是一名资深的计算机文件系统安全专家。请根据提供的文件元数据进行深度分析，语言为简体中文。

                【任务要求】
                1. 分析该文件的具体用途、所属软件或系统组件。
                2. 评估该文件的删除风险（数字越大删除风险越大）。
                3. 给出简短的操作建议（保留、备份、删除、隔离）。

                【严格输出格式】
                你必须且只能输出一个标准的 JSON 对象。
                严禁输出 markdown 代码块（如 ```json ... ```）。
                严禁输出 JSON 之外的任何解释性文字。
                JSON 格式如下：
                {
                  "origin": "文件归属/用途说明",
                  "risk": 0-100的整数,0表示删除无风险,100表示系统级别文件灾难性后果,
                  "advice": "操作建议"
                }

                【待分析文件数据】
                - 完整路径：%s
                - 文件名：%s
                - 大小：%s 字节
                - 创建时间：%s
                - 最后修改：%s
                """.formatted(
                escape(r.fullpath),
                escape(extractName(r.fullpath)),
                r.size,
                r.creation,
                r.lastWrite
        );
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        baseUrl = baseUrl.trim();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl;
    }

    private static String extractName(String path) {
        if (path == null) return "";
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    // JSON string escape（你原来的 escape 可复用）
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c <= 0x1F) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 提取 choices[0].message.content （仍不引库）
     * 兼容 OpenAI 风格：{"choices":[{"message":{"content":"..."}}]}
     */
    private static String extractContent(String json) {
        if (json == null) return "";
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return json;

        int a = json.indexOf("\"", idx + 9);
        if (a < 0) return json;

        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = a + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else if (c == '"') break;
                else out.append(c);
            }
        }
        return out.toString().trim();
    }
}