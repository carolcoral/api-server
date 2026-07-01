/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock 数据模板引擎
 * 支持 Faker.js 风格的随机数据生成，包括中文姓名、手机号、身份证等
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-07-01
 */
@Service
public class MockTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(MockTemplateEngine.class);
    private static final Faker faker = new Faker(new Locale("zh-CN"));

    /**
     * 模板占位符匹配模式：{{functionName}} 或 {{functionName:param}}
     */
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*(?::[^}]+)?)\\s*\\}\\}");

    /**
     * 支持的模板函数及其处理器
     */
    private final Map<String, TemplateFunction> functions = new LinkedHashMap<>();

    public MockTemplateEngine() {
        registerFunctions();
    }

    /**
     * 模板函数接口
     */
    @FunctionalInterface
    private interface TemplateFunction {
        Object generate(String param);
    }

    /**
     * 注册所有支持的模板函数
     */
    private void registerFunctions() {
        // ===== 姓名相关 =====
        functions.put("name", p -> faker.name().fullName());
        functions.put("firstName", p -> faker.name().firstName());
        functions.put("lastName", p -> faker.name().lastName());
        functions.put("nameMale", p -> faker.name().fullName()); // datafaker 2.x uses Faker
        functions.put("nameFemale", p -> faker.name().fullName());

        // ===== 联系方式 =====
        functions.put("phone", p -> faker.phoneNumber().cellPhone());
        functions.put("phoneNumber", p -> faker.phoneNumber().phoneNumber());
        functions.put("email", p -> faker.internet().emailAddress());
        functions.put("emailDomain", p -> faker.internet().domainName());

        // ===== 身份证/证件 =====
        functions.put("idCard", p -> generateIdCard());
        functions.put("idNumber", p -> generateIdCard());

        // ===== 地址 =====
        functions.put("province", p -> faker.address().state());
        functions.put("city", p -> faker.address().city());
        functions.put("district", p -> faker.address().streetName());
        functions.put("address", p -> faker.address().fullAddress());
        functions.put("street", p -> faker.address().streetAddress());
        functions.put("zipCode", p -> faker.address().zipCode());

        // ===== 公司/职业 =====
        functions.put("company", p -> faker.company().name());
        functions.put("job", p -> faker.job().title());
        functions.put("department", p -> faker.job().field());

        // ===== 数字 =====
        functions.put("integer", p -> {
            int[] range = parseRange(p, 0, 100);
            return String.valueOf(ThreadLocalRandom.current().nextInt(range[0], range[1] + 1));
        });
        functions.put("decimal", p -> {
            double[] range = parseDoubleRange(p, 0.0, 100.0);
            double val = ThreadLocalRandom.current().nextDouble(range[0], range[1]);
            return String.format("%.2f", val);
        });
        functions.put("number", p -> String.valueOf(ThreadLocalRandom.current().nextInt(0, 100)));
        functions.put("age", p -> String.valueOf(ThreadLocalRandom.current().nextInt(18, 65)));
        functions.put("price", p -> String.format("%.2f", ThreadLocalRandom.current().nextDouble(0.01, 9999.99)));

        // ===== 字符串 =====
        functions.put("uuid", p -> UUID.randomUUID().toString());
        functions.put("guid", p -> UUID.randomUUID().toString());
        functions.put("word", p -> faker.lorem().word());
        functions.put("words", p -> {
            int count = parseCount(p, 3);
            return faker.lorem().sentence(count).replace(".", "");
        });
        functions.put("sentence", p -> faker.lorem().sentence());
        functions.put("paragraph", p -> faker.lorem().paragraph());
        functions.put("text", p -> {
            int len = parseCount(p, 50);
            String text = faker.lorem().paragraph();
            return text.length() > len ? text.substring(0, len) : text;
        });

        // ===== 日期时间 =====
        functions.put("date", p -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(faker.date().birthday());
        });
        functions.put("datetime", p -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(faker.date().birthday());
        });
        functions.put("time", p -> {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            return sdf.format(new Date());
        });
        functions.put("timestamp", p -> String.valueOf(System.currentTimeMillis()));
        functions.put("year", p -> String.valueOf(ThreadLocalRandom.current().nextInt(2000, 2026)));
        functions.put("month", p -> String.valueOf(ThreadLocalRandom.current().nextInt(1, 13)));
        functions.put("day", p -> String.valueOf(ThreadLocalRandom.current().nextInt(1, 29)));

        // ===== 互联网 =====
        functions.put("url", p -> faker.internet().url());
        functions.put("domain", p -> faker.internet().domainName());
        functions.put("ip", p -> faker.internet().ipV4Address());
        functions.put("ipv6", p -> faker.internet().ipV6Address());
        functions.put("username", p -> faker.internet().username());
        functions.put("password", p -> faker.internet().password(8, 16));
        functions.put("image", p -> faker.internet().image());

        // ===== 颜色 =====
        functions.put("color", p -> faker.color().name());
        functions.put("hexColor", p -> faker.color().hex());

        // ===== 布尔 =====
        functions.put("boolean", p -> String.valueOf(ThreadLocalRandom.current().nextBoolean()));
        functions.put("bool", p -> String.valueOf(ThreadLocalRandom.current().nextBoolean()));

        // ===== 数组/枚举 =====
        functions.put("enum", p -> {
            if (p == null || p.isEmpty()) return "";
            String[] values = p.split(",");
            return values[ThreadLocalRandom.current().nextInt(values.length)].trim();
        });
        functions.put("pick", p -> {
            if (p == null || p.isEmpty()) return "";
            String[] values = p.split(",");
            return values[ThreadLocalRandom.current().nextInt(values.length)].trim();
        });

        // ===== 序列号 =====
        functions.put("index", new TemplateFunction() {
            private final Map<String, Integer> counters = new HashMap<>();
            @Override
            public Object generate(String p) {
                String key = p != null ? p : "default";
                int val = counters.getOrDefault(key, 0) + 1;
                counters.put(key, val);
                return String.valueOf(val);
            }
        });
        functions.put("seq", new TemplateFunction() {
            private final Map<String, Integer> counters = new HashMap<>();
            @Override
            public Object generate(String p) {
                String key = p != null ? p : "default";
                int start = parseCount(p, 1);
                int val = counters.getOrDefault(key, start - 1) + 1;
                counters.put(key, val);
                return String.valueOf(val);
            }
        });
    }

    /**
     * 处理响应体中的模板占位符，替换为随机生成的数据
     *
     * @param template 包含 {{functionName}} 或 {{functionName:param}} 占位符的模板字符串
     * @return 替换后的结果
     */
    public String process(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        Map<String, Object> cache = new HashMap<>(); // 缓存同一占位符的值

        while (matcher.find()) {
            String fullMatch = matcher.group(1).trim();
            String functionName;
            String param = null;

            int colonIdx = fullMatch.indexOf(':');
            if (colonIdx > 0) {
                functionName = fullMatch.substring(0, colonIdx).trim();
                param = fullMatch.substring(colonIdx + 1).trim();
            } else {
                functionName = fullMatch;
            }

            // 检查缓存（同一模板中相同占位符返回相同值）
            String cacheKey = fullMatch;
            Object replacement;
            if (cache.containsKey(cacheKey)) {
                replacement = cache.get(cacheKey);
            } else {
                TemplateFunction func = functions.get(functionName);
                if (func != null) {
                    try {
                        replacement = func.generate(param);
                        cache.put(cacheKey, replacement);
                    } catch (Exception e) {
                        log.warn("模板函数 {} 执行失败: {}", functionName, e.getMessage());
                        replacement = fullMatch;
                    }
                } else {
                    log.warn("未知的模板函数: {}", functionName);
                    replacement = fullMatch;
                }
            }

            // 根据上下文决定是否加引号
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    replacement instanceof String ? (String) replacement : String.valueOf(replacement)));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 处理 JSON 模板，递归替换所有字符串值中的占位符
     *
     * @param jsonTemplate JSON 格式的模板字符串
     * @return 替换后的 JSON 字符串
     */
    public String processJson(String jsonTemplate) {
        if (jsonTemplate == null || jsonTemplate.isEmpty()) {
            return jsonTemplate;
        }

        try {
            Object parsed = JSON.parse(jsonTemplate);
            Object processed = processObject(parsed);
            return JSON.toJSONString(processed);
        } catch (Exception e) {
            log.warn("JSON 模板解析失败，回退到纯文本处理: {}", e.getMessage());
            return process(jsonTemplate);
        }
    }

    /**
     * 递归处理 JSON 对象
     */
    @SuppressWarnings("unchecked")
    private Object processObject(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jsonObj = (JSONObject) obj;
            JSONObject result = new JSONObject();
            for (Map.Entry<String, Object> entry : jsonObj.entrySet()) {
                result.put(entry.getKey(), processObject(entry.getValue()));
            }
            return result;
        } else if (obj instanceof JSONArray) {
            JSONArray jsonArr = (JSONArray) obj;
            JSONArray result = new JSONArray();
            for (Object item : jsonArr) {
                result.add(processObject(item));
            }
            return result;
        } else if (obj instanceof String) {
            return process((String) obj);
        } else {
            return obj;
        }
    }

    /**
     * 获取支持的模板函数列表
     *
     * @return 函数名和说明的映射
     */
    public Map<String, String> getSupportedFunctions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("{{name}}", "随机中文姓名");
        descriptions.put("{{firstName}}", "随机中文名");
        descriptions.put("{{lastName}}", "随机中文姓");
        descriptions.put("{{phone}}", "随机手机号");
        descriptions.put("{{email}}", "随机邮箱");
        descriptions.put("{{idCard}}", "随机身份证号（18位）");
        descriptions.put("{{province}}", "随机省份");
        descriptions.put("{{city}}", "随机城市");
        descriptions.put("{{address}}", "随机详细地址");
        descriptions.put("{{zipCode}}", "随机邮编");
        descriptions.put("{{company}}", "随机公司名称");
        descriptions.put("{{job}}", "随机职位");
        descriptions.put("{{integer:0-100}}", "随机整数（支持范围）");
        descriptions.put("{{decimal:0-100}}", "随机小数（支持范围）");
        descriptions.put("{{age}}", "随机年龄（18-65）");
        descriptions.put("{{uuid}}", "随机UUID");
        descriptions.put("{{word}}", "随机单词");
        descriptions.put("{{sentence}}", "随机句子");
        descriptions.put("{{paragraph}}", "随机段落");
        descriptions.put("{{text:100}}", "随机文本（指定长度）");
        descriptions.put("{{date}}", "随机日期（yyyy-MM-dd）");
        descriptions.put("{{datetime}}", "随机日期时间");
        descriptions.put("{{timestamp}}", "当前时间戳");
        descriptions.put("{{url}}", "随机URL");
        descriptions.put("{{ip}}", "随机IPv4地址");
        descriptions.put("{{username}}", "随机用户名");
        descriptions.put("{{password}}", "随机密码");
        descriptions.put("{{avatar}}", "随机头像URL");
        descriptions.put("{{color}}", "随机颜色名");
        descriptions.put("{{hexColor}}", "随机十六进制颜色");
        descriptions.put("{{boolean}}", "随机布尔值");
        descriptions.put("{{enum:值1,值2,值3}}", "从枚举中随机选择");
        descriptions.put("{{index}}", "自增序号（从1开始）");
        return descriptions;
    }

    /**
     * 解析范围参数 "min-max" 格式
     */
    private int[] parseRange(String param, int defaultMin, int defaultMax) {
        if (param == null || param.isEmpty()) {
            return new int[]{defaultMin, defaultMax};
        }
        String[] parts = param.split("-");
        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : defaultMax;
            return new int[]{min, max};
        } catch (NumberFormatException e) {
            return new int[]{defaultMin, defaultMax};
        }
    }

    private double[] parseDoubleRange(String param, double defaultMin, double defaultMax) {
        if (param == null || param.isEmpty()) {
            return new double[]{defaultMin, defaultMax};
        }
        String[] parts = param.split("-");
        try {
            double min = Double.parseDouble(parts[0].trim());
            double max = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : defaultMax;
            return new double[]{min, max};
        } catch (NumberFormatException e) {
            return new double[]{defaultMin, defaultMax};
        }
    }

    private int parseCount(String param, int defaultCount) {
        if (param == null || param.isEmpty()) {
            return defaultCount;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException e) {
            return defaultCount;
        }
    }

    /**
     * 生成合法的 18 位身份证号码
     */
    private String generateIdCard() {
        // 随机地区码（6位）
        String[] areaCodes = {
            "110101", "110102", "310101", "310105", "440103", "440104",
            "330102", "330103", "320102", "320104", "510104", "510105",
            "420102", "420103", "610102", "610103", "210102", "210103"
        };
        String areaCode = areaCodes[ThreadLocalRandom.current().nextInt(areaCodes.length)];

        // 随机出生日期（8位）：1950-2005
        int year = ThreadLocalRandom.current().nextInt(1950, 2006);
        int month = ThreadLocalRandom.current().nextInt(1, 13);
        int day = ThreadLocalRandom.current().nextInt(1, 29);
        String birthday = String.format("%04d%02d%02d", year, month, day);

        // 随机顺序码（3位）
        int sequence = ThreadLocalRandom.current().nextInt(1, 1000);
        String sequenceCode = String.format("%03d", sequence);

        // 前17位
        String id17 = areaCode + birthday + sequenceCode;

        // 校验码（第18位）
        char checkCode = calculateIdCardCheckCode(id17);

        return id17 + checkCode;
    }

    /**
     * 计算身份证校验码
     */
    private char calculateIdCardCheckCode(String id17) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id17.charAt(i) - '0') * weights[i];
        }
        return checkCodes[sum % 11];
    }
}
