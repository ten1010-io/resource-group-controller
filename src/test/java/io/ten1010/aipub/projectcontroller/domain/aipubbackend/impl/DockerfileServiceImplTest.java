package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ten1010.common.apiclient.ApiClient;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

/**
 * aipub-backend 와의 HTTP 계약을 고정한다. 경로·메서드·쿼리 파라미터명이 어긋나면 백엔드는 404/400 을 주는데,
 * 호출부(ProjectReconciler)가 best-effort 로 삼켜 로그만 남고 정리는 조용히 실패한다. 여기서 막는다.
 */
class DockerfileServiceImplTest {

  private static final String BASE_PATH = "https://aipub-backend-gateway:8443/api/v1alpha1";

  private final AtomicReference<Request> sent = new AtomicReference<>();

  /** 네트워크 없이 실제 OkHttp 요청을 가로채, ApiClient 가 조립한 Request 를 그대로 관찰한다. */
  private ApiClient client(int statusCode) {
    ApiClient client = new ApiClient();
    client.setBasePath(BASE_PATH);
    client.setHttpClient(client.getHttpClient().newBuilder()
        .addInterceptor(chain -> {
          Request request = chain.request();
          this.sent.set(request);
          return new Response.Builder()
              .request(request)
              .protocol(Protocol.HTTP_1_1)
              .code(statusCode)
              .message("stub")
              .body(ResponseBody.create("", MediaType.parse("application/json")))
              .build();
        })
        .build());
    return client;
  }

  @Test
  void deleteByProject_issuesDeleteOnDockerfilesWithProjectNameQuery() {
    new DockerfileServiceImpl(client(204)).deleteDockerfilesByProject("proj-a");

    Request request = this.sent.get();
    assertThat(request.method()).isEqualTo("DELETE");
    assertThat(request.url().encodedPath()).isEqualTo("/api/v1alpha1/dockerfiles");
    assertThat(request.url().queryParameter("projectName")).isEqualTo("proj-a");
  }

  @Test
  void nonSuccessResponse_surfacesAsException() {
    // 계약이 어긋나면(404 등) 조용히 성공으로 보이지 않고 호출부가 로그를 남길 수 있어야 한다.
    assertThatThrownBy(
        () -> new DockerfileServiceImpl(client(404)).deleteDockerfilesByProject("proj-a"))
        .isInstanceOf(AipubBackendResponseException.class)
        .hasMessageContaining("statusCode=404");
  }
}
