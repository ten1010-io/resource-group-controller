package io.ten1010.coaster.groupcontroller.conversion;

import io.kubernetes.client.common.KubernetesObject;

public interface Converter<R extends KubernetesObject, HUB extends KubernetesObject> {

    HUB convertToHub(R resource);

    R convertFromHub(HUB hub);
}
