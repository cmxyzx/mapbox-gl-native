package com.mapbox.mapboxsdk.http;

import java.io.IOException;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.RealResponseBody;
import okio.GzipSource;
import okio.Okio;
import timber.log.Timber;

/**
 * Created by tonyduandi@gmail.com on 2018/1/16.
 *
 * Using GZIP stream format ID1 & ID2 (RFC1952) to determine whether the stream need to decompress or not.
 * http://www.ietf.org/rfc/rfc1952.txt
 */

public class GzipInterceptor implements Interceptor {
    private static final String TAG = "GzipInterceptor";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (null != response && HttpHeaders.hasBody(response)) {
            try {
                if (!"gzip".equalsIgnoreCase(response.header("Content-Encoding"))) {
                    ResponseBody responseGzipHead = response.peekBody(2);
                    byte[] gzipHeadBytes = responseGzipHead.bytes();
                    if (null != gzipHeadBytes && gzipHeadBytes.length >= 2) {
                        //refer to rfc1952  http://www.ietf.org/rfc/rfc1952.txt
                        if (((gzipHeadBytes[0] & 0xff) == 0x1f) && ((gzipHeadBytes[1] & 0xff) == 0x8b)) {
                            Timber.w(TAG, "detected gzip head in stream using gzip to decode stream manually");
                            Response.Builder responseBuilder = response.newBuilder()
                                    .request(request);
                            GzipSource responseBody = new GzipSource(response.body().source());
                            Headers strippedHeaders = response.headers().newBuilder()
                                    .removeAll("Content-Encoding")
                                    .removeAll("Content-Length")
                                    .build();
                            responseBuilder.headers(strippedHeaders);
                            String contentType = response.header("Content-Type");
                            responseBuilder.body(new RealResponseBody(contentType, -1L, Okio.buffer(responseBody)));

                            return responseBuilder.build();
                        }
                    }
                }
            } catch (Exception e) {
                Timber.e(TAG, e);
            }
        }
        return response;
    }
}
