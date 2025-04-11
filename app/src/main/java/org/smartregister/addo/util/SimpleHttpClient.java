package org.smartregister.addo.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * A simple HTTP client that supports GET and POST requests.
 * <p>
 * This class is intended as a base for making HTTP calls.
 * It supports specifying custom headers and reading the response from both successful and error responses.
 */
public class SimpleHttpClient {
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList("raw.githubusercontent.com","ucs.nacp.go.tz");
    public String post(String urlString, Object o){
        return post(urlString,null,JsonQ.fromPOJO(o).toString());
    }
    public String get(String urlString){
        return get(urlString,null);
    }
    public String get(String urlString, Map<String, String> headers){
        try {
            HttpURLConnection connection = openConnection(urlString,headers);
            connection.setRequestMethod("GET");
            return getResponse(connection);
        } catch (IOException e) {Timber.e(e);}
        return "";
    }
    public String post(String urlString, Map<String, String> headers, String body){
        try{
            HttpURLConnection connection = openConnection(urlString,headers);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            if (body != null && !body.isEmpty()) {
                byte[] postData = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", Integer.toString(postData.length));

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postData);
                    os.flush();
                }
                return getResponse(connection);
            }
        }
        catch (Exception e){Timber.e(e);}
        return "";
    }
    private HttpURLConnection openConnection(String urlString,Map<String,String>headers)throws IOException{
        URL url = new URL(urlString);
        if (!ALLOWED_DOMAINS.contains(url.getHost())) {
            throw new SecurityException("Host not allowed: " + url.getHost());
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        if (headers == null) return connection;

        for (String key: headers.keySet()) {
            connection.setRequestProperty(key, headers.get(key));
        }
        return connection;
    }
    private String getResponse(HttpURLConnection connection) throws IOException {
        int responseCode = connection.getResponseCode();
        InputStream inputStream = (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_BAD_REQUEST)
                ? connection.getInputStream()
                : connection.getErrorStream();

        // Read the stream and close the connection
        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) responseBuilder.append(inputLine);
        }
        connection.disconnect();
        return responseBuilder.toString();
    }
}
