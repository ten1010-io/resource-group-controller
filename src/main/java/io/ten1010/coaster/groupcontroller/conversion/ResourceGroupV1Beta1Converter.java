package io.ten1010.coaster.groupcontroller.conversion;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.coaster.groupcontroller.model.*;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ResourceGroupV1Beta1Converter implements Converter<V1Beta1ResourceGroup, V1Beta2ResourceGroup> {

    @Override
    public V1Beta2ResourceGroup convertToHub(V1Beta1ResourceGroup from) {
        V1Beta2ResourceGroup to = V1Beta2ResourceGroup.withDefaultApiVersionAndKind();
        to.setMetadata(Objects.requireNonNull(from.getMetadata()));
        to.setSpec(convertResourceGroupSpecToHub(
                Objects.requireNonNull(from.getSpec())));
        to.setStatus(new V1Beta2ResourceGroupStatus());
        Boolean allowAllDaemonSet = Optional.ofNullable(from.getMetadata())
                .map(V1ObjectMeta::getAnnotations)
                .map(annotations -> annotations.get(ConversionAnnotation.ALLOW_ALL_DAEMON_SET))
                .map(Boolean::parseBoolean)
                .orElse(false);
        to.getSpec().getDaemonSet().setAllowAll(allowAllDaemonSet);
        return to;
    }

    private V1Beta2ResourceGroupSpec convertResourceGroupSpecToHub(V1Beta1ResourceGroupSpec from) {
        V1Beta2ResourceGroupSpec to = new V1Beta2ResourceGroupSpec();
        to.setNodes(from.getNodes());
        to.setNamespaces(from.getNamespaces());
        to.setDaemonSet(convertResourceGroupDaemonSetToHub(Objects.requireNonNull(from.getDaemonSet())));
        to.setSubjects(from.getSubjects());

        return to;
    }

    private V1Beta2DaemonSet convertResourceGroupDaemonSetToHub(V1Beta1DaemonSet from) {
        V1Beta2DaemonSet to = new V1Beta2DaemonSet();
        to.setDaemonSets(from.getDaemonSets().stream()
                .map(this::convertObjectReferenceToHub).collect(Collectors.toList()));
        return to;
    }

    private V1Beta2K8sObjectReference convertObjectReferenceToHub(V1Beta1K8sObjectReference from) {
        V1Beta2K8sObjectReference to = new V1Beta2K8sObjectReference();
        to.setName(from.getName());
        to.setNamespace(from.getNamespace());
        return to;
    }

    @Override
    public V1Beta1ResourceGroup convertFromHub(V1Beta2ResourceGroup from) {
        V1Beta1ResourceGroup to = V1Beta1ResourceGroup.withDefaultApiVersionAndKind();
        to.setMetadata(Objects.requireNonNull(from.getMetadata()));
        to.setSpec(convertResourceGroupSpecFromHub(Objects.requireNonNull(from.getSpec())));
        to.setStatus(new V1Beta1ResourceGroupStatus());

        boolean allowAllDaemonSet = from.getSpec().getDaemonSet().isAllowAll();
        to.getMetadata().putAnnotationsItem(
                ConversionAnnotation.ALLOW_ALL_DAEMON_SET, Boolean.toString(allowAllDaemonSet));
        return to;
    }

    private V1Beta1ResourceGroupSpec convertResourceGroupSpecFromHub(V1Beta2ResourceGroupSpec from) {
        V1Beta1ResourceGroupSpec to = new V1Beta1ResourceGroupSpec();
        to.setNodes(from.getNodes());
        to.setNamespaces(from.getNamespaces());
        to.setDaemonSet(convertResourceGroupDaemonSetFromHub(Objects.requireNonNull(from.getDaemonSet())));
        to.setSubjects(from.getSubjects());

        return to;
    }

    private V1Beta1DaemonSet convertResourceGroupDaemonSetFromHub(V1Beta2DaemonSet from) {
        V1Beta1DaemonSet to = new V1Beta1DaemonSet();
        to.setDaemonSets(from.getDaemonSets().stream()
                .map(this::convertObjectReferenceFromHub).collect(Collectors.toList()));
        return to;
    }

    private V1Beta1K8sObjectReference convertObjectReferenceFromHub(V1Beta2K8sObjectReference from) {
        V1Beta1K8sObjectReference to = new V1Beta1K8sObjectReference();
        to.setName(from.getName());
        to.setNamespace(from.getNamespace());
        return to;
    }
}
