package io.ten1010.aipub.projectcontroller.controller.cr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.DockerfileService;
import io.ten1010.aipub.projectcontroller.domain.k8s.FinalizersConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** project 삭제 훅의 Dockerfile 정리. 정리는 best-effort 라 실패해도 삭제 흐름이 멈추지 않는 것까지 함께 고정한다. */
class ProjectReconcilerDockerfileCleanupTest {

  private static final String PROJECT_NAME = "proj-a";

  private Cache<V1alpha1Project> projectCache;
  private DockerfileService dockerfileService;
  private ProjectReconciler reconciler;

  @BeforeEach
  void setUp() {
    this.projectCache = new Cache<>();
    this.dockerfileService = mock(DockerfileService.class);

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    bindInformer(factory, V1alpha1Project.class, this.projectCache);
    bindInformer(factory, V1Namespace.class, new Cache<>());
    bindInformer(factory, V1ResourceQuota.class, new Cache<>());
    bindInformer(factory, V1alpha1AipubUser.class, new Cache<>());
    bindInformer(factory, V1alpha1NodeGroup.class, new Cache<>());
    bindInformer(factory, V1alpha1ImageHub.class, new Cache<>());
    bindInformer(factory, V1alpha1ResourceSet.class, new Cache<>());
    bindInformer(factory, V1Node.class, new Cache<>());
    bindInformer(factory, V1PersistentVolume.class, new Cache<>());

    K8sApiProvider k8sApiProvider = mock(K8sApiProvider.class);
    when(k8sApiProvider.getProjectApi()).thenReturn(mock(GenericKubernetesApi.class));
    when(k8sApiProvider.getApiClient()).thenReturn(new ApiClient());

    this.reconciler = new ProjectReconciler(
        mock(ReconciliationService.class),
        factory,
        k8sApiProvider,
        List.of(),
        this.dockerfileService);
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesObject> void bindInformer(
      SharedInformerFactory factory, Class<T> type, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(type)).thenReturn(informer);
  }

  private V1alpha1Project project(boolean terminating) {
    List<String> finalizers = new ArrayList<>();
    finalizers.add(FinalizersConstants.PROJECT_FINALIZER);
    V1ObjectMeta metadata = new V1ObjectMeta().name(PROJECT_NAME).finalizers(finalizers);
    if (terminating) {
      metadata.deletionTimestamp(OffsetDateTime.now());
    }
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(metadata);
    return project;
  }

  @Test
  void terminatingProject_deletesItsDockerfiles() throws ApiException {
    this.projectCache.add(project(true));

    this.reconciler.reconcileInternal(new Request(PROJECT_NAME));

    verify(this.dockerfileService).deleteDockerfilesByProject(PROJECT_NAME);
  }

  @Test
  void cleanupFailure_doesNotBreakProjectDeletion() throws ApiException {
    this.projectCache.add(project(true));
    doThrow(new RuntimeException("backend down"))
        .when(this.dockerfileService).deleteDockerfilesByProject(any());

    // 예외가 밖으로 새면 같은 루프의 finalizer 제거까지 함께 중단된다
    this.reconciler.reconcileInternal(new Request(PROJECT_NAME));

    verify(this.dockerfileService).deleteDockerfilesByProject(PROJECT_NAME);
  }

  @Test
  void nonTerminatingProject_leavesDockerfilesAlone() throws ApiException {
    this.projectCache.add(project(false));

    this.reconciler.reconcileInternal(new Request(PROJECT_NAME));

    verifyNoInteractions(this.dockerfileService);
  }
}
