package io.cloudsoft.brooklyn.stackato.example;

import org.jclouds.compute.ComputeService
import org.jclouds.compute.domain.NodeMetadata;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Predicate;

import io.cloudsoft.brooklyn.stackato.StackatoDeployment;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.LocationRegistry;

public class StackatoRolloutToCloud extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(StackatoRolloutToCloud.class);
    
    StackatoDeployment stackato = new StackatoDeployment(this,
        password: "stackato",
        clusterName: "brooklyn-stackato-"+id,
        domain: "geopaas.org"
    );

    public static void main(String[] args) {
        String location = 
            "jclouds:hpcloud-compute";
//            "jclouds:aws-ec2";
        
        // start it, and the Brooklyn mgmt console
        StackatoRolloutToCloud app = new StackatoRolloutToCloud();
        BrooklynLauncher.manage(app, 808);
        app.start([new LocationRegistry().resolve(location)]);
        
        // display info
        Entities.dump();
    }
    
}
