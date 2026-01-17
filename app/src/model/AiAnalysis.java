package model;

public class AiAnalysis {
    public String origin = "";
    public int risk = 0;        // 0-100
    public String advice = "";
    public String raw = "";     // 原始 JSON 或错误信息

    /**
     * 宽容解析：即使模型输出不是完美 JSON，也尽量提取 origin/risk/advice
     * 不引入第三方 JSON 库，靠简单扫描/正则式解析关键字段。
     */
    public static AiAnalysis fromJsonLenient(String s) {
        AiAnalysis a = new AiAnalysis();
        if (s == null) return a;

        // 尝试按 JSON key 提取
        a.origin = extractJsonString(s, "origin");
        a.advice = extractJsonString(s, "advice");

        Integer r = extractJsonInt(s, "risk");
        a.risk = (r == null) ? 0 : clamp(r, 0, 100);

        // 如果提取失败，兜底：尝试从旧格式/自然语言里抓
        if (a.origin.isEmpty() && s.contains("文件可能来自")) {
            a.origin = roughLine(s, "文件可能来自");
        }
        if ((a.risk == 0) && s.contains("删除风险")) {
            Integer rr = roughIntAfter(s, "删除风险");
            if (rr != null) a.risk = clamp(rr, 0, 100);
        }
        if (a.advice.isEmpty() && s.contains("建议")) {
            a.advice = roughLine(s, "建议");
        }

        // 最后兜底，避免空白
        if (a.origin.isEmpty()) a.origin = "未知来源";
        if (a.advice.isEmpty()) a.advice = "请结合实际判断";
        return a;
    }

    private static int clamp(int x, int lo, int hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private static String extractJsonString(String s, String key) {
        // 形如 "key": "value"
        String pat = "\"" + key + "\"";
        int idx = s.indexOf(pat);
        if (idx < 0) return "";
        int colon = s.indexOf(':', idx + pat.length());
        if (colon < 0) return "";

        // 找到第一个双引号
        int q1 = s.indexOf('"', colon + 1);
        if (q1 < 0) return "";

        // 读到下一个未转义的双引号
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = q1 + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                out.append(c);
                esc = false;
            } else {
                if (c == '\\') esc = true;
                else if (c == '"') break;
                else out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static Integer extractJsonInt(String s, String key) {
        String pat = "\"" + key + "\"";
        int idx = s.indexOf(pat);
        if (idx < 0) return null;
        int colon = s.indexOf(':', idx + pat.length());
        if (colon < 0) return null;

        // 从 colon+1 开始跳过空格/引号，读取连续数字
        int i = colon + 1;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '"' )) i++;
        int j = i;
        boolean neg = false;
        if (j < s.length() && s.charAt(j) == '-') { neg = true; j++; }
        int start = j;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        if (j == start) return null;

        int val = Integer.parseInt(s.substring(i, j).replace("\"", ""));
        return neg ? -val : val;
    }

    private static String roughLine(String s, String key) {
        int idx = s.indexOf(key);
        if (idx < 0) return "";
        int colon = s.indexOf(':', idx);
        if (colon < 0) colon = idx + key.length();
        int end = s.indexOf('\n', colon);
        if (end < 0) end = s.length();
        return s.substring(colon + 1, end).trim();
    }

    private static Integer roughIntAfter(String s, String key) {
        int idx = s.indexOf(key);
        if (idx < 0) return null;
        int colon = s.indexOf(':', idx);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < s.length() && !Character.isDigit(s.charAt(i)) && s.charAt(i) != '-') i++;
        int j = i;
        if (j < s.length() && s.charAt(j) == '-') j++;
        int start = j;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        if (j == start) return null;
        return Integer.parseInt(s.substring(i, j));
    }
}