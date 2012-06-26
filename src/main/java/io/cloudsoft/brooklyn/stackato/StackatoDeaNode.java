package io.cloudsoft.brooklyn.stackato;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.MutableMap;

public class StackatoDeaNode extends StackatoNode {

    public StackatoDeaNode(Entity owner) {
        this(new MutableMap(), owner);
    }
    public StackatoDeaNode(Map flags, Entity owner) {
        super(flags, owner);
        setConfig(STACKATO_NODE_ROLES, Arrays.asList("dea"));
    }
    
    public void blockUntilReadyToLaunch() {
        // DEA nodes must block until the master comes up
        DependentConfiguration.waitForTask(
                DependentConfiguration.attributeWhenReady(getStackatoDeployment(), StackatoDeployment.MASTER_UP), this);
    }

}
