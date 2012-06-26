package io.cloudsoft.brooklyn.stackato;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;
import brooklyn.util.ShellUtils;
import brooklyn.util.flags.SetFromFlag;

public class StackatoDeployment extends AbstractEntity implements StackatoConfigKeys, Startable {

    public static final Logger log = LoggerFactory.getLogger(StackatoDeployment.class);

    @SetFromFlag("initialNumDeas")
    public static final BasicConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(DynamicCluster.INITIAL_SIZE, 2);
    
    public static final AttributeSensor<String> MASTER_PUBLIC_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.hostname.canonical", "Public IP of master (supplied by cloud provider)");
    public static final AttributeSensor<String> MASTER_INTERNAL_IP = new BasicAttributeSensor<String>(String.class, "stackato.master.ip.internal");
    public static final AttributeSensor<Boolean> MASTER_UP = new BasicAttributeSensor<Boolean>(Boolean.class, "stackato.master.up", "announces that the master is up");
    
    public static final AttributeSensor<String> STACKATO_ENDPOINT = new BasicAttributeSensor<String>(String.class, "stackato.endpoint", "Hostname to use as endpoint (e.g. api.my1.example.com)");
    
    public static final AttributeSensor<String> STACKATO_MGMT_CONSOLE_URL = new BasicAttributeSensor<String>(String.class, "stackato.mgmt.url", "URL for management console");
    static { RendererHints.register(STACKATO_MGMT_CONSOLE_URL, new RendererHints.NamedActionWithUrl("Open")); }
    
    private StackatoMasterNode master;
    
