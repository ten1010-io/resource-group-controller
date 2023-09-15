package io.ten1010.coaster.groupcontroller.conversion;

import io.ten1010.coaster.groupcontroller.model.V1Beta2ResourceGroup;

public class ResourceGroupV1Beta2Converter implements Converter<V1Beta2ResourceGroup, V1Beta2ResourceGroup> {

    @Override
    public V1Beta2ResourceGroup convertToHub(V1Beta2ResourceGroup from) {
        return from;
    }

    @Override
    public V1Beta2ResourceGroup convertFromHub(V1Beta2ResourceGroup from) {
        return from;
    }
}
