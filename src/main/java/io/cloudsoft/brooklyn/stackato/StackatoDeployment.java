package io.cloudsoft.brooklyn.stackato;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

public class StackatoDeployment extends AbstractEntity implements StackatoConfigKeys, Startable {

    public static final Logger log = LoggerFactory.getLogger(StackatoDeployment.class);
    
    public static final AttributeSensor<String> STACKATO_ENDPOINT = new BasicAttributeSensor<String>(String.class, "stackato.endpoint", "Hostname to use as endpoint (e.g. api.my1.example.com)");
    public static final AttributeSensor<String> MASTER_PUBLIC_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.hostname.canonical", "Public IP of master (supplied by cloud provider)");
    public static final AttributeSensor<String> MASTER_INTERNAL_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.ip.internal");
    
    private StackatoMasterNode master;
    
    @SetFromFlag("skipDeaCluster")
    private boolean skipDeaCluster;

    public StackatoDeployment(Entity owner) { this(new MutableMap(), owner); }
    public StackatoDeployment(Map flags, Entity owner) { 
        super(flags, owner); 
        addMaster();
        if (skipDeaCluster!=Boolean.TRUE) addDeaCluster();
    }
    
    public void addMaster() {
        if (master!=null) return;
        master = new StackatoMasterNode(this);
        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(master, MASTER_INTERNAL_IP));
    }

    public void addDeaCluster() {
        BasicConfigurableEntityFactory deaFactory = new BasicConfigurableEntityFactory(StackatoDeaNode.class);
        DynamicCluster deaCluster = new DynamicCluster(new MutableMap().
                add("factory", deaFactory).
                add("initialSize", 2), 
            this);
        
        log.info("created descriptor for "+this+", containing master "+master+" and deaCluster "+deaCluster);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        StartableMethods.start(this, locations);
        setAttribute(MASTER_PUBLIC_IP, master.getAttribute(StackatoMasterNode.ADDRESS));
        setAttribute(STACKATO_ENDPOINT, master.getAttribute(StackatoMasterNode.STACKATO_ENDPOINT));
        
        // and a welcome / next steps message
        String ip = getAttribute(StackatoDeployment.MASTER_PUBLIC_IP);
        String endpoint = getAttribute(StackatoDeployment.STACKATO_ENDPOINT);
        String name = getConfig(StackatoDeployment.STACKATO_CLUSTER_NAME);
        log.info("Stackato set up, running at "+ip+" listening "+"for endpoint "+endpoint+"\n"+
"*************************************************\n"+
"** You must now set up the following DNS records\n"+
"** for:\n"+
"**       "+getConfig(StackatoDeployment.STACKATO_CLUSTER_DOMAIN)+"\n"+
"\n"+
name+"   A     "+ip+"}\n"+
"*."+name+" CNAME "+name+"\n"+
"\n"+
"*************************************************");
        // TODO we can set up the DNS automatically eg at geoscaling
    }
    
    @Override
    public void stop() {
        StartableMethods.stop(this);
    }
    @Override
    public void restart() {
        StartableMethods.restart(this);
    }
    
}
