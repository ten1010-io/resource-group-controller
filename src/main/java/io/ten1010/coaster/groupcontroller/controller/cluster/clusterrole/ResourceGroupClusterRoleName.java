package io.ten1010.coaster.groupcontroller.controller.cluster.clusterrole;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourceGroupClusterRoleName {

    public static boolean isResourceGroupClusterRoleName(String roleName) {
        return RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN.matcher(roleName).matches();
    }

    public static ResourceGroupClusterRoleName fromClusterRoleName(String roleName) {
        Matcher matcher = RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN.matcher(roleName);
        matcher.matches();
        return new ResourceGroupClusterRoleName(matcher.group(1));
    }

    private static final String CLUSTER_ROLE_NAME_PREFIX = "resource-group-controller.resource-group.ten1010.io:";
    private static final Pattern RESOURCE_GROUP_CLUSTER_ROLE_NAME_PATTERN = Pattern.compile(CLUSTER_ROLE_NAME_PREFIX + "(.*)");

    @Getter
    private String resourceGroupName;

    public ResourceGroupClusterRoleName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public String getName() {
        return CLUSTER_ROLE_NAME_PREFIX + this.resourceGroupName;
    }

}
