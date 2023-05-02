package io.ten1010.coaster.groupcontroller.controller.node;

import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.event.EventType;
import io.kubernetes.client.extended.event.legacy.EventRecorder;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeBuilder;
import io.kubernetes.client.openapi.models.V1Taint;
import io.kubernetes.client.openapi.models.V1TaintBuilder;
import io.ten1010.coaster.groupcontroller.controller.KubernetesApiReconcileExceptionHandlingTemplate;
import io.ten1010.coaster.groupcontroller.controller.ReconcilerUtil;
import io.ten1010.coaster.groupcontroller.core.*;
import io.ten1010.coaster.groupcontroller.model.V1ResourceGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class NodeReconciler implements Reconciler {

    public static final Duration INVALID_STATE_REQUEUE_DURATION = Duration.ofSeconds(30);
    public static final Duration API_CONFLICT_REQUEUE_DURATION = Duration.ofSeconds(5);
    public static final Duration API_FAIL_REQUEUE_DURATION = Duration.ofSeconds(60);

    private static final String MSG_NODE_BELONGS_TO_MULTIPLE_GROUPS = "Node [%s] belongs to multiple groups";

    private static Map<String, String> getLabels(V1Node node) {
        Objects.requireNonNull(node.getMetadata());
        Objects.requireNonNull(node.getMetadata());
        Map<String, String> labels = node.getMetadata().getLabels();
        return labels == null ? new HashMap<>() : labels;
    }

    private static List<V1Taint> getTaints(V1Node node) {
        Objects.requireNonNull(node.getSpec());
        List<V1Taint> taints = node.getSpec().getTaints();
        return taints == null ? new ArrayList<>() : taints;
    }

    /**
     * Validate whether given taint has ResourceGroup{@link V1ResourceGroup} key which, is constant string.
     * <p>
     *     See constant string in TaintConstants{@link TaintConstants}.
     * </p>
     * @param taint
     * @return
     */
    private static boolean isResourceGroupExclusiveTaint(V1Taint taint) {
        if (taint.getKey() == null) {
            return false;
        }

        return taint.getKey().equals(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
    }

    /**
     * Build exclusive taints for ResourceGroup{@link V1ResourceGroup} which are no schedule and no execute effect, with value as constant string and key as resource group name.
     * @param group
     * @return list of taint
     */
    private static List<V1Taint> buildResourceGroupExclusiveTaints(V1ResourceGroup group) {
        V1TaintBuilder baseBuilder = new V1TaintBuilder()
                .withKey(TaintConstants.KEY_RESOURCE_GROUP_EXCLUSIVE)
                .withValue(ReconcilerUtil.getName(group));
        V1Taint noSchedule = baseBuilder
                .withEffect(TaintConstants.EFFECT_NO_SCHEDULE)
                .build();
        V1Taint noExecute = baseBuilder
                .withEffect(TaintConstants.EFFECT_NO_EXECUTE)
                .build();

        return List.of(noSchedule, noExecute);
    }

    private KubernetesApiReconcileExceptionHandlingTemplate template;
    private Indexer<V1Node> nodeIndexer;
    private Indexer<V1ResourceGroup> groupIndexer;
    private CoreV1Api coreV1Api;

    private EventRecorder eventRecorder;

    public NodeReconciler(Indexer<V1Node> nodeIndexer, Indexer<V1ResourceGroup> groupIndexer, CoreV1Api coreV1Api, EventRecorder eventRecorder) {
        this.template = new KubernetesApiReconcileExceptionHandlingTemplate(API_CONFLICT_REQUEUE_DURATION, API_FAIL_REQUEUE_DURATION);
        this.nodeIndexer = nodeIndexer;
        this.groupIndexer = groupIndexer;
        this.coreV1Api = coreV1Api;
        this.eventRecorder = eventRecorder;
    }

    /**
     * Reconcile the desired resource state after comparing it with the actual resource state.
     * If given node via request doesn't match the labels and taint of resource group, then do nothing.
     *
     * @param request
     * @return
     */
    @Override
    public Result reconcile(Request request) {
        return this.template.execute(() -> {
            String nodeKey = KeyUtil.buildKey(request.getName());
            V1Node node = this.nodeIndexer.getByKey(nodeKey);
            if (node == null) {
                log.debug("Node [{}] not founded while reconciling", nodeKey);
                return new Result(false);
            }
            log.debug("Node [{}] founded while reconciling\n{}", nodeKey, node.toString());

            List<V1ResourceGroup> groups = this.groupIndexer.byIndex(IndexNameConstants.BY_NODE_NAME_TO_GROUP_OBJECT, ReconcilerUtil.getName(node));
            if (groups.size() > 1) {
                for (V1ResourceGroup g : groups) {
                    this.eventRecorder.event(
                            g,
                            EventType.Warning,
                            EventConstants.REASON_NODE_CONFLICT, MSG_NODE_BELONGS_TO_MULTIPLE_GROUPS,
                            ReconcilerUtil.getName(node));
                }
                return new Result(true, INVALID_STATE_REQUEUE_DURATION);
            }

            Map<String, String> labels = getLabels(node);
            Map<String, String> reconcileLabels = reconcileLabels(node, groups.size() == 1 ? groups.get(0) : null);
            List<V1Taint> taints = getTaints(node);
            List<V1Taint> reconciledTaints = reconcileTaints(node, groups.size() == 1 ? groups.get(0) : null);
            if (labels.equals(reconcileLabels) && taints.equals(reconciledTaints)) {
                return new Result(false);
            }
            updateNode(node, reconcileLabels, reconciledTaints);
            return new Result(false);
        }, request);
    }

    /**
     * Get all labels from given node, and remove labels specified in resource group.
     * Then, put labels on node with resource group exclusive and node own.
     * @param node
     * @param group
     * @return
     */
    private Map<String, String> reconcileLabels(V1Node node, @Nullable V1ResourceGroup group) {
        Map<String, String> labels = getLabels(node);
        labels.remove(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE);
        if (group == null) {
            return labels;
        }
        labels.put(LabelConstants.KEY_RESOURCE_GROUP_EXCLUSIVE, ReconcilerUtil.getName(group));
        return labels;
    }

    /**
     * Get all taints from given node, and remove labels specified in resource group.
     * Then, put taints on node with resource group exclusive's and node own.
     * @param node
     * @param group
     * @return
     */
    private List<V1Taint> reconcileTaints(V1Node node, @Nullable V1ResourceGroup group) {
        List<V1Taint> taints = getTaints(node).stream()
                .filter(e -> !isResourceGroupExclusiveTaint(e))
                .collect(Collectors.toList());
        if (group == null) {
            return taints;
        }
        taints.addAll(buildResourceGroupExclusiveTaints(group));
        return taints;
    }

    /**
     * Create new node with desired spec(labels, taints), then replace existing node through CoreApi.
     * @param target
     * @param labels
     * @param taints
     * @throws ApiException
     */
    private void updateNode(V1Node target, Map<String, String> labels, List<V1Taint> taints) throws ApiException {
        V1Node updated = new V1NodeBuilder(target)
                .editMetadata()
                .withLabels(labels)
                .endMetadata()
                .editSpec()
                .withTaints(taints)
                .endSpec()
                .build();
        this.coreV1Api.replaceNode(
                ReconcilerUtil.getName(target),
                updated,
                null,
                null,
                null,
                null);
    }

}
