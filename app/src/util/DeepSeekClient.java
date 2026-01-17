package util;

import model.AiAnalysis;
import model.FileRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeepSeekClient {

    private static final String API_KEY = "sk-763d9baa957e4f80856172cbfc53bc50";
    private static final String URL = "https://api.deepseek.com/chat/completions";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 结构化分析：要求模型输出 JSON
     * 返回 AiAnalysis：包含 origin/risk/advice/rawJson
     */
    public static AiAnalysis analyseFileStructured(FileRecord r) {

        String prompt = """
        你是一个给普通用户使用的垃圾文件清理助手。
        你必须仅输出一个 JSON 对象，不能输出任何多余文本（不要 Markdown、不要代码块）。
        JSON 格式固定为：
        {
          "origin": "一句话说明来源或用途",
          "risk": 0-100 的整数（越大越危险）,
          "advice": "一句话建议（例如：可删/建议保留/建议备份后删）"
        }

        文件信息：
        - 文件名：%s
        - 文件路径：%s
        - 文件大小：%s 字节
        - 创建时间：%s
        - 最后修改时间：%s
        - 最后访问时间：%s
        """.formatted(
                extractName(r.fullpath),
                escape(r.fullpath),
                r.size,
                r.creation,
                r.lastWrite,
                r.lastAccess
        );

        String raw = "";
        try {
            String jsonReq = """
            {
              "model": "deepseek-chat",
              "messages": [
                { "role": "user", "content": "%s" }
              ],
              "stream": false
            }
            """.formatted(escape(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            raw = extractContent(resp.body());        // 模型 content（仍是字符串）
            AiAnalysis parsed = AiAnalysis.fromJsonLenient(raw);
            parsed.raw = raw;
            return parsed;

        } catch (Exception e) {
            // 失败也返回对象，便于 UI/DB 统一处理
            AiAnalysis a = new AiAnalysis();
            a.origin = "分析失败";
            a.risk = 100;
            a.advice = "请检查网络或 API Key";
            a.raw = raw.isEmpty() ? ("error=" + e.getMessage()) : raw;
            return a;
        }
    }

    private static String extractName(String path) {
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c <= 0x1F) sb.append(String.format("\\u%04x", (int)c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 从 deepseek 的 response JSON 里提取 choices[0].message.content
     * 你原来的实现用 indexOf("content") 很脆弱，这里做一个稍稳的截取（仍然不引入第三方库）。
     */
    private static String extractContent(String json) {
        // 定位 "content"
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return json;

        // 找到 content 后第一个引号
        int a = json.indexOf("\"", idx + 9);
        if (a < 0) return json;

        // 从 a+1 开始找到匹配的结束引号（考虑转义）
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = a + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                // 只处理常见转义
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
