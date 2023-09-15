package io.ten1010.coaster.groupcontroller.conversion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(ConversionController.class)
@Import(ConversionService.class)
class ConversionControllerTest {

    @Autowired
    MockMvc mockMvc;


    @Test
    void convert() throws Exception {
        String body = "{\"kind\":\"ConversionReview\",\"apiVersion\":\"apiextensions.k8s.io/v1\",\"request\":{\"uid\":\"cea0080e-0ef8-48a8-8623-f0f93abdd333\",\"desiredAPIVersion\":\"resource-group.ten1010.io/v1beta1\",\"objects\":[{\"apiVersion\":\"resource-group.ten1010.io/v1beta2\",\"kind\":\"ResourceGroup\",\"metadata\":{\"annotations\":{\"kubectl.kubernetes.io/last-applied-configuration\":\"{\\\"apiVersion\\\":\\\"resource-group.ten1010.io/v1beta2\\\",\\\"kind\\\":\\\"ResourceGroup\\\",\\\"metadata\\\":{\\\"annotations\\\":{},\\\"name\\\":\\\"group1\\\"},\\\"spec\\\":{\\\"daemonSet\\\":{\\\"allowAll\\\":true,\\\"daemonSets\\\":[]},\\\"namespaces\\\":[\\\"ns1\\\"],\\\"nodes\\\":[\\\"minikube-m02\\\"],\\\"subjects\\\":[]}}\\n\"},\"creationTimestamp\":\"2023-09-12T08:11:36Z\",\"generation\":1,\"managedFields\":[{\"apiVersion\":\"resource-group.ten1010.io/v1beta2\",\"fieldsType\":\"FieldsV1\",\"fieldsV1\":{\"f:metadata\":{\"f:annotations\":{\".\":{},\"f:kubectl.kubernetes.io/last-applied-configuration\":{}}},\"f:spec\":{\".\":{},\"f:daemonSet\":{\".\":{},\"f:allowAll\":{},\"f:daemonSets\":{}},\"f:namespaces\":{},\"f:nodes\":{},\"f:subjects\":{}}},\"manager\":\"kubectl-client-side-apply\",\"operation\":\"Update\",\"time\":\"2023-09-12T08:11:36Z\"}],\"name\":\"group1\",\"uid\":\"c39db20a-2863-4007-935c-7eb22c9c9b5b\"},\"spec\":{\"daemonSet\":{\"allowAll\":true,\"daemonSets\":[]},\"namespaces\":[\"ns1\"],\"nodes\":[\"minikube-m02\"],\"subjects\":[]}}]},\"response\":{\"uid\":\"\",\"convertedObjects\":null,\"result\":{\"metadata\":{}}}}\n";

        mockMvc.perform(post("/crdconvert")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print());

    }
}