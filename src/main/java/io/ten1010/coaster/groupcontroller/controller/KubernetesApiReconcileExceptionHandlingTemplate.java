package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.coaster.groupcontroller.core.LogUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.HttpURLConnection;
import java.time.Duration;

@Slf4j
public class KubernetesApiReconcileExceptionHandlingTemplate {

    private Duration apiConflictRequeueDuration;
    private Duration apiFailRequeueDuration;

    public KubernetesApiReconcileExceptionHandlingTemplate(Duration apiConflictRequeueDuration, Duration apiFailRequeueDuration) {
        this.apiConflictRequeueDuration = apiConflictRequeueDuration;
        this.apiFailRequeueDuration = apiFailRequeueDuration;
    }

    public Result execute(KubernetesApiReconcileCallback callback, Request request) {
        try {
            return callback.doWithApiExceptionHandling();
        } catch (ApiException e) {
            if (e.getCode() == HttpURLConnection.HTTP_CONFLICT) {
                log.info("Kubernetes api call conflicted while reconciling request [{}]\n{}",
                        request.toString(),
                        LogUtil.buildApiExceptionDetailLog(e, true));

                return new Result(true, this.apiConflictRequeueDuration);
            }

            log.error("Kubernetes api call failed while reconciling request [{}]\n{}",
                    request.toString(),
                    LogUtil.buildApiExceptionDetailLog(e, true));

            return new Result(true, this.apiFailRequeueDuration);
        }
    }

}
