package util;

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

    // 🎯 直接传 FileRecord
    public static String analyseFile(FileRecord r) {

        String prompt = """
        你是一个给普通用户使用的垃圾文件清理助手。
        请根据下面的文件信息进行非常简洁的分析，不要输出多余内容。

        文件信息：
        - 文件名：%s
        - 文件路径：%s
        - 文件大小：%s 字节
        - 创建时间：%s
        - 最后修改时间：%s
        - 最后访问时间：%s

        请按以下格式输出（不要添加额外说明）：

        1. 文件可能来自：一句话说明来源或用途。
        2. 删除风险：0-100（值越大越危险）。
        3. 建议：一句话。
        """.formatted(
                extractName(r.fullpath),
                escape(r.fullpath),
                r.size,
                r.creation,
                r.lastWrite,
                r.lastAccess
        );

        try {
            String json = """
            {
              "model": "deepseek-chat",
              "messages": [
                {
                  "role": "user",
                  "content": "%s"
                }
              ],
              "stream": false
            }
            """.formatted(escape(prompt));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            return extractContent(resp.body());

        } catch (Exception e) {
            return "分析失败：" + e.getMessage();
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
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }

        return sb.toString();
    }

    private static String extractContent(String json) {
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return json;
        int a = json.indexOf("\"", idx + 10);
        int b = json.indexOf("\"", a + 1);
        return json.substring(a + 1, b).replace("\\n", "\n");
    }
}