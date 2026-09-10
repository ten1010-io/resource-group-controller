package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.TemplateService;
import io.ten1010.common.apiclient.ApiClient;
import java.util.Map;
import okhttp3.Call;

public class TemplateServiceImpl implements TemplateService {

  private final ApiClient aipubBackendClient;
  private final CallHelper callHelper;

  public TemplateServiceImpl(ApiClient aipubBackendClient) {
    this.aipubBackendClient = aipubBackendClient;
    this.callHelper = new CallHelper(aipubBackendClient);
  }

  @Override
  public void deleteTemplatesByProject(String projectName) {
    Call call = this.aipubBackendClient.buildCall(
        "/templates",
        "DELETE",
        Map.of("projectName", projectName));

    this.callHelper.executeCall(call);
  }

}
