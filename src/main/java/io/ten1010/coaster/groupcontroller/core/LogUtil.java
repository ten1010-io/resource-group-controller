package io.ten1010.coaster.groupcontroller.core;

import io.kubernetes.client.openapi.ApiException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public final class LogUtil {

    public static String buildApiExceptionDetailLog(ApiException e, boolean includeStackTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append("ApiException detail : \n");
        sb
                .append("code : ")
                .append(e.getCode())
                .append("\n")
                .append("response body : ")
                .append("\n")
                .append(e.getResponseBody().trim())
                .append("\n");
        sb.append(buildExceptionDetailLog(e, includeStackTrace));

        return sb.toString();
    }

    public static String buildExceptionDetailLog(Exception e, boolean includeStackTrace) {
        StringBuilder sb = new StringBuilder();
        sb.append("Exception detail : \n");
        if (includeStackTrace) {
            Writer strWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(strWriter);
            e.printStackTrace(printWriter);
            printWriter.close();
            sb
                    .append("stack trace : ")
                    .append("\n")
                    .append(strWriter.toString());

            return sb.toString();
        }
        sb
                .append("class : ")
                .append(e.getClass().getSimpleName())
                .append("\n")
                .append("message : ")
                .append(e.getMessage())
                .append("\n");

        return sb.toString();
    }

    private LogUtil() {
        throw new UnsupportedOperationException();
    }

}
