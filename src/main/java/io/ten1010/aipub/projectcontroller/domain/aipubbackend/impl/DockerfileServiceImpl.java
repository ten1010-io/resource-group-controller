package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.DockerfileService;
import io.ten1010.common.apiclient.ApiClient;
import java.util.Map;
import okhttp3.Call;

public class DockerfileServiceImpl implements DockerfileService {

  private final ApiClient aipubBackendClient;
  private final CallHelper callHelper;

  public DockerfileServiceImpl(ApiClient aipubBackendClient) {
    this.aipubBackendClient = aipubBackendClient;
    this.callHelper = new CallHelper(aipubBackendClient);
  }

  @Override
  public void deleteDockerfilesByProject(String projectName) {
    Call call = this.aipubBackendClient.buildCall(
        "/dockerfiles",
        "DELETE",
        Map.of("projectName", projectName));

    this.callHelper.executeCall(call);
  }

}
