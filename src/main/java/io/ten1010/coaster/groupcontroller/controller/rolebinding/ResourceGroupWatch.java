package io.ten1010.coaster.groupcontroller.controller.rolebinding;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1Subject;
import io.ten1010.coaster.groupcontroller.controller.role.RoleNameUtil;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class ResourceGroupWatch implements ControllerWatch<V1ResourceGroup> {

    public static final Duration RESYNC_PERIOD = Duration.ofSeconds(30);

    public static class EventHandler implements ResourceEventHandler<V1ResourceGroup> {

        private static List<String> getNamespaces(V1ResourceGroup obj) {
            if (obj.getSpec() == null) {
                return new ArrayList<>();
            }
            return obj.getSpec().getNamespaces();
        }

        private static Set<String> getAddedOrDeletedNamespaces(List<String> oldNamespaces, List<String> newNamespaces) {
            Set<String> deleted = new HashSet<>(oldNamespaces);
            deleted.removeAll(newNamespaces);
            Set<String> added = new HashSet<>(newNamespaces);
            added.removeAll(oldNamespaces);
            deleted.addAll(added);

            return deleted;
        }

        private static Set<String> getNotChangedNamespaces(List<String> oldNamespaces, List<String> newNamespaces) {
            Set<String> intersection = new HashSet<>(oldNamespaces);
            intersection.retainAll(new HashSet<>(newNamespaces));

            return intersection;
        }

        private static String getName(KubernetesObject obj) {
            Objects.requireNonNull(obj.getMetadata());
            Objects.requireNonNull(obj.getMetadata().getName());

            return obj.getMetadata().getName();
        }

        private static List<V1Subject> getSubjects(V1ResourceGroup obj) {
            if (obj.getSpec() == null) {
                return new ArrayList<>();
            }
            return obj.getSpec().getSubjects();
        }

        private static boolean changeExistOnSubjects(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            return !getSubjects(oldObj).equals(getSubjects(newObj));
        }

        private WorkQueue<Request> queue;
        private RoleNameUtil roleNameUtil;

        public EventHandler(WorkQueue<Request> queue, RoleNameUtil roleNameUtil) {
            this.queue = queue;
            this.roleNameUtil = roleNameUtil;
        }

        @Override
        public void onAdd(V1ResourceGroup obj) {
            String groupName = getName(obj);
            Set<Request> requests = getNamespaces(obj).stream()
                    .map(e -> buildRequest(groupName, e))
                    .collect(Collectors.toSet());
            requests.forEach(this.queue::add);
        }

        @Override
        public void onUpdate(V1ResourceGroup oldObj, V1ResourceGroup newObj) {
            String groupName = getName(newObj);
            Set<Request> requests1 = getAddedOrDeletedNamespaces(getNamespaces(oldObj), getNamespaces(newObj)).stream()
                    .map(e -> buildRequest(groupName, e))
                    .collect(Collectors.toSet());
            if (changeExistOnSubjects(oldObj, newObj)) {
                Set<Request> requests2 = getNotChangedNamespaces(getNamespaces(oldObj), getNamespaces(newObj)).stream()
                        .map(e -> buildRequest(groupName, e))
                        .collect(Collectors.toSet());
                requests1.addAll(requests2);
            }
            requests1.forEach(this.queue::add);
        }

        @Override
        public void onDelete(V1ResourceGroup obj, boolean deletedFinalStateUnknown) {
        }

        private Request buildRequest(String groupName, String roleNamespace) {
            return new Request(roleNamespace, this.roleNameUtil.buildRoleBindingName(groupName));
        }

    }

    private WorkQueue<Request> queue;
    private RoleNameUtil roleNameUtil;

    public ResourceGroupWatch(WorkQueue<Request> queue, RoleNameUtil roleNameUtil) {
        this.queue = queue;
        this.roleNameUtil = roleNameUtil;
    }

    @Override
    public Class<V1ResourceGroup> getResourceClass() {
        return V1ResourceGroup.class;
    }

    @Override
    public ResourceEventHandler<V1ResourceGroup> getResourceEventHandler() {
        return new EventHandler(this.queue, this.roleNameUtil);
    }

    @Override
    public Duration getResyncPeriod() {
        return RESYNC_PERIOD;
    }

}
