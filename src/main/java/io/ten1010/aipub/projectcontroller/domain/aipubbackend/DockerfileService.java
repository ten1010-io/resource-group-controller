package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

/** Dockerfile 의 SoT 는 aipub-backend DB 라, project CR 이 사라져도 행이 남는다. 삭제 훅에서 이 서비스로 맞춰 준다. */
public interface DockerfileService {

  void deleteDockerfilesByProject(String projectName);

}
