package io.ten1010.coaster.groupcontroller.controller.cluster.clusterrolebinding;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceGroupClusterRoleBindingName {

    public static boolean isResourceGroupClusterRoleBindingName(String bindingName) {
        return RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN.matcher(bindingName).matches();
    }

    public static ResourceGroupClusterRoleBindingName fromClusterRoleBindingName(String bindingName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN.matcher(bindingName);
        matcher.matches();
        return new ResourceGroupClusterRoleBindingName(matcher.group(1));
    }

    private static final String CLUSTER_ROLE_BINDING_NAME_PREFIX = "resource-group-controller.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_BINDING_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_BINDING_NAME_PREFIX + "(.*)");

    @Getter
    private String resourceGroupName;

    public ResourceGroupClusterRoleBindingName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public String getName() {
        return CLUSTER_ROLE_BINDING_NAME_PREFIX + this.resourceGroupName;
    }

}
