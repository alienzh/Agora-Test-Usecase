package io.agora.mediarelay.rtc.transcoder;

import android.os.Build;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.Locale;


public class TranscodeAuthor {

    private String customerKey;
    private String secret;

    // 你的声网项目的 App ID
    // 需要设置环境变量 AGORA_APP_ID
    // 客户 ID
    // 需要设置环境变量 AGORA_CUSTOMER_USER_NAME
    // 客户密钥
    // 需要设置环境变量 AGORA_CUSTOMER_SECRET
    public TranscodeAuthor(String customerKey, String secret) {
        this.customerKey = customerKey;
        this.secret = secret;
    }

    public String basicAuth() {
        // 拼接客户 ID 和客户密钥并使用 base64 编码
        String plainCredentials = customerKey + ":" + secret;
        String base64Credentials = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            base64Credentials = new String(Base64.getEncoder().encode(plainCredentials.getBytes()));
        }
        // 创建 authorization header
        String authorizationHeader = "Basic " + base64Credentials;
        return authorizationHeader;
    }

    public String hmacAuth(String host, String path, String method, String data)  {
        if (customerKey == null || secret == null) {
            System.out.println("Please check environments: AGORA_APP_ID, AGORA_CUSTOMER_USER_NAME, AGORA_CUSTOMER_SECRET");
            return "";
        }
        // 获取请求的时间戳
        // 后续请求头中会用到 date 字段
        String date = getCurrentUTCString();
        // 生成 HAMC 签名
        String bodySign = hashData(data);
        // 生成 digest
        // 后续请求头中会用到 digest 字段
        String digest = "SHA-256=" + bodySign;
        String reqline = method + " " + path + " HTTP/1.1";
        String signingStr = "host: " + host + "\ndate: " + date + "\n" + reqline + "\ndigest: " + digest;
        String sign = signData(signingStr, secret);
        // 生成 authorization
        // 后续请求头中会用到 authorization 字段
        String authorization = "hmac username=\"" + customerKey + "\", ";
        authorization += "algorithm=\"hmac-sha256\", ";
        authorization += "headers=\"host date request-line digest\", ";
        authorization += "signature=\"" + sign + "\"";
        return authorization;
    }

    private static String hashData(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Base64.getEncoder().encodeToString(hashBytes);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    static String getCurrentUTCString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(new Date());
    }

    private static String signData(String data, String secret) {
        if (data == null || data.equals("")) {
            return "";
        }
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            hmacSha256.init(secretKeySpec);
            byte[] signature = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Base64.getEncoder().encodeToString(signature);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
