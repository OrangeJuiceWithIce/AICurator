package util;

import model.AiAnalysis;
import model.FileRecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeepSeekClient {

    private static final String API_KEY = "sk-98bf073f9d354fb599b22aba169dde09";
    private static final String URL = "https://api.deepseek.com/chat/completions";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 结构化分析：优化后的 Prompt
     * 角色：系统文件安全专家
     * 能力：根据路径推断软件归属，给出更精准的建议
     */
    public static AiAnalysis analyseFileStructured(FileRecord r) {

        // ==========================================
        // 优化点 1：更专业的角色设定和更详细的规则
        // ==========================================
        String prompt = """
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
                  "risk": 0-100的整数,
                  "advice": "操作建议"
                }

                【待分析文件数据】
                - 完整路径：%s
                - 文件名：%s
                - 大小：%s 字节
                - 创建时间：%s
                - 最后修改：%s
                """.formatted(
                // 这里我们把全路径放在最前面，因为路径包含了最重要的上下文信息（如 .git, Program Files 等）
                escape(r.fullpath),
                extractName(r.fullpath),
                r.size,
                r.creation,
                r.lastWrite);

        String raw = "";
        try {
            // ==========================================
            // 优化点 2：加上 temperature 参数 (0.1)
            // 让 AI 的回答更稳定、更严谨，减少胡乱发挥
            // ==========================================
            String jsonReq = """
                    {
                      "model": "deepseek-chat",
                      "messages": [
                        { "role": "user", "content": "%s" }
                      ],
                      "temperature": 0.1,
                      "stream": false
                    }
                    """.formatted(escape(prompt)); // 注意这里要把 prompt 转义放进去

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 调试用：打印 AI 返回的原始文本，方便你看控制台查错
            // System.out.println("AI Response: " + resp.body());

            raw = extractContent(resp.body());
            AiAnalysis parsed = AiAnalysis.fromJsonLenient(raw);
            parsed.raw = raw;
            return parsed;

        } catch (Exception e) {
            e.printStackTrace(); // 打印报错堆栈
            AiAnalysis a = new AiAnalysis();
            a.origin = "连接超时或分析出错";
            a.risk = -1;
            a.advice = "请检查网络配置";
            a.raw = raw.isEmpty() ? ("error=" + e.getMessage()) : raw;
            return a;
        }
    }

    private static String extractName(String path) {
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String escape(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 0x1F)
                        sb.append(String.format("\\u%04x", (int) c));
                    else
                        sb.append(c);
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
        if (idx < 0)
            return json;

        // 找到 content 后第一个引号
        int a = json.indexOf("\"", idx + 9);
        if (a < 0)
            return json;

        // 从 a+1 开始找到匹配的结束引号（考虑转义）
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = a + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                // 只处理常见转义
                if (c == 'n')
                    out.append('\n');
                else if (c == 'r')
                    out.append('\r');
                else if (c == 't')
                    out.append('\t');
                else
                    out.append(c);
                esc = false;
            } else {
                if (c == '\\')
                    esc = true;
                else if (c == '"')
                    break;
                else
                    out.append(c);
            }
        }
        return out.toString().trim();
    }
}
