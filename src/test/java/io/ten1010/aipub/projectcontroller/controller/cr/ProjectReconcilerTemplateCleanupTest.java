package io.ten1010.aipub.projectcontroller.controller.cr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.TemplateService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectReconcilerTemplateCleanupTest {

  private static final String PROJECT_NAME = "proj-a";

  private Cache<V1alpha1Project> projectCache;
  private Cache<V1Namespace> namespaceCache;
  private TemplateService templateService;
  private ProjectReconciler reconciler;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.projectCache = new Cache<>();
    this.namespaceCache = new Cache<>();
    this.templateService = mock(TemplateService.class);

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    bindInformer(factory, V1alpha1Project.class, this.projectCache);
    bindInformer(factory, V1Namespace.class, this.namespaceCache);
    bindInformer(factory, V1ResourceQuota.class, new Cache<>());
    bindInformer(factory, V1alpha1AipubUser.class, new Cache<>());
    bindInformer(factory, V1alpha1NodeGroup.class, new Cache<>());
    bindInformer(factory, V1alpha1ImageHub.class, new Cache<>());
    bindInformer(factory, V1alpha1ResourceSet.class, new Cache<>());
    bindInformer(factory, V1Node.class, new Cache<>());
    bindInformer(factory, V1PersistentVolume.class, new Cache<>());

    K8sApiProvider k8sApiProvider = mock(K8sApiProvider.class);
    when(k8sApiProvider.getProjectApi())
        .thenReturn(mock(GenericKubernetesApi.class));
    when(k8sApiProvider.getApiClient()).thenReturn(new ApiClient());

    this.reconciler = new ProjectReconciler(
        mock(ReconciliationService.class),
        factory,
        k8sApiProvider,
        List.of(),
        this.templateService);
  }

  @SuppressWarnings("unchecked")
  private <T extends io.kubernetes.client.common.KubernetesObject> void bindInformer(
      SharedInformerFactory factory, Class<T> type, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(type)).thenReturn(informer);
  }

  private V1alpha1Project terminatingProject() {
    List<String> finalizers = new ArrayList<>();
    finalizers.add(FinalizersConstants.PROJECT_FINALIZER);
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta()
        .name(PROJECT_NAME)
        .deletionTimestamp(OffsetDateTime.now())
        .finalizers(finalizers));
    return project;
  }

  @Test
  @DisplayName("프로젝트가 삭제되면 그 프로젝트의 템플릿도 함께 정리한다")
  void givenTerminatingProject_whenReconcile_thenDeletesItsTemplates() throws ApiException {
    // given
    this.projectCache.add(terminatingProject());

    // when
    this.reconciler.reconcileInternal(new Request(PROJECT_NAME));

    // then
    verify(this.templateService).deleteTemplatesByProject(PROJECT_NAME);
  }

  @Test
  @DisplayName("템플릿 정리에 실패해도 프로젝트 삭제는 멈추지 않는다")
  void givenTemplateCleanupFails_whenReconcile_thenProjectDeletionProceeds() throws ApiException {
    // given
    this.projectCache.add(terminatingProject());
    doThrow(new RuntimeException("backend down"))
        .when(this.templateService).deleteTemplatesByProject(any());

    // when, then — 예외가 밖으로 새지 않는다
    this.reconciler.reconcileInternal(new Request(PROJECT_NAME));

    verify(this.templateService).deleteTemplatesByProject(PROJECT_NAME);
  }

  @Test
  @DisplayName("삭제 중이 아닌 프로젝트는 템플릿을 건드리지 않는다")
  void givenLiveProject_whenReconcile_thenKeepsTemplates() {
    // given
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta().name(PROJECT_NAME));
    this.projectCache.add(project);

    // when
    try {
      this.reconciler.reconcileInternal(new Request(PROJECT_NAME));
    } catch (Exception ignored) {
      // 살아있는 프로젝트 경로는 이 테스트의 관심사가 아니다
    }

    // then
    verifyNoInteractions(this.templateService);
  }
}
