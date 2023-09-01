package io.ten1010.coaster.groupcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EventHandlerUtil {

    public static Request resolveNamespacedObjectToRequest(KubernetesObject object) {
        Objects.requireNonNull(object.getMetadata());
        return new Request(object.getMetadata().getNamespace(), object.getMetadata().getName());
    }

    public static Set<String> getAddedOrDeletedNamespaces(List<String> oldNamespaces, List<String> newNamespaces) {
        Set<String> deleted = new HashSet<>(oldNamespaces);
        newNamespaces.forEach(deleted::remove);
        Set<String> added = new HashSet<>(newNamespaces);
        oldNamespaces.forEach(added::remove);
        deleted.addAll(added);
        return deleted;
    }

    private EventHandlerUtil() {
        throw new UnsupportedOperationException();
    }

}
