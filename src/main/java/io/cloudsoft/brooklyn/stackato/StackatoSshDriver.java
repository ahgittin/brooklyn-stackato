package io.cloudsoft.brooklyn.stackato;

import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.basic.lifecycle.StartStopSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;

import com.google.common.base.Throwables;

public class StackatoSshDriver extends StartStopSshDriver {

    public static final Logger log = LoggerFactory.getLogger(StackatoSshDriver.class);
    
    StackatoNode node;
    
    public StackatoSshDriver(StackatoNode entity, SshMachineLocation machine) {
        super(entity, machine);
        this.node = entity;
    }

    @Override
    public boolean isRunning() {
        try {
            newScript("stackato-is-running").
                    failOnNonZeroResultCode().
                    body.append("stackato-admin status | grep RUNNING").
                    execute();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void stop() {
        newScript("stackato-stop").
            failOnNonZeroResultCode().
            body.append("stackato-admin stop").
            execute();
    }

    public void checkMachineHealthy() {
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        newScript("stackato-install-check-machine-can-do-root").
            failOnNonZeroResultCode().
            body.append("echo "+password+" | sudo -S whoami || exit 1").
            execute();
        newScript("stackato-install-check-machine-stackato-status").
            failOnNonZeroResultCode().
            body.append("stackato-admin status").
            execute();
    }

    private String getRequiredConfig(ConfigKey<String> key) {
        String v = node.getConfig(key);
        if (v==null) throw new IllegalStateException("Missing required configuration "+key+"; set on cluster or entity");
        return v;
    }
    
    public void customizeImage() {
        log.info("Customizing Stackato machine at "+getMachine()+" for "+node);
        String publicKey = (String)getMachine().getLocationProperty("sshPublicKeyData");
        if (publicKey==null) 
            throw new IllegalStateException("SSH keys required; no public key specified");
        getMachine().copyTo(new StringReader(publicKey), "/tmp/id.pub");
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        String internalIp = ((JcloudsSshMachineLocation)getMachine()).getSubnetHostname();
        if (internalIp==null) throw new IllegalStateException("Cannot resolve subnet hostname");
        String clusterName = getRequiredConfig(StackatoNode.STACKATO_CLUSTER_NAME);
        String clusterDomain = getRequiredConfig(StackatoNode.STACKATO_CLUSTER_DOMAIN);
        
        
        newScript("stackato-customizeImage").
            failOnNonZeroResultCode().
            body.append(
                    // needed if sudo access isn't enabled by default
                    "echo stackato | sudo -S echo sudo access granted",
//                    // setup passwordless sudo -- actually this is a bad idea on some OS's (ubuntu)
//                    // because the second sudo is disallowed once permissions are changed!
//                    "sudo chmod u+w /etc/sudoers && "+
//                    "sudo bash -c 'echo \"stackato ALL=(ALL) NOPASSWD: ALL\" >> /etc/sudoers' && "+
//                    "sudo chmod u-w /etc/sudoers || exit 1",
                    // add key for users root (already done) & stackato
                    "cat /tmp/id.pub >> ~stackato/.ssh/authorized_keys || exit 2",
                    // change stackato passwd
                    "echo \""+password+"\n"+password+"\" | sudo -S passwd stackato || exit 3",
                    // setup hosts file (TODO hosts file always points to local machine, is that okay?)
                    // 10.0.0.1 stackato-xyz1 stackato-xyz1.geopaas.org api.stackato-xyz1.geopaas.org >> /etc/hosts
                    "sudo bash -c 'echo "+internalIp+" "+clusterName+" "+
                            clusterName+"."+clusterDomain+" "+
                            "api."+clusterName+"."+clusterDomain+" "+
                        " >> /etc/hosts' || exit 4"
            ).execute();
    }
    
    @Override
    public void install() {
        checkMachineHealthy();
        log.info("Confirmed health for installaton of Stackato machine at "+getMachine()+" for "+node);
    }
    
    @Override
    public void customize() {
        node.becomeDesiredStackatoRole();
    }

    public void rebootAndWait() {
        log.info("Rebooting machine for "+node);
        String password = getRequiredConfig(StackatoNode.STACKATO_PASSWORD);
        newScript("reboot").
            failOnNonZeroResultCode().
            body.append("echo "+password+" | sudo -S reboot").
            execute();
        try {
            Thread.sleep(30*1000);
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
        log.debug("Rebooted machine for "+node);
    }
    
    @Override
    public void launch() {
        rebootAndWait();
    }

}
