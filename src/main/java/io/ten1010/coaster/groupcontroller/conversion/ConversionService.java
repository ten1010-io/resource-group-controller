package io.ten1010.coaster.groupcontroller.conversion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kubernetes.client.common.KubernetesObject;
import io.ten1010.coaster.groupcontroller.model.V1Beta1ResourceGroup;
import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversionService {

    private final Map<String, Converter> converters = Map.of(
            V1Beta1ResourceGroup.API_VERSION, new ResourceGroupV1Beta1Converter(),
            V1Beta2ResourceGroup.API_VERSION, new ResourceGroupV1Beta2Converter()
    );

    private final ObjectMapper objectMapper;

    public ConversionService() {
        this.objectMapper =  new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public ConversionResponse convert(ConversionRequest request) {

        ConversionResponse response = new ConversionResponse();
        response.setUid(Objects.requireNonNull(request.getUid()));
        String desiredVersion = request.getDesiredAPIVersion();
        List<Object> convertedObjects = request.getObjects().stream()
                .map(group -> {
                    String apiVersion = (String) group.get("apiVersion");
                    KubernetesObject groupObject = mapToObject(group, apiVersion);
                    return convert(groupObject, desiredVersion);
                })
                .collect(Collectors.toList());

        response.setConvertedObjects(convertedObjects);

        response.setResult(ConversionResponse.Result.success());

        return response;
    }

    private KubernetesObject mapToObject(Map<String, Object> object, String apiVersion) {
        if (V1Beta1ResourceGroup.API_VERSION.equals(apiVersion)) {
            return objectMapper.convertValue(object, V1Beta1ResourceGroup.class);
        } else if (V1Beta2ResourceGroup.API_VERSION.equals(apiVersion)) {
            return objectMapper.convertValue(object, V1Beta2ResourceGroup.class);
        } else {
            throw new IllegalArgumentException("Not supported api version: " + apiVersion);
        }
    }

    private Object convert(KubernetesObject resource, String targetVersion) {
        String sourceVersion = resource.getApiVersion();
        if (!converters.containsKey(sourceVersion)) {
            throw new IllegalArgumentException("Missing converter from version: " + sourceVersion);
        }
        if (!converters.containsKey(targetVersion)) {
            throw new IllegalArgumentException("Missing converter from version: " + targetVersion);
        }

        return converters.get(targetVersion).convertFromHub(converters.get(sourceVersion).convertToHub(resource));
    }

}
