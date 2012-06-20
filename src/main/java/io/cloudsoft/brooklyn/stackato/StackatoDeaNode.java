package io.cloudsoft.brooklyn.stackato;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;

public class StackatoDeaNode extends StackatoNode {

    public StackatoDeaNode(Entity owner) {
        this(new MutableMap(), owner);
    }
    public StackatoDeaNode(Map flags, Entity owner) {
        super(flags, owner);
        setConfig(STACKATO_NODE_ROLES, Arrays.asList("dea"));
    }

}
