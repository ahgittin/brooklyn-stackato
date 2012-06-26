package io.cloudsoft.brooklyn.stackato.example;

import org.slf4j.Logger
import org.slf4j.LoggerFactory


import io.cloudsoft.brooklyn.stackato.StackatoDeployment;
import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.LocationRegistry;

public class StackatoRolloutToCloud extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(StackatoRolloutToCloud.class);
    
    StackatoDeployment stackato = new StackatoDeployment(this,
        cluster: "brooklyn-stackato-"+id,
        domain: "geopaas.org",
        admin: "me@fun.net",
        password: "funfunfun",
        initialNumDeas: 4,
        minRam: 4096
    );

    // optionally use a DNS service to configure our domain name
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();
    { 
        stackato.useDnsClient(new GeoscalingWebClient(
            config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
            config.getFirst("brooklyn.geoscaling.password", failIfNone:true) )); 
    }

    public static void main(String[] args) {
        // choose where you want to deploy
        String location = "jclouds:hpcloud-compute";
        
        // start it, and the Brooklyn mgmt console
        StackatoRolloutToCloud app = new StackatoRolloutToCloud();
        BrooklynLauncher.manage(app, 8081);
        app.start([new LocationRegistry().resolve(location)]);
    }
    
}
