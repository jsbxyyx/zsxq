package zsxq;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dongliu.requests.Requests;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static final String htmlTemplate = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>{title}</h1>\n" +
            "<p>{create_time}</p>\n" +
            "<p>{text}</p>\n" +
            "</body>\n" +
            "</html>";

    public static void main(String[] args) throws Exception {
//        test_x_signature();
        start();
    }

    static void test_x_signature() {
        String url = "https://api.zsxq.com/v2/groups/51122814854444/topics?scope=all&count=20";
        String x_request_id = "d362915cc-6837-50b3-b6e1-dea905307cb";
        long seconds = 1684464539;
        String s = x_signature(url, x_request_id, seconds);
        System.out.println("8307c9cb628421123509d75bfe060bd6a9325979");
        System.out.println(s);
    }

    static void start() throws Exception {
        //    请先登录你有权限查看的星球的账号，进入该星球页面
        //    请使用谷歌浏览器刷新页面，在 Network 面板的抓包内容中找到 topics?... 这样的请求，返回的是 json 内容
        //    将这个包的 cookie 部分复制到 headers 部分的 Cookie 一栏
        //    将这个请求的 url，域名为 api.zsxq.com 开头的，复制到下面 start_url 的部分
        final String startUrl = "https://api.zsxq.com/v2/groups/51122814854444/topics?scope=all&count=20";
        String end_time = "2022-07-12T09:48:52.313+0800";
        do {
            String _url = startUrl + (!"".equals(end_time) ? "&end_time=" + URLEncoder.encode(end_time, "utf-8") : "");
            end_time = getData(_url, getHeaders(_url));
            System.out.println("end_time :: " + end_time);
            TimeUnit.SECONDS.sleep(1);
        } while (!end_time.equals(""));
    }

    static String getData(String url, Map<String, String> headers) {
        String s = Requests.get(url).headers(headers).send().readToText();
        System.out.println(s);
        JsonObject json = JsonParser.parseString(s).getAsJsonObject();
        boolean succeeded = json.get("succeeded").getAsBoolean();
        String end_time = "";
        if (succeeded) {
            JsonObject resp_data = json.get("resp_data").getAsJsonObject();
            JsonArray topics = resp_data.get("topics").getAsJsonArray();
            int size = topics.size();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    JsonObject topic = topics.get(i).getAsJsonObject();
                    String create_time = topic.getAsJsonObject().get("create_time").getAsString();
                    String type = topic.get("type").getAsString();
                    if ("talk".equals(type)) {
                        JsonElement article = topic.get("talk").getAsJsonObject().get("article");
                        if (article != null) {
                            String inline_article_url = article.getAsJsonObject().get("inline_article_url").getAsString();
                            String s1 = Requests.get(inline_article_url).headers(headers).send().readToText();
                            Element content_container = Jsoup.parse(s1).getElementsByClass("content-container").get(0);
                            Element content = content_container.getElementsByClass("content").get(0);
                            String title = content_container.getElementsByClass("title").get(0).text();
                            System.out.println(title);
                            String html = content.html();
                            html = convertImg(html, (src) -> {
                                byte[] bytes = Requests.get(src).headers(getHeaders(null)).send().readToBytes();
                                return "data:image/png;base64, " + Base64.getEncoder().encodeToString(bytes);
                            });
                            String out_html = htmlTemplate.replace("{title}", title)
                                    .replace("{create_time}", create_time)
                                    .replace("{text}", html);
                            try {
                                Files.write(new File(System.getProperty("user.dir") + "/html/" + obtainFilename(title) + ".html").toPath(), out_html.getBytes(StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                String create_time = topics.get(size - 1).getAsJsonObject().get("create_time").getAsString();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                try {
                    Date parse = sdf.parse(create_time);
                    Date date = new Date(parse.getTime() - 1);
                    end_time = sdf.format(date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println(url);
            System.out.println(headers);
        }
        return end_time;
    }

    static Map<String, String> getHeaders(String url) {
        Map<String, String> headers = new HashMap<>();
        String x_request_id = UUID.randomUUID().toString();
        long seconds = System.currentTimeMillis() / 1000;
        headers.put("Cookie", "zsxq_access_token=xxxx; zsxqsessionid=xxxx; abtest_env=product;");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36 Edg/113.0.1774.42");
        headers.put("referer", "https://wx.zsxq.com/");
        headers.put("origin", "https://wx.zsxq.com");
        if (url != null) {
            headers.put("x-request-id", x_request_id);
            headers.put("x-timestamp", seconds + "");
            headers.put("x-signature", x_signature(url, x_request_id, seconds));
            headers.put("x-version", "2.37.0");
        }
        return headers;
    }

    static String x_signature(String url, String x_request_id, long seconds) {
        url = url.replace("'", "%27");
        String cn = url + " " + seconds + " " + x_request_id;
        return sha1(cn);
    }

    static String sha1(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            byte[] md5 = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : md5) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    static String obtainFilename(String str) {
        String s = str.replaceAll("[/\\\\:*?|]", ".");
        s = s.replaceAll("[\"<>]", "'");
        return s.replaceAll("\\s", "-");
    }

    static String convertImg(String content, Function<String, String> f) {
        String patternStr = "<img\\s*([^>]*)\\s*src=\\\"(.*?)\\\"\\s*([^>]*)>";
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        String result = content;
        while (matcher.find()) {
            String src = matcher.group(2);
            String img = matcher.group(0);
            String img2 = matcher.group(0);
            String replaceSrc = "";
            if (src.startsWith("http:") || src.startsWith("https:")) {
                replaceSrc = f.apply(src);
                img = img.replaceAll(src, replaceSrc);
                result = result.replaceAll(img2, img);
            }
        }
        return result;
    }

}