    private GeoscalingWebClient dnsClient;
    
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
        addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(master, MASTER_INTERNAL_IP, StackatoMasterNode.MASTER_UP));
    }

    public void addDeaCluster() {
        if (master==null)
            log.warn("DEA cluster being added to "+this+" but no master is configured; may not start unless sensors are wired in correclty");
        
        BasicConfigurableEntityFactory deaFactory = new BasicConfigurableEntityFactory(StackatoDeaNode.class);
        DynamicCluster deaCluster = new DynamicCluster(new MutableMap().
                add("factory", deaFactory).
                add("initialSize", getConfig(INITIAL_SIZE)), 
            this);
        
        log.info("created descriptor for "+this+", containing master "+master+" and deaCluster "+deaCluster);
    }
    
    // for now Geoscaling is the only one supported; this could be abstracted however, all we need is editSubdomainRecord
    public void useDnsClient(GeoscalingWebClient dnsClient) {
        this.dnsClient = dnsClient;
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        StartableMethods.start(this, locations);

        String ip = master.getAttribute(StackatoMasterNode.ADDRESS);
        String endpoint = master.getAttribute(StackatoMasterNode.STACKATO_ENDPOINT);
        String cluster = getConfig(StackatoDeployment.STACKATO_CLUSTER_NAME);
        String url = "https://"+master.getAttribute(StackatoMasterNode.STACKATO_ENDPOINT);
        String domain = getConfig(StackatoDeployment.STACKATO_CLUSTER_DOMAIN);

        log.info("Setting up DNS and accounts for Stackato at "+endpoint);
        boolean setupStackatoDns = setupStackatoDns(ip, endpoint, cluster, domain);
        boolean setupLicensedAdminUser = setupLicensedAdminUser(endpoint);        
        boolean setupLocalCliAccounts = setupStackatoDns && setupLocalCliAccounts(endpoint);
        
        setAttribute(MASTER_PUBLIC_IP, ip);
        setAttribute(STACKATO_ENDPOINT, endpoint);
        setAttribute(STACKATO_MGMT_CONSOLE_URL, url);
        
        // and a welcome / next steps message
        log.info("Stackato setup complete, running at "+ip+" listening on endpoint "+endpoint+"\n"+
    "**************************************************************************\n"+
(setupStackatoDns ? "" :               
    "* Set up following DNS records\n"+
    "* for "+domain+":\n"+
    "*        "+cluster+"   A     "+ip+"\n"+
    "*        *."+cluster+" CNAME "+cluster+"."+domain+"\n"+
    "**************************************************************************\n") +
(setupLicensedAdminUser ? "" :
    "* Login (creating the admin user)\n"+
    "* at:\n" + 
    "*        "+url+"\n"+
    "**************************************************************************\n") +
(setupLocalCliAccounts ? "" : 
    "* Set up local credentials\n"+
    "* with:\n"+
    "*        stackato target "+endpoint+"\n"+
    "*        stackato login\n"+
    "*        vmc target "+endpoint+"\n"+
    "*        vmc login\n"+
    "**************************************************************************\n") +
(setupStackatoDns && setupLicensedAdminUser && setupLocalCliAccounts ? 
    // none of the above apply
    "* Stackato ready (no manual configuration needed)\n"+
    "* at:\n"+
    "*        "+endpoint+"\n"+ 
    "**************************************************************************"
    : ""));
    }
    
    private boolean setupLocalCliAccounts(String endpoint) {
        boolean localLoginDone = false;
        try {
            String username = master.getConfig(STACKATO_ADMIN_USER_EMAIL);
            String password = master.getConfig(STACKATO_PASSWORD);
            // need to target https URL also (default is http)
            ShellUtils.exec("vmc target https://"+endpoint+"/", log, this);
            ShellUtils.exec("echo "+password+" | vmc login "+username, log, this);
            try {
                ShellUtils.exec("vmc target "+endpoint, log, this);
                ShellUtils.exec("echo "+password+" | vmc login "+username, log, this);
            } catch (Exception e) {
                log.info("Command-line vmc access to "+endpoint+" using http could not be configured locally (https worked; likely it is required on server)");
            }
            try {
                ShellUtils.exec("stackato target https://"+endpoint+"/", log, this);
                ShellUtils.exec("echo "+password+" | stackato login "+username, log, this);                
                try {
                    ShellUtils.exec("stackato target "+endpoint, log, this);
                    ShellUtils.exec("echo "+password+" | stackato login "+username, log, this);                
                } catch (Exception e) {
                    log.info("Command-line Stackato access to "+endpoint+" using http could not be configured locally (https worked; likely it is required on server)");
                }
            } catch (Exception e) {
                log.warn("Command-line Stackato access to "+endpoint+" could not be configured locally; ensure `stackato` installed");
            }
            localLoginDone = true;
        } catch (Exception e) {
            // will throw if any problems
            log.warn("Brooklyn Stackato access to "+endpoint+" (using vmc) could not be configured locally; ensure `vmc` installed");
            log.debug("Reason for failed local configuration: "+e, e);            
        }
        return localLoginDone;
    }
    
    private boolean setupStackatoDns(String ip, String endpoint, String cluster, String domain) {
        boolean dnsDone = false;
        try {
            if (dnsClient!=null) {
                Domain gd = dnsClient.getPrimaryDomain(domain);
                if (gd!=null) {
                    gd.editRecord(cluster, "A", ip);
                    gd.editRecord("*."+cluster, "CNAME", cluster+"."+domain);
                    dnsDone = true;
                    log.debug("set up DNS for "+cluster+"."+domain+" using "+dnsClient+", for "+this);
                } else {
                    log.debug("no domain "+domain+" found at "+dnsClient+"; not setting up DNS for "+this);
                }
            }
        } catch (Exception e) {
            //won't normally throw
            log.warn("Failed to set up DNS for "+endpoint+": "+e, e);
        }
        return dnsDone;
    }
    
    private boolean setupLicensedAdminUser(String endpoint) {
        boolean userDone = false;
        try {
            master.addLicensedUser();
            userDone = true;
        } catch (Exception e) {
            // will throw if any problems
            log.warn("Stackato user at "+endpoint+" could not be automatically created (consult logs for more info)");
            log.debug("Reason for failed user creation: "+e, e);
        }
        return userDone;
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
