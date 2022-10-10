package io.ten1010.coaster.groupcontroller.model;

import java.util.Objects;

public class V1ResourceGroupStatus {

    public V1ResourceGroupStatus() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }

    @Override
    public String toString() {
        return "V1ResourceGroup{" +
                '}';
    }

}
