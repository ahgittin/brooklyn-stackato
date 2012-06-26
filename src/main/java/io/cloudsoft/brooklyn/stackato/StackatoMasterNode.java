package io.cloudsoft.brooklyn.stackato;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;
import brooklyn.util.task.BasicTask;

public class StackatoMasterNode extends StackatoNode {

    public static final AttributeSensor<String> STACKATO_ENDPOINT = StackatoDeployment.STACKATO_ENDPOINT;
    public static final AttributeSensor<String> MASTER_INTERNAL_IP = StackatoDeployment.MASTER_INTERNAL_IP;
    public static final AttributeSensor<Boolean> MASTER_UP = StackatoDeployment.MASTER_UP;
    
    public StackatoMasterNode(Entity owner) {
        super(owner);
        
        //stackato-admin become controller stager router -m 10.2.17.233 -e api.stackato-xyz1.geopaas.org 
        //   -o stackato-xyz1.geopaas.org -n stackato-xyz1
        setConfig(STACKATO_NODE_ROLES, Arrays.asList("controller", "stager", "router"));
        addOptionForBecomeIfNotPresent("-o", new BasicTask(new Callable() { public Object call() { 
            return getConfig(STACKATO_CLUSTER_NAME)+"."+getConfig(STACKATO_CLUSTER_DOMAIN); 
        } }));
        addOptionForBecomeIfNotPresent("-n", new BasicTask(new Callable() { public Object call() { 
            return getConfig(STACKATO_CLUSTER_NAME); 
        } }));
    }

    public void becomeDesiredStackatoRole() {
        super.becomeDesiredStackatoRole();
    }
    
    public void onMachineReady() {
        setAttribute(MASTER_INTERNAL_IP, ((JcloudsSshMachineLocation)getDriver().getMachine()).getSubnetHostname());
        setAttribute(STACKATO_ENDPOINT, getApiEndpoint());
    }

    public void addLicensedUser() {
        getDriver().createAdminUser(
                getDriver().getRequiredConfig(StackatoNode.STACKATO_ADMIN_USER_EMAIL), 
                getDriver().getRequiredConfig(StackatoNode.STACKATO_PASSWORD));
        // happens automatically by the above only if unix password is unchanged ("stackato")
        // which it isn't so do it manually:
        getDriver().createLicenseFile();
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        setAttribute(MASTER_UP, true); 
    }
    
}
